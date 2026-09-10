package com.hm.achievement.listener.statistics;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.Test;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.domain.Achievement.AchievementBuilder;
import com.hm.achievement.utils.MaterialHelper;

class BrewingListenerTest {

	@Test
	void shouldCountBothTotalAndExactPotionRecipe() {
		AchievementMap achievementMap = new AchievementMap();
		achievementMap.put(new AchievementBuilder()
				.category(NormalAchievements.BREWING)
				.subcategory("lingering_potion/turtle_master")
				.threshold(1)
				.name("brewing_lingering_turtle_master")
				.displayName("Lingering Turtle Master")
				.build());
		CacheManager cacheManager = mock(CacheManager.class);
		MaterialHelper materialHelper = mock(MaterialHelper.class);
		BrewingListener underTest = new BrewingListener(new YamlConfiguration(), achievementMap, cacheManager,
				mock(AdvancedAchievements.class), new YamlConfiguration(), materialHelper);
		underTest.extractConfigurationParameters();

		UUID playerId = UUID.randomUUID();
		Player player = mock(Player.class);
		World world = mock(World.class);
		InventoryClickEvent event = potionClick(player, Material.LINGERING_POTION, PotionType.TURTLE_MASTER);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.getWorld()).thenReturn(world);
		when(world.getName()).thenReturn("world");
		when(materialHelper.isAnyPotionButWater(event.getCurrentItem())).thenReturn(true);
		when(cacheManager.hasPlayerAchievement(playerId, "brewing_lingering_turtle_master")).thenReturn(true);

		underTest.onInventoryClick(event);

		verify(cacheManager).getAndIncrementStatisticAmount(NormalAchievements.BREWING, playerId, 1);
		verify(cacheManager).getAndIncrementStatisticAmount(MultipleAchievements.BREWINGRECIPES,
				"lingering_potion/turtle_master", playerId, 1);
	}

	@Test
	void shouldNotCountDifferentPotionAsConfiguredRecipe() {
		AchievementMap achievementMap = new AchievementMap();
		achievementMap.put(new AchievementBuilder()
				.category(NormalAchievements.BREWING)
				.subcategory("potion/slow_falling")
				.threshold(1)
				.name("brewing_slow_falling")
				.displayName("Slow Falling")
				.build());
		CacheManager cacheManager = mock(CacheManager.class);
		MaterialHelper materialHelper = mock(MaterialHelper.class);
		BrewingListener underTest = new BrewingListener(new YamlConfiguration(), achievementMap, cacheManager,
				mock(AdvancedAchievements.class), new YamlConfiguration(), materialHelper);
		underTest.extractConfigurationParameters();

		UUID playerId = UUID.randomUUID();
		Player player = mock(Player.class);
		World world = mock(World.class);
		InventoryClickEvent event = potionClick(player, Material.SPLASH_POTION, PotionType.SLOW_FALLING);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.getWorld()).thenReturn(world);
		when(world.getName()).thenReturn("world");
		when(materialHelper.isAnyPotionButWater(event.getCurrentItem())).thenReturn(true);

		underTest.onInventoryClick(event);

		verify(cacheManager).getAndIncrementStatisticAmount(NormalAchievements.BREWING, playerId, 1);
		verify(cacheManager, never()).getAndIncrementStatisticAmount(MultipleAchievements.BREWINGRECIPES,
				"potion/slow_falling", playerId, 1);
	}

	private InventoryClickEvent potionClick(Player player, Material material, PotionType potionType) {
		Inventory inventory = mock(Inventory.class);
		ItemStack item = mock(ItemStack.class);
		PotionMeta meta = mock(PotionMeta.class);
		InventoryClickEvent event = mock(InventoryClickEvent.class);
		when(event.getInventory()).thenReturn(inventory);
		when(inventory.getType()).thenReturn(InventoryType.BREWING);
		when(event.getAction()).thenReturn(InventoryAction.PICKUP_ALL);
		when(event.getClick()).thenReturn(ClickType.LEFT);
		when(event.getCurrentItem()).thenReturn(item);
		when(event.getWhoClicked()).thenReturn(player);
		when(item.getType()).thenReturn(material);
		when(item.getItemMeta()).thenReturn(meta);
		when(item.getAmount()).thenReturn(1);
		when(meta.getBasePotionType()).thenReturn(potionType);
		return event;
	}
}
