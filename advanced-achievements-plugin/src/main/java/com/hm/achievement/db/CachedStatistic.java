package com.hm.achievement.db;

/**
 * Class used to provide a cache wrapper for a database statistic.
 *
 * @author Pyves
 *
 */
public class CachedStatistic {

	// Value of the statistic. Can only be modified by the main server thread.
	private long value;
	private long revision;
	private long persistedRevision;
	// Indicates whether the player linked to this statistic has recently disconnected. Can only be modified by the main
	// server thread.
	private volatile boolean disconnection;

	public CachedStatistic(long value, boolean databaseConsistent) {
		this.value = value;
		revision = databaseConsistent ? 0L : 1L;
		persistedRevision = 0L;
		disconnection = false;
	}

	public synchronized long getValue() {
		return value;
	}

	public synchronized void setValue(long value) {
		this.value = value;
		revision++;
	}

	public synchronized boolean isDatabaseConsistent() {
		return revision == persistedRevision;
	}

	public synchronized WriteSnapshot snapshotForWrite() {
		return isDatabaseConsistent() ? null : new WriteSnapshot(value, revision);
	}

	public synchronized void markPersisted(long writtenRevision) {
		if (revision == writtenRevision) {
			persistedRevision = writtenRevision;
		}
	}

	public boolean didPlayerDisconnect() {
		return disconnection;
	}

	public void signalPlayerDisconnection() {
		disconnection = true;
	}

	public void resetDisconnection() {
		disconnection = false;
	}

	public static final class WriteSnapshot {

		private final long value;
		private final long revision;

		private WriteSnapshot(long value, long revision) {
			this.value = value;
			this.revision = revision;
		}

		public long getValue() {
			return value;
		}

		public long getRevision() {
			return revision;
		}
	}
}
