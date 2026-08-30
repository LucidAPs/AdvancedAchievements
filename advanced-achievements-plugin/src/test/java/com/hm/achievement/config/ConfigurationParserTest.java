package com.hm.achievement.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.category.Category;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.domain.Achievement.AchievementBuilder;
import com.hm.achievement.exception.PluginLoadError;
import com.hm.achievement.utils.MaterialHelper;

class ConfigurationParserTest {

	@Test
	void shouldKeepLiveConfigurationWhenReloadValidationFails(@TempDir File tempDir) throws Exception {
		Files.writeString(tempDir.toPath().resolve("config.yml"), "DatabaseType: invalid\n");
		AdvancedAchievements plugin = mock(AdvancedAchievements.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);

		YamlConfiguration mainConfig = new YamlConfiguration();
		mainConfig.set("sentinel", "main");
		YamlConfiguration langConfig = new YamlConfiguration();
		langConfig.set("sentinel", "lang");
		YamlConfiguration guiConfig = new YamlConfiguration();
		guiConfig.set("sentinel", "gui");
		AchievementMap achievementMap = new AchievementMap();
		Achievement originalAchievement = new AchievementBuilder().name("original").displayName("Original").build();
		achievementMap.put(originalAchievement);
		Set<Category> disabledCategories = new HashSet<>();
		disabledCategories.add(NormalAchievements.ANVILS);
		StringBuilder pluginHeader = new StringBuilder("original header");

		ConfigurationParser underTest = new ConfigurationParser(mainConfig, langConfig, guiConfig, achievementMap,
				disabledCategories, pluginHeader, Logger.getAnonymousLogger(), 21, mock(YamlUpdater.class), plugin,
				mock(RewardParser.class));

		assertThrows(PluginLoadError.class, underTest::loadAndParseConfiguration);
		assertEquals("main", mainConfig.getString("sentinel"));
		assertEquals("lang", langConfig.getString("sentinel"));
		assertEquals("gui", guiConfig.getString("sentinel"));
		assertEquals(originalAchievement, achievementMap.getForName("original"));
		assertEquals(Set.of(NormalAchievements.ANVILS), disabledCategories);
		assertEquals("original header", pluginHeader.toString());
	}

	@Test
	void shouldLoadBundledConfigurationIntoTemporaryStateBeforeCommit(@TempDir File tempDir) throws Exception {
		AdvancedAchievements plugin = mock(AdvancedAchievements.class);
		Server server = mock(Server.class);
		PluginManager pluginManager = mock(PluginManager.class);
		MaterialHelper materialHelper = mock(MaterialHelper.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getServer()).thenReturn(server);
		when(plugin.getResource(anyString())).thenAnswer(invocation -> getClass()
				.getResourceAsStream("/" + invocation.getArgument(0, String.class)));
		when(server.getPluginManager()).thenReturn(pluginManager);
		when(materialHelper.matchMaterial(anyString(), anyString())).thenReturn(Optional.of(Material.STONE));

		YamlConfiguration mainConfig = new YamlConfiguration();
		YamlConfiguration langConfig = new YamlConfiguration();
		YamlConfiguration guiConfig = new YamlConfiguration();
		AchievementMap achievementMap = new AchievementMap();
		Set<Category> disabledCategories = new HashSet<>();
		RewardParser rewardParser = new RewardParser(mainConfig, langConfig, plugin, materialHelper);
		ConfigurationParser underTest = new ConfigurationParser(mainConfig, langConfig, guiConfig, achievementMap,
				disabledCategories, new StringBuilder(), Logger.getAnonymousLogger(), 21, new YamlUpdater(plugin), plugin,
				rewardParser);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			underTest.loadAndParseConfiguration();
		}

		assertEquals("h2", mainConfig.getString("DatabaseType"));
		assertEquals(62, achievementMap.getAll().size());
	}
}
