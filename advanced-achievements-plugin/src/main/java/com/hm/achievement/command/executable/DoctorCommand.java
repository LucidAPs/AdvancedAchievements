package com.hm.achievement.command.executable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.category.Category;
import com.hm.achievement.command.pagination.CommandPagination;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.AbstractDatabaseManager;

/** Provides a read-only health report for administrators. */
@Singleton
@CommandSpec(name = "doctor", permission = "doctor", minArgs = 1, maxArgs = 2)
public class DoctorCommand extends AbstractCommand {

	private static final int REPORT_LINES_PER_PAGE = 12;

	private final AdvancedAchievements advancedAchievements;
	private final AbstractDatabaseManager databaseManager;
	private final AchievementMap achievementMap;
	private final Set<Category> disabledCategories;

	@Inject
	public DoctorCommand(@Named("main") YamlConfiguration mainConfig, @Named("lang") YamlConfiguration langConfig,
			StringBuilder pluginHeader, AdvancedAchievements advancedAchievements,
			AbstractDatabaseManager databaseManager, AchievementMap achievementMap, Set<Category> disabledCategories) {
		super(mainConfig, langConfig, pluginHeader);
		this.advancedAchievements = advancedAchievements;
		this.databaseManager = databaseManager;
		this.achievementMap = achievementMap;
		this.disabledCategories = disabledCategories;
	}

	@Override
	void onExecute(CommandSender sender, String[] args) {
		int page = args.length == 2 ? Math.max(1, NumberUtils.toInt(args[1], 1)) : 1;
		File dataFolder = advancedAchievements.getDataFolder();
		int achievementCount = achievementMap.getAll().size();
		int disabledCategoryCount = disabledCategories.size();
		String version = advancedAchievements.getPluginMeta().getVersion();
		String platform = advancedAchievements.getServer().getName() + " "
				+ advancedAchievements.getServer().getBukkitVersion();
		List<String> integrations = integrationStates();

		sender.sendMessage(pluginHeader + langConfig.getString("doctor-running"));
		advancedAchievements.getServer().getScheduler().runTaskAsynchronously(advancedAchievements, () -> {
			DoctorReport report = new DoctorReport();
			report.ok("Advanced Achievements " + version + " is enabled.");
			report.info("Platform: " + platform);
			int javaFeature = Runtime.version().feature();
			if (javaFeature >= 21) {
				report.ok("Java " + javaFeature + " meets the Java 21 minimum.");
			} else {
				report.fail("Java " + javaFeature + " is unsupported; Java 21 or newer is required.");
			}

			YamlConfiguration diskMainConfig = checkYaml(report, new File(dataFolder, "config.yml"), "config.yml");
			checkYaml(report, new File(dataFolder, "gui.yml"), "gui.yml");
			YamlConfiguration inspectedMainConfig = diskMainConfig == null ? mainConfig : diskMainConfig;
			String databaseType = inspectedMainConfig.getString("DatabaseType", "sqlite").toLowerCase(Locale.ROOT);
			String languageFile = inspectedMainConfig.getString("LanguageFileName", "lang.yml");
			if (languageFile.matches("[A-Za-z0-9._-]+")) {
				checkYaml(report, new File(dataFolder, languageFile), languageFile);
			} else {
				report.fail("LanguageFileName must be a file name inside the plugin data folder.");
			}

			if (Set.of("sqlite", "h2", "mysql", "postgresql").contains(databaseType)) {
				report.ok("Configured database type: " + databaseType + ".");
			} else {
				report.fail("Unknown DatabaseType '" + databaseType + "'.");
			}
			String tablePrefix = inspectedMainConfig.getString("TablePrefix", "");
			if (tablePrefix.matches("[A-Za-z0-9_]*")) {
				report.ok("TablePrefix contains only safe identifier characters.");
			} else {
				report.fail("TablePrefix may contain only letters, digits, and underscores.");
			}
			if (("mysql".equals(databaseType) || "postgresql".equals(databaseType))
					&& inspectedMainConfig.getString("DatabaseAddress", "").isBlank()) {
				report.fail("DatabaseAddress is required for a remote database.");
			}
			if (("mysql".equals(databaseType) || "postgresql".equals(databaseType))
					&& inspectedMainConfig.getString("DatabaseUser", "").isBlank()) {
				report.fail("DatabaseUser is required for a remote database.");
			}
			if ("h2".equals(databaseType)) {
				report.warn("H2 is retained for existing installations; SQLite is recommended for new local databases.");
			}
			try {
				if (databaseManager.checkHealth()) {
					report.ok("Database connection check succeeded.");
				} else {
					report.fail("Database connection check returned no usable connection.");
				}
			} catch (RuntimeException e) {
				report.fail("Database connection check failed; inspect the server log for the SQL error.");
			}
			int pendingOperations = databaseManager.getPendingOperationCount();
			if (pendingOperations > 50) {
				report.warn("Database queue is elevated: " + pendingOperations + " operations pending.");
			} else {
				report.info("Database queue: " + pendingOperations + " operations pending.");
			}
			if (databaseManager.getLastFailureTimestamp() > 0) {
				report.warn("A database failure has occurred since this plugin instance started.");
			}

			if (achievementCount > 0) {
				report.ok(achievementCount + " achievements loaded; " + disabledCategoryCount + " categories disabled.");
			} else {
				report.fail("No achievements are loaded.");
			}
			integrations.forEach(report::info);
			report.info("This report is read-only; no settings or files were changed.");

			advancedAchievements.getServer().getScheduler().runTask(advancedAchievements,
					() -> sendReport(sender, page, report));
		});
	}

