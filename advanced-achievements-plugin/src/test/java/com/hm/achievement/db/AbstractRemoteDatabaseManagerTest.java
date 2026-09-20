package com.hm.achievement.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class AbstractRemoteDatabaseManagerTest {

	@Test
	void appendsNormalisedConnectionOptionsWithoutCredentials() {
		AbstractRemoteDatabaseManager manager = manager();
		manager.databaseAddress = "jdbc:mysql://localhost:3306/minecraft";
		manager.databaseUser = "name with spaces";
		manager.databasePassword = "secret&value";
		manager.additionalConnectionOptions = "&useUnicode=yes&characterEncoding=UTF-8";

		assertEquals("jdbc:mysql://localhost:3306/minecraft?useUnicode=yes&characterEncoding=UTF-8",
				manager.buildConnectionUrl());
	}

	@Test
	void preservesExistingQuerySeparator() {
		AbstractRemoteDatabaseManager manager = manager();
		manager.databaseAddress = "jdbc:postgresql://localhost/minecraft?ssl=true";
		manager.additionalConnectionOptions = "applicationName=AdvancedAchievements";

		assertEquals("jdbc:postgresql://localhost/minecraft?ssl=true&applicationName=AdvancedAchievements",
				manager.buildConnectionUrl());
	}

	private AbstractRemoteDatabaseManager manager() {
		Logger logger = Logger.getAnonymousLogger();
		return new AbstractRemoteDatabaseManager(new YamlConfiguration(), logger, new DatabaseUpdater(logger), "unused",
				"mysql", new DatabaseExecutor());
	}
}
