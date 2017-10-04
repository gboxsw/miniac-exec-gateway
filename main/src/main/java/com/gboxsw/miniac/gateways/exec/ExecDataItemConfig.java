package com.gboxsw.miniac.gateways.exec;

import com.gboxsw.miniac.Converter;

/**
 * Configuration of an {@link ExecDataItem}.
 * 
 * @param <T>
 *            the value type of configured data item.
 */
public class ExecDataItemConfig<T> {

	/**
	 * Update period in seconds.
	 */
	private int updatePeriod = 60;

	/**
	 * Update command.
	 */
	private String updateCommand = null;

	/**
	 * Timeout in seconds for execution of update command.
	 */
	private int updateCommandTimeout = 10;

	/**
	 * Identifier of execution queue used for execution of commands.
	 */
	private String queueId = null;

	/**
	 * Converter that converts output of update command to value.
	 */
	private Converter<byte[], T> converter;

	/**
	 * Command factory that produces commands changing value of data item.
	 */
	private ChangeCommandFactory<T> changeCommandFactory;

	/**
	 * Timeout in seconds for execution of change command.
	 */
	private int changeCommandTimeout = 10;

	/**
	 * Gets update period in seconds.
	 * 
	 * @return the update period in seconds.
	 */
	public int getUpdatePeriod() {
		return updatePeriod;
	}

	/**
	 * Sets update period.
	 * 
	 * @param updatePeriod
	 *            the update period in seconds.
	 */
	public void setUpdatePeriod(int updatePeriod) {
		if (updatePeriod <= 0) {
			throw new IllegalArgumentException("Period must be a positive integer.");
		}

		this.updatePeriod = updatePeriod;
	}

	/**
	 * Returns the update command.
	 * 
	 * @return the update command.
	 */
	public String getUpdateCommand() {
		return updateCommand;
	}

	/**
	 * Sets the update command.
	 * 
	 * @param updateCommand
	 *            the update command.
	 */
	public void setUpdateCommand(String updateCommand) {
		this.updateCommand = updateCommand;
	}

	/**
	 * Returns timeout in seconds to complete the execution of update command.
	 * 
	 * @return the timeout in seconds, if the value is 0 or negative value, no
	 *         timeout is applied.
	 */
	public int getUpdateCommandTimeout() {
		return updateCommandTimeout;
	}

	/**
	 * Sets timeout in seconds to complete the execution of update command.
	 * 
	 * @param updateCommandTimeout
	 *            the timeout in seconds, it the value is 0 or a negative value,
	 *            no timeout is applied.
	 */
	public void setUpdateCommandTimeout(int updateCommandTimeout) {
		this.updateCommandTimeout = updateCommandTimeout;
	}

	/**
	 * Returns identifier of execution queue utilized for execution of commands.
	 * 
	 * @return the identifier of execution queue.
	 */
	public String getQueueId() {
		return queueId;
	}

	/**
	 * Sets the identifier of execution queue utilized for execution of
	 * commands. If the value is null, the execution queue shared by all
	 * instances of {@link ExecDataItem} is used.
	 * 
	 * @param queueId
	 *            the identifier of execution queue.
	 */
	public void setQueueId(String queueId) {
		this.queueId = queueId;
	}

	/**
	 * Returns the converter of execution output to value of data item.
	 * 
	 * @return the converter.
	 */
	public Converter<byte[], T> getConverter() {
		return converter;
	}

	/**
	 * Sets the converter of execution output to value of data item.
	 * 
	 * @param converter
	 *            the converter.
	 */
	public void setConverter(Converter<byte[], T> converter) {
		this.converter = converter;
	}

	/**
	 * Returns the command factory producing commands that change value.
	 * 
	 * @return the command factory.
	 */
	public ChangeCommandFactory<T> getChangeCommandFactory() {
		return changeCommandFactory;
	}

	/**
	 * Sets the command factory producing commands that change value.
	 * 
	 * @param changeCommandFactory
	 *            the command factory.
	 */
	public void setChangeCommandFactory(ChangeCommandFactory<T> changeCommandFactory) {
		this.changeCommandFactory = changeCommandFactory;
	}

	/**
	 * Returns timeout in seconds to complete the execution of change command.
	 * 
	 * @return the timeout in seconds, if the value is 0 or negative value, no
	 *         timeout is applied.
	 */
	public int getChangeCommandTimeout() {
		return changeCommandTimeout;
	}

	/**
	 * Sets timeout in seconds to complete the execution of change command.
	 * 
	 * @param changeCommandTimeout
	 *            the timeout in seconds, it the value is 0 or a negative value,
	 *            no timeout is applied.
	 */
	public void setChangeCommandTimeout(int changeCommandTimeout) {
		this.changeCommandTimeout = changeCommandTimeout;
	}

	/**
	 * Returns clone of configuration.
	 */
	public ExecDataItemConfig<T> clone() {
		ExecDataItemConfig<T> result = new ExecDataItemConfig<>();

		result.queueId = queueId;

		result.updatePeriod = updatePeriod;
		result.updateCommand = updateCommand;
		result.updateCommandTimeout = updateCommandTimeout;
		result.converter = converter;

		result.changeCommandFactory = changeCommandFactory;
		result.changeCommandTimeout = changeCommandTimeout;

		return result;
	}

}
