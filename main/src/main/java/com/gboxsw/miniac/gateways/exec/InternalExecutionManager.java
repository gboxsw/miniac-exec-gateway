package com.gboxsw.miniac.gateways.exec;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * Internal manager of asynchronous command executions.
 */
final class InternalExecutionManager {

	/**
	 * Logger.
	 */
	private static final Logger logger = Logger.getLogger(InternalExecutionManager.class.getName());

	/**
	 * The task to asynchronously execute a command.
	 */
	public static final class Task {

		/**
		 * The identifier of the task.
		 */
		final String id;

		/**
		 * The command to be executed.
		 */
		final String command;

		/**
		 * The executor that executes the command.
		 */
		final CommandExecutor executor;

		/**
		 * Timeout in milliseconds to complete execution of the command.
		 */
		final long timeout;

		/**
		 * Constructs the task to execute a command.
		 * 
		 * @param command
		 *            the command to be executed.
		 * @param executor
		 *            the command executor.
		 * @param id
		 *            the internal identifier of the command.
		 * @param timeout
		 *            the timeout to complete execution of the command,
		 *            non-positive value indicates that there is no timeout.
		 */
		public Task(String command, CommandExecutor executor, String id, long timeout) {
			if (executor == null) {
				throw new NullPointerException("The command executor cannot be null.");
			}

			if (command == null) {
				command = "";
			}

			this.id = id;
			this.command = command.trim();
			this.executor = executor;
			this.timeout = timeout;
		}
	}

	/**
	 * The listener interface for receiving notifications about completed
	 * execution of task to execute a command.
	 */
	public interface ExecutionListener {

		/**
		 * Invoked when execution of task is completed.
		 * 
		 * @param task
		 *            the completed task.
		 * @param result
		 *            the execution result.
		 */
		public void taskCompleted(Task task, byte[] result);
	}

	/**
	 * The queued task encapsulates all data related to execution of the task by
	 * the manager.
	 */
	private static class QueuedTask {

		/**
		 * Identifier of execution queue where the task is executed.
		 */
		private final String queueId;

		/**
		 * The task to be executed.
		 */
		private final Task task;

		/**
		 * Execution result.
		 */
		private byte[] result = null;

		/**
		 * Constructs the queued task, i.e. a task submitted/queued for
		 * execution.
		 * 
		 * @param queueId
		 *            the identifier of execution queue.
		 * @param task
		 *            the task that is executed.
		 */
		public QueuedTask(String queueId, Task task) {
			this.queueId = queueId;
			this.task = task;
		}
	}

	/**
	 * Executor service used for executing asynchronous actions during execution
	 * of commands.
	 */
	private final ExecutorService executorService;

	/**
	 * The registered execution listener.
	 */
	private final ExecutionListener executionListener;

	/**
	 * Queued tasks in execution groups.
	 */
	private final Map<String, Queue<QueuedTask>> executionQueues = new HashMap<>();

	/**
	 * Constructs the execution manager using given executor service.
	 */
	public InternalExecutionManager(ExecutorService executorService, ExecutionListener executionListener) {
		this.executorService = executorService;
		this.executionListener = executionListener;
	}

	/**
	 * Executes a task in a given execution queue.
	 * 
	 * @param queueId
	 *            the identifier of execution queue in which the task should be
	 *            executed.
	 * @param task
	 *            the task to be executed.
	 */
	public void execute(String queueId, Task task) {
		QueuedTask taskExecution = new QueuedTask(queueId, task);

		synchronized (executionQueues) {
			Queue<QueuedTask> executionQueue = executionQueues.get(taskExecution.queueId);
			if (executionQueue == null) {
				executionQueue = new LinkedList<>();
				executionQueues.put(taskExecution.queueId, executionQueue);
			}

			executionQueue.offer(taskExecution);
			if (executionQueue.size() == 1) {
				scheduleExecution(taskExecution);
			}
		}
	}

	/**
	 * Schedules a queued task for execution.
	 * 
	 * @param task
	 *            the task to be scheduled for execution.
	 */
	private void scheduleExecution(final QueuedTask task) {
		try {
			executorService.submit(new Runnable() {
				@Override
				public void run() {
					executeTaskAsynchronously(task);
				}
			});
		} catch (Exception e) {
			notifyCompletedTask(task);
		}
	}

	/**
	 * Realizes asynchronous execution of the task.
	 * 
	 * @param task
	 *            the queued task to be executed.
	 */
	private void executeTaskAsynchronously(QueuedTask queuedTask) {
		try {
			// execute task
			Task task = queuedTask.task;
			queuedTask.result = task.executor.execute(task.command, task.timeout, executorService);
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Execution of submitted task failed.", e);
		}

		// remove task from execution queue and start next pending task in
		// the same queue
		synchronized (executionQueues) {
			Queue<QueuedTask> executionQueue = executionQueues.get(queuedTask.queueId);
			if ((executionQueue == null) || (executionQueue.peek() != queuedTask)) {
				logger.log(Level.SEVERE, "Invalid internal state of CommandExecutor.");
			}

			executionQueue.poll();
			if (executionQueue.isEmpty()) {
				executionQueues.remove(queuedTask.queueId);
			} else {
				scheduleExecution(executionQueue.peek());
			}
		}

		// notify completion
		notifyCompletedTask(queuedTask);
	}

	/**
	 * Notifies that execution of task is completed (successfully or with a
	 * failure).
	 * 
	 * @param queuedTask
	 *            the completed task.
	 */
	private void notifyCompletedTask(QueuedTask queuedTask) {
		if (executionListener != null) {
			executionListener.taskCompleted(queuedTask.task, queuedTask.result);
		}
	}
}
