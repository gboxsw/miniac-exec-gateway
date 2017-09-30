package com.gboxsw.miniac.gateways.exec;

/**
 * The result of command execution.
 */
public class ExecutionResult {

	/**
	 * Indicates successful execution.
	 */
	private final boolean success;

	/**
	 * The content of standard output.
	 */
	private final byte[] stdout;

	/**
	 * The content of error output.
	 */
	private final byte[] stderr;

	/**
	 * The exit code.
	 */
	private final int exitCode;

	/**
	 * Constructs the execution result.
	 * 
	 * @param success
	 *            the success of execution.
	 * @param stdout
	 *            the standard output.
	 * @param stderr
	 *            the error output.
	 * @param exitCode
	 *            the exit code.
	 */
	ExecutionResult(boolean success, byte[] stdout, byte[] stderr, int exitCode) {
		this.success = success;
		this.stdout = stdout;
		this.stderr = stderr;
		this.exitCode = exitCode;
	}

	/**
	 * Returns whether execution was successful.
	 * 
	 * @return true, if the execution was successful, false otherwise.
	 */
	public boolean isSuccessful() {
		return success;
	}

	/**
	 * Returns the standard output produced during command execution.
	 * 
	 * @return the standard output.
	 */
	public byte[] getStdout() {
		return stdout;
	}

	/**
	 * Returns the error output produced during command execution.
	 * 
	 * @return the error output.
	 */
	public byte[] getStderr() {
		return stderr;
	}

	/**
	 * Returns the exit code.
	 * 
	 * @return the exit code.
	 */
	public int getExitCode() {
		return exitCode;
	}
}
