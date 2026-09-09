package com.hm.achievement.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.domain.Achievement.AchievementBuilder;
import com.hm.achievement.listener.PlayerAdvancedAchievementListener;

class StatisticIncreaseHandlerTest {

	private static final UUID PLAYER_UUID = UUID.randomUUID();

	private final YamlConfiguration mainConfig = new YamlConfiguration();
	private final AchievementMap achievementMap = new AchievementMap();
	private final CacheManager cacheManager = mock(CacheManager.class);
	private final PlayerAdvancedAchievementListener achievementListener = mock(PlayerAdvancedAchievementListener.class);
	private final Player player = mock(Player.class);

	private StatisticIncreaseHandler underTest;

	@BeforeEach
	void setUp() {
		underTest = new StatisticIncreaseHandler(mainConfig, achievementMap, cacheManager);
		underTest.playerAdvancedAchievementListener = achievementListener;
		when(player.getUniqueId()).thenReturn(PLAYER_UUID);
	}

	@Test
	void shouldAwardMissingAchievementAboveThresholdWithoutPlayerPermission() {
		Achievement first = achievement("itemdrops_15", 15);
		Achievement second = achievement("itemdrops_50", 50);
		Achievement missing = achievement("itemdrops_500", 500);
		Achievement future = achievement("itemdrops_5000", 5000);
		achievementMap.put(first);
		achievementMap.put(second);
		achievementMap.put(missing);
		achievementMap.put(future);
		when(cacheManager.hasPlayerAchievement(PLAYER_UUID, first.getName())).thenReturn(true);
		when(cacheManager.hasPlayerAchievement(PLAYER_UUID, second.getName())).thenReturn(true);
		when(cacheManager.hasPlayerAchievement(PLAYER_UUID, missing.getName())).thenReturn(false);
		when(player.hasPermission(anyString())).thenReturn(false);

		underTest.checkThresholdsAndAchievements(player, NormalAchievements.DROPS, 864);

		verify(achievementListener).awardAchievement(player, missing);
		verify(achievementListener, never()).awardAchievement(player, future);
		verify(player, never()).hasPermission(anyString());
	}

	@Test
	void shouldAwardLowerThresholdAchievementWhenListIsNotAscending() {
		Achievement high = achievement("itemdrops_20000", 20000);
		Achievement mid = achievement("itemdrops_1000", 1000);
		Achievement low = achievement("itemdrops_100", 100);
		achievementMap.put(high);
		achievementMap.put(mid);
		achievementMap.put(low);

		underTest.checkThresholdsAndAchievements(player, NormalAchievements.DROPS, 250);

		verify(achievementListener).awardAchievement(player, low);
	}

	@Test
	void shouldAwardAnvilTiersOneByOneAsEachThresholdIsReached() {
		Achievement first = anvilAchievement("anvilsused_1", 1);
		Achievement novice = anvilAchievement("anvilsused_20", 20);
		Achievement highest = anvilAchievement("anvilsused_100", 100);
		// Use the most hostile ordering to ensure tier progression never depends on map iteration order.
		achievementMap.put(highest);
		achievementMap.put(novice);
		achievementMap.put(first);

		Set<String> received = new HashSet<>();
		when(cacheManager.hasPlayerAchievement(eq(PLAYER_UUID), anyString()))
				.thenAnswer(invocation -> received.contains(invocation.getArgument(1, String.class)));
		doAnswer(invocation -> {
			received.add(invocation.getArgument(1, Achievement.class).getName());
			return null;
		}).when(achievementListener).awardAchievement(eq(player), any(Achievement.class));

		underTest.checkThresholdsAndAchievements(player, NormalAchievements.ANVILS, 1);
		assertEquals(Set.of("anvilsused_1"), received);

		underTest.checkThresholdsAndAchievements(player, NormalAchievements.ANVILS, 20);
		assertEquals(Set.of("anvilsused_1", "anvilsused_20"), received);

		underTest.checkThresholdsAndAchievements(player, NormalAchievements.ANVILS, 100);
		assertEquals(Set.of("anvilsused_1", "anvilsused_20", "anvilsused_100"), received);
	}

	@Test
	void shouldNotAwardBelowThreshold() {
		achievementMap.put(achievement("itemdrops_500", 500));

		underTest.checkThresholdsAndAchievements(player, NormalAchievements.DROPS, 499);

		verifyNoInteractions(achievementListener);
	}

	@Test
	void shouldNotAwardPreviouslyReceivedAchievement() {
		Achievement received = achievement("itemdrops_500", 500);
		achievementMap.put(received);
		when(cacheManager.hasPlayerAchievement(PLAYER_UUID, received.getName())).thenReturn(true);

		underTest.checkThresholdsAndAchievements(player, NormalAchievements.DROPS, 864);

		verifyNoInteractions(achievementListener);
	}

	@Test
	void shouldAcceptEligibleGameplayWithoutCategoryPermission() {
		World world = mock(World.class);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.getWorld()).thenReturn(world);
		when(world.getName()).thenReturn("world");
		when(player.hasPermission(anyString())).thenReturn(false);
		underTest.extractConfigurationParameters();

		assertTrue(underTest.shouldIncreaseBeTakenIntoAccount(player));
		verify(player, never()).hasPermission(anyString());
	}

	@Test
	void shouldKeepConfiguredWorldAndGameModeRestrictions() {
		mainConfig.set("RestrictCreative", true);
		mainConfig.set("RestrictSpectator", true);
		mainConfig.set("RestrictAdventure", true);
		mainConfig.set("ExcludedWorlds", List.of("blocked_world"));
		World world = mock(World.class);
		when(player.getWorld()).thenReturn(world);
		underTest.extractConfigurationParameters();

		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(world.getName()).thenReturn("blocked_world");
		assertFalse(underTest.shouldIncreaseBeTakenIntoAccount(player));

		when(world.getName()).thenReturn("world");
		when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
		assertFalse(underTest.shouldIncreaseBeTakenIntoAccount(player));

		when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
		assertFalse(underTest.shouldIncreaseBeTakenIntoAccount(player));

		when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
		assertFalse(underTest.shouldIncreaseBeTakenIntoAccount(player));
	}

	@Test
	void shouldKeepNpcRestriction() {
		when(player.hasMetadata("NPC")).thenReturn(true);
		underTest.extractConfigurationParameters();

		assertFalse(underTest.shouldIncreaseBeTakenIntoAccount(player));
	}

	private Achievement achievement(String name, long threshold) {
		return new AchievementBuilder()
				.category(NormalAchievements.DROPS)
				.subcategory("")
				.threshold(threshold)
				.name(name)
				.displayName(name)
				.build();
	}

	private Achievement anvilAchievement(String name, long threshold) {
		return new AchievementBuilder()
				.category(NormalAchievements.ANVILS)
				.subcategory("")
				.threshold(threshold)
				.name(name)
				.displayName(name)
				.build();
	}
}
