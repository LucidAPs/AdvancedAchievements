package com.hm.achievement.lifecycle;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.hm.achievement.AdvancedAchievements;
import com.hm.achievement.JobsEnableWatcher;
import com.hm.achievement.advancement.AdvancementTabListener;
import com.hm.achievement.category.Category;
import com.hm.achievement.category.NormalAchievements;
import com.hm.achievement.command.completer.CommandTabCompleter;
import com.hm.achievement.command.executable.ReloadCommand;
import com.hm.achievement.command.executor.PluginCommandExecutor;
import com.hm.achievement.config.ConfigurationParser;
import com.hm.achievement.db.AbstractDatabaseManager;
import com.hm.achievement.db.AsyncCachedRequestsSender;
import com.hm.achievement.listener.JoinListener;
import com.hm.achievement.listener.ListGUIListener;
import com.hm.achievement.listener.PlayerAdvancedAchievementListener;
import com.hm.achievement.listener.TeleportListener;
import com.hm.achievement.listener.statistics.DropsListener;
import com.hm.achievement.placeholder.AchievementPlaceholderHook;
import com.hm.achievement.runnable.AchieveDistanceRunnable;
import com.hm.achievement.runnable.AchievePlayTimeRunnable;

import dagger.Lazy;

class PluginLoaderTest {

	@Test
	void shouldRegisterAdvancementListenerWithOtherReloadableListeners() {
		AdvancedAchievements plugin = mock(AdvancedAchievements.class);
		Server server = mock(Server.class);
		PluginManager pluginManager = mock(PluginManager.class);
		when(plugin.getServer()).thenReturn(server);
		when(server.getPluginManager()).thenReturn(pluginManager);
		AdvancementTabListener advancementListener = mock(AdvancementTabListener.class);

		PluginLoader underTest = new PluginLoader(plugin, Logger.getAnonymousLogger(), Collections.emptySet(),
				mock(JoinListener.class), advancementListener, mock(ListGUIListener.class), mock(TeleportListener.class),
				mock(PlayerAdvancedAchievementListener.class), mock(Cleaner.class), mockPlaceholderHook(),
				mock(AbstractDatabaseManager.class), mock(AsyncCachedRequestsSender.class),
				mock(PluginCommandExecutor.class), mock(CommandTabCompleter.class), Collections.emptySet(),
				new YamlConfiguration(), mock(ConfigurationParser.class), mock(AchieveDistanceRunnable.class),
				mock(AchievePlayTimeRunnable.class), mock(ReloadCommand.class), mock(JobsEnableWatcher.class));

		underTest.registerListeners();

		verify(pluginManager).registerEvents(advancementListener, plugin);
	}

	@Test
	void shouldNotRegisterListenerForDisabledCategory() {
		AdvancedAchievements plugin = mock(AdvancedAchievements.class);
		Server server = mock(Server.class);
		PluginManager pluginManager = mock(PluginManager.class);
		when(plugin.getServer()).thenReturn(server);
		when(server.getPluginManager()).thenReturn(pluginManager);
		DropsListener dropsListener = mock(DropsListener.class);
		when(dropsListener.getCategory()).thenReturn(NormalAchievements.DROPS);

		PluginLoader underTest = new PluginLoader(plugin, Logger.getAnonymousLogger(),
				Collections.singleton(dropsListener), mock(JoinListener.class), mock(AdvancementTabListener.class),
				mock(ListGUIListener.class), mock(TeleportListener.class), mock(PlayerAdvancedAchievementListener.class),
				mock(Cleaner.class), mockPlaceholderHook(), mock(AbstractDatabaseManager.class),
				mock(AsyncCachedRequestsSender.class), mock(PluginCommandExecutor.class), mock(CommandTabCompleter.class),
				Collections.singleton(NormalAchievements.DROPS), new YamlConfiguration(), mock(ConfigurationParser.class),
				mock(AchieveDistanceRunnable.class), mock(AchievePlayTimeRunnable.class), mock(ReloadCommand.class),
				mock(JobsEnableWatcher.class));

		underTest.registerListeners();

		verify(pluginManager, never()).registerEvents(dropsListener, plugin);
	}

	@Test
	void shouldRescheduleConfigDependentTasksOnReload() {
		AdvancedAchievements plugin = mock(AdvancedAchievements.class);
		YamlConfiguration mainConfig = new YamlConfiguration();
		mainConfig.set("BungeeMode", false);
		Set<Category> disabledCategories = new HashSet<>(Arrays.asList(NormalAchievements.values()));
		AsyncCachedRequestsSender requestsSender = mock(AsyncCachedRequestsSender.class);
		Cleaner cleaner = mock(Cleaner.class);
		PluginLoader underTest = new PluginLoader(plugin, Logger.getAnonymousLogger(), Collections.emptySet(),
				mock(JoinListener.class), mock(AdvancementTabListener.class), mock(ListGUIListener.class),
				mock(TeleportListener.class), mock(PlayerAdvancedAchievementListener.class), cleaner, mockPlaceholderHook(),
				mock(AbstractDatabaseManager.class), requestsSender, mock(PluginCommandExecutor.class),
				mock(CommandTabCompleter.class), disabledCategories, mainConfig, mock(ConfigurationParser.class),
				mock(AchieveDistanceRunnable.class), mock(AchievePlayTimeRunnable.class), mock(ReloadCommand.class),
				mock(JobsEnableWatcher.class));
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		BukkitTask firstSenderTask = mock(BukkitTask.class);
		BukkitTask secondSenderTask = mock(BukkitTask.class);
		BukkitTask firstCleanerTask = mock(BukkitTask.class);
		BukkitTask secondCleanerTask = mock(BukkitTask.class);
		when(scheduler.runTaskTimerAsynchronously(eq(plugin), eq(requestsSender), eq(1200L), eq(1200L)))
				.thenReturn(firstSenderTask);
		when(scheduler.runTaskTimerAsynchronously(eq(plugin), eq(requestsSender), eq(40L), eq(40L)))
				.thenReturn(secondSenderTask);
		when(scheduler.runTaskTimer(eq(plugin), eq(cleaner), eq(20000L), eq(20000L))).thenReturn(firstCleanerTask);
		when(scheduler.runTaskTimer(eq(plugin), eq(cleaner), eq(50L), eq(50L))).thenReturn(secondCleanerTask);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
			underTest.launchScheduledTasks();
			mainConfig.set("BungeeMode", true);
			underTest.launchScheduledTasks();
		}

		verify(firstSenderTask).cancel();
		verify(firstCleanerTask).cancel();
	}

	@SuppressWarnings("unchecked")
	private Lazy<AchievementPlaceholderHook> mockPlaceholderHook() {
		return mock(Lazy.class);
	}
}
