package com.gboxsw.miniac.gateways.exec.executor;

/**
 * The listener interface for receiving notifications about completed execution
 * of a command.
 */
public interface ExecutionListener {

	/**
	 * Invoke when processing of command is completed.
	 * 
	 * @param command
	 *            the completed command.
	 * @param result
	 *            the execution result.
	 */
	void commandCompleted(Command command, ExecutionResult result);
}
