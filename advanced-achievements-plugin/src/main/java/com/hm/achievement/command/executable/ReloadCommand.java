package com.hm.achievement.command.executable;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.hm.achievement.exception.PluginLoadError;
import com.hm.achievement.lifecycle.PluginLoader;
import com.hm.achievement.lifecycle.Reloadable;

import dagger.Lazy;

/**
 * Class in charge of handling the /aach reload command, which reloads the plugin's configuration files.
 *
 * @author Pyves
 */
@Singleton
@CommandSpec(name = "reload", permission = "reload", minArgs = 1, maxArgs = 1)
public class ReloadCommand extends AbstractCommand {

	private final Logger logger;
	private final Lazy<PluginLoader> pluginLoader;
	private final Lazy<Set<Reloadable>> reloadables;

	private String langConfigReloadFailed;
	private String langConfigSuccessfullyReloaded;

	@Inject
	public ReloadCommand(@Named("main") YamlConfiguration mainConfig, @Named("lang") YamlConfiguration langConfig,
			StringBuilder pluginHeader, Logger logger, Lazy<PluginLoader> pluginLoader,
			Lazy<Set<Reloadable>> reloadables) {
		super(mainConfig, langConfig, pluginHeader);
		this.logger = logger;
		this.pluginLoader = pluginLoader;
		this.reloadables = reloadables;
	}

	@Override
	public void extractConfigurationParameters() {
		super.extractConfigurationParameters();

		langConfigReloadFailed = pluginHeader + langConfig.getString("configuration-reload-failed");
		langConfigSuccessfullyReloaded = pluginHeader + langConfig.getString("configuration-successfully-reloaded");
	}

	/**
	 * Notifies all Reloadables.
	 *
	 * @throws PluginLoadError
	 */
	public void notifyObservers() throws PluginLoadError {
		for (Reloadable reloadable : reloadables.get()) {
			reloadable.extractConfigurationParameters();
		}
	}

	@Override
	void onExecute(CommandSender sender, String[] args) {
		try {
			pluginLoader.get().loadAdvancedAchievements();
		} catch (PluginLoadError e) {
			if (sender instanceof Player) {
				sender.sendMessage(langConfigReloadFailed);
			}
			logger.log(Level.SEVERE, "Configuration reload failed; the previous configuration remains active:", e);
			return;
		}

		if (sender instanceof Player) {
			sender.sendMessage(langConfigSuccessfullyReloaded);
		}
		logger.info("Plugin successfully reloaded.");
	}
}
