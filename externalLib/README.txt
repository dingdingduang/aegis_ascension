Optional local mod dependencies
===============================

Every .jar in this directory is loaded by Gradle as compileOnly and deobfuscated
by ForgeGradle. These JARs are not bundled into Perk Selection and are not added
to the development runtime automatically.

To update an integration, remove its old JAR and place the updated 1.20.1 Forge
JAR here. Use the filename format <artifact>-<version>.jar. The artifact name and
version may change; build.gradle discovers all matching .jar files automatically.

To test an integration in runClient/runServer, also place the mod and all of its
required runtime dependencies in run/mods, or in the mods folder of the external
Minecraft instance used for testing.
