package com.hm.achievement.db;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;

import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.exception.PluginLoadError;

/**
 * Class used to update the database schema.
 * 
 * @author Pyves
 *
 */
@Singleton
public class DatabaseUpdater {

	private static final String LEGACY_TABLE_SUFFIX = "_legacy";
	private static final String LEGACY_ITEMBREAKS_SUBCATEGORY = "any";

	private final Logger logger;

	@Inject
	DatabaseUpdater(Logger logger) {
		this.logger = logger;
	}

	/**
	 * Renames the database tables with the prefix given in the configuration file. This method is only used and only
	 * works if the tables had the default name. It does not support multiple successive table renamings.
	 * 
	 * @param databaseManager
	 * @throws PluginLoadError
	 */
	void renameExistingTables(AbstractDatabaseManager databaseManager) throws PluginLoadError {
		// If a prefix is set in the config, check whether the tables with the default names exist. If so do renaming.
		if (StringUtils.isNotBlank(databaseManager.getPrefix())) {
			try (ResultSet rs = databaseManager.getConnection().getMetaData().getTables(null, null, "achievements", null)) {
				// If the achievements table still has its default name (ie. no prefix), but a prefix is set in the
				// configuration, do a renaming of all tables.
				if (rs.next()) {
					logger.info("Adding " + databaseManager.getPrefix() + " prefix to database table names, please wait...");
					try (Statement st = databaseManager.getConnection().createStatement()) {
						st.addBatch("ALTER TABLE achievements RENAME TO " + databaseManager.getPrefix() + "achievements");
						for (NormalAchievements category : NormalAchievements.values()) {
							st.addBatch("ALTER TABLE " + category.toDBName() + " RENAME TO " + databaseManager.getPrefix()
									+ category.toDBName());
						}
						for (MultipleAchievements category : MultipleAchievements.values()) {
							st.addBatch("ALTER TABLE " + category.toDBName() + " RENAME TO " + databaseManager.getPrefix()
									+ category.toDBName());
						}
						st.executeBatch();
					}
				}
			} catch (SQLException e) {
				throw new PluginLoadError("Error while setting prefix of database tables.", e);
			}
		}
	}

	/**
	 * Initialises database tables by creating non existing ones. We batch the requests to send a unique batch to the
	 * database.
	 * 
	 * @param databaseManager
	 * @param size
	 * @throws PluginLoadError
	 */
	void initialiseTables(AbstractDatabaseManager databaseManager, int size) throws PluginLoadError {
		try (Statement st = databaseManager.getConnection().createStatement()) {
			st.addBatch("CREATE TABLE IF NOT EXISTS " + databaseManager.getPrefix()
					+ "achievements (playername char(36),achievement varchar(64),date TIMESTAMP,PRIMARY KEY (playername, achievement))");

			for (MultipleAchievements category : MultipleAchievements.values()) {
				st.addBatch("CREATE TABLE IF NOT EXISTS " + databaseManager.getPrefix() + category.toDBName()
						+ " (playername char(36)," + category.toSubcategoryDBName() + " varchar(" + size + "),"
						+ category.toDBName() + " INT,PRIMARY KEY(playername, " + category.toSubcategoryDBName() + "))");
			}

			for (NormalAchievements category : NormalAchievements.values()) {
				if (category == NormalAchievements.CONNECTIONS) {
					st.addBatch("CREATE TABLE IF NOT EXISTS " + databaseManager.getPrefix() + category.toDBName()
							+ " (playername char(36)," + category.toDBName()
							+ " INT,date varchar(10),PRIMARY KEY (playername))");
				} else {
					st.addBatch("CREATE TABLE IF NOT EXISTS " + databaseManager.getPrefix() + category.toDBName()
							+ " (playername char(36)," + category.toDBName() + " BIGINT,PRIMARY KEY (playername))");
				}
			}
			st.executeBatch();
		} catch (SQLException e) {
			throw new PluginLoadError("Error while initialising database tables.", e);
		}
	}

