package com.hm.achievement.command.executable;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import com.hm.achievement.category.CommandAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.domain.Achievement.AchievementBuilder;
import com.hm.achievement.listener.PlayerAdvancedAchievementListener;

class GiveCommandTest {

	@Test
	void shouldGiveConfiguredAchievementWithoutTargetPermission() {
		YamlConfiguration mainConfig = new YamlConfiguration();
		mainConfig.set("MultiCommand", false);
		YamlConfiguration langConfig = new YamlConfiguration();
		langConfig.set("achievement-already-received", "Already received");
		langConfig.set("achievement-given", "Achievement given!");
		langConfig.set("achievement-not-found", "Not found: CLOSEST_MATCH");
		AchievementMap achievementMap = new AchievementMap();
		Achievement achievement = new AchievementBuilder()
				.category(CommandAchievements.COMMANDS)
				.subcategory("daily")
				.name("command_daily")
				.displayName("Daily")
				.build();
		achievementMap.put(achievement);
		CacheManager cacheManager = mock(CacheManager.class);
		PlayerAdvancedAchievementListener achievementListener = mock(PlayerAdvancedAchievementListener.class);
		GiveCommand underTest = new GiveCommand(mainConfig, langConfig, new StringBuilder(), cacheManager, achievementMap,
				achievementListener);
		underTest.extractConfigurationParameters();
		CommandSender sender = mock(CommandSender.class);
		Player player = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(false);

		underTest.onExecuteForPlayer(sender, new String[] { "give", "daily", "Tealon" }, player);

		verify(achievementListener).awardAchievement(player, achievement);
		verify(sender).sendMessage("Achievement given!");
		verify(player, never()).hasPermission(anyString());
	}

	@Test
	void shouldStillRequireGivePermissionFromCommandSender() {
		YamlConfiguration langConfig = new YamlConfiguration();
		langConfig.set("no-permissions", "No permission");
		PlayerAdvancedAchievementListener achievementListener = mock(PlayerAdvancedAchievementListener.class);
		GiveCommand underTest = new GiveCommand(new YamlConfiguration(), langConfig, new StringBuilder(),
				mock(CacheManager.class), new AchievementMap(), achievementListener);
		underTest.extractConfigurationParameters();
		CommandSender sender = mock(CommandSender.class);
		when(sender.hasPermission("achievement.give")).thenReturn(false);

		underTest.execute(sender, new String[] { "give", "daily", "Tealon" });

		verify(sender).hasPermission("achievement.give");
		verify(sender).sendMessage("No permission");
		verifyNoInteractions(achievementListener);
	}
}
