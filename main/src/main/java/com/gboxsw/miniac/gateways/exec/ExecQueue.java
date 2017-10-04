package com.gboxsw.miniac.gateways.exec;

import java.util.*;
import com.gboxsw.miniac.*;

/**
 * Execution queue of an {@link ExecGateway}. It is a helper class for messaging
 * with {@link ExecGateway} instances.
 */
public class ExecQueue {

	/**
	 * Interface for handling output of an execution.
	 */
	public interface OutputConsumer {
		/**
		 * Invoked when execution of command is completed.
		 * 
		 * @param output
		 *            the output of the execution.
		 */
		public void handleOutput(String output);
	}

	/**
	 * The application.
	 */
	private final Application application;

	/**
	 * Identifier of execution gateway that is used to execute commands.
	 */
	private final String execGatewayId;

	/**
	 * Identifier of execution queue.
	 */
	private final String queueId;

	/**
	 * Indicates whether the queue is closed.
	 */
	private boolean closed = false;

	/**
	 * Subscription for receiving execution outputs.
	 */
	private Subscription subscription = null;

	/**
	 * Message listener used by the subscription.
	 */
	private final MessageListener messageListener;

	/**
	 * Default execution timeout in seconds. If the value is 0 or negative, no
	 * timeout is applied.
	 */
	private long defaultTimeout;

	/**
	 * Output consumers for commands in execution.
	 */
	private Map<String, OutputConsumer> outputConsumers = new HashMap<>();

	/**
	 * Internal synchronization lock.
	 */
	private final Object lock = new Object();

	/**
	 * Constructs the execution queue.
	 * 
	 * @param queueId
	 *            the identifier of queue.
	 * @param application
	 *            the application instance.
	 * @param execGatewayId
	 *            the identifier of {@link ExecGateway} instance registered in
	 *            the application utilized to execute commands.
	 */
	public ExecQueue(String queueId, Application application, String execGatewayId) {
		if (queueId == null) {
			throw new NullPointerException("Queue id cannot be null.");
		}

		if (application == null) {
			throw new NullPointerException("Application cannot be null.");
		}

		Gateway gateway = application.getGateway(execGatewayId);
		if (gateway == null) {
			throw new IllegalArgumentException(
					"The gateway with id " + execGatewayId + " does not exist in the application.");
		}

		if (!(gateway instanceof ExecGateway)) {
			throw new IllegalArgumentException("The gateway with id " + execGatewayId + " is not instance of "
					+ ExecGateway.class.getName() + ".");
		}

		this.queueId = queueId;
		this.application = application;
		this.execGatewayId = execGatewayId;
		this.messageListener = new MessageListener() {
			@Override
			public void onMessage(Message message) {
				handleMessage(message);
			}
		};

		// subscribe to messages with outputs of executions
		subscription = application.subscribe(execGatewayId + "/" + queueId + "/#", messageListener);
	}

	/**
	 * Constructs the execution queue.
	 * 
	 * @param queueId
	 *            the identifier of queue.
	 * @param execGateway
	 *            the instance of {@link ExecGateway} utilized to execute
	 *            commands.
	 */
	public ExecQueue(String queueId, ExecGateway execGateway) {
		this(queueId, execGateway.getApplication(), execGateway.getId());
	}

	/**
	 * Closes the execution queue. After closing no commands can be executed and
	 * no outputs are received.
	 */
	public void close() {
		synchronized (lock) {
			if (closed) {
				return;
			}

			subscription.close();
			subscription = null;
			outputConsumers.clear();
			closed = true;
		}
	}

	/**
	 * Asynchronously executes a commands with default timeout.
	 * 
	 * @param command
	 *            the string command to be executed asynchronously in a separate
	 *            process.
	 * @param outputConsumer
	 *            the output consumer that handles the execution result.
	 */
	public void execute(String command, OutputConsumer outputConsumer) {
		execute(command, defaultTimeout, outputConsumer);
	}

	/**
	 * Asynchronously executes a commands.
	 * 
	 * @param command
	 *            the string command to be executed asynchronously in a separate
	 *            process.
	 * @param timeout
	 *            the execution timeout in seconds. The timeout does not
	 *            consider wait time. If the value is negative or zero, no
	 *            timeout is applied.
	 * @param outputConsumer
	 *            the output consumer that handles the execution result.
	 */
	public void execute(String command, long timeout, OutputConsumer outputConsumer) {
		synchronized (lock) {
			if (closed) {
				throw new IllegalStateException("The queue is closed.");
			}

			String commandId = application.createUniqueId();

			String publicationTopic = execGatewayId + "/" + queueId + "/" + commandId;
			if (timeout > 0) {
				publicationTopic = publicationTopic + "/" + timeout;
			}

			if (outputConsumer != null) {
				outputConsumers.put(commandId, outputConsumer);
			}

			application.publish(new Message(publicationTopic, command));
		}
	}

	/**
	 * Handles the execution output. The method is execution in the application
	 * thread.
	 * 
	 * @param message
	 *            the message with output.
	 */
	private void handleMessage(Message message) {
		String[] topicHierarchy = Application.parseTopicHierarchy(message.getTopic());
		String commandId = topicHierarchy[2];
		String output = message.getContent();

		OutputConsumer outputConsumer;
		synchronized (lock) {
			outputConsumer = outputConsumers.get(commandId);
		}

		if (outputConsumer != null) {
			outputConsumer.handleOutput(output);
		}
	}
}
