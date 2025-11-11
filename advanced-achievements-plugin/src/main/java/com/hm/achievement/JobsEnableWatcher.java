package com.hm.achievement;

import com.hm.achievement.exception.PluginLoadError;
import com.hm.achievement.lifecycle.PluginLoader;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class JobsEnableWatcher implements Listener {
    private final AdvancedAchievements aa;
    private final PluginLoader loader;
    private final Logger log;

    @Inject
    public JobsEnableWatcher(AdvancedAchievements aa, PluginLoader loader, Logger log) {
        this.aa = aa; this.loader = loader; this.log = log;
    }

    @EventHandler
    public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent e) {
        if (e.getPlugin().getName().equalsIgnoreCase("Jobs")) {
            Bukkit.getScheduler().runTask(aa, () -> {
                try {
                    loader.loadAdvancedAchievements(); // mirrors /aach reload
                    log.info("[AdvancedAchievements] Jobs enabled; JobsReborn category now active.");
                } catch (PluginLoadError ex) {
                    log.log(Level.SEVERE, "Could not enable JobsReborn category after Jobs enable:", ex);
                }
            });
        }
    }
}

