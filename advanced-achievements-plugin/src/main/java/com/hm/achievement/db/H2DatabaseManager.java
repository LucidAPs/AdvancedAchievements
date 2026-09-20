package com.hm.achievement.db;

import java.io.File;
import java.util.logging.Logger;

import javax.inject.Named;

import org.bukkit.configuration.file.YamlConfiguration;

import com.hm.achievement.AdvancedAchievements;

/**
 * Class used to handle a H2 database.
 *
 * @author Pyves
 *
 */
public class H2DatabaseManager extends AbstractFileDatabaseManager {

	public H2DatabaseManager(@Named("main") YamlConfiguration mainConfig, Logger logger, DatabaseUpdater databaseUpdater,
			AdvancedAchievements advancedAchievements, DatabaseExecutor databaseExecutor) {
		super(mainConfig, logger, databaseUpdater, advancedAchievements, "org.h2.Driver",
				createUrl(advancedAchievements), "achievements.mv.db", databaseExecutor);
	}

	private static String createUrl(AdvancedAchievements advancedAchievements) {
		String databasePath = new File(advancedAchievements.getDataFolder(), "achievements").toPath().toAbsolutePath()
				.normalize().toString().replace('\\', '/');
		return "jdbc:h2:" + databasePath + ";DATABASE_TO_UPPER=false;MODE=MySQL";
	}
}
