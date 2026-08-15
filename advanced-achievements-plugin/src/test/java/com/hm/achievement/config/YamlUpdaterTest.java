package com.hm.achievement.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hm.achievement.AdvancedAchievements;

@ExtendWith(MockitoExtension.class)
class YamlUpdaterTest {

	private static final Logger LOGGER = Logger.getLogger("YamlUpdaterTestLogger");

	@TempDir
	static File tempDir;

	@Mock
	private AdvancedAchievements plugin;

	private YamlUpdater underTest;

	@BeforeEach
	void setUp() {
		underTest = new YamlUpdater(plugin, LOGGER);
	}

	@Test
	void shouldAppendMissingDefaultSectionsToUserConfiguration() throws Exception {
		mockDefaultConfigResource();
		when(plugin.getDataFolder()).thenReturn(tempDir);
		File userFile = createFileFromTestResource("config-missing-sections.yml");

		underTest.update("config-default.yml", userFile.getName(), YamlConfiguration.loadConfiguration(userFile));

		byte[] expectedUserConfig = Files.readAllBytes(Paths.get(getClass().getResource("/config-updated.yml").toURI()));
		byte[] actualUserConfig = Files.readAllBytes(userFile.toPath());
		assertEquals(new String(expectedUserConfig), new String(actualUserConfig));
	}

	@Test
	void shouldReloadConfigurationIfThereWereMissingSections() throws Exception {
		mockDefaultConfigResource();
		when(plugin.getDataFolder()).thenReturn(tempDir);
		File userFile = createFileFromTestResource("config-missing-sections.yml");
		YamlConfiguration config = YamlConfiguration.loadConfiguration(userFile);

		underTest.update("config-default.yml", userFile.getName(), config);

		assertEquals("Book created on DATE.", config.getString("book-date"));
	}

	@Test
	void shouldNotChangeUserConfigIfThereAreNoMissingKeys() throws Exception {
		mockDefaultConfigResource();
		File userFile = createFileFromTestResource("config-default.yml");
		long lastModified = userFile.lastModified();

		underTest.update("config-default.yml", userFile.getName(), YamlConfiguration.loadConfiguration(userFile));

		assertEquals(lastModified, userFile.lastModified());
	}

	@Test
	void shouldAppendTheMissingKeyRatherThanOneItIsAPrefixOf() throws Exception {
		when(plugin.getResource("config-prefix-default.yml"))
				.thenReturn(getClass().getResourceAsStream("/config-prefix-default.yml"));
		when(plugin.getDataFolder()).thenReturn(tempDir);
		File userFile = createFileFromTestResource("config-prefix-user.yml");
		YamlConfiguration config = YamlConfiguration.loadConfiguration(userFile);

		underTest.update("config-prefix-default.yml", userFile.getName(), config);

		// 'Fish' is a prefix of the 'FishableFish' key the user already has, which must not be appended again.
		assertEquals("fish_1", config.getString("Fish.1.Name"));
		assertEquals(Arrays.asList("cod", "salmon"), config.getStringList("FishableFish"));
		assertEquals(1, Files.readAllLines(userFile.toPath()).stream().filter(l -> l.startsWith("FishableFish:")).count());
	}

	@Test
	void shouldMoveLegacyItemBreaksAchievementsToAnySubcategory() throws Exception {
		when(plugin.getDataFolder()).thenReturn(tempDir);
		File userFile = createFileFromTestResource("config-legacy-itembreaks.yml");
		YamlConfiguration config = YamlConfiguration.loadConfiguration(userFile);

		underTest.migrateItemBreaksSection(userFile.getName(), config);

		assertEquals(Collections.singleton("any"), config.getConfigurationSection("ItemBreaks").getKeys(false));
		assertEquals("itembreaks_1", config.getString("ItemBreaks.any.1.Name"));
		// Other categories must not be affected by the conversion.
		assertEquals("eatenitems_1", config.getString("EatenItems.1.Name"));
	}

	@Test
	void shouldNotChangeItemBreaksSectionAlreadyUsingSubcategories() throws Exception {
		when(plugin.getDataFolder()).thenReturn(tempDir);
		File userFile = createFileFromTestResource("config-legacy-itembreaks.yml");
		YamlConfiguration config = YamlConfiguration.loadConfiguration(userFile);
		underTest.migrateItemBreaksSection(userFile.getName(), config);
		long lastModified = userFile.lastModified();

		underTest.migrateItemBreaksSection(userFile.getName(), config);

		assertEquals(lastModified, userFile.lastModified());
		assertTrue(config.isConfigurationSection("ItemBreaks.any"));
	}

	private void mockDefaultConfigResource() {
		when(plugin.getResource("config-default.yml")).thenReturn(getClass().getResourceAsStream("/config-default.yml"));
	}

	private File createFileFromTestResource(String testResourceName) throws Exception {
		File userFile = new File(tempDir, testResourceName);
		try (FileOutputStream targetUserConfig = new FileOutputStream(userFile)) {
			Files.copy(Paths.get(getClass().getClassLoader().getResource(testResourceName).toURI()), targetUserConfig);
		}
		return userFile;
	}
}
