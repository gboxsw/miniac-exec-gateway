package com.gboxsw.miniac.gateways.exec;

/**
 * The command to be executed by the system.
 */
public class Command {

	/**
	 * The identifier of the command.
	 */
	private final String id;

	/**
	 * The command.
	 */
	private final String command;

	/**
	 * Timeout in milliseconds for process to complete the command.
	 */
	private final long timeout;

	/**
	 * Constructs new command with timeout.
	 * 
	 * @param command
	 *            the command to be executed.
	 * @param id
	 *            the internal identifier of the command.
	 * @param timeout
	 *            the timeout for process to terminated in milliseconds,
	 *            non-positive value indicates that there is no timeout.
	 */
	public Command(String command, String id, long timeout) {
		if (command == null) {
			command = "";
		}

		this.id = id;
		this.command = command.trim();
		this.timeout = timeout;
	}

	/**
	 * Constructs new command with timeout.
	 * 
	 * @param command
	 *            the command to be executed.
	 * @param timeout
	 *            the timeout for process to terminated in milliseconds,
	 *            non-positive value indicates that there is no timeout.
	 */
	public Command(String command, long timeout) {
		this(command, null, timeout);
	}

	/**
	 * Constructs new command without timeout.
	 * 
	 * @param command
	 *            the command to be executed.
	 * @param id
	 *            the internal identifier of the command.
	 */
	public Command(String command, String id) {
		this(command, id, -1);
	}

	/**
	 * Constructs new command without timeout.
	 * 
	 * @param command
	 *            the command to be executed.
	 */
	public Command(String command) {
		this(command, null, -1);
	}

	/**
	 * Returns the command for execution by the system platform.
	 * 
	 * @return the command.
	 */
	public final String getCommand() {
		return command;
	}

	/**
	 * Timeout for termination of the process.
	 * 
	 * @return the timeout in milliseconds, non-positive value means that there
	 *         is no timeout.
	 */
	public final long getTimeout() {
		return timeout;
	}

	/**
	 * Returns the internal identifier of the command.
	 * 
	 * @return the internal identifier.
	 */
	public String getId() {
		return id;
	}
}
