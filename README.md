<p align="center">
  <img src="images/banner.png" alt="Advanced Achievements" />
</p>

# Advanced Achievements

Advanced Achievements adds configurable, server-wide achievement progression to Minecraft. It includes an in-game GUI, rankings, rewards, effects, achievement books, generated advancements, and integrations with popular plugins.

## Requirements

- Paper or Purpur 1.21 or newer
- Java 21 or newer
- Optional integrations: Vault, PlaceholderAPI, EssentialsX, PetMaster, and Jobs Reborn

Spigot and Minecraft versions older than 1.21 are not supported by the current release line.

## Installation

1. Download the latest `AdvancedAchievements.jar` release.
2. Place it in the server's `plugins` directory.
3. Start the server once to generate the configuration files.
4. Edit `plugins/AdvancedAchievements/config.yml`, then run `/aach reload`.

New installations use SQLite by default. Existing H2 installations remain supported and are not migrated automatically. MySQL and PostgreSQL can be selected in `config.yml`.

Run `/aach doctor` as an operator after installation or after a configuration change. The command performs read-only checks of YAML files, runtime compatibility, integrations, and database health.

## Commands and permissions

Use `/aach` for the in-game command list. Administrative commands include:

- `/aach reload` — reload configuration files
- `/aach generate` — generate Minecraft advancements
- `/aach inspect <achievement> [page]` — list recent recipients
- `/aach doctor [page]` — run read-only diagnostics

The parent permission `achievement.*` grants all command permissions. The diagnostic command uses `achievement.doctor`.

## Building

The build produces Java 21 bytecode:

```shell
bash ./mvnw -B -ntp test
bash ./mvnw -B -ntp package formatter:validate
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

The shaded plugin is written to `advanced-achievements-plugin/target/AdvancedAchievements.jar`.

## Support and contributing

Please use the issue forms in this repository and include the output of `/aach doctor`, the plugin version, the Paper/Purpur version, and relevant logs. Remove passwords or other secrets before posting configuration.

See [CONTRIBUTING.md](CONTRIBUTING.md) for development instructions.
