package com.hm.achievement.listener.statistics;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.domain.PotionRecipe;
import com.hm.achievement.utils.InventoryHelper;
import com.hm.achievement.utils.MaterialHelper;

/**
 * Listener class to deal with Brewing achievements.
 *
 * @author Pyves
 *
 */
@Singleton
public class BrewingListener extends AbstractRateLimitedListener {

	private final MaterialHelper materialHelper;

	@Inject
	public BrewingListener(@Named("main") YamlConfiguration mainConfig, AchievementMap achievementMap,
			CacheManager cacheManager, AdvancedAchievements advancedAchievements,
			@Named("lang") YamlConfiguration langConfig, MaterialHelper materialHelper) {
		super(NormalAchievements.BREWING, mainConfig, achievementMap, cacheManager, advancedAchievements, langConfig);
		this.materialHelper = materialHelper;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		ItemStack item = event.getCurrentItem();
		if (event.getInventory().getType() != InventoryType.BREWING || event.getAction() == InventoryAction.NOTHING
				|| event.getClick() == ClickType.NUMBER_KEY && event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD
				|| !isBrewablePotion(item)) {
			return;
		}

		Player player = (Player) event.getWhoClicked();
		int eventAmount = item.getAmount();
		if (event.isShiftClick()) {
			eventAmount = Math.min(eventAmount, InventoryHelper.getAvailableSpace(player, item));
			if (eventAmount == 0) {
				return;
			}
		}

		if (!updateStatisticAndAwardAchievementsIfAvailable(player, eventAmount, event.getRawSlot())) {
			return;
		}

		Optional<PotionRecipe> recipe = PotionRecipe.fromItem(item);
		if (recipe.isPresent()) {
			updateRecipeStatistic(player, recipe.get(), eventAmount);
		}
	}

	private void updateRecipeStatistic(Player player, PotionRecipe recipe, int eventAmount) {
		String recipeKey = recipe.key();
		if (achievementMap.getForCategoryAndSubcategory(category, recipeKey).isEmpty()) {
			return;
		}
		long amount = cacheManager.getAndIncrementStatisticAmount(MultipleAchievements.BREWINGRECIPES, recipeKey,
				player.getUniqueId(), eventAmount);
		checkThresholdsAndAchievements(player, category, recipeKey, amount);
	}

	/**
	 * Determine whether the event corresponds to a brewing output: a non-water drinkable potion, or a splash/lingering
	 * potion produced by another brewing step.
	 *
	 * @param item
	 * @return true if for any brewable potion
	 */
	private boolean isBrewablePotion(ItemStack item) {
		if (item == null) {
			return false;
		}
		Optional<PotionRecipe> recipe = PotionRecipe.fromItem(item);
		return materialHelper.isAnyPotionButWater(item)
				|| recipe.filter(value -> value.itemType() != Material.POTION).isPresent();
	}
}
