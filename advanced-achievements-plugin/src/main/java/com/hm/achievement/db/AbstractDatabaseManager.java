package com.hm.achievement.db;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.bukkit.configuration.file.YamlConfiguration;

import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.db.data.AwardedDBAchievement;
import com.hm.achievement.db.data.ConnectionInformation;
import com.hm.achievement.exception.PluginLoadError;
import com.hm.achievement.lifecycle.Reloadable;

/**
 * Abstract class in charge of factoring out common functionality for the database manager.
 *
 * @author Pyves
 */
public abstract class AbstractDatabaseManager implements Reloadable {

	final YamlConfiguration mainConfig;
	final Logger logger;
	final String driverPath;
	final DatabaseExecutor databaseExecutor;

	volatile String prefix;

	// Connection to the database; remains opened and shared.
	private final AtomicReference<Connection> connectionHolder = new AtomicReference<>();
	private final DatabaseUpdater databaseUpdater;

	private volatile DateFormat dateFormat;
	private volatile long lastFailureTimestamp;
	private boolean configBookChronologicalOrder;
	private volatile boolean initialised = false;

	public AbstractDatabaseManager(YamlConfiguration mainConfig, Logger logger, DatabaseUpdater databaseUpdater,
			String driverPath, DatabaseExecutor databaseExecutor) {
		this.mainConfig = mainConfig;
		this.logger = logger;
		this.databaseUpdater = databaseUpdater;
		this.driverPath = driverPath;
		this.databaseExecutor = databaseExecutor;
	}

