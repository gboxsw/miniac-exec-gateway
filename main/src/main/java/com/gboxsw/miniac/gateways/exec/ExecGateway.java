package com.gboxsw.miniac.gateways.exec;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.gboxsw.miniac.Application;
import com.gboxsw.miniac.Bundle;
import com.gboxsw.miniac.Gateway;
import com.gboxsw.miniac.Message;

/**
 * Gateway that provides message-based asynchronous execution of system
 * commands. Command to execute can be published to any topic. The output of
 * command is send back to the same topic. The heading part of the topic name is
 * considered as identifier of an execution queue. Commands in the same
 * execution queue are executed in serial order.
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
		Command cmd = new Command(message.getContent(), message.getTopic());
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
