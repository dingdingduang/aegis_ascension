package com.whatever.aegis_ascension.api;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.soullink.SoulLinkCatalog;

import java.util.List;

/**
 * Registration API for mods that ship their own talents, Aegises, or Soul Links.
 *
 * <p>A dependent mod places its catalogues under {@code assets/<its mod id>/} and calls
 * the matching method with its own mod id during setup:</p>
 *
 * <pre>{@code
 * // FMLCommonSetupEvent, inside event.enqueueWork(...)
 * AegisCatalogAPI.registerTalents("my_mod");
 * AegisCatalogAPI.registerAegises("my_mod");
 * AegisCatalogAPI.registerSoulLinks("my_mod");
 * }</pre>
 *
 * <h2>Two files per catalogue</h2>
 *
 * <p>Each catalogue is split the same way this mod's own are. The
 * <b>{@code _serverside}</b> file holds what an entry <i>does</i> and is sent to every
 * client on join. The <b>{@code _clientside}</b> file holds what it <i>looks like</i> -
 * title, description, icon - and never crosses the wire, because a picture and a
 * translation key change nothing the server acts on. Splitting them keeps roughly half
 * the catalogue off the network.</p>
 *
 * <p>Only the {@code _serverside} file is required, and it has no presentation fields at
 * all - a title or an icon written there is simply ignored. Presentation resolves in this
 * order:</p>
 *
 * <ol>
 *   <li>the player's own {@code config/aegis_ascension/<catalogue>_clientside.json};</li>
 *   <li>the addon's {@code _clientside.json}, merged at registration;</li>
 *   <li>a visible placeholder.</li>
 * </ol>
 *
 * <h2>No naming convention</h2>
 *
 * <p>Nothing is derived from an entry's id. An entry no {@code _clientside.json}
 * describes renders as {@code Default String} with a fallback icon rather than guessing
 * at a translation key or a texture path that may not exist. An addon that wants its
 * entries named therefore ships a {@code _clientside.json}; there is no arrangement of
 * asset names that makes one unnecessary.</p>
 *
 * <h2>File shape</h2>
 *
 * <pre>{@code
 * // assets/my_mod/talents_serverside.json
 * { "perks": [ {
 *     "id": "my_mod:perk_example",
 *     "tier": "SR",
 *     "stats": { "lucky_strike": 0.2 },
 *     "maxRank": 1,
 *     "manualToggle": false
 * } ] }
 *
 * // assets/my_mod/talents_clientside.json   (optional)
 * { "entries": {
 *     "my_mod:perk_example": {
 *       "name": "perk.my_mod.example.name",
 *       "description": "perk.my_mod.example.description",
 *       "icon": "my_mod:textures/gui/talents/example.png"
 *     }
 * } }
 * }</pre>
 *
 * <p>Aegises use {@code aegises_serverside.json} with an {@code aegises} array; Soul Links
 * use {@code soul_links_serverside.json} with a {@code soul_links} array. Both take the
 * same {@code entries} shape in their {@code _clientside.json}.</p>
 *
 * <p>Rarity weights, attribute mappings, and spawn-buff tables are ignored in addon
 * files: those are whole-game balance decisions that stay with the host mod and the
 * server operator.</p>
 *
 * <p><b>Ids must be namespaced</b> to the registering mod ({@code my_mod:perk_example}),
 * which makes collisions between addons impossible and is what the convention above reads.
 * Registration also adds the owning mod to each entry's required mods, so the entry is
 * excluded wherever that mod is absent.</p>
 *
 * <p><b>Stats are the extension point.</b> An entry built from stat keys this mod already
 * understands works with no further code. Keys it does not understand are stored and
 * carried, but nothing reads them.</p>
 *
 * <p><b>Register during setup.</b> Ranks are held in player data keyed by entry identity,
 * so a catalogue rebuilt after a world has loaded would read every stored rank back as
 * zero. Registering once a server is running is refused outright.</p>
 */
public final class AegisCatalogAPI {
    public static final String TALENT_SERVERSIDE_FILE = "talents_serverside.json";
    public static final String TALENT_CLIENTSIDE_FILE = "talents_clientside.json";

    public static final String AEGIS_SERVERSIDE_FILE = "aegises_serverside.json";
    public static final String AEGIS_CLIENTSIDE_FILE = "aegises_clientside.json";

    public static final String SOUL_LINK_SERVERSIDE_FILE = "soul_links_serverside.json";
    public static final String SOUL_LINK_CLIENTSIDE_FILE = "soul_links_clientside.json";

    private AegisCatalogAPI() {
    }

    /**
     * Reads and registers this mod's talents from its own jar.
     *
     * <p>Nothing changes unless the whole file validates, so a rejected file leaves the
     * existing catalogue intact. Calling again for the same mod replaces that mod's
     * previous contribution rather than adding to it.</p>
     *
     * @param modId the calling mod's own id
     * @return the number of talents registered for this mod
     * @throws IllegalStateException if the mod is absent, ships no serverside file,
     *         declares an entry this catalogue cannot accept, or registers after a server
     *         has started
     */
    public static int registerTalents(String modId) {
        return Perk.registerAddonTalents(modId);
    }

    /** As {@link #registerTalents(String)}, for {@code aegises_serverside.json}. */
    public static int registerAegises(String modId) {
        return Aegis.registerAddonAegises(modId);
    }

    /** As {@link #registerTalents(String)}, for {@code soul_links_serverside.json}. */
    public static int registerSoulLinks(String modId) {
        return SoulLinkCatalog.registerAddonSoulLinks(modId);
    }

    /** Mod ids that have registered talents, in registration order. */
    public static List<String> registeredTalentMods() {
        return Perk.registeredAddonMods();
    }
}
