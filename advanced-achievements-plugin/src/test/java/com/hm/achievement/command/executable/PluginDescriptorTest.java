package com.hm.achievement.command.executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {

	@Test
	void descriptorContainsEveryCommandPermissionAndWildcardChild() {
		YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(new InputStreamReader(
				PluginDescriptorTest.class.getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8));
		ConfigurationSection wildcardChildren = descriptor
				.getConfigurationSection("permissions.achievement.*.children");
		assertNotNull(wildcardChildren);

		for (Class<? extends AbstractCommand> commandClass : commandClasses()) {
			CommandSpec spec = commandClass.getAnnotation(CommandSpec.class);
			assertNotNull(spec);
			if (!spec.permission().isEmpty()) {
				String permission = "achievement." + spec.permission();
				assertTrue(descriptor.contains("permissions." + permission), permission + " is missing");
				assertTrue(wildcardChildren.getBoolean(permission), permission + " is missing from achievement.*");
			}
		}
	}

	@Test
	void descriptorContainsResolvedVersionAndCurrentPlatformMetadata() {
		YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(new InputStreamReader(
				PluginDescriptorTest.class.getResourceAsStream("/plugin.yml"), StandardCharsets.UTF_8));
		String version = descriptor.getString("version");
		assertNotNull(version);
		assertFalse(version.isBlank());
		assertFalse(version.contains("${"), "Maven resource filtering did not resolve the plugin version");
		assertEquals("1.21", descriptor.getString("api-version"));
		assertEquals("https://github.com/LucidAPs/AdvancedAchievements", descriptor.getString("website"));
	}

	private List<Class<? extends AbstractCommand>> commandClasses() {
		return List.of(AddCommand.class, BookCommand.class, CheckCommand.class, DeleteCommand.class, DoctorCommand.class,
				EasterEggCommand.class, GenerateCommand.class, GiveCommand.class, HelpCommand.class, InfoCommand.class,
				InspectCommand.class, ListCommand.class, MonthCommand.class, ReloadCommand.class, ResetCommand.class,
				StatsCommand.class, ToggleCommand.class, TopCommand.class, WeekCommand.class);
	}
}
