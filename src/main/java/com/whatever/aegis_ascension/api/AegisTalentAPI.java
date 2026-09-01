package com.whatever.aegis_ascension.api;

import com.whatever.aegis_ascension.perk.Perk;

import java.util.List;

/**
 * Registration API for mods that ship their own Aegis Ascension talents.
 *
 * <p>A dependent mod places its talents at {@code assets/<its mod id>/talents.json} and
 * calls {@link #registerTalents(String)} with its own mod id during setup:</p>
 *
 * <pre>{@code
 * // FMLCommonSetupEvent, inside event.enqueueWork(...)
 * AegisTalentAPI.registerTalents("my_mod");
 * }</pre>
 *
 * <p><b>File shape.</b> Only the {@code perks} array is read, and each entry uses the same
 * fields as this mod's own {@code talents.json}:</p>
 *
 * <pre>{@code
 * {
 *   "perks": [
 *     {
 *       "id": "my_mod:perk_example",
 *       "tier": "SR",
 *       "name": "perk.my_mod.example.name",
 *       "description": "perk.my_mod.example.description",
 *       "icon": "my_mod:textures/gui/talents/example.png",
 *       "stats": { "lucky_strike": 0.2 },
 *       "maxRank": 1,
 *       "manualToggle": false
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>Rarity weights and attribute mappings are ignored in addon files: those are
 * whole-game balance decisions that stay with the host mod and the server operator.</p>
 *
 * <p><b>Ids must be namespaced</b> to the registering mod ({@code my_mod:perk_example}),
 * which makes collisions between addons impossible. Registration also adds the owning mod
 * to each talent's required mods, so the entry is excluded from selection wherever that
 * mod is absent.</p>
 *
 * <p><b>Stats are the extension point.</b> A talent built from stat keys this mod already
 * understands works with no further code. Keys it does not understand are stored and
 * carried, but nothing reads them.</p>
 *
 * <p><b>Register during setup.</b> Talent ranks are held in player data keyed by talent
 * identity, so a catalog rebuilt after a world has loaded would read every stored rank
 * back as zero. Registering once a server is running is refused outright.</p>
 */
public final class AegisTalentAPI {
    /** The file a dependent mod ships, relative to its own {@code assets/<modId>/}. */
    public static final String TALENT_FILE_NAME = "talents.json";

    private AegisTalentAPI() {
    }

    /**
     * Reads and registers {@code assets/<modId>/talents.json} from that mod's own jar.
     *
     * <p>Nothing changes unless the whole file validates, so a rejected file leaves the
     * existing catalog intact. Calling again for the same mod replaces that mod's
     * previous contribution rather than adding to it.</p>
     *
     * @param modId the calling mod's own id
     * @return the number of talents registered for this mod
     * @throws IllegalStateException if the mod is absent, ships no talent file, declares a
     *         talent this catalog cannot accept, or registers after a server has started
     */
    public static int registerTalents(String modId) {
        return Perk.registerAddonTalents(modId);
    }

    /** Mod ids that have registered talents, in registration order. */
    public static List<String> registeredMods() {
        return Perk.registeredAddonMods();
    }
}
