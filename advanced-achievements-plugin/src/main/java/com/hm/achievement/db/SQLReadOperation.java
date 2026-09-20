package com.hm.achievement.db;

import com.hm.achievement.exception.DatabaseReadError;

import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Class used to perform read operations to the database and automatically retry if a SQLException is thrown.
 *
 * @param <T>
 * @author Pyves
 */
@FunctionalInterface
public interface SQLReadOperation<T> {

	int MAX_ATTEMPTS = 3;

	/**
	 * Performs a single read operation on the database.
	 *
	 * @return result of the read operation
	 * @throws SQLException
	 */
	T performRead() throws SQLException;

	/**
	 * Calls {@code performRead} repeatedly until the read succeeds or {@code MAX_ATTEMPTS} is reached, in which case a
	 * runtime exception is thrown.
	 *
	 * @param operationMessage
	 * @return the result of a successful read operation
	 */
	default T executeOperation(String operationMessage) {
		return executeOperation(operationMessage, exception -> {
		});
	}

	default T executeOperation(String operationMessage, Consumer<SQLException> failureHandler) {
		SQLException cause = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; ++attempt) {
			try {
				return performRead();
			} catch (SQLException e) {
				cause = e;
				failureHandler.accept(e);
			}
		}
		throw new DatabaseReadError("Database read error while " + operationMessage + ".", cause);
	}
}
