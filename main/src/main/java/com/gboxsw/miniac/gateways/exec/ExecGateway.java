package com.gboxsw.miniac.gateways.exec;

import java.util.*;
import java.util.logging.*;
import java.util.regex.Pattern;

import com.gboxsw.miniac.*;

import com.gboxsw.miniac.gateways.exec.InternalExecutionManager.*;

/**
 * Gateway that provides message-based asynchronous execution of commands. In
 * order to execute a command, the command must be published in a specific
 * topic. The structure of topics provided by the gateway is as follows:
 * 
 * [id of execution queue]/[id of command]
 * 
 * It is possible to send multiple commands with the same id. However, the their
 * results are indistinguishable. Note that within the execution queue, all
 * commands are executed in a serial order. The result of execution is send back
 * to the same topic. In order to define execution timeout for command, append
 * timeout to topic.
 * 
 * Example:
 * 
 * system/cpu/10 - the command will be executed in the execution queue "system",
 * identifier of the command is "cpu", the execution timeout is 10 seconds
 * (waiting time is not considered), the result will be published in topic
 * "system/cpu".
 * 
 * All published command must be prefixed with identifier of a registered
 * command executor. For instance, the command "echo 1" to executor with id
 * "cmd" is submitted as "@cmd echo 1"
 * 
 * Note that initially no command executors are registered.
 */
public class ExecGateway extends Gateway {

	/**
	 * Default (pre-defined) name of the gateway.
	 */
	public static final String DEFAULT_ID = "exec";

	/**
	 * Logger.
	 */
	private static final Logger logger = Logger.getLogger(ExecGateway.class.getName());

	/**
	 * Pattern defining valid identifier of a command executor.
	 */
	private static final Pattern idPattern = Pattern.compile("^[a-zA-Z][a-zA-Z0-9]*$");

	/**
	 * List of registered command executors.
	 */
	private final Map<String, CommandExecutor> commandExecutors = new HashMap<>();

	/**
	 * The execution manager that manages and realizes asynchronous execution of
	 * tasks/commands.
	 */
	private InternalExecutionManager executionManager;

	/**
	 * Constructs the gateway.
	 */
	public ExecGateway() {

	}

	/**
	 * Registers a command executor.
	 * 
	 * @param id
	 *            the identifier of the command executor.
	 * @param commandExecutor
	 *            the command executor.
	 */
	public void registerCommandExecutor(String id, CommandExecutor commandExecutor) {
		if ((id == null) || id.trim().isEmpty()) {
			throw new NullPointerException("The identifier of command executor cannot be null or an empty string.");
		}

		if (commandExecutor == null) {
			throw new NullPointerException("The command executor cannot be null.");
		}

		id = id.trim();
		if (!idPattern.matcher(id).matches()) {
			throw new IllegalArgumentException("Invalid identifier of command executor.");
		}

		synchronized (commandExecutors) {
			if (isRunning()) {
				throw new IllegalStateException("The gateway is started. No command executor can be registered.");
			}

			if (commandExecutors.containsKey(id)) {
				if (commandExecutors.get(id) == commandExecutor) {
					return;
				}

				throw new IllegalStateException("Command executor with identifier " + id + " is already registered.");
			}

			commandExecutors.put(id, commandExecutor);
		}
	}

	@Override
	protected void onStart(Map<String, Bundle> bundles) {
		executionManager = new InternalExecutionManager(getApplication().getExecutorService(), new ExecutionListener() {
			@Override
			public void taskCompleted(Task command, byte[] result) {
				handleReceivedMessage(new Message(command.id, result));
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

		// analyze topic hierarchy
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

		// reply topic
		String replyTopic = queueId + "/" + commandId;

		// parse command, the expected format is "@commandExecutor localCommand"
		String command = message.getContent().trim();
		String executorId = "";
		String localCommand = "";
		if (command.startsWith("@")) {
			int sepIdx = command.indexOf(' ');
			if (sepIdx >= 0) {
				executorId = command.substring(1, sepIdx).trim();
				localCommand = command.substring(sepIdx + 1).trim();
			}
		}

		// check executor
		CommandExecutor commandExecutor;
		synchronized (commandExecutors) {
			commandExecutor = commandExecutors.get(executorId);
		}

		if (commandExecutor == null) {
			logger.log(Level.SEVERE, "Unknown or undefined command executor in the received command:" + command);
			handleReceivedMessage(new Message(replyTopic, (byte[]) null));
		}

		// submit task to execute by the execution manager
		Task task = new Task(localCommand, commandExecutor, replyTopic, timeout > 0 ? timeout * 1000 : -1);
		executionManager.execute(queueId, task);
	}

	@Override
	protected void onSaveState(Map<String, Bundle> outBundles) {
		// nothing to do - the gateway is state-less.
	}

	@Override
	protected void onStop() {
		// cmdExecutor.shutdown(false);
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
}
