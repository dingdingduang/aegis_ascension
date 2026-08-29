package com.whatever.aegis_ascension.platform;

/**
 * Current platform service wiring.
 *
 * <p>When target source sets are introduced, this is the small wiring seam that
 * can point to Forge 1.16.5 or NeoForge 26.1 implementations.</p>
 */
public final class PlatformServices {
    private static final AttributeAccess ATTRIBUTES = new ForgeAttributeAccess();
    private static final ConfigAccess CONFIG = new ForgeConfigAccess();
    private static final EntityDataAccess ENTITY_DATA = new ForgeEntityDataAccess();
    private static final RegistryAccess REGISTRIES = new ForgeRegistryAccess();
    private static final ResourceAccess RESOURCES = new ForgeResourceAccess();
    private static final MenuAccess MENUS = new ForgeMenuAccess();
    private static final ModAccess MODS = new ForgeModAccess();
    private static final PathAccess PATHS = new ForgePathAccess();
    private static final ServerAccess SERVER = new ForgeServerAccess();
    private static final PersistenceAccess PERSISTENCE = new ForgePersistenceAccess(SERVER);

    private PlatformServices() {
    }

    public static AttributeAccess attributes() {
        return ATTRIBUTES;
    }

    public static ConfigAccess config() {
        return CONFIG;
    }

    public static EntityDataAccess entityData() {
        return ENTITY_DATA;
    }

    public static RegistryAccess registries() {
        return REGISTRIES;
    }

    public static ResourceAccess resources() {
        return RESOURCES;
    }

    public static MenuAccess menus() {
        return MENUS;
    }

    public static ModAccess mods() {
        return MODS;
    }

    public static NetworkAccess network() {
        return NetworkHolder.INSTANCE;
    }

    public static PathAccess paths() {
        return PATHS;
    }

    public static ServerAccess server() {
        return SERVER;
    }

    public static PersistenceAccess persistence() {
        return PERSISTENCE;
    }

    /** Keeps channel creation at first network use instead of general platform startup. */
    private static final class NetworkHolder {
        private static final NetworkAccess INSTANCE = new ForgeNetworkAccess();

        private NetworkHolder() {
        }
    }
}
