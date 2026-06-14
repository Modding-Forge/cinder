# `neoforge/` - STUB ONLY

This directory exists to reserve the namespace and to make it obvious in
code review where the future NeoForge loader shim will live. **It is
intentionally not part of the Gradle build** in Phase 0.

## Why this is a stub

There is no NeoForge build available for Minecraft `26.2-pre-4` at the
time this project was created. Adding a `neoforge/` subproject to
`settings.gradle` before a matching NeoForge toolchain exists would
either fail to resolve, or pull in the wrong Minecraft version. We do
neither.

## When this becomes a real module

The directory will be promoted to a full Gradle subproject as soon as:

1. NeoForge publishes a build that targets Minecraft `26.2-pre-4`
   (or a compatible 26.2 snapshot, in which case the `minecraft_version`
   in `gradle.properties` is bumped accordingly), and
2. the `common` module is verified to compile against the corresponding
   Mojang-mapped NeoForge source drop.

When that happens, the following will be added in a single, reviewable
commit:

- `settings.gradle` gets `include 'neoforge'`,
- `neoforge/build.gradle` (using ModDevGradle),
- `neoforge/src/main/resources/META-INF/neoforge.mods.toml`,
- `neoforge/src/main/java/com/cinder/neoforge/CinderNeoForge.java`,
- `neoforge/src/main/java/com/cinder/neoforge/platform/NeoForgePlatform.java`,
- `neoforge/src/main/resources/META-INF/services/com.cinder.platform.Platform`
  registering `com.cinder.neoforge.platform.NeoForgePlatform`,
- a `neoforge` mixin source set mirroring the `fabric` one.

No code in `common/` or `fabric/` will need to change.

## Why the directory is not empty

Leaving an empty `neoforge/` directory tends to be deleted by IDEs,
cleanups, and merge bots. The `README_STUB.md` makes the intent
unambiguous to anyone browsing the repository.
