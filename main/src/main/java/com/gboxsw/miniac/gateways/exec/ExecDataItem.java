package com.gboxsw.miniac.gateways.exec;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.gboxsw.miniac.*;

/**
 * Data item whose value is obtained from outputs of periodical execution of
 * (command line) commands.
 * 
 * @param <T>
 *            the type of value.
 */
public class ExecDataItem<T> extends DataItem<T> {

	/**
	 * Identifier of shared execution queue used for all execution of all
	 * commands.
	 */
	private static final String SHARED_EXEC_QUEUE_ID = "shared-exec-queue-" + UUID.randomUUID().toString();

	/**
	 * Logger.
	 */
	private static final Logger logger = Logger.getLogger(ExecDataItem.class.getName());

	/**
	 * Identifier of execution gateway.
	 */
	private final String execGatewayId;

	/**
	 * Configuration of data item.
	 */
	private final ExecDataItemConfig<T> config;

	/**
	 * Subscription for receiving update outputs.
	 */
	private Subscription updateSubscription;

	/**
	 * Cancellable for cancelling periodical requests to update value of data
	 * item.
	 */
	private Cancellable updateCancellable;

	/**
	 * Latest value decoded from output of update command.
	 */
	private T latestValue;

	/**
	 * Base topic for publication of commands changing value of the data item.
	 */
	private String baseChangeTopic;

	/**
	 * Message that forces update of value.
	 */
	private Message updateMessage;

	/**
	 * Constructs the
	 * 
	 * @param execGatewayId
	 * @param config
	 * @param type
	 */
	public ExecDataItem(String execGatewayId, ExecDataItemConfig<T> config, Class<T> type) {
		super(type, config.getChangeCommandFactory() == null);
		this.config = config.clone();
		this.execGatewayId = execGatewayId;

		String updateCommand = this.config.getUpdateCommand();
		if ((updateCommand == null) || (updateCommand.trim().isEmpty())) {
			throw new IllegalArgumentException("Configuration does not define update command.");
		}

		if (this.config.getConverter() == null) {
			throw new IllegalArgumentException("Configuration does not define value converter.");
		}

		if (execGatewayId == null) {
			throw new IllegalArgumentException("The identifier of gateway cannot be null.");
		}
	}

	@Override
	protected void onActivate(Bundle savedState) {
		Application app = getApplication();
		String updateCommandId = app.createUniqueId();

		// determine execution queue
		String queueId = config.getQueueId();
		if (queueId == null) {
			queueId = SHARED_EXEC_QUEUE_ID;
		}

		// construct topic structure for execution gateway
		String baseUpdateTopic = execGatewayId + "/" + queueId + "/" + updateCommandId;
		String updateTopic = baseUpdateTopic;
		if (config.getUpdateCommandTimeout() > 0) {
			updateTopic = updateTopic + "/" + config.getUpdateCommandTimeout();
		}

		// setup publication of update messages
		updateMessage = new Message(updateTopic, config.getUpdateCommand());
		updateCancellable = app.publishWithFixedDelay(updateMessage, 0, config.getUpdatePeriod(), TimeUnit.SECONDS);

		// setup subscription for receiving outputs of update commands
		updateSubscription = app.subscribe(baseUpdateTopic, new MessageListener() {
			@Override
			public void onMessage(Message message) {
				try {
					latestValue = config.getConverter().convertSourceToTarget(message.getPayload());
				} catch (Exception e) {
					latestValue = null;
					logger.log(Level.WARNING, "Conversion of value for data item " + getId() + " failed.", e);
				}

				update();
			}
		});

		// create base change topic
		baseChangeTopic = execGatewayId + "/" + queueId + "/" + app.createUniqueId();
	}

	@Override
	protected T onSynchronizeValue() {
		return latestValue;
	}

	@Override
	protected void onValueChangeRequested(T newValue) {
		ChangeCommandFactory<T> commandFactory = config.getChangeCommandFactory();
		if (commandFactory == null) {
			return;
		}

		String changeCommand = commandFactory.createCommand(newValue);
		if ((changeCommand == null) || changeCommand.trim().isEmpty()) {
			logger.log(Level.WARNING, "Command factory of the data item " + getId()
					+ " generated an empty command (new value: " + newValue + ")");
			return;
		}

		String changeTopic = baseChangeTopic;
		if (config.getChangeCommandTimeout() > 0) {
			changeTopic = changeTopic + "/" + config.getChangeCommandTimeout();
		}

		getApplication().publish(new Message(changeTopic, changeCommand));
		getApplication().publish(updateMessage);
	}

	@Override
	protected void onSaveState(Bundle outState) {
		// nothing to do
	}

	@Override
	protected void onDeactivate() {
		updateCancellable.cancel();
		updateSubscription.close();
	}
}