	/**
	 * Renames the itembreaks table if it still uses the old schema of the days when ItemBreaks was a category without
	 * sub-categories. The renamed table is kept as a backup and its data is copied over by
	 * {@link #copyLegacyItemBreaksData(AbstractDatabaseManager)} once the new table has been created.
	 *
	 * @param databaseManager
	 * @return true if a legacy table was renamed and its data must be copied over, false otherwise
	 */
	boolean renameLegacyItemBreaksTable(AbstractDatabaseManager databaseManager) {
		String table = databaseManager.getPrefix() + MultipleAchievements.ITEMBREAKS.toDBName();
		try (Statement st = databaseManager.getConnection().createStatement()) {
			if (!tableExistsWithoutSubcategoryColumn(st, table)) {
				return false;
			}
			logger.info("Converting the " + table + " table to the sub-category format, please wait...");
			st.execute("DROP TABLE IF EXISTS " + table + LEGACY_TABLE_SUFFIX);
			st.execute("ALTER TABLE " + table + " RENAME TO " + table + LEGACY_TABLE_SUFFIX);
			return true;
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Database error while converting the old " + table + " table:", e);
			return false;
		}
	}

	/**
	 * Copies the statistics of the legacy itembreaks table into the 'any' sub-category of the new one. The legacy table
	 * is left in the database as a backup.
	 *
	 * @param databaseManager
	 */
	void copyLegacyItemBreaksData(AbstractDatabaseManager databaseManager) {
		String dbName = MultipleAchievements.ITEMBREAKS.toDBName();
		String table = databaseManager.getPrefix() + dbName;
		String legacyTable = table + LEGACY_TABLE_SUFFIX;
		try (Statement st = databaseManager.getConnection().createStatement()) {
			int migrated = st.executeUpdate("INSERT INTO " + table + " (playername, "
					+ MultipleAchievements.ITEMBREAKS.toSubcategoryDBName() + ", " + dbName + ") SELECT playername, '"
					+ LEGACY_ITEMBREAKS_SUBCATEGORY + "', " + dbName + " FROM " + legacyTable);
			logger.info("Converted " + migrated + " " + dbName + " statistics to the '" + LEGACY_ITEMBREAKS_SUBCATEGORY
					+ "' sub-category. The previous data was kept in the " + legacyTable + " table.");
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Database error while copying the old " + table + " statistics:", e);
		}
	}

	/**
	 * Determines whether the table exists and is missing the sub-category column of the ItemBreaks category.
	 *
	 * @param st
	 * @param table
	 * @return true if the table uses the old schema, false if it does not exist or already has the column
	 */
	private boolean tableExistsWithoutSubcategoryColumn(Statement st, String table) {
		try (ResultSet rs = st.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
			ResultSetMetaData metaData = rs.getMetaData();
			for (int column = 1; column <= metaData.getColumnCount(); ++column) {
				if (MultipleAchievements.ITEMBREAKS.toSubcategoryDBName().equalsIgnoreCase(metaData.getColumnName(column))) {
					return false;
				}
			}
			return true;
		} catch (SQLException tableDoesNotExist) {
			// Fresh installation, or table renamed by a previous run: nothing to convert.
			return false;
		}
	}

	/**
	 * Increases the size of the sub-category column of MultipleAchievements database tables to accommodate new
	 * parameters such as specificplayer-56c79b19-4500-466c-94ea-514a755fdd09 or grouped sub-categories.
	 * 
	 * @param databaseManager
	 * @param category
	 * @param size
	 */
	void updateOldDBColumnSize(AbstractDatabaseManager databaseManager, MultipleAchievements category, int size) {
		// SQLite ignores size for varchar datatype.
		if (!(databaseManager instanceof SQLiteDatabaseManager)) {
			try (Statement st = databaseManager.getConnection().createStatement();
					ResultSet rs = st.executeQuery("SELECT " + category.toSubcategoryDBName() + " FROM "
							+ databaseManager.getPrefix() + category.toDBName() + " LIMIT 1")) {
				if (rs.getMetaData().getPrecision(1) < size) {
					logger.info("Changing " + category.toDBName() + " database column size to " + size + ", please wait...");
					String alterOperation = databaseManager instanceof MySQLDatabaseManager
							? "MODIFY " + category.toSubcategoryDBName() + " varchar(" + size + ")"
							: "ALTER COLUMN " + category.toSubcategoryDBName() + " TYPE varchar(" + size + ")";
					st.execute("ALTER TABLE " + databaseManager.getPrefix() + category.toDBName() + " " + alterOperation);
				}
			} catch (SQLException e) {
				logger.log(Level.SEVERE, "Database error while updating old " + category.toDBName() + " table:", e);
			}
		}
	}
}
