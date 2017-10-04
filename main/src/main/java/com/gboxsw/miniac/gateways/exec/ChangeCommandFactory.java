package com.gboxsw.miniac.gateways.exec;

/**
 * The command factory that produces command that force a change of an
 * {@link ExecDataItem}.
 * 
 * @param <T>
 *            the type of changing value.
 */
public interface ChangeCommandFactory<T> {

	/**
	 * Creates command that changes value of an {@link ExecDataItem}.
	 * 
	 * @param value
	 *            the desired value.
	 * @return the command to be executed.
	 */
	public String createCommand(T value);

}
