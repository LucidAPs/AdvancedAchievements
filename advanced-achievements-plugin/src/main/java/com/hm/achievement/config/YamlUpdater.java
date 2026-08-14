package com.hm.achievement.config;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.hm.achievement.AdvancedAchievements;

public class YamlUpdater {

	private static final String ITEM_BREAKS_CATEGORY = "ItemBreaks";
	private static final String LEGACY_ITEM_BREAKS_SUBCATEGORY = "any";

	private final AdvancedAchievements plugin;
	private final Logger logger;

	@Inject
	public YamlUpdater(AdvancedAchievements plugin, Logger logger) {
		this.plugin = plugin;
		this.logger = logger;
	}

	/**
	 * Updates user configurations by appending any YAML sections that are present in the default files shipped with the
	 * plugin. Comments, if any, are also included. If file updates are performed, the config object is reloaded.
	 * 
	 * @param defaultConfigName
	 * @param userConfigName
	 * @param userConfig
	 * @throws InvalidConfigurationException
	 * @throws IOException
	 */
	public void update(String defaultConfigName, String userConfigName, YamlConfiguration userConfig)
			throws InvalidConfigurationException, IOException {
		try (BufferedReader defaultConfigReader = new BufferedReader(
				new InputStreamReader(plugin.getResource(defaultConfigName), UTF_8))) {
			List<String> defaultLines = defaultConfigReader.lines().collect(Collectors.toList());
			YamlConfiguration defaultConfig = new YamlConfiguration();
			defaultConfig.loadFromString(StringUtils.join(defaultLines, System.lineSeparator()));

			List<String> sectionsToAppend = defaultConfig.getKeys(false).stream()
					.filter(key -> !userConfig.getKeys(false).contains(key))
					.flatMap(missingKey -> extractSectionForMissingKey(defaultLines, missingKey))
					.collect(Collectors.toList());

			if (!sectionsToAppend.isEmpty()) {
				Path userConfigPath = Paths.get(plugin.getDataFolder().getPath(), userConfigName);
				Files.write(userConfigPath, sectionsToAppend, StandardOpenOption.APPEND);
				userConfig.load(userConfigPath.toFile());
			}
		}
	}

	/**
	 * Converts an ItemBreaks section written for previous plugin versions, i.e. with thresholds directly below the
	 * category, to the current format where thresholds live below a sub-category. The achievements are moved to the
	 * 'any' sub-category, which matches any broken item and holds the statistics migrated in the database.
	 *
	 * @param userConfigName
	 * @param userConfig
	 * @throws InvalidConfigurationException
	 * @throws IOException
	 */
	public void migrateItemBreaksSection(String userConfigName, YamlConfiguration userConfig)
			throws InvalidConfigurationException, IOException {
		if (!isLegacyItemBreaksSection(userConfig)) {
			return;
		}

		Path userConfigPath = Paths.get(plugin.getDataFolder().getPath(), userConfigName);
		List<String> lines = Files.readAllLines(userConfigPath, UTF_8);
		List<String> updatedLines = new ArrayList<>(lines.size() + 1);
		for (int i = 0; i < lines.size(); ++i) {
			String line = lines.get(i);
			updatedLines.add(line);
			if (line.startsWith(ITEM_BREAKS_CATEGORY + ":")) {
				updatedLines.add("  " + LEGACY_ITEM_BREAKS_SUBCATEGORY + ":");
				// Indent all the lines belonging to the section, i.e. starting with spaces.
				while (i + 1 < lines.size() && lines.get(i + 1).startsWith(" ")) {
					updatedLines.add("  " + lines.get(++i));
				}
			}
		}

		logger.info("Converting the " + ITEM_BREAKS_CATEGORY + " section of " + userConfigName
				+ " to the sub-category format, its achievements are now below the '" + LEGACY_ITEM_BREAKS_SUBCATEGORY
				+ "' sub-category.");
		Files.write(userConfigPath, updatedLines);
		userConfig.load(userConfigPath.toFile());
	}

	/**
	 * Determines whether the ItemBreaks section uses the old format, i.e. thresholds are used as keys instead of
	 * sub-categories.
	 *
	 * @param userConfig
	 * @return true if the section must be converted, false otherwise
	 */
	private boolean isLegacyItemBreaksSection(YamlConfiguration userConfig) {
		if (!userConfig.isConfigurationSection(ITEM_BREAKS_CATEGORY)) {
			return false;
		}
		Set<String> keys = userConfig.getConfigurationSection(ITEM_BREAKS_CATEGORY).getKeys(false);
		return !keys.isEmpty() && keys.stream().allMatch(StringUtils::isNumeric);
	}

	private Stream<String> extractSectionForMissingKey(List<String> defaultLines, String key) {
		for (int i = 0; i < defaultLines.size(); ++i) {
			if (defaultLines.get(i).startsWith(key)) {
				int start = i;
				// Include all comments lines above the missing key, if any.
				while (defaultLines.get(start - 1).startsWith("#")) {
					--start;
				}
				int end = i + 1;
				// Include all lines belonging to the same YAML section, i.e. starting with spaces.
				while (defaultLines.get(end).startsWith(" ")) {
					++end;
				}
				return Stream.concat(Stream.of(""), defaultLines.subList(start, end).stream());
			}
		}
		return Stream.of();
	}

}
