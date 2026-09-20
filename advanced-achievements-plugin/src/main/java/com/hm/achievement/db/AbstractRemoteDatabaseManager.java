package com.hm.achievement.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

import javax.inject.Named;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Class used to handle a remote (in the sense not managed by the plugin) database.
 *
 * @author Pyves
 *
 */
public class AbstractRemoteDatabaseManager extends AbstractDatabaseManager {

	volatile String databaseAddress;
	volatile String databaseUser;
	volatile String databasePassword;
	volatile String additionalConnectionOptions;

	private final String databaseType;

	public AbstractRemoteDatabaseManager(@Named("main") YamlConfiguration mainConfig, Logger logger,
			DatabaseUpdater databaseUpdater, String driverPath, String databaseType, DatabaseExecutor databaseExecutor) {
		super(mainConfig, logger, databaseUpdater, driverPath, databaseExecutor);
		this.databaseType = databaseType;
	}

	@Override
	void performPreliminaryTasks() throws ClassNotFoundException {
		Class.forName(driverPath);

		databaseAddress = getDatabaseAddress();
		databaseUser = mainConfig.getString("DatabaseUser");
		databasePassword = mainConfig.getString("DatabasePassword");
		additionalConnectionOptions = mainConfig.getString("AdditionalConnectionOptions");
	}

	@Override
	Connection createConnection() throws SQLException {
		return DriverManager.getConnection(buildConnectionUrl(), databaseUser, databasePassword);
	}

	String buildConnectionUrl() {
		String options = additionalConnectionOptions == null ? "" : additionalConnectionOptions.trim();
		while (options.startsWith("?") || options.startsWith("&")) {
			options = options.substring(1);
		}
		if (options.isEmpty()) {
			return databaseAddress;
		}
		return databaseAddress + (databaseAddress.contains("?") ? "&" : "?") + options;
	}

	private String getDatabaseAddress() {
		String databaseAddress = mainConfig.getString("DatabaseAddress");
		// Attempt to deal with common address mistakes where prefixes such as jdbc: or jdbc:mysql:// are omitted.
		if (!databaseAddress.startsWith("jdbc:")) {
			if (databaseAddress.startsWith(databaseType + "://")) {
				return "jdbc:" + databaseAddress;
			} else {
				return "jdbc:" + databaseType + "://" + databaseAddress;
			}
		}
		return databaseAddress;
	}
}