	private List<String> integrationStates() {
		List<String> states = new ArrayList<>();
		for (String plugin : List.of("Vault", "PlaceholderAPI", "PetMaster", "Jobs")) {
			String state = advancedAchievements.getServer().getPluginManager().isPluginEnabled(plugin)
					? "enabled"
					: "not installed or disabled";
			states.add(plugin + " integration: " + state + ".");
		}
		return states;
	}

	private YamlConfiguration checkYaml(DoctorReport report, File file, String displayName) {
		if (!file.isFile()) {
			report.fail(displayName + " is missing.");
			return null;
		}
		try {
			YamlConfiguration yaml = new YamlConfiguration();
			yaml.load(file);
			report.ok(displayName + " is valid YAML.");
			return yaml;
		} catch (IOException | InvalidConfigurationException e) {
			report.fail(displayName + " could not be parsed; inspect the server log for details.");
			advancedAchievements.getLogger().warning("Doctor could not parse " + displayName + ": " + e.getMessage());
			return null;
		}
	}

	private void sendReport(CommandSender sender, int page, DoctorReport report) {
		sender.sendMessage(ChatColor.DARK_PURPLE + "Advanced Achievements Doctor: " + ChatColor.GREEN + report.okCount
				+ " OK" + ChatColor.GRAY + ", " + ChatColor.GOLD + report.warningCount + " warnings" + ChatColor.GRAY
				+ ", " + ChatColor.RED + report.failureCount + " failures");
		new CommandPagination(report.lines, REPORT_LINES_PER_PAGE, langConfig).sendPage(page, sender);
	}

	private static class DoctorReport {

		private final List<String> lines = new ArrayList<>();
		private int okCount;
		private int warningCount;
		private int failureCount;

		void ok(String message) {
			okCount++;
			lines.add(ChatColor.GREEN + "[OK] " + ChatColor.GRAY + message);
		}

		void warn(String message) {
			warningCount++;
			lines.add(ChatColor.GOLD + "[WARN] " + ChatColor.GRAY + message);
		}

		void fail(String message) {
			failureCount++;
			lines.add(ChatColor.RED + "[FAIL] " + ChatColor.GRAY + message);
		}

		void info(String message) {
			lines.add(ChatColor.AQUA + "[INFO] " + ChatColor.GRAY + message);
		}
	}
}
