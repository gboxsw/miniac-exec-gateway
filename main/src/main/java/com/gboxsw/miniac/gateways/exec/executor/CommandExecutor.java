package com.gboxsw.miniac.gateways.exec.executor;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The asynchronous executor of commands in separate processes.
 */
public class CommandExecutor {

	/**
	 * Logger.
	 */
	private static final Logger logger = Logger.getLogger(CommandExecutor.class.getName());

	/**
	 * Request to execute a command.
	 */
	private static class ExecRequest {

		/**
		 * Identifier of execution queue.
		 */
		private final String queueId;

		/**
		 * The command to be executed.
		 */
		private final Command command;

		/**
		 * Indicates whether the request was successfully completed.
		 */
		private boolean success = false;

		/**
		 * Standard output.
		 */
		private ByteArrayOutputStream stdout;

		/**
		 * Error output.
		 */
		private ByteArrayOutputStream stderr;

		/**
		 * The exit code.
		 */
		private int exitCode;

		/**
		 * Constructs the execution request.
		 * 
		 * @param queueId
		 *            the identifier of execution queue.
		 * @param command
		 *            the command.
		 */
		public ExecRequest(String queueId, Command command) {
			this.queueId = queueId;
			this.command = command;
		}
	}

	/**
	 * Executor used for executing asynchronous actions during execution of
	 * commands.
	 */
	private final ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * Requests in execution groups.
	 */
	private final Map<String, Queue<ExecRequest>> executionQueues = new HashMap<>();

	/**
	 * Indicates whether the execution platform is windows.
	 */
	private final boolean windowsPlatform;

	/**
	 * The registered execution listener.
	 */
	private ExecutionListener executionListener;

	/**
	 * Directory where commands will be executed.
	 */
	private final File executionDirectory;

	/**
	 * Internal synchronization lock.
	 */
	private final Object lock = new Object();

