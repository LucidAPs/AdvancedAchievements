package com.hm.achievement.listener.statistics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.inventory.ItemStack;

import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.utils.ItemFilter;
import com.hm.achievement.utils.MaterialHelper;

/**
 * Listener class to deal with ItemBreaks achievements. Sub-categories describe the broken item: its material and,
 * optionally, its enchantments, custom name, colour or unbreakable flag. See {@link ItemFilter} for the syntax.
 *
 * @author Pyves
 *
 */
@Singleton
public class ItemBreaksListener extends AbstractListener {

	private final MaterialHelper materialHelper;
	private final Logger logger;

	private Map<String, ItemFilter> subcategoriesToFilters = new HashMap<>();

	@Inject
	public ItemBreaksListener(@Named("main") YamlConfiguration mainConfig, AchievementMap achievementMap,
			CacheManager cacheManager, MaterialHelper materialHelper, Logger logger) {
		super(MultipleAchievements.ITEMBREAKS, mainConfig, achievementMap, cacheManager);
		this.materialHelper = materialHelper;
		this.logger = logger;
	}

	@Override
	public void extractConfigurationParameters() {
		super.extractConfigurationParameters();
		Map<String, ItemFilter> filters = new HashMap<>();
		for (String subcategory : subcategories) {
			filters.put(subcategory, ItemFilter.parse(subcategory, materialHelper, logger));
		}
		subcategoriesToFilters = filters;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerItemBreak(PlayerItemBreakEvent event) {
		Player player = event.getPlayer();
		ItemStack brokenItem = event.getBrokenItem();
		if (!player.hasPermission(category.toChildPermName(brokenItem.getType().name().toLowerCase(Locale.ROOT)))) {
			return;
		}

		Set<String> matchingSubcategories = new HashSet<>();
		subcategoriesToFilters.forEach((subcategory, filter) -> {
			if (filter.matches(brokenItem)) {
				matchingSubcategories.add(subcategory);
			}
		});
		if (matchingSubcategories.isEmpty()) {
			return;
		}

		updateStatisticAndAwardAchievementsIfAvailable(player, matchingSubcategories, 1);
	}
}
