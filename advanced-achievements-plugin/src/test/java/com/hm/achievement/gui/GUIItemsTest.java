package com.hm.achievement.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Locale;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import com.hm.achievement.utils.MaterialHelper;

class GUIItemsTest {

	@Test
	void shouldSupportLegacyDefaultAchievementItemsAlongsideTypedItems() throws Exception {
		MockBukkit.mock();
		try {
			YamlConfiguration mainConfig = loadConfiguration("config.yml");
			YamlConfiguration langConfig = loadConfiguration("lang.yml");
			YamlConfiguration guiConfig = loadConfiguration("gui.yml");
			guiConfig.set("AchievementNotStarted.Item", "blue_terracotta");
			guiConfig.set("AchievementStarted.Item", "orange_terracotta");
			guiConfig.set("AchievementReceived.Item", "green_terracotta");
			MaterialHelper materialHelper = mock(MaterialHelper.class);
			when(materialHelper.matchMaterial(anyString(), any(Material.class), anyString()))
					.thenAnswer(invocation -> {
						Material material = Material
								.getMaterial(invocation.<String> getArgument(0).toUpperCase(Locale.ROOT));
						return material == null ? invocation.getArgument(1) : material;
					});

			GUIItems underTest = new GUIItems(mainConfig, langConfig, guiConfig, materialHelper);
			underTest.extractConfigurationParameters();

			assertEquals(Material.BLUE_TERRACOTTA, underTest.getAchievementNotStarted("custom").getType());
			assertEquals(Material.ORANGE_TERRACOTTA, underTest.getAchievementStarted("custom").getType());
			assertEquals(Material.GREEN_TERRACOTTA, underTest.getAchievementReceived("custom").getType());
			assertEquals(Material.RED_GLAZED_TERRACOTTA, underTest.getAchievementNotStarted("rare").getType());
			assertEquals(Material.YELLOW_GLAZED_TERRACOTTA, underTest.getAchievementStarted("rare").getType());
			assertEquals(Material.LIME_GLAZED_TERRACOTTA, underTest.getAchievementReceived("rare").getType());
		} finally {
			MockBukkit.unmock();
		}
	}

	private YamlConfiguration loadConfiguration(String resourceName) throws Exception {
		File resource = new File(getClass().getClassLoader().getResource(resourceName).toURI());
		YamlConfiguration configuration = new YamlConfiguration();
		configuration.load(resource);
		return configuration;
	}
}
