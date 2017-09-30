package com.gboxsw.miniac.gateways.exec;

import java.io.*;

/**
 * Class implementing a thread consuming stream output
 */
class StreamGobbler implements Runnable {

	/**
	 * Size of internal buffer.
	 */
	private static int BUFFER_SIZE = 1024;

	/**
	 * The consumed stream
	 */
	private final InputStream input;

	/**
	 * The stream where input stream is redirected.
	 */
	private final OutputStream output;

	/**
	 * Indicates whether the gobbler is terminated.
	 */
	private boolean terminated = false;

	/**
	 * The termination wait lock.
	 */
	private final Object waitLock = new Object();

	/**
	 * Constructs the stream gobbler.
	 * 
	 * @param input
	 *            the input steam.
	 * @param output
	 *            the output stream.
	 */
	public StreamGobbler(InputStream input, OutputStream output) {
		this.input = input;
		this.output = output;
	}

	@Override
	public void run() {
		try {
			byte[] buffer = new byte[BUFFER_SIZE];
			try {
				int bytesCount = 0;
				while ((bytesCount = input.read(buffer)) >= 0) {
					if (output != null)
						output.write(buffer, 0, bytesCount);
				}
			} catch (IOException e) {
				return;
			}
		} finally {
			synchronized (waitLock) {
				terminated = true;
				waitLock.notifyAll();
			}
		}
	}

	/**
	 * Waits for termination.
	 */
	public void join() {
		synchronized (waitLock) {
			if (!terminated) {
				try {
					waitLock.wait();
				} catch (Exception ignore) {
				}
			}
		}
	}
}
