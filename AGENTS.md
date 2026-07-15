# SkyHUD agent guidance

SkyHUD is a client-side Kotlin/Fabric mod for Hypixel SkyBlock. It replaces the
cramped Ender Chest, Loadouts, Wardrobe, and Equipment Sets inventory menus with
cleaner searchable interfaces while preserving Hypixel's normal server-backed
slot clicks, item handling, and vanilla fallback behavior. The project supports
Minecraft 26.1.2 and 26.2 from shared sources plus version-specific compatibility
code under `src/26.1.2` and `src/26.2`.

## Multi-version architecture

- Keep every actively supported Minecraft version on `main`. Do not create a
  permanent branch per Minecraft version; use a legacy maintenance branch only
  after a target has been removed from `main` and still needs an exceptional
  fix.
- Treat `gradle/targets.properties` as the source of truth for supported target
  names, Minecraft versions, and target-specific dependency coordinates.
  `settings.gradle.kts` includes the corresponding `versions/<target>` Gradle
  projects from this catalog, and the release workflow consumes a manifest
  generated from the same data.
- Keep gameplay, detection, repositories, controllers, screens, configuration,
  and other behavior shared under `src/main` whenever the APIs compile for all
  targets.
- Put only compile-time Minecraft/Fabric differences under
  `src/<minecraft-version>/kotlin`. Version folders should expose matching
  classes and method contracts so shared code can use a stable compatibility
  boundary without runtime version checks.
- Each `versions/<target>` directory is a Gradle build target, not a copy of the
  mod. Its JAR combines `src/main/kotlin`, `src/main/resources`, and the matching
  `src/<minecraft-version>/kotlin` source set.
- All active targets share one `mod_version` from `gradle.properties`. A release
  tag `v<mod_version>` must build every target. GitHub receives one release with
  one clearly named JAR per Minecraft version; Modrinth receives one version
  record per JAR with an exact `gameVersions` value.

## Adding or removing a Minecraft target

1. Add or remove the target and all dependency coordinates in
   `gradle/targets.properties`.
2. Add or remove its marker build file under `versions/<target>/build.gradle.kts`.
3. Add `src/<minecraft-version>/kotlin` implementations only for APIs that
   differ from shared code. Do not copy the entire shared source tree.
4. Run `releaseManifest` and inspect `build/release/manifest.json` when changing
   release targets. The GitHub Actions workflow must remain target-agnostic and
   must not hardcode individual Minecraft versions or JAR paths.
5. Run the complete build and do not release while any active target fails.

## Release policy

- Releases are synchronized across all active Minecraft targets. Do not publish
  only a subset under a shared mod version; remove an unsupported target from
  the catalog before tagging if it can no longer be released.
- Only pushed `v*` tags invoke `.github/workflows/release.yml`. The tag must
  exactly equal `v<mod_version>`; normal pushes and pull requests do not build or
  publish anything in GitHub Actions.
- The `release` GitHub environment provides `MODRINTH_TOKEN` as a secret and
  `MODRINTH_PROJECT_ID` as a variable. Never commit either credential.
- The workflow generates its target list with `./gradlew releaseManifest`,
  builds all targets, skips Modrinth versions that already exist, publishes each
  missing target, and creates or updates one GitHub Release with all production
  JARs.

## Working expectations

- Keep screen detection narrow and fail closed. Only replace a menu after its
  title, layout, and key items have been verified; unrelated containers must be
  left untouched.
- Preserve normal Minecraft/Hypixel inventory interactions. Map custom UI
  actions to validated backing slots instead of bypassing server behavior.
- Keep shared behavior in `src/main` and put only version-specific compatibility
  code in the matching version source directory.
- Commit mod work locally at natural checkpoints, such as after a coherent
  feature, fix, or refactor is complete and verified. Use clear conventional
  commit messages. Do not push unless the user explicitly asks.
- Before considering a mod change complete, run `./gradlew build` (or
  `.\gradlew.bat build` on Windows) and fix any failures. This must build every
  target in `gradle/targets.properties` and produce its JAR under
  `versions/*/build/libs`.
