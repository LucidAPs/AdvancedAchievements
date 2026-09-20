package com.hm.achievement.db;

import java.util.logging.Logger;

import javax.inject.Named;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Class used to handle a MySQL database.
 *
 * @author Pyves
 *
 */
public class MySQLDatabaseManager extends AbstractRemoteDatabaseManager {

	public MySQLDatabaseManager(@Named("main") YamlConfiguration mainConfig, Logger logger,
			DatabaseUpdater databaseUpdater, DatabaseExecutor databaseExecutor) {
		super(mainConfig, logger, databaseUpdater, "com.mysql.cj.jdbc.Driver", "mysql", databaseExecutor);
	}

	@Override
	void performPreliminaryTasks() throws ClassNotFoundException {
		super.performPreliminaryTasks();

		String options = additionalConnectionOptions == null ? "" : additionalConnectionOptions.trim();
		if (!options.toLowerCase().contains("usessl=")) {
			additionalConnectionOptions = "useSSL=false&" + options.replaceFirst("^[?&]+", "");
		}
	}
}
