package com.hm.achievement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CachedStatisticTest {

	@Test
	void marksExactWrittenRevisionAsPersisted() {
		CachedStatistic statistic = new CachedStatistic(4L, true);
		assertNull(statistic.snapshotForWrite());

		statistic.setValue(5L);
		CachedStatistic.WriteSnapshot snapshot = statistic.snapshotForWrite();
		assertNotNull(snapshot);
		assertEquals(5L, snapshot.getValue());
		assertFalse(statistic.isDatabaseConsistent());

		statistic.markPersisted(snapshot.getRevision());
		assertTrue(statistic.isDatabaseConsistent());
	}

	@Test
	void newerUpdateRemainsDirtyWhenOlderSnapshotCompletes() {
		CachedStatistic statistic = new CachedStatistic(1L, true);
		statistic.setValue(2L);
		CachedStatistic.WriteSnapshot firstWrite = statistic.snapshotForWrite();

		statistic.setValue(3L);
		statistic.markPersisted(firstWrite.getRevision());

		assertFalse(statistic.isDatabaseConsistent());
		assertEquals(3L, statistic.snapshotForWrite().getValue());
	}
}
