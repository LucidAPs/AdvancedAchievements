package com.hm.achievement.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
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

	@Test
	void shouldAllowProgressiveAchievementsToShareDisplayName(@TempDir File tempDir) throws Exception {
		YamlConfiguration customConfig = YamlConfiguration.loadConfiguration(
				new InputStreamReader(getClass().getResourceAsStream("/config.yml")));
		addSmeltingAchievement(customConfig, 500, "smeltitems_500");
		addSmeltingAchievement(customConfig, 1000, "smeltitems_1000");
		customConfig.save(new File(tempDir, "config.yml"));

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
		AchievementMap achievementMap = new AchievementMap();
		RewardParser rewardParser = new RewardParser(mainConfig, langConfig, plugin, materialHelper);
		ConfigurationParser underTest = new ConfigurationParser(mainConfig, langConfig, new YamlConfiguration(),
				achievementMap, new HashSet<>(), new StringBuilder(), Logger.getAnonymousLogger(), 21,
				new YamlUpdater(plugin), plugin, rewardParser);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			underTest.loadAndParseConfiguration();
		}

		assertEquals("The Smelter", achievementMap.getForName("smeltitems_250").getDisplayName());
		assertEquals("The Smelter", achievementMap.getForName("smeltitems_500").getDisplayName());
		assertEquals("The Smelter", achievementMap.getForName("smeltitems_1000").getDisplayName());
	}

	@Test
	void shouldSkipInvalidAchievementsAndLoadRemainingConfiguration(@TempDir File tempDir) throws Exception {
		YamlConfiguration customConfig = YamlConfiguration.loadConfiguration(
				new InputStreamReader(getClass().getResourceAsStream("/config.yml")));
		addSmeltingAchievement(customConfig, 500, "smeltitems_500");
		addSmeltingAchievement(customConfig, 1000, "broken_smelt");
		customConfig.set("Smelting.1000.Message", null);
		customConfig.set("Smelting.not-a-threshold.Name", "broken_threshold");
		customConfig.set("Smelting.not-a-threshold.Message", "This entry must be skipped.");
		customConfig.save(new File(tempDir, "config.yml"));

		AdvancedAchievements plugin = mock(AdvancedAchievements.class);
		Server server = mock(Server.class);
		PluginManager pluginManager = mock(PluginManager.class);
		MaterialHelper materialHelper = mock(MaterialHelper.class);
		Logger logger = mock(Logger.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getServer()).thenReturn(server);
		when(plugin.getResource(anyString())).thenAnswer(invocation -> getClass()
				.getResourceAsStream("/" + invocation.getArgument(0, String.class)));
		when(server.getPluginManager()).thenReturn(pluginManager);
		when(materialHelper.matchMaterial(anyString(), anyString())).thenReturn(Optional.of(Material.STONE));

		YamlConfiguration mainConfig = new YamlConfiguration();
		YamlConfiguration langConfig = new YamlConfiguration();
		AchievementMap achievementMap = new AchievementMap();
		RewardParser rewardParser = new RewardParser(mainConfig, langConfig, plugin, materialHelper);
		ConfigurationParser underTest = new ConfigurationParser(mainConfig, langConfig, new YamlConfiguration(),
				achievementMap, new HashSet<>(), new StringBuilder(), logger, 21, new YamlUpdater(plugin), plugin,
				rewardParser);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			underTest.loadAndParseConfiguration();
		}

		assertEquals(63, achievementMap.getAll().size());
		assertEquals("The Smelter", achievementMap.getForName("smeltitems_500").getDisplayName());
		assertNull(achievementMap.getForName("broken_smelt"));
		assertNull(achievementMap.getForName("broken_threshold"));
		verify(logger).warning(contains("Smelting.1000"));
		verify(logger).warning(contains("Smelting.not-a-threshold"));
		verify(logger).warning(contains("Skipped 2 invalid achievement entries"));
	}

	@Test
	void shouldNotReserveAdvancementKeyWhenAchievementParsingFails(@TempDir File tempDir) throws Exception {
		YamlConfiguration customConfig = YamlConfiguration.loadConfiguration(
				new InputStreamReader(getClass().getResourceAsStream("/config.yml")));
		addSmeltingAchievement(customConfig, 500, "retry-key!");
		addSmeltingAchievement(customConfig, 1000, "retry-key?");
		customConfig.save(new File(tempDir, "config.yml"));

		AdvancedAchievements plugin = mock(AdvancedAchievements.class);
		PluginManager pluginManager = mock(PluginManager.class);
		Logger logger = mock(Logger.class);
		when(plugin.getDataFolder()).thenReturn(tempDir);
		when(plugin.getResource(anyString())).thenAnswer(invocation -> getClass()
				.getResourceAsStream("/" + invocation.getArgument(0, String.class)));
		RewardParser rewardParser = mock(RewardParser.class);
		when(rewardParser.withConfigurations(any(YamlConfiguration.class), any(YamlConfiguration.class)))
				.thenReturn(rewardParser);
		when(rewardParser.parseRewards(anyString())).thenAnswer(invocation -> {
			if ("Smelting.500.Rewards".equals(invocation.getArgument(0, String.class))) {
				throw new IllegalArgumentException("Invalid reward configuration");
			}
			return List.of();
		});

		AchievementMap achievementMap = new AchievementMap();
		ConfigurationParser underTest = new ConfigurationParser(new YamlConfiguration(), new YamlConfiguration(),
				new YamlConfiguration(), achievementMap, new HashSet<>(), new StringBuilder(), logger, 21,
				new YamlUpdater(plugin), plugin, rewardParser);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
			underTest.loadAndParseConfiguration();
		}

		assertNull(achievementMap.getForName("retry-key!"));
		assertEquals("retry-key?", achievementMap.getForName("retry-key?").getName());
		verify(logger).log(eq(java.util.logging.Level.SEVERE), contains("Smelting.500"), any(RuntimeException.class));
		verify(logger).warning(contains("Skipped 1 invalid achievement entry"));
	}

	private void addSmeltingAchievement(YamlConfiguration config, int threshold, String name) {
		String path = "Smelting." + threshold;
		config.set(path + ".Goal", "Smelt " + threshold + " items.");
		config.set(path + ".Message", threshold + " items smelt in a furnace!");
		config.set(path + ".Name", name);
		config.set(path + ".DisplayName", "The Smelter");
	}
}
