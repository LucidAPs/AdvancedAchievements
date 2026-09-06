package com.hm.achievement.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
		categoriesToAchievements.computeIfAbsent(category, c -> new ArrayList<>()).add(achievement);
		categoriesToSubcategories.computeIfAbsent(category, c -> new HashSet<>()).add(achievement.getSubcategory());
		if (category instanceof NormalAchievements) {
			categoriesSubcategoriesToAchievements.computeIfAbsent(category.toString(), c -> new ArrayList<>())
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
		// Sort by threshold before re-inserting: getAll() is backed by a HashMap and is therefore unordered, while
		// put() appends to the per-subcategory lists. Consumers read those lists in order, so the ordering has to be
		// established here rather than inherited from the replacement's iteration order.
		replacement.getAll().stream()
				.sorted(Comparator.comparingLong(Achievement::getThreshold))
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
		return categoriesSubcategoriesToAchievements.get(category + "." + subcategory);
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
