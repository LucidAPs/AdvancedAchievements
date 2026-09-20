package com.hm.achievement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DatabaseExecutorTest {

	@Test
	void executesQueuedWritesBeforeFollowingRead() {
		DatabaseExecutor executor = new DatabaseExecutor();
		List<Integer> order = new ArrayList<>();
		executor.execute(() -> order.add(1));
		executor.execute(() -> order.add(2));

		List<Integer> observed = executor.call(() -> List.copyOf(order));

		assertEquals(List.of(1, 2), observed);
		assertTrue(executor.shutdown());
	}

	@Test
	void nestedCallRunsOnDatabaseThreadWithoutDeadlock() {
		DatabaseExecutor executor = new DatabaseExecutor();
		int value = executor.call(() -> executor.call(() -> 42));

		assertEquals(42, value);
		assertTrue(executor.shutdown());
	}
}
