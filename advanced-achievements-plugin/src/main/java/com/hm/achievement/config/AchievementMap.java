package com.hm.achievement.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.hm.achievement.category.Category;
import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.utils.StringHelper;

@Singleton
public class AchievementMap {

	private final Map<String, Achievement> namesToAchievements = new HashMap<>();
	private final Map<String, Achievement> sanitisedDisplayNamesToAchievements = new HashMap<>();
	private final Map<Category, List<Achievement>> categoriesToAchievements = new HashMap<>();
	private final Map<String, List<Achievement>> categoriesSubcategoriesToAchievements = new HashMap<>();
	private final Map<Category, Set<String>> categoriesToSubcategories = new HashMap<>();

	@Inject
	public AchievementMap() {
	}

	public void put(Achievement achievement) {
		namesToAchievements.put(achievement.getName(), achievement);
		sanitisedDisplayNamesToAchievements.put(sanitise(achievement.getDisplayName()), achievement);
		Category category = achievement.getCategory();
		String subcategory = Objects.toString(achievement.getSubcategory(), "");
		categoriesToAchievements.computeIfAbsent(category, c -> new ArrayList<>()).add(achievement);
		categoriesToSubcategories.computeIfAbsent(category, c -> new HashSet<>()).add(subcategory);
		if (category instanceof NormalAchievements) {
			String key = subcategory.isEmpty()
					? category.toString()
					: category + "." + subcategory;
			categoriesSubcategoriesToAchievements.computeIfAbsent(key, c -> new ArrayList<>())
					.add(achievement);
		} else if (category instanceof MultipleAchievements) {
			categoriesSubcategoriesToAchievements
					.computeIfAbsent(category + "." + achievement.getSubcategory(), c -> new ArrayList<>()).add(achievement);
		}
	}

	public Collection<Achievement> getAll() {
		return namesToAchievements.values();
	}

	public Collection<String> getAllNames() {
		return namesToAchievements.keySet();
	}

	public Collection<String> getAllSanitisedDisplayNames() {
		return sanitisedDisplayNamesToAchievements.keySet();
	}

	public void clearAll() {
		namesToAchievements.clear();
		sanitisedDisplayNamesToAchievements.clear();
		categoriesToAchievements.clear();
		categoriesSubcategoriesToAchievements.clear();
		categoriesToSubcategories.clear();
	}

	public void replaceWith(AchievementMap replacement) {
		clearAll();
		// Sort before re-inserting: getAll() is backed by a HashMap and is therefore unordered, while put() appends to
		// the lists consumers read in order. Sorting by subcategory first keeps a category's achievements contiguous
		// per subcategory, which AdvancementManager relies on to find advancement-chain boundaries; sorting by
		// threshold within a subcategory is what StatisticIncreaseHandler's early exit expects.
		replacement.getAll().stream()
				.sorted(Comparator.comparing((Achievement achievement) -> Objects.toString(achievement.getSubcategory(), ""))
						.thenComparingLong(Achievement::getThreshold))
				.forEach(this::put);
	}

	public Achievement getForName(String name) {
		return namesToAchievements.get(name);
	}

	public Achievement getForDisplayName(String displayName) {
		return sanitisedDisplayNamesToAchievements.get(sanitise(displayName));
	}

	public List<Achievement> getForCategory(Category category) {
		return categoriesToAchievements.getOrDefault(category, Collections.emptyList());
	}

	public List<Achievement> getForCategoryAndSubcategory(Category category, String subcategory) {
		String key = category instanceof NormalAchievements && subcategory.isEmpty()
				? category.toString()
				: category + "." + subcategory;
		return categoriesSubcategoriesToAchievements.getOrDefault(key, Collections.emptyList());
	}

	public Set<String> getSubcategoriesForCategory(Category category) {
		return categoriesToSubcategories.getOrDefault(category, Collections.emptySet());
	}

	public Set<String> getCategorySubcategories() {
		return categoriesSubcategoriesToAchievements.keySet();
	}

	private String sanitise(String displayName) {
		return StringHelper.removeFormattingCodes(displayName).toLowerCase();
	}

}
