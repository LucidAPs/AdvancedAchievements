package com.hm.achievement.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.advancement.AdvancementManager;
import com.hm.achievement.category.Category;
import com.hm.achievement.category.CommandAchievements;
import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.domain.Achievement.AchievementBuilder;
import com.hm.achievement.exception.PluginLoadError;
import com.hm.achievement.utils.StringHelper;

/**
 * Class in charge of parsing the config.yml, lang.yml and gui.yml configuration files. It loads the files and populates
 * common data structures used in other parts of the plugin. Basic validation is performed on the achievements.
 *
 * @author Pyves
 */
public class ConfigurationParser {

	private static final Set<String> DATABASE_TYPES = Set.of("sqlite", "mysql", "postgresql", "h2");
	private static final Pattern LANGUAGE_FILE_PATTERN = Pattern.compile("[A-Za-z0-9._-]+\\.yml");
	private static final Pattern TABLE_PREFIX_PATTERN = Pattern.compile("[A-Za-z0-9_]*");

	private final YamlConfiguration mainConfig;
	private final YamlConfiguration langConfig;
	private final YamlConfiguration guiConfig;
	private final AchievementMap achievementMap;
	private final Set<Category> disabledCategories;
	private final StringBuilder pluginHeader;
	private final Logger logger;
	private final int serverVersion;
	private final YamlUpdater yamlUpdater;
	private final AdvancedAchievements plugin;
	private final RewardParser rewardParser;

	@Inject
	public ConfigurationParser(@Named("main") YamlConfiguration mainConfig, @Named("lang") YamlConfiguration langConfig,
			@Named("gui") YamlConfiguration guiConfig, AchievementMap achievementMap, Set<Category> disabledCategories,
			StringBuilder pluginHeader, Logger logger, int serverVersion, YamlUpdater yamlUpdater,
			AdvancedAchievements plugin, RewardParser rewardParser) {
		this.mainConfig = mainConfig;
		this.langConfig = langConfig;
		this.guiConfig = guiConfig;
		this.achievementMap = achievementMap;
		this.disabledCategories = disabledCategories;
		this.pluginHeader = pluginHeader;
		this.logger = logger;
		this.serverVersion = serverVersion;
		this.yamlUpdater = yamlUpdater;
		this.plugin = plugin;
		this.rewardParser = rewardParser;
	}

	/**
	 * Loads the files and populates common data structures used in other parts of the plugin. Performs basic validation
	 * on the achievements.
	 *
	 * @throws PluginLoadError
	 */
	public void loadAndParseConfiguration() throws PluginLoadError {
		logger.info("Backing up and loading configuration files...");

		YamlConfiguration parsedMainConfig = backupAndLoadConfiguration("config.yml", "config.yml");
		validateMainConfiguration(parsedMainConfig);
		String languageFileName = parsedMainConfig.getString("LanguageFileName");
		YamlConfiguration parsedLangConfig = backupAndLoadConfiguration("lang.yml", languageFileName);
		YamlConfiguration parsedGuiConfig = backupAndLoadConfiguration("gui.yml", "gui.yml");

		AchievementMap parsedAchievementMap = new AchievementMap();
		Set<Category> parsedDisabledCategories = new HashSet<>();
		StringBuilder parsedPluginHeader = new StringBuilder();
		RewardParser parsedRewardParser = rewardParser.withConfigurations(parsedMainConfig, parsedLangConfig);

		parseHeader(parsedMainConfig, parsedPluginHeader);
		parseDisabledCategories(parsedMainConfig, parsedDisabledCategories);
		parseAchievements(parsedMainConfig, parsedAchievementMap, parsedDisabledCategories, parsedRewardParser);

		commitConfiguration(parsedMainConfig, parsedLangConfig, parsedGuiConfig, parsedAchievementMap,
				parsedDisabledCategories, parsedPluginHeader);
		logLoadingMessages(parsedAchievementMap, parsedDisabledCategories);
	}

