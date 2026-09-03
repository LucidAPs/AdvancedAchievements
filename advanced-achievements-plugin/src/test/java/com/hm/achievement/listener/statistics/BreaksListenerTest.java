package com.hm.achievement.listener.statistics;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.junit.jupiter.api.Test;

import com.hm.achievement.category.MultipleAchievements;
import com.hm.achievement.config.AchievementMap;
import com.hm.achievement.db.CacheManager;
import com.hm.achievement.domain.Achievement.AchievementBuilder;

class BreaksListenerTest {

	@Test
	void shouldCountConfiguredSubcategoryWithoutPlayerPermission() {
		YamlConfiguration mainConfig = new YamlConfiguration();
		AchievementMap achievementMap = new AchievementMap();
		achievementMap.put(new AchievementBuilder()
				.category(MultipleAchievements.BREAKS)
				.subcategory("stone")
				.threshold(2)
				.name("breaks_2_stone")
				.displayName("Stone Breaker")
				.build());
		CacheManager cacheManager = mock(CacheManager.class);
		BreaksListener underTest = new BreaksListener(mainConfig, achievementMap, cacheManager);
		underTest.extractConfigurationParameters();
		Player player = mock(Player.class);
		World world = mock(World.class);
		Block block = mock(Block.class);
		BlockBreakEvent event = mock(BlockBreakEvent.class);
		UUID playerId = UUID.randomUUID();
		when(event.getPlayer()).thenReturn(player);
		when(event.getBlock()).thenReturn(block);
		when(block.getType()).thenReturn(Material.STONE);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
		when(player.getWorld()).thenReturn(world);
		when(world.getName()).thenReturn("world");
		when(player.hasPermission(anyString())).thenReturn(false);
		when(cacheManager.getAndIncrementStatisticAmount(MultipleAchievements.BREAKS, "stone", playerId, 1))
				.thenReturn(1L);

		underTest.onBlockBreak(event);

		verify(cacheManager).getAndIncrementStatisticAmount(MultipleAchievements.BREAKS, "stone", playerId, 1);
		verify(player, never()).hasPermission(anyString());
	}
}
