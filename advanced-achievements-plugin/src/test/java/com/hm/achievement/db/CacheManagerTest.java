package com.hm.achievement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.category.MultipleAchievements;

class CacheManagerTest {

	@Test
	void shouldBulkLoadMultipleAchievementStatisticsOnce() {
		AbstractDatabaseManager databaseManager = mock(AbstractDatabaseManager.class);
		CacheManager underTest = new CacheManager(mock(AdvancedAchievements.class), databaseManager);
		UUID playerId = UUID.randomUUID();
		when(databaseManager.getMultipleAchievementAmounts(playerId, MultipleAchievements.CRAFTS))
				.thenReturn(Map.of("diamond_axe", 7L));

		Map<String, Long> firstResult = underTest.getMultipleAchievementAmounts(MultipleAchievements.CRAFTS,
				Arrays.asList("diamond_axe", "stick"), playerId);
		Map<String, Long> secondResult = underTest.getMultipleAchievementAmounts(MultipleAchievements.CRAFTS,
				Arrays.asList("diamond_axe", "stick"), playerId);

		assertEquals(Map.of("diamond_axe", 7L, "stick", 0L), firstResult);
		assertEquals(firstResult, secondResult);
		verify(databaseManager, times(1)).getMultipleAchievementAmounts(playerId, MultipleAchievements.CRAFTS);
	}

	@Test
	void shouldUseJobsRebornDefaultForMissingStatistics() {
		AbstractDatabaseManager databaseManager = mock(AbstractDatabaseManager.class);
		CacheManager underTest = new CacheManager(mock(AdvancedAchievements.class), databaseManager);
		UUID playerId = UUID.randomUUID();
		when(databaseManager.getMultipleAchievementAmounts(playerId, MultipleAchievements.JOBSREBORN))
				.thenReturn(Map.of());

		Map<String, Long> result = underTest.getMultipleAchievementAmounts(MultipleAchievements.JOBSREBORN,
				Arrays.asList("hunter"), playerId);

		assertEquals(Map.of("hunter", 1L), result);
	}
}