	@Override
	public void extractConfigurationParameters() {
		configBookChronologicalOrder = mainConfig.getBoolean("BookChronologicalOrder");
		String localeString = mainConfig.getString("DateLocale");
		boolean dateDisplayTime = mainConfig.getBoolean("DateDisplayTime");
		Locale locale = new Locale(localeString);
		if (dateDisplayTime) {
			dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);
		} else {
			dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale);
		}
	}

	/**
	 * Initialises the database system by extracting settings, performing setup tasks and updating schemas if necessary.
	 *
	 * @throws PluginLoadError
	 */
	public void initialise() throws PluginLoadError {
		logger.info("Initialising " + mainConfig.getString("DatabaseType") + " database...");

		prefix = mainConfig.getString("TablePrefix");

		try {
			performPreliminaryTasks();
		} catch (ClassNotFoundException e) {
			logger.severe("The JBDC driver for the chosen database type was not found.");
		}

		databaseExecutor.callChecked(() -> {
			// Try to establish connection with database; stays opened until explicitly closed by the plugin.
			try {
				getConnection();
			} catch (SQLException e) {
				throw new PluginLoadError(
						"Failed to establish database connection. Please verify your settings in config.yml.", e);
			}

			databaseUpdater.renameExistingTables(this);
			int size = mainConfig.getInt("TableMaxSizeOfGroupedSubcategories");
			databaseUpdater.initialiseTables(this, size);
			Arrays.stream(MultipleAchievements.values())
					.forEach(m -> databaseUpdater.updateOldDBColumnSize(this, m, size));
			initialised = true;
			return null;
		});
	}

	public boolean isInitialised() {
		return initialised;
	}

	/**
	 * Performs any needed tasks before opening a connection to the database.
	 *
	 * @throws ClassNotFoundException
	 * @throws PluginLoadError
	 */
	abstract void performPreliminaryTasks() throws ClassNotFoundException, PluginLoadError;

	/**
	 * Finishes queued operations and closes the database connection.
	 */
	public void shutdown() {
		databaseExecutor.execute(this::closeConnection);
		if (!databaseExecutor.shutdown()) {
			logger.warning("Some database operations could not be completed during plugin shutdown.");
		}
	}

	/**
	 * Retrieves SQL connection to MySQL, PostgreSQL, H2 or SQLite database.
	 *
	 * @return the cached SQL connection or a new one
	 */
	Connection getConnection() throws SQLException {
		Connection currentConnection = connectionHolder.get();
		// Check if Connection was not previously closed.
		if (currentConnection == null || currentConnection.isClosed()) {
			Connection newConnection = createConnection();
			if (!connectionHolder.compareAndSet(currentConnection, newConnection)) {
				newConnection.close();
			}
			return connectionHolder.get();
		}
		return currentConnection;
	}

	private void invalidateConnection(SQLException exception) {
		lastFailureTimestamp = System.currentTimeMillis();
		closeConnection();
	}

	private void closeConnection() {
		Connection connection = connectionHolder.getAndSet(null);
		if (connection != null) {
			try {
				connection.close();
			} catch (SQLException e) {
				logger.log(Level.SEVERE, "Error while closing connection to the database:", e);
			}
		}
	}

	private <T> T executeRead(SQLReadOperation<T> operation, String operationMessage) {
		return databaseExecutor.call(() -> operation.executeOperation(operationMessage, this::invalidateConnection));
	}

	void queueWrite(SQLWriteOperation operation, String operationMessage) {
		queueWrite(operation, operationMessage, () -> {
		});
	}

	void queueWrite(SQLWriteOperation operation, String operationMessage, Runnable successHandler) {
		databaseExecutor.execute(() -> {
			if (operation.attemptWrites(logger, operationMessage, this::invalidateConnection)) {
				successHandler.run();
			}
		});
	}

	public int getPendingOperationCount() {
		return databaseExecutor.getPendingOperationCount();
	}

	public long getLastFailureTimestamp() {
		return lastFailureTimestamp;
	}

	public boolean checkHealth() {
		return executeRead(() -> getConnection() != null && !getConnection().isClosed(), "checking database health");
	}

	/**
	 * Creates a new Connection object to the database.
	 *
	 * @return connection object to database
	 * @throws SQLException
	 */
	abstract Connection createConnection() throws SQLException;

	/**
	 * Gets the list of names of all the achievements of a player.
	 *
	 * @param uuid
	 * @return array list with Name parameters
	 */
	public Set<String> getPlayerAchievementNames(UUID uuid) {
		return executeRead(() -> {
			String sql = "SELECT achievement FROM " + prefix + "achievements WHERE playername = ?";
			Set<String> achievementNamesList = new HashSet<>();
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.setFetchSize(1000);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						achievementNamesList.add(rs.getString(1));
					}
				}
			}
			return achievementNamesList;
		}, "retrieving the names of received achievements");
	}

	/**
	 * Gets the reception date of a specific achievement.
	 *
	 * @param uuid
	 * @param achName
	 * @return date represented as a string
	 */
	public String getPlayerAchievementDate(UUID uuid, String achName) {
		return executeRead(() -> {
			String sql = "SELECT date FROM " + prefix + "achievements WHERE playername = ? AND achievement = ?";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.setString(2, achName);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						return formatDate(new Date(rs.getTimestamp(1).getTime()));
					}
				}
			}
			return null;
		}, "retrieving an achievement's reception date");
	}

	/**
	 * Gets all achievement reception dates for a player in a single query.
	 *
	 * @param uuid
	 * @return mapping of achievement names to formatted reception dates
	 */
	public Map<String, String> getPlayerAchievementDates(UUID uuid) {
		return executeRead(() -> {
			String sql = "SELECT achievement, date FROM " + prefix + "achievements WHERE playername = ?";
			Map<String, String> achievementDates = new HashMap<>();
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.setFetchSize(1000);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						achievementDates.put(rs.getString(1), formatDate(rs.getTimestamp(2)));
					}
				}
			}
			return achievementDates;
		}, "retrieving achievement reception dates");
	}

	/**
	 * Gets the total number of achievements received by every player; this method is provided as a convenience for
	 * other plugins.
	 *
	 * @return map containing number of achievements for every players
	 */
	public Map<UUID, Integer> getPlayersAchievementsAmount() {
		return executeRead(() -> {
			String sql = "SELECT playername, COUNT(*) FROM " + prefix + "achievements GROUP BY playername";
			Map<UUID, Integer> achievementAmounts = new HashMap<>();
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setFetchSize(1000);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						String uuid = rs.getString(1);
						if (StringUtils.isNotEmpty(uuid)) {
							achievementAmounts.put(UUID.fromString(uuid), rs.getInt(2));
						}
					}
				}
			}
			return achievementAmounts;
		}, "counting all players' achievements");
	}

	/**
	 * Constructs a mapping of players with the most achievements over a given period.
	 *
	 * @param start
	 * @return LinkedHashMap with keys corresponding to player UUIDs and values corresponding to their achievement count
	 */
	public Map<String, Integer> getTopList(long start) {
		return executeRead(() -> {
			// Either consider all the achievements or only those received after the start date.
			String sql = start == 0L
					? "SELECT playername, COUNT(*) FROM " + prefix
							+ "achievements GROUP BY playername ORDER BY COUNT(*) DESC"
					: "SELECT playername, COUNT(*) FROM " + prefix
							+ "achievements WHERE date > ? GROUP BY playername ORDER BY COUNT(*) DESC";
			Map<String, Integer> topList = new LinkedHashMap<>();
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				if (start > 0L) {
					ps.setTimestamp(1, new Timestamp(start));
				}
				ps.setFetchSize(1000);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						topList.put(rs.getString(1), rs.getInt(2));
					}
				}
			}
			return topList;
		}, "computing the list of top players");
	}

	/**
	 * Registers a new achievement for a player.
	 *
	 * @param uuid
	 * @param achName
	 * @param epochMs Moment the achievement was registered at.
	 */
	public void registerAchievement(UUID uuid, String achName, long epochMs) {
		queueWrite(() -> {
			String sql = "REPLACE INTO " + prefix + "achievements VALUES (?,?,?)";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.setString(2, achName);
				ps.setTimestamp(3, new Timestamp(epochMs));
				ps.execute();
			}
		}, "registering an achievement");
	}

	/**
	 * Gets a player's NormalAchievement statistic.
	 *
	 * @param uuid
	 * @param category
	 * @return statistic
	 */
	public long getNormalAchievementAmount(UUID uuid, NormalAchievements category) {
		return executeRead(() -> {
			String dbName = category.toDBName();
			String sql = "SELECT " + dbName + " FROM " + prefix + dbName + " WHERE playername = ?";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						return rs.getLong(1);
					}
				}
			}
			return 0L;
		}, "retrieving " + category + " statistics");
	}

	/**
	 * Gets a player's MultipleAchievement statistic.
	 *
	 * @param uuid
	 * @param category
	 * @param subcategory
	 * @return statistic
	 */
	public long getMultipleAchievementAmount(UUID uuid, MultipleAchievements category, String subcategory) {
		return executeRead(() -> {
			String dbName = category.toDBName();
			String sql = "SELECT " + dbName + " FROM " + prefix + dbName + " WHERE playername = ? AND "
					+ category.toSubcategoryDBName() + " = ?";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.setString(2, subcategory);
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						return rs.getLong(1);
					}
				}
			}
			return category == MultipleAchievements.JOBSREBORN ? 1L : 0L;
		}, "retrieving " + category + "." + subcategory + " statistics");
	}

	/**
	 * Gets all of a player's statistics for a multiple-achievement category in a single query.
	 *
	 * @param uuid
	 * @param category
	 * @return mapping of subcategories to statistics
	 */
	public Map<String, Long> getMultipleAchievementAmounts(UUID uuid, MultipleAchievements category) {
		return executeRead(() -> {
			String dbName = category.toDBName();
			String sql = "SELECT " + category.toSubcategoryDBName() + ", " + dbName + " FROM " + prefix + dbName
					+ " WHERE playername = ?";
			Map<String, Long> achievementAmounts = new HashMap<>();
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.setFetchSize(1000);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						achievementAmounts.put(rs.getString(1), rs.getLong(2));
					}
				}
			}
			return achievementAmounts;
		}, "retrieving " + category + " statistics");
	}

	/**
	 * Returns a player's last connection data and the total number of connections.
	 *
	 * @param uuid
	 * @return the connection information
	 */
	public ConnectionInformation getConnectionInformation(UUID uuid) {
		return executeRead(() -> {
			String dbName = NormalAchievements.CONNECTIONS.toDBName();
			String sql = "SELECT " + dbName + ", date FROM " + prefix + dbName + " WHERE playername = ?";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						return new ConnectionInformation(rs.getString(2), rs.getLong(1));
					}
				}
			}
			return new ConnectionInformation();
		}, "retrieving a player's connection information");
	}

	/**
	 * Updates a player's number of connections and last connection date.
	 *
	 * @param uuid
	 * @param connections
	 */
	public void updateConnectionInformation(UUID uuid, long connections) {
		queueWrite(() -> {
			String sql = "REPLACE INTO " + prefix + NormalAchievements.CONNECTIONS.toDBName() + " VALUES (?,?,?)";
			try (PreparedStatement writePrep = getConnection().prepareStatement(sql)) {
				writePrep.setString(1, uuid.toString());
				writePrep.setLong(2, connections);
				writePrep.setString(3, ConnectionInformation.today());
				writePrep.execute();
			}
		}, "updating connection date and count");
	}

	/**
	 * Deletes an achievement from a player.
	 *
	 * @param uuid
	 * @param achName
	 */
	public void deletePlayerAchievement(UUID uuid, String achName) {
		queueWrite(() -> {
			String sql = "DELETE FROM " + prefix + "achievements WHERE playername = ? AND achievement = ?";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.setString(2, achName);
				ps.execute();
			}
		}, "deleting an achievement");
	}

	/**
	 * Deletes all achievements from a player.
	 *
	 * @param uuid
	 */
	public void deleteAllPlayerAchievements(UUID uuid) {
		queueWrite(() -> {
			String sql = "DELETE FROM " + prefix + "achievements WHERE playername = ?";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setString(1, uuid.toString());
				ps.execute();
			}
		}, "deleting all achievements");
	}

	/**
	 * Clears Connection statistics for a given player.
	 *
	 * @param uuid
	 */
	public void clearConnection(UUID uuid) {
		queueWrite(() -> {
			String sql = "DELETE FROM " + prefix + "connections WHERE playername = '" + uuid + "'";
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.execute();
			}
		}, "clearing connection statistics");
	}

	String getPrefix() {
		return prefix;
	}

	/**
	 * Returns a list of AwardedDBAchievements get by a player.
	 *
	 * @param uuid UUID of a player.
	 * @return ArrayList containing all information about achievements awarded to a player.
	 */
	public List<AwardedDBAchievement> getPlayerAchievementsList(UUID uuid) {
		return executeRead(() -> {
			// Either oldest date to newest one or newest date to oldest one.
			String sql = "SELECT achievement, date FROM " + prefix + "achievements WHERE playername = ? ORDER BY date "
					+ (configBookChronologicalOrder ? "ASC" : "DESC");
			List<AwardedDBAchievement> achievements = new ArrayList<>();
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setFetchSize(1000);
				ps.setString(1, uuid.toString());
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						Timestamp dateAwarded = rs.getTimestamp(2);
						achievements.add(new AwardedDBAchievement(uuid, rs.getString(1), dateAwarded.getTime(),
								formatDate(dateAwarded)));
					}
				}
			}
			return achievements;
		}, "retrieving the full data of received achievements");
	}

	/**
	 * Retrieve matching list of achievements for a name of an achievement.
	 * <p>
	 * Limited to 1000 most recent entries to save memory.
	 *
	 * @param achievementName Name of an achievement in database format.
	 * @return List of AwardedDBAchievement objects, message field is empty to save memory.
	 */
	public List<AwardedDBAchievement> getAchievementsRecipientList(String achievementName) {
		String sql = "SELECT playername, date FROM " + prefix + "achievements WHERE achievement = ?" +
				" ORDER BY date DESC LIMIT 1000";
		return executeRead(() -> {
			List<AwardedDBAchievement> achievements = new ArrayList<>();
			try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
				ps.setFetchSize(1000);
				ps.setString(1, achievementName);
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						UUID uuid;
						try {
							String uuidString = rs.getString(1);
							uuid = UUID.fromString(uuidString);
						} catch (IllegalArgumentException improperUUIDFormatException) {
							continue;
						}
						Date dateAwarded = new Date(rs.getTimestamp(2).getTime());
						achievements.add(new AwardedDBAchievement(uuid, achievementName, dateAwarded.getTime(),
								formatDate(dateAwarded)));
					}
				}
			}
			return achievements;
		}, "retrieving the recipients of an achievement");
	}

	private String formatDate(java.util.Date date) {
		DateFormat currentDateFormat = dateFormat;
		synchronized (currentDateFormat) {
			return currentDateFormat.format(date);
		}
	}
}