	/**
	 * Constructs the command executor with defined execution directory.
	 * 
	 * @param executionDirectory
	 *            the directory where all commands will be executed.
	 */
	public CommandExecutor(File executionDirectory) {
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

	/**
	 * Constructs the command executor.
	 */
	public CommandExecutor() {
		this(null);
	}

	/**
	 * Executes a command in a separate process.
	 * 
	 * @param queueId
	 *            the identifier of execution queue in which the command should
	 *            be executed.
	 * @param command
	 *            the command to be executed.
	 */
	public void execute(String queueId, Command command) {
		ExecRequest request = new ExecRequest(queueId, command);

		synchronized (lock) {
			Queue<ExecRequest> executionQueue = executionQueues.get(request.queueId);
			if (executionQueue == null) {
				executionQueue = new LinkedList<>();
				executionQueues.put(request.queueId, executionQueue);
			}

			executionQueue.offer(request);
			if (executionQueue.size() == 1) {
				executeRequest(request);
			}
		}
	}

	/**
	 * Shutdowns the executor.
	 * 
	 * @param wait
	 *            if true, the method is blocked until the executor is
	 *            terminated.
	 */
	public void shutdown(boolean wait) {
		executor.shutdown();
		if (wait) {
			try {
				executor.awaitTermination(1, TimeUnit.HOURS);
			} catch (InterruptedException ignore) {

			}
		}
	}

	/**
	 * Sets the execution listener.
	 * 
	 * @param listener
	 *            the listener.
	 */
	public void setExecutionListener(ExecutionListener listener) {
		synchronized (lock) {
			this.executionListener = listener;
		}
	}

	/**
	 * Returns the registered execution listener.
	 * 
	 * @return the execution listener.
	 */
	public ExecutionListener getExecutionListener() {
		synchronized (lock) {
			return executionListener;
		}
	}

	/**
	 * Executes a request.
	 * 
	 * @param request
	 *            the request to be executed.
	 */
	private void executeRequest(final ExecRequest request) {
		String sanitizedCommand = windowsPlatform ? "cmd.exe /c " + request.command.getCommand()
				: request.command.getCommand();

		final ProcessBuilder pb = new ProcessBuilder(sanitizedCommand.split(" "));
		pb.directory(executionDirectory);

		try {
			executor.execute(new Runnable() {
				@Override
				public void run() {
					createAndExecuteProcess(request, pb);
				}
			});
		} catch (RejectedExecutionException e) {
			synchronized (request) {
				request.success = false;
			}

			notifyCompletedRequest(request);
		}
	}

	/**
	 * Starts the new execution request with given preconfigured process
	 * builder.
	 * 
	 * @param request
	 *            the request to be started.
	 * @param pb
	 *            the pre-configured instance of {@link ProcessBuilder}.
	 */
	private void createAndExecuteProcess(ExecRequest request, ProcessBuilder pb) {
		Process process = null;
		int exitCode = 0;
		boolean success = true;
		try {
			// initialize execution of request
			synchronized (request) {
				request.stdout = new ByteArrayOutputStream();
				request.stderr = new ByteArrayOutputStream();
			}

			long startTime = System.currentTimeMillis();

			// create process
			process = pb.start();

			// create output consumers
			StreamGobbler stdoutGobbler;
			StreamGobbler stderrGobbler;
			synchronized (request) {
				stdoutGobbler = new StreamGobbler(process.getInputStream(), request.stdout);
				stderrGobbler = new StreamGobbler(process.getErrorStream(), request.stderr);
			}

			// start output consumers
			executor.execute(stdoutGobbler);
			executor.execute(stderrGobbler);

			// wait for termination
			boolean destroyed = false;
			try {
				if (request.command.getTimeout() > 0) {
					boolean terminated = process.waitFor(request.command.getTimeout(), TimeUnit.MILLISECONDS);
					if (!terminated) {
						process.destroyForcibly();
						destroyed = true;
						logger.log(Level.WARNING, "Timeout (" + request.command.getTimeout()
								+ ") occured when executing [" + request.command.getCommand() + "]");
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
			exitCode = process.exitValue();
			if (destroyed) {
				success = false;
			}

			logger.log(Level.INFO, "Execution of [" + request.command.getCommand() + "] terminated in "
					+ (System.currentTimeMillis() - startTime) + " ms.");

			synchronized (request) {
				request.exitCode = exitCode;
			}

		} catch (Exception e) {
			logger.log(Level.SEVERE, "Failed system command [" + request.command.getCommand() + "]", e);
			if (process.isAlive()) {
				process.destroyForcibly();
			}

			success = false;
		}

		synchronized (request) {
			request.success = success;
		}

		// remove request from execution queue and start next pending request in
		// the same queue
		synchronized (lock) {
			Queue<ExecRequest> executionQueue = executionQueues.get(request.queueId);
			if ((executionQueue == null) || (executionQueue.peek() != request)) {
				logger.log(Level.SEVERE, "Invalid internal state of CommandExecutor.");
			}

			executionQueue.poll();
			if (executionQueue.isEmpty()) {
				executionQueues.remove(request.queueId);
			} else {
				executeRequest(executionQueue.peek());
			}
		}

		// notify completion
		notifyCompletedRequest(request);
	}

	/**
	 * Notifies the a request is completed.
	 * 
	 * @param request
	 *            the completed request.
	 */
	private void notifyCompletedRequest(ExecRequest request) {
		ExecutionResult result;
		synchronized (request) {
			byte[] stdout = (request.stdout != null) ? request.stdout.toByteArray() : null;
			byte[] stderr = (request.stderr != null) ? request.stderr.toByteArray() : null;
			result = new ExecutionResult(request.success, stdout, stderr, request.exitCode);
		}

		ExecutionListener listener;
		synchronized (lock) {
			listener = executionListener;
		}

		if (listener != null) {
			listener.commandCompleted(request.command, result);
		}
	}
}
