package com.hm.achievement.db;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;

/**
 * Class used to write the modified cached statistics to the database.
 *
 * @author Pyves
 *
 */
public class AsyncCachedRequestsSender implements Runnable {

	private final CacheManager cacheManager;
	private final AbstractDatabaseManager databaseManager;

	@Inject
	public AsyncCachedRequestsSender(CacheManager cacheManager, AbstractDatabaseManager databaseManager) {
		this.cacheManager = cacheManager;
		this.databaseManager = databaseManager;
	}

	/**
	 * Writes cached statistics to the database, with batched writes for efficiency purposes. If a failure occurs, the
	 * same queries will be attempted again.
	 */
	@Override
	public void run() {
		sendBatchedRequests();
		cacheManager.cleanStaleCaches();
	}

	/**
	 * Writes cached statistics to the database, with batched writes for efficiency purposes. If a failure occurs, the
	 * same queries will be attempted again.
	 */
	public void sendBatchedRequests() {
		List<String> batchedRequests = new ArrayList<>();
		List<PersistedStatistic> persistedStatistics = new ArrayList<>();
		for (MultipleAchievements category : MultipleAchievements.values()) {
			addRequestsForMultipleCategory(batchedRequests, persistedStatistics, category);
		}
		for (NormalAchievements category : NormalAchievements.values()) {
			addRequestsForNormalCategory(batchedRequests, persistedStatistics, category);
		}

		if (!batchedRequests.isEmpty()) {
			databaseManager.queueWrite(() -> {
				try (Statement st = databaseManager.getConnection().createStatement()) {
					for (String request : batchedRequests) {
						st.addBatch(request);
					}
					st.executeBatch();
				}
			}, "batching statistic updates", () -> persistedStatistics.forEach(PersistedStatistic::markPersisted));
		}
	}

	/**
	 * Adds the database queries to perform for a given Multiple category.
	 *
	 * PostgreSQL has no REPLACE operator. We have to use the INSERT ... ON CONFLICT construct, which is available for
	 * PostgreSQL 9.5+.
	 *
	 * @param batchedRequests
	 * @param category
	 */
	private void addRequestsForMultipleCategory(List<String> batchedRequests,
			List<PersistedStatistic> persistedStatistics, MultipleAchievements category) {
		Map<SubcategoryUUID, CachedStatistic> categoryMap = cacheManager.getHashMap(category);
		for (Entry<SubcategoryUUID, CachedStatistic> entry : categoryMap.entrySet()) {
			CachedStatistic statistic = entry.getValue();
			CachedStatistic.WriteSnapshot snapshot = statistic.snapshotForWrite();
			if (snapshot != null) {
				persistedStatistics.add(new PersistedStatistic(statistic, snapshot));
				UUID uuid = entry.getKey().getUUID();
				String subcategory = StringUtils.replace(entry.getKey().getSubcategory(), "'", "''");
				if (databaseManager instanceof PostgreSQLDatabaseManager) {
					batchedRequests.add("INSERT INTO " + databaseManager.getPrefix() + category.toDBName() + " VALUES ('"
							+ uuid + "', '" + subcategory + "', " + snapshot.getValue() + ") ON CONFLICT (playername, "
							+ category.toSubcategoryDBName() + ") DO UPDATE SET (" + category.toDBName() + ")=("
							+ snapshot.getValue() + ")");
				} else {
					batchedRequests.add("REPLACE INTO " + databaseManager.getPrefix() + category.toDBName() + " VALUES ('"
							+ uuid + "', '" + subcategory + "', " + snapshot.getValue() + ")");
				}
			}
		}
	}

	/**
	 * Adds the database queries to perform for a given Normal category.
	 *
	 * PostgreSQL has no REPLACE operator. We have to use the INSERT ... ON CONFLICT construct, which is available for
	 * PostgreSQL 9.5+.
	 *
	 * @param batchedRequests
	 * @param category
	 */
	private void addRequestsForNormalCategory(List<String> batchedRequests,
			List<PersistedStatistic> persistedStatistics, NormalAchievements category) {
		Map<UUID, CachedStatistic> categoryMap = cacheManager.getHashMap(category);
		for (Entry<UUID, CachedStatistic> entry : categoryMap.entrySet()) {
			CachedStatistic statistic = entry.getValue();
			CachedStatistic.WriteSnapshot snapshot = statistic.snapshotForWrite();
			if (snapshot != null) {
				persistedStatistics.add(new PersistedStatistic(statistic, snapshot));
				UUID uuid = entry.getKey();
				if (databaseManager instanceof PostgreSQLDatabaseManager) {
					batchedRequests.add("INSERT INTO " + databaseManager.getPrefix() + category.toDBName() + " VALUES ('"
							+ uuid + "', " + snapshot.getValue() + ") ON CONFLICT (playername) DO UPDATE SET ("
							+ category.toDBName() + ")=(" + snapshot.getValue() + ")");
				} else {
					batchedRequests.add("REPLACE INTO " + databaseManager.getPrefix() + category.toDBName() + " VALUES ('"
							+ uuid + "', " + snapshot.getValue() + ")");
				}
			}
		}
	}

	private static class PersistedStatistic {

		private final CachedStatistic statistic;
		private final CachedStatistic.WriteSnapshot snapshot;

		PersistedStatistic(CachedStatistic statistic, CachedStatistic.WriteSnapshot snapshot) {
			this.statistic = statistic;
			this.snapshot = snapshot;
		}

		void markPersisted() {
			statistic.markPersisted(snapshot.getRevision());
		}
	}

}
