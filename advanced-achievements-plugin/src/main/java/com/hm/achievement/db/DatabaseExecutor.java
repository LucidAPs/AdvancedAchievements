package com.hm.achievement.db;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Serialises all access to the shared JDBC connection. */
public class DatabaseExecutor {

	private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

	private final AtomicReference<Thread> databaseThread = new AtomicReference<>();
	private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
			new LinkedBlockingQueue<>(), runnable -> {
				Thread thread = new Thread(runnable, "AdvancedAchievements-Database");
				thread.setDaemon(true);
				databaseThread.set(thread);
				return thread;
			});

	public void execute(Runnable operation) {
		executor.execute(operation);
	}

	public <T> T call(Callable<T> operation) {
		if (Thread.currentThread() == databaseThread.get()) {
			return callDirectly(operation);
		}
		try {
			return executor.submit(operation).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for a database operation.", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			throw new IllegalStateException("Database operation failed.", cause);
		} catch (RejectedExecutionException e) {
			throw new IllegalStateException("The database executor has already shut down.", e);
		}
	}

	public <T, E extends Exception> T callChecked(CheckedCallable<T, E> operation) throws E {
		if (Thread.currentThread() == databaseThread.get()) {
			return operation.call();
		}
		try {
			return executor.submit(operation::call).get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for a database operation.", e);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			@SuppressWarnings("unchecked")
			E checkedCause = (E) cause;
			throw checkedCause;
		} catch (RejectedExecutionException e) {
			throw new IllegalStateException("The database executor has already shut down.", e);
		}
	}

	public int getPendingOperationCount() {
		return executor.getQueue().size() + executor.getActiveCount();
	}

	public boolean shutdown() {
		executor.shutdown();
		try {
			return executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private static <T> T callDirectly(Callable<T> operation) {
		try {
			return operation.call();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Database operation failed.", e);
		}
	}

	@FunctionalInterface
	public interface CheckedCallable<T, E extends Exception> {

		T call() throws E;
	}
}
