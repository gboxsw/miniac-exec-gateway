package com.gboxsw.miniac.gateways.exec;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.gboxsw.miniac.*;
import com.gboxsw.miniac.gateways.exec.executor.*;

/**
 * Gateway that provides message-based asynchronous execution of system
 * commands. Command to execute has to be published in a specific topic. The
 * structure of topics is as follows: [id of execution queue]/[id of command]
 * 
 * It is possible to send multiple commands with the same id. However, the their
 * results are indistinguishable. Note that within an execution queue, all
 * commands are executed in serial order. The result of execution is send back
 * to the same topic. In order to define execution timeout for command, append
 * timeout to topic.
 * 
 * Example:
 * 
 * system/cpu/10 - the command will be executed in the execution queue "system",
 * identifier of the command is "cpu", the execution timeout is 10 seconds
 * (waiting time is not considered), the result will be published in topic
 * "system/cpu".
 */
public class ExecGateway extends Gateway {

	/**
	 * Logger.
	 */
	private static final Logger logger = Logger.getLogger(ExecGateway.class.getName());

	/**
	 * The executor of system commands.
	 */
	private CommandExecutor cmdExecutor;

	@Override
	protected void onStart(Map<String, Bundle> bundles) {
		cmdExecutor = new CommandExecutor();
		cmdExecutor.setExecutionListener(new ExecutionListener() {
			@Override
			public void commandCompleted(Command command, ExecutionResult result) {
				handleCommandCompletion(command, result);
			}
		});
	}

	@Override
	protected void onAddTopicFilter(String topicFilter) {
		// nothing to do - the gateway don't use the information about currently
		// active topic filters.
	}

	@Override
	protected void onRemoveTopicFilter(String topicFilter) {
		// nothing to do - the gateway don't use the information about currently
		// active topic filters.
	}

	@Override
	protected void onPublish(Message message) {
		logger.log(Level.FINE, "Received message with command: " + message.getContent());

		String[] topicHierarchy = Application.parseTopicHierarchy(message.getTopic());
		String queueId = topicHierarchy[0];
		String commandId = topicHierarchy[1];
		long timeout = -1;
		if (topicHierarchy.length >= 3) {
			try {
				timeout = Long.parseLong(topicHierarchy[2]);
			} catch (NumberFormatException e) {
				timeout = -1;
			}
		}

		Command cmd;
		if (timeout > 0) {
			cmd = new Command(message.getContent(), queueId + "/" + commandId, timeout * 1000);
		} else {
			cmd = new Command(message.getContent(), queueId + "/" + commandId);
		}

		cmdExecutor.execute(topicHierarchy[0], cmd);
	}

	@Override
	protected void onSaveState(Map<String, Bundle> outBundles) {
		// nothing to do - the gateway is state-less.
	}

	@Override
	protected void onStop() {
		cmdExecutor.shutdown(false);
	}

	@Override
	protected boolean isValidTopicName(String topicName) {
		if ((topicName == null) || topicName.isEmpty()) {
			return false;
		}

		String[] topicHierarchy = Application.parseTopicHierarchy(topicName);

		// topic hierarchy must have at least 2 level (execution group and
		// command id)
		if (topicHierarchy.length < 2) {
			return false;
		}

		// if the hierarchy path has length 2, there are no extras
		if (topicHierarchy.length == 2) {
			return true;
		}

		// at most 3 levels in the hierarchy (path) are expected
		if (topicHierarchy.length > 3) {
			return false;
		}

		// check whether 3-rd level is formed by timeout
		try {
			int timeout = Integer.parseInt(topicHierarchy[2]);
			if (timeout <= 0) {
				return false;
			}
		} catch (NumberFormatException e) {
			return false;
		}

		return true;
	}

	/**
	 * Handles completion of a command. The method is not invoked in the main
	 * application thread.
	 * 
	 * @param command
	 *            the completed command.
	 * @param result
	 *            the execution result.
	 */
	private void handleCommandCompletion(Command command, ExecutionResult result) {
		handleReceivedMessage(new Message(command.getId(), result.isSuccessful() ? result.getStdout() : null));
	}
}
