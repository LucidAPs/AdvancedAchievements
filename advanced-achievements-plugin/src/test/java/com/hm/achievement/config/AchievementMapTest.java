package com.hm.achievement.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.hm.achievement.category.CommandAchievements;
import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.domain.Achievement;
import com.hm.achievement.domain.Achievement.AchievementBuilder;

class AchievementMapTest {

	private AchievementMap underTest;

	@BeforeEach
	void setUp() throws Exception {
		underTest = new AchievementMap();
	}

	@Test
	void shouldReturnAllAchievements() {
		Achievement achievement1 = new AchievementBuilder().name("ach1").displayName("&1Display 1").build();
		Achievement achievement2 = new AchievementBuilder().name("ach2").displayName("Display 2").build();

		underTest.put(achievement1);
		underTest.put(achievement2);

		Collection<Achievement> allAchievements = underTest.getAll();
		assertEquals(2, allAchievements.size());
		assertTrue(allAchievements.contains(achievement1));
		assertTrue(allAchievements.contains(achievement2));
	}

	@Test
	void shouldReturnAllAchievementNames() {
		Achievement achievement1 = new AchievementBuilder().name("ach1").displayName("&1Display 1").build();
		Achievement achievement2 = new AchievementBuilder().name("ach2").displayName("Display 2").build();

		underTest.put(achievement1);
		underTest.put(achievement2);

		Collection<String> names = underTest.getAllNames();
		assertEquals(2, names.size());
		assertTrue(names.contains("ach1"));
		assertTrue(names.contains("ach2"));
	}

	@Test
	void shouldReturnAchievementDisplayNamesInLowercaseWithoutFormattingCodes() {
		Achievement achievement1 = new AchievementBuilder().displayName("&1Display 1").build();
		Achievement achievement2 = new AchievementBuilder().displayName("Display 2").build();

		underTest.put(achievement1);
		underTest.put(achievement2);

		Collection<String> names = underTest.getAllSanitisedDisplayNames();
		assertEquals(2, names.size());
		assertTrue(names.contains("display 1"));
		assertTrue(names.contains("display 2"));
	}

	@Test
	void shouldReturnAchievementByName() {
		Achievement achievement1 = new AchievementBuilder().name("ach1").displayName("&1Display 1").build();
		Achievement achievement2 = new AchievementBuilder().name("ach2").displayName("Display 2").build();

		underTest.put(achievement1);
		underTest.put(achievement2);

		assertEquals(achievement2, underTest.getForName("ach2"));
	}

	@Test
	void shouldReturnAchievementByDisplayName() {
		Achievement achievement1 = new AchievementBuilder().displayName("&1Display 1").build();
		Achievement achievement2 = new AchievementBuilder().displayName("Display 2").build();

		underTest.put(achievement1);
		underTest.put(achievement2);

		assertEquals(achievement1, underTest.getForDisplayName("&1Display 1"));
	}

	@Test
	void shouldReturnAchievementsByCategory() {
		Achievement achievement1 = new AchievementBuilder().name("ach1").displayName("Display 1")
				.category(MultipleAchievements.PLACES).build();
		Achievement achievement2 = new AchievementBuilder().name("ach2").displayName("Display 2")
				.category(NormalAchievements.SMELTING).build();
		Achievement achievement3 = new AchievementBuilder().name("ach3").displayName("Display 3")
				.category(MultipleAchievements.PLACES).build();

		underTest.put(achievement1);
		underTest.put(achievement2);
		underTest.put(achievement3);

		assertEquals(Arrays.asList(achievement1, achievement3), underTest.getForCategory(MultipleAchievements.PLACES));
		assertEquals(Arrays.asList(achievement2), underTest.getForCategory(NormalAchievements.SMELTING));
	}

	@Test
	void shouldReturnAchievementsByCategoryAndSubcategory() {
		Achievement achievement1 = new AchievementBuilder().name("ach1").displayName("Display 1")
				.category(MultipleAchievements.PLACES).subcategory("stone").build();
		Achievement achievement2 = new AchievementBuilder().name("ach2").displayName("Display 2")
				.category(NormalAchievements.SMELTING).subcategory("iron_ingot").build();
		Achievement achievement3 = new AchievementBuilder().name("ach3").displayName("Display 3")
				.category(MultipleAchievements.PLACES).subcategory("brick").build();

		underTest.put(achievement1);
		underTest.put(achievement2);
		underTest.put(achievement3);

		assertEquals(Arrays.asList(achievement1),
				underTest.getForCategoryAndSubcategory(MultipleAchievements.PLACES, "stone"));
		assertEquals(Arrays.asList(achievement2),
				underTest.getForCategoryAndSubcategory(NormalAchievements.SMELTING, "iron_ingot"));
	}

