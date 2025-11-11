package com.hm.achievement;

import com.hm.achievement.exception.PluginLoadError;
import com.hm.achievement.lifecycle.PluginLoader;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;

import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class JobsEnableWatcher implements Listener {
    private final AdvancedAchievements aa;
    private final Provider<PluginLoader> loaderProvider; // <-- Provider breaks the cycle
    private final Logger log;

    @Inject
    public JobsEnableWatcher(AdvancedAchievements aa, Provider<PluginLoader> loaderProvider, Logger log) {
        this.aa = aa;
        this.loaderProvider = loaderProvider;
        this.log = log;
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent e) {
        if (e.getPlugin().getName().equalsIgnoreCase("Jobs")) {
            Bukkit.getScheduler().runTask(aa, () -> {
                try {
                    loaderProvider.get().loadAdvancedAchievements(); // mirrors /aach reload
                    log.info("[AdvancedAchievements] Jobs enabled; JobsReborn category now active.");
                } catch (PluginLoadError ex) {
                    log.log(Level.SEVERE, "Could not enable JobsReborn category after Jobs enable:", ex);
                }
            });
        }
    }
}
