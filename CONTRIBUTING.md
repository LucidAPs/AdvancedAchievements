# Contributing

Bug fixes, tests, documentation, and focused feature additions are welcome.

## Before opening an issue

1. Confirm the problem on a current Paper or Purpur 1.21+ server running Java 21+.
2. Upgrade to the latest Advanced Achievements release.
3. Run `/aach doctor` and review the server log.
4. Search the [existing issues](https://github.com/LucidAPs/AdvancedAchievements/issues).

Never post database passwords, connection URLs containing credentials, or other secrets.

## Development setup

The project requires JDK 21 or newer and compiles to Java 21 bytecode. Use the checked-in Maven wrapper:

```shell
bash ./mvnw -B -ntp test
bash ./mvnw -B -ntp formatter:validate
bash ./mvnw -B -ntp package
```

On Windows, replace `./mvnw` with `mvnw.cmd`.

The plugin uses Dagger for dependency injection. New commands must be bound in `CommandModule`, declare a `CommandSpec`, document their permission in `plugin.yml`, and include that permission under `achievement.*` when appropriate.

## Pull requests

- Keep changes scoped and explain observable behavior changes.
- Add or update tests for correctness fixes.
- Preserve compatibility with existing H2 1.4 databases; do not introduce an automatic H2 2.x migration without a dedicated migration design.
- Keep Bukkit/Paper API access on the main server thread unless the API explicitly documents asynchronous use.
- Run tests and formatting validation before submitting.

Open pull requests and issues against [LucidAPs/AdvancedAchievements](https://github.com/LucidAPs/AdvancedAchievements).