	/**
	 * Reterive keys associated with the configuration section at the given path
	 *
	 * @param path
	 * @return A set containing the keys
	 */
	private Set<String> getSectionKeys(YamlConfiguration config, String path) throws PluginLoadError {
		if (!config.contains(path)) {
			return Collections.emptySet();
		}
		ConfigurationSection section = config.getConfigurationSection(path);
		if (section == null) {
			throw new PluginLoadError(path + " must be a YAML section.");
		}
		return section.getKeys(false);
	}

	/**
	 * Loads and backs up a configuration file.
	 *
	 * @param latestConfigName
	 * @param userConfigName
	 * @throws PluginLoadError
	 */
	private YamlConfiguration backupAndLoadConfiguration(String latestConfigName, String userConfigName)
			throws PluginLoadError {
		if (StringUtils.isBlank(userConfigName) || !LANGUAGE_FILE_PATTERN.matcher(userConfigName).matches()) {
			throw new PluginLoadError("Invalid configuration file name: " + userConfigName
					+ ". File names may only contain letters, numbers, dots, underscores and hyphens, and must end in .yml.");
		}

		File configFile = new File(plugin.getDataFolder(), userConfigName);
		try {
			File backupFile = new File(plugin.getDataFolder(), userConfigName + ".bak");
			// Overwrite previous backup only if a newer version of the file exists.
			if (configFile.lastModified() > backupFile.lastModified()) {
				Files.copy(configFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Failed to back up " + userConfigName + ":", e);
		}

		try {
			if (!configFile.exists()) {
				if (!configFile.getParentFile().exists() && !configFile.getParentFile().mkdirs()) {
					throw new IOException("Failed to create plugin data folder " + configFile.getParent());
				}
				try (InputStream defaultConfig = plugin.getResource(userConfigName)) {
					if (defaultConfig == null) {
						throw new IOException("No bundled default exists for " + userConfigName);
					}
					Files.copy(defaultConfig, configFile.toPath());
				}
			}
			YamlConfiguration userConfig = new YamlConfiguration();
			userConfig.load(configFile);
			yamlUpdater.update(latestConfigName, userConfigName, userConfig);
			return userConfig;
		} catch (IOException | InvalidConfigurationException e) {
			throw new PluginLoadError("Failed to load " + userConfigName
					+ ". Verify its syntax and use the following logs.", e);
		}
	}

	private void validateMainConfiguration(YamlConfiguration config) throws PluginLoadError {
		String databaseType = config.getString("DatabaseType");
		if (DATABASE_TYPES.stream().noneMatch(type -> type.equalsIgnoreCase(databaseType))) {
			throw new PluginLoadError("DatabaseType must be one of " + DATABASE_TYPES + ", but was " + databaseType + ".");
		}

		validateColor(config, "Color");
		validateColor(config, "ListColorNotReceived");
		validatePositiveInteger(config, "TopList");
		validatePositiveInteger(config, "PlaytimeTaskInterval");
		validatePositiveInteger(config, "DistanceTaskInterval");
		validatePositiveInteger(config, "AdvancementGenerationPerTick");
		validatePositiveInteger(config, "TableMaxSizeOfGroupedSubcategories");
		validateNonNegativeInteger(config, "TimeBook");

		ConfigurationSection cooldowns = config.getConfigurationSection("StatisticCooldown");
		if (cooldowns == null) {
			throw new PluginLoadError("StatisticCooldown must be a YAML section.");
		}
		for (String category : cooldowns.getKeys(false)) {
			validateNonNegativeInteger(config, "StatisticCooldown." + category);
		}

		String tablePrefix = config.getString("TablePrefix");
		if (tablePrefix == null || !TABLE_PREFIX_PATTERN.matcher(tablePrefix).matches()) {
			throw new PluginLoadError("TablePrefix may only contain letters, numbers and underscores.");
		}
	}

	private void validateColor(YamlConfiguration config, String path) throws PluginLoadError {
		String colorCode = config.getString(path);
		ChatColor color = StringUtils.isEmpty(colorCode) ? null : ChatColor.getByChar(colorCode);
		if (color == null || !color.isColor()) {
			throw new PluginLoadError(path + " must be a valid Minecraft color code (0-9 or a-f).");
		}
	}

	private void validatePositiveInteger(YamlConfiguration config, String path) throws PluginLoadError {
		if (!config.isInt(path) || config.getInt(path) <= 0) {
			throw new PluginLoadError(path + " must be a positive whole number.");
		}
	}

	private void validateNonNegativeInteger(YamlConfiguration config, String path) throws PluginLoadError {
		if (!config.isInt(path) || config.getInt(path) < 0) {
			throw new PluginLoadError(path + " must be zero or a positive whole number.");
		}
	}

	private void commitConfiguration(YamlConfiguration parsedMainConfig, YamlConfiguration parsedLangConfig,
			YamlConfiguration parsedGuiConfig, AchievementMap parsedAchievementMap,
			Set<Category> parsedDisabledCategories, StringBuilder parsedPluginHeader) throws PluginLoadError {
		try {
			mainConfig.loadFromString(parsedMainConfig.saveToString());
			langConfig.loadFromString(parsedLangConfig.saveToString());
			guiConfig.loadFromString(parsedGuiConfig.saveToString());
		} catch (InvalidConfigurationException e) {
			throw new PluginLoadError("Failed to activate the validated configuration.", e);
		}

		achievementMap.replaceWith(parsedAchievementMap);
		disabledCategories.clear();
		disabledCategories.addAll(parsedDisabledCategories);
		pluginHeader.setLength(0);
		pluginHeader.append(parsedPluginHeader);
		pluginHeader.trimToSize();
	}

	/**
	 * Parses the plugin's header, used throughout the project.
	 */
	private void parseHeader(YamlConfiguration config, StringBuilder header) {
		header.setLength(0);
		String icon = StringEscapeUtils.unescapeJava(config.getString("Icon"));
		if (StringUtils.isNotBlank(icon)) {
			String coloredIcon = ChatColor.getByChar(config.getString("Color")) + icon;
			header
					.append(ChatColor.translateAlternateColorCodes('&',
							StringUtils.replace(config.getString("ChatHeader"), "%ICON%", coloredIcon)))
					.append(" ");
		}
		header.trimToSize();
	}

	/**
	 * Extracts disabled categories from the configuration file.
	 *
	 * @throws PluginLoadError
	 */
	private void parseDisabledCategories(YamlConfiguration config, Set<Category> categories) throws PluginLoadError {
		extractDisabledCategoriesFromConfig(config, categories);
		// Need PetMaster for PetMasterGive and PetMasterReceive categories.
		if ((!categories.contains(NormalAchievements.PETMASTERGIVE)
				|| !categories.contains(NormalAchievements.PETMASTERRECEIVE))
				&& !Bukkit.getPluginManager().isPluginEnabled("PetMaster")) {
			categories.add(NormalAchievements.PETMASTERGIVE);
			categories.add(NormalAchievements.PETMASTERRECEIVE);
			logger.warning("Overriding configuration: disabling PetMasterGive and PetMasterReceive categories.");
			logger.warning(
					"Ensure you have placed Pet Master in your plugins folder or add PetMasterGive and PetMasterReceive to the DisabledCategories list in config.yml.");
		}
		// Need Jobs for JobsReborn category.
		if (!categories.contains(MultipleAchievements.JOBSREBORN)
				&& !Bukkit.getPluginManager().isPluginEnabled("Jobs")) {
			categories.add(MultipleAchievements.JOBSREBORN);
			logger.warning("Overriding configuration: disabling JobsReborn category.");
			logger.warning(
					"Ensure you have placed JobsReborn in your plugins folder or add JobsReborn to the DisabledCategories list in config.yml.");
		}
		// Raids introduced in 1.14.
		if (!categories.contains(NormalAchievements.RAIDSWON) && serverVersion < 14) {
			categories.add(NormalAchievements.RAIDSWON);
			logger.warning("Overriding configuration: disabling RaidsWon category.");
			logger.warning(
					"Raids are not available in your server version, please add RaidsWon to the DisabledCategories list in config.yml.");
		}
	}

	/**
	 * Performs validation for the DisabledCategories list and maps the values to Category instances.
	 *
	 * @throws PluginLoadError
	 */
	private void extractDisabledCategoriesFromConfig(YamlConfiguration config, Set<Category> categories)
			throws PluginLoadError {
		categories.clear();
		for (String disabledCategory : config.getStringList("DisabledCategories")) {
			Category category = CommandAchievements.COMMANDS.toString().equals(disabledCategory)
					? CommandAchievements.COMMANDS
					: null;
			if (category == null) {
				category = NormalAchievements.getByName(disabledCategory);
			}
			if (category == null) {
				category = MultipleAchievements.getByName(disabledCategory);
			}
			if (category == null) {
				List<String> allCategories = new ArrayList<>();
				Arrays.stream(NormalAchievements.values()).forEach(n -> allCategories.add(n.toString()));
				Arrays.stream(MultipleAchievements.values()).forEach(m -> allCategories.add(m.toString()));
				allCategories.add(CommandAchievements.COMMANDS.toString());
				throw new PluginLoadError("Category " + disabledCategory + " specified in DisabledCategories is misspelt. "
						+ "Did you mean " + StringHelper.getClosestMatch(disabledCategory, allCategories) + "?");
			}
			categories.add(category);
		}
	}

	/**
	 * Goes through all the achievements for non-disabled categories.
	 *
	 * Populates relevant data structures and performs basic validation.
	 *
	 * @throws PluginLoadError If an achievement fails to parse due to misconfiguration.
	 */
	private void parseAchievements(YamlConfiguration config, AchievementMap achievements, Set<Category> categories,
			RewardParser configurationRewardParser) throws PluginLoadError {
		Set<String> advancementKeys = new HashSet<>();

		// Enumerate Commands achievements.
		if (!categories.contains(CommandAchievements.COMMANDS)) {
			Set<String> commands = getSectionKeys(config, CommandAchievements.COMMANDS.toString());
			if (commands.isEmpty()) {
				categories.add(CommandAchievements.COMMANDS);
			} else {
				for (String command : commands) {
					parseAchievement(config, achievements, configurationRewardParser, advancementKeys,
							CommandAchievements.COMMANDS, command, -1L);
				}
			}
		}

		// Enumerate the normal achievements.
		for (NormalAchievements category : NormalAchievements.values()) {
			if (!categories.contains(category)) {
				if (getSectionKeys(config, category.toString()).isEmpty()) {
					categories.add(category);
					continue;
				}
				for (long threshold : getSortedThresholds(config, category.toString())) {
					parseAchievement(config, achievements, configurationRewardParser, advancementKeys, category, "",
							threshold);
				}
			}
		}

		// Enumerate the achievements with multiple categories.
		for (MultipleAchievements category : MultipleAchievements.values()) {
			if (!categories.contains(category)) {
				Set<String> subcategories = getSectionKeys(config, category.toString());
				if (subcategories.isEmpty()) {
					categories.add(category);
					continue;
				}

				for (String subcategory : subcategories) {
					for (long threshold : getSortedThresholds(config, category + "." + subcategory)) {
						parseAchievement(config, achievements, configurationRewardParser, advancementKeys, category,
								subcategory, threshold);
					}
				}
			}
		}
	}

	private List<Long> getSortedThresholds(YamlConfiguration config, String path) throws PluginLoadError {
		ConfigurationSection section = config.getConfigurationSection(path);
		if (section == null) {
			throw new PluginLoadError(path + " must be a YAML section containing achievement thresholds.");
		}
		try {
			List<Long> thresholds = section.getKeys(false).stream()
					.map(Long::parseLong)
					.sorted()
					.collect(Collectors.toList());
			if (thresholds.stream().anyMatch(threshold -> threshold <= 0)) {
				throw new PluginLoadError("Achievement thresholds under " + path + " must be positive whole numbers.");
			}
			return thresholds;
		} catch (NumberFormatException e) {
			throw new PluginLoadError("Achievement thresholds under " + path + " must be positive whole numbers.", e);
		}
	}

	/**
	 * Performs validation for a single achievement and populates an entry in the namesToDisplayNames map.
	 *
	 * @param category
	 * @param subcategory
	 * @param threshold
	 *
	 * @throws PluginLoadError If the achievement fails to parse due to misconfiguration.
	 */
	private void parseAchievement(YamlConfiguration config, AchievementMap achievements,
			RewardParser configurationRewardParser, Set<String> advancementKeys, Category category, String subcategory,
			long threshold) throws PluginLoadError {
		String path;
		if (category instanceof CommandAchievements) {
			path = category + "." + subcategory;
		} else if (category instanceof NormalAchievements) {
			path = category + "." + threshold;
		} else {
			path = category + "." + subcategory + "." + threshold;
		}
		ConfigurationSection section = config.getConfigurationSection(path);
		if (section == null) {
			throw new PluginLoadError("Achievement with path (" + path + ") must be a YAML section.");
		}
		String name = section.getString("Name");
		String message = section.getString("Message");
		String displayName = StringUtils.defaultString(section.getString("DisplayName"), name);
		if (StringUtils.isBlank(name)) {
			throw new PluginLoadError("Achievement with path (" + path + ") is missing its Name parameter in config.yml.");
		} else if (achievements.getForName(name) != null) {
			throw new PluginLoadError("Duplicate achievement Name (" + name + "). "
					+ "Please ensure each Name is unique in config.yml.");
		} else if (StringUtils.isBlank(message)) {
			throw new PluginLoadError(
					"Achievement with path (" + path + ") is missing its Message parameter in config.yml.");
		} else if (StringUtils.isBlank(displayName)) {
			throw new PluginLoadError(
					"Achievement with path (" + path + ") must have a non-empty DisplayName parameter.");
		} else if (achievements.getForDisplayName(displayName) != null) {
			throw new PluginLoadError("Duplicate achievement DisplayName (" + displayName
					+ "). Display names must be unique after formatting codes are removed.");
		}

		String advancementKey = AdvancementManager.getKey(name);
		if (StringUtils.isBlank(advancementKey)) {
			throw new PluginLoadError(
					"Achievement Name (" + name + ") does not contain any characters usable in an advancement key.");
		} else if (!advancementKeys.add(advancementKey)) {
			throw new PluginLoadError("Achievement Name (" + name + ") produces duplicate advancement key ("
					+ advancementKey + "). Rename it so it remains unique after punctuation is removed.");
		}

		String rewardPath = config.isConfigurationSection(path + ".Reward") ? path + ".Reward" : path + ".Rewards";

		Achievement achievement = new AchievementBuilder()
				.name(name)
				.displayName(displayName)
				.message(message)
				.goal(StringUtils.defaultString(section.getString("Goal"), message))
				.type(section.getString("Type"))
				.threshold(threshold)
				.category(category)
				.subcategory(subcategory)
				.rewards(configurationRewardParser.parseRewards(rewardPath))
				.build();
		achievements.put(achievement);
	}

	private void logLoadingMessages(AchievementMap achievements, Set<Category> categoriesDisabled) {
		int disabledCategoryCount = categoriesDisabled.size();
		int categories = NormalAchievements.values().length + MultipleAchievements.values().length + 1
				- disabledCategoryCount;
		logger.info("Loaded " + achievements.getAll().size() + " achievements in " + categories + " categories.");

		if (!categoriesDisabled.isEmpty()) {
			String noun = disabledCategoryCount == 1 ? "category" : "categories";
			logger.info(disabledCategoryCount + " disabled " + noun + ": " + categoriesDisabled.toString());
		}
	}

}