	@Test
	void shouldReturnSubcategoriesForCategory() {
		Achievement achievement1 = new AchievementBuilder().name("ach1").displayName("Display 1")
				.category(MultipleAchievements.PLACES).subcategory("stone").build();
		Achievement achievement2 = new AchievementBuilder().name("ach2").displayName("Display 2")
				.category(MultipleAchievements.KILLS).subcategory("skeleton").build();
		Achievement achievement3 = new AchievementBuilder().name("ach3").displayName("Display 3")
				.category(MultipleAchievements.PLACES).subcategory("brick").build();

		underTest.put(achievement1);
		underTest.put(achievement2);
		underTest.put(achievement3);

		Set<String> materials = new HashSet<>();
		materials.add("stone");
		materials.add("brick");
		assertEquals(materials, underTest.getSubcategoriesForCategory(MultipleAchievements.PLACES));
		assertEquals(Collections.singleton("skeleton"), underTest.getSubcategoriesForCategory(MultipleAchievements.KILLS));
	}

	@Test
	void shouldReturnAllCategorySubcategoriesForNormalAndMutipleCategories() {
		Achievement achievement1 = new AchievementBuilder().name("ach1").displayName("Display 1")
				.category(MultipleAchievements.PLACES).subcategory("stone").build();
		Achievement achievement2 = new AchievementBuilder().name("ach2").displayName("Display 2")
				.category(CommandAchievements.COMMANDS).subcategory("yourAch1").build();
		Achievement achievement3 = new AchievementBuilder().name("ach3").displayName("Display 3")
				.category(NormalAchievements.ANVILS).subcategory("").build();

		underTest.put(achievement1);
		underTest.put(achievement2);
		underTest.put(achievement3);

		Set<String> categorySubcategories = new HashSet<>();
		categorySubcategories.add("Places.stone");
		categorySubcategories.add("AnvilsUsed");
		assertEquals(categorySubcategories, underTest.getCategorySubcategories());
	}

	@Test
	void shouldClearAllMapEntries() {
		Achievement achievement1 = new AchievementBuilder().category(MultipleAchievements.KILLS).subcategory("skeleton")
				.name("ach1").displayName("Display 1").build();

		underTest.put(achievement1);

		underTest.clearAll();

		assertNull(underTest.getForName("ach1"));
		assertNull(underTest.getForDisplayName("Display 1"));
		assertTrue(underTest.getForCategory(MultipleAchievements.KILLS).isEmpty());
		assertTrue(underTest.getSubcategoriesForCategory(MultipleAchievements.KILLS).isEmpty());
		assertTrue(underTest.getCategorySubcategories().isEmpty());
	}

	/**
	 * Regression test for the 11.6 threshold-ordering defect.
	 *
	 * <p>
	 * {@code replaceWith} re-inserts via {@code getAll()}, which is {@code HashMap.values()} and therefore unordered.
	 * The per-subcategory list it rebuilds must still come out ascending by threshold, because
	 * {@code StatisticIncreaseHandler} relies on that ordering for its early exit.
	 *
	 * <p>
	 * The three names below are chosen deliberately, not arbitrarily: under a real {@link java.util.HashMap} at this
	 * map's table size they land in buckets 15, 11 and 0, so {@code values()} yields them in threshold order 20000,
	 * 1000, 100 — the exact inversion observed in production on the {@code stone|deepslate} group. A triple of
	 * arbitrary names would very likely iterate ascending by chance and the test would pass against the unpatched code,
	 * proving nothing.
	 */
	@Test
	void shouldReplaceWithAscendingThresholdOrderRegardlessOfInputOrder() {
		Achievement high = new AchievementBuilder().category(MultipleAchievements.BREAKS).subcategory("deepslate")
				.name("break_20000_deepslate").displayName("High").threshold(20000).build();
		Achievement mid = new AchievementBuilder().category(MultipleAchievements.BREAKS).subcategory("deepslate")
				.name("break_1000_deepslate").displayName("Mid").threshold(1000).build();
		Achievement low = new AchievementBuilder().category(MultipleAchievements.BREAKS).subcategory("deepslate")
				.name("break_100_deepslate").displayName("Low").threshold(100).build();
		AchievementMap replacement = new AchievementMap();
		replacement.put(high);
		replacement.put(low);
		replacement.put(mid);

		// Guard the test's own premise: if the shuffled input ever stops being scrambled -- a fourth achievement
		// added here, or a JDK that changes HashMap's spread function -- this test would pass trivially against
		// unpatched code. Fail loudly instead.
		assertNotEquals(Arrays.asList(low, mid, high), new ArrayList<>(replacement.getAll()),
				"test premise broken: replacement iterates in ascending threshold order, so it cannot detect the defect");

		underTest.replaceWith(replacement);

		assertEquals(Arrays.asList(low, mid, high),
				underTest.getForCategoryAndSubcategory(MultipleAchievements.BREAKS, "deepslate"));
	}

	@Test
	void shouldReplaceAllMapEntries() {
		Achievement oldAchievement = new AchievementBuilder().category(NormalAchievements.ANVILS).name("old")
				.displayName("Old").build();
		Achievement newAchievement = new AchievementBuilder().category(MultipleAchievements.KILLS)
				.subcategory("skeleton").name("new").displayName("New").build();
		underTest.put(oldAchievement);
		AchievementMap replacement = new AchievementMap();
		replacement.put(newAchievement);

		underTest.replaceWith(replacement);

		assertNull(underTest.getForName("old"));
		assertEquals(newAchievement, underTest.getForName("new"));
		assertEquals(Collections.singletonList(newAchievement),
				underTest.getForCategory(MultipleAchievements.KILLS));
	}

}
