package com.hm.achievement.advancement;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AdvancementTabListener implements Listener {
    private final JavaPlugin plugin;
    private final AdvancementManager advancementManager;

    public AdvancementTabListener(JavaPlugin plugin, AdvancementManager advancementManager) {
        this.plugin = plugin;
        this.advancementManager = advancementManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // 1 tick later so generation (if running) has time to register the parent
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                advancementManager.ensureRootVisible(e.getPlayer()), 1L);
    }
}
