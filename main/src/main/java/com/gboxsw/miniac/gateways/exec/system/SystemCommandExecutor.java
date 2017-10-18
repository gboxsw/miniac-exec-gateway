package com.gboxsw.miniac.gateways.exec.system;

import java.io.*;
import java.util.concurrent.*;
import java.util.logging.*;

import com.gboxsw.miniac.gateways.exec.*;

/**
 * Executor of system commands.
 */
public class SystemCommandExecutor implements CommandExecutor {
	/**
	 * Logger.
	 */
	private static final Logger logger = Logger.getLogger(SystemCommandExecutor.class.getName());

	/**
	 * Indicates whether the execution platform is windows.
	 */
	private final boolean windowsPlatform;

	/**
	 * Directory where commands will be executed.
	 */
	private final File executionDirectory;

	/**
	 * Constructs the command executor with defined execution directory.
	 * 
	 * @param executionDirectory
	 *            the directory where all commands will be executed.
	 */
	public SystemCommandExecutor(File executionDirectory) {
		windowsPlatform = System.getProperty("os.name").toLowerCase().startsWith("windows");

		if ((executionDirectory != null) && !executionDirectory.exists()) {
			logger.log(Level.SEVERE,
					"The execution directory " + executionDirectory.getAbsolutePath() + " does not exist.");
			executionDirectory = null;
		}

		if ((executionDirectory != null) && !executionDirectory.isDirectory()) {
			logger.log(Level.SEVERE,
					"The execution directory " + executionDirectory.getAbsolutePath() + " is not a directory.");
			executionDirectory = null;
		}

		if (executionDirectory == null) {
			executionDirectory = new File(System.getProperty("user.dir"));
		}

		this.executionDirectory = executionDirectory;
	}

	@Override
	public byte[] execute(String command, long timeout, ExecutorService executorService) {
		String sanitizedCommand = windowsPlatform ? "cmd.exe /c " + command : command;

		final ProcessBuilder pb = new ProcessBuilder(sanitizedCommand.split(" "));
		pb.directory(executionDirectory);

		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();

		Process process = null;
		boolean success = true;
		try {
			long startTime = System.currentTimeMillis();

			// create process
			process = pb.start();

			// create output consumers
			StreamGobbler stdoutGobbler = new StreamGobbler(process.getInputStream(), stdout);
			StreamGobbler stderrGobbler = new StreamGobbler(process.getErrorStream(), stderr);

			// start output consumers
			executorService.execute(stdoutGobbler);
			executorService.execute(stderrGobbler);

			// wait for termination
			boolean destroyed = false;
			try {
				if (timeout > 0) {
					boolean terminated = process.waitFor(timeout, TimeUnit.MILLISECONDS);
					if (!terminated) {
						process.destroyForcibly();
						destroyed = true;
						logger.log(Level.WARNING, "Timeout (" + timeout + ") occured when executing [" + command + "]");
					}
				} else {
					process.waitFor();
				}
			} catch (InterruptedException e) {
				process.destroyForcibly();
				destroyed = true;
			}

			// wait for output
			stdoutGobbler.join();
			stderrGobbler.join();

			// determine success
			if (destroyed) {
				success = false;
			}

			logger.log(Level.INFO, "Execution of [" + command + "] terminated in "
					+ (System.currentTimeMillis() - startTime) + " ms.");
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed system command [" + command + "]", e);
			if ((process != null) && process.isAlive()) {
				process.destroyForcibly();
			}
		}

		if (success) {
			return stdout.toByteArray();
		} else {
			return null;
		}
	}

}
