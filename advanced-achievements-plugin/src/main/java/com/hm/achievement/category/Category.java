package com.hm.achievement.category;

/**
 * Interface for Achievement Category Enums.
 */
public interface Category {

	/**
	 * Converts to database name: name of the enum in lower case.
	 *
	 * @return the name used for the database table
	 */
	String toDBName();
}
