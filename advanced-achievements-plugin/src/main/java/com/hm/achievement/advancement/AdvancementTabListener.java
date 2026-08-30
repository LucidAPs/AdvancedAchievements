package com.hm.achievement.advancement;

import javax.inject.Inject;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.hm.achievement.AdvancedAchievements;

public class AdvancementTabListener implements Listener {

	private final AdvancedAchievements advancedAchievements;
	private final AdvancementManager advancementManager;

	@Inject
	public AdvancementTabListener(AdvancedAchievements advancedAchievements, AdvancementManager advancementManager) {
		this.advancedAchievements = advancedAchievements;
		this.advancementManager = advancementManager;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		// 1 tick later so generation (if running) has time to register the parent
		Bukkit.getScheduler().runTaskLater(advancedAchievements,
				() -> advancementManager.ensureRootVisible(event.getPlayer()), 1L);
	}
}
