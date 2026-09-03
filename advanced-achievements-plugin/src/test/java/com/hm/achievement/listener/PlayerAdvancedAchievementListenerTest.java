package com.hm.achievement.listener;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.advancement.AdvancementManager;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.AbstractDatabaseManager;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.domain.Achievement.AchievementBuilder;
import com.hm.achievement.domain.Reward;
import com.hm.achievement.utils.FancyMessageSender;

/**
 * Class for testing PlayerAdvancedAchievementListener. Currently covers AllAchievementsReceivedRewards usage.
 *
 * @author Pyves
 */
@ExtendWith(MockitoExtension.class)
class PlayerAdvancedAchievementListenerTest {

	private static final String PLUGIN_HEADER = "[HEADER]";
	private static final UUID PLAYER_UUID = UUID.randomUUID();

	@Mock
	private Server server;
	@Mock
	private Player player;
	@Mock
	private World world;
	@Mock
	private AbstractDatabaseManager abstractDatabaseManager;
	@Mock
	private AdvancedAchievements plugin;

	private PlayerAdvancedAchievementListener underTest;

	@Test
	void itShouldRegisterNewAchievementInDatabase() {
		AchievementMap achievementMap = new AchievementMap();
		achievementMap.put(new AchievementBuilder().name("connect_1").displayName("Good Choice").build());
		achievementMap.put(new AchievementBuilder().name("place_500_smooth_brick").displayName("Stone Brick Layer").build());
		YamlConfiguration mainConfig = YamlConfiguration
				.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config-reception.yml")));
		YamlConfiguration langConfig = YamlConfiguration
				.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/lang.yml")));
		underTest = new PlayerAdvancedAchievementListener(mainConfig, langConfig, mock(Logger.class),
				new StringBuilder(PLUGIN_HEADER), new CacheManager(plugin, abstractDatabaseManager), plugin, null,
				achievementMap, abstractDatabaseManager, null, new FancyMessageSender(16));
		underTest.extractConfigurationParameters();
		when(player.getUniqueId()).thenReturn(PLAYER_UUID);
		when(player.getName()).thenReturn("DarkPyves");
		when(plugin.getServer()).thenReturn(server);
		doReturn(Arrays.asList(player)).when(server).getOnlinePlayers();
		Set<String> receivedAchievements = new HashSet<>();
		receivedAchievements.add("connect_1");
		when(abstractDatabaseManager.getPlayerAchievementNames(PLAYER_UUID)).thenReturn(receivedAchievements);
		Achievement achievement = new AchievementBuilder()
				.name("connect_1")
				.displayName("Good Choice")
				.message("Connected for the first time!")
				.build();

		underTest.awardAchievement(player, achievement);

		verify(abstractDatabaseManager).registerAchievement(eq(PLAYER_UUID), eq("connect_1"), anyLong());
	}

	@Test
	void advancementFailureShouldNotPreventPersistenceOrRewards() {
		AchievementMap achievementMap = new AchievementMap();
		@SuppressWarnings("unchecked")
		Consumer<Player> rewarder = mock(Consumer.class);
		Reward reward = new Reward(Collections.singletonList("reward"), Collections.emptyList(), rewarder);
		Achievement achievement = new AchievementBuilder()
				.name("itemdrops_500")
				.displayName("Earth is Pissed")
				.message("You dropped enough items!")
				.rewards(Collections.singletonList(reward))
				.build();
		achievementMap.put(achievement);
		achievementMap.put(new AchievementBuilder().name("another").displayName("Another").build());
		YamlConfiguration mainConfig = YamlConfiguration
				.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/config-reception.yml")));
		YamlConfiguration langConfig = YamlConfiguration
				.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/lang.yml")));
		Logger logger = mock(Logger.class);
		CacheManager cacheManager = new CacheManager(plugin, abstractDatabaseManager);
		underTest = new PlayerAdvancedAchievementListener(mainConfig, langConfig, logger,
				new StringBuilder(PLUGIN_HEADER), cacheManager, plugin, null,
				achievementMap, abstractDatabaseManager, null, new FancyMessageSender(16));
		underTest.extractConfigurationParameters();
		when(player.getUniqueId()).thenReturn(PLAYER_UUID);
		when(player.getName()).thenReturn("Tealon");
		when(plugin.getName()).thenReturn("AdvancedAchievements");
		when(plugin.getServer()).thenReturn(server);
		doReturn(Collections.emptyList()).when(server).getOnlinePlayers();
		when(abstractDatabaseManager.getPlayerAchievementNames(PLAYER_UUID)).thenReturn(new HashSet<>());
		NamespacedKey advancementKey = new NamespacedKey(plugin, AdvancementManager.getKey(achievement.getName()));

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getAdvancement(advancementKey))
					.thenThrow(new IllegalStateException("advancement unavailable"));

			underTest.awardAchievement(player, achievement);
		}

		verify(abstractDatabaseManager).registerAchievement(eq(PLAYER_UUID), eq("itemdrops_500"), anyLong());
		verify(rewarder).accept(player);
		verify(logger).log(eq(Level.WARNING), anyString(), any(RuntimeException.class));
		assertTrue(cacheManager.hasPlayerAchievement(PLAYER_UUID, achievement.getName()));
	}

}
