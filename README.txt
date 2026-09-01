Aegis Ascension (Minecraft 1.20.1 / Forge 47)
================================================

Gameplay
--------
- A player receives one perk selection charge at each absolute 10-level milestone.
  Milestones are awarded only once: after reaching 34, the next reward is at level 40
  even if the player loses all XP; after reaching 999, the next reward is at 1000.
- Press K (configurable in Controls > Key Binds > Aegis Ascension) to open the ACG screen.
  Offers, owned Talents, Soul Links, and Custom Stats have separate pages. Custom Stats are authoritative server
  totals. They refresh when opened; the server can optionally permit once-per-second live
  refresh through config/aegis_ascension-common.toml.
- The server rolls each card using the workbook rarities: R 90%, SR 8%, SSR 2%.
  Offers contain 3 distinct eligible talents plus one option per whole point of the
  player's vanilla Luck attribute. Luck I therefore shows 4 and Luck II shows 5.
- Clicking a card sends its ID to the server. The server only accepts a perk that was
  present in that player's latest offer, deducts one charge, applies it, and saves it.
- If charges remain after a selection, the same screen refreshes with a new random offer.
  Press the configured key again (or Escape) to close the selection session.
- Closing and reopening does not reroll: the pending offer is saved until a perk is selected.

Workbook talent pool
--------------------
- 52 R talents, 36 SR talents, and 18 SSR talents are loaded from the editable
  config/aegis_ascension/talents.json file. The bundled default is copied there on
  first launch. Restart Minecraft after editing it.
- 20 Soul Links are loaded from config/aegis_ascension/soul_links.json. They are
  evaluated automatically from their prerequisite talents and are
  not rolled as cards. Plum Blossom Garden activates from Kokona and Sunohara Shun,
  then grants its configured one-time SSR reward.
- Told you it's magic is repeatable up to rank 3. Other workbook talents are one-time.
- Magic Blade, Skill Damage Conversion, and Plateau Witch are manually toggleable.
- Percentage Lucky Strike/Luck bonuses map to vanilla Luck as decimals: 20% = +0.2.
- Primary Attribute bonuses currently modify Attack Damage.
- With selection-triggered Breakthrough enabled, each spent Perk charge executes one
  stored trigger. After the final charge or upon reaching the unique-talent slot cap,
  all stored triggers still remaining execute automatically. A selected talent affects
  later triggers, never its own trigger.
- `resetTalentRefreshOnBreakthrough` optionally clears unused Talent refresh charges
  whenever a Breakthrough begins; it is disabled by default.
- Shop, gold, mana, shield, summon, and spell-cooldown fields remain integration hooks.

JSON talent configuration
-------------------------
- The root `perks` list contains id, tier, translation-key name/description, icon,
  stats, maxRank, manualToggle, and source_row for every workbook talent.
- The separate `soul_links.json` root `soul_links` list contains required_perks,
  rank_perks, translated synergy name and description, icon, bonus_stats, enabled state,
  and source_row for every Soul Link.
- All workbook balance numbers are exposed as JSON numbers. Percentages are decimal
  fractions: 0.20 means 20%. Durations use seconds unless a key ends in `_ticks`.
- English text lives in assets/aegis_ascension/lang/en_us.json. Add another Minecraft
  language JSON with the same keys to translate talent and combination text.
- Run `node scripts/validate_talent_catalog.mjs` after editing the bundled default to
  check IDs, tiers, numeric stats, translation keys, icons, and prerequisites.

Addon catalog registration
--------------------------
Dependent mods can contribute data during common setup without editing this mod's config.
Ship whichever files the addon needs in its own jar:

  assets/<your_mod_id>/talents.json
  assets/<your_mod_id>/aegises.json
  assets/<your_mod_id>/soul_links.json

Then register them from the addon's common-setup work queue. Register talents before Soul
Links when the links reference addon talents; Aegises are independent:

  Perk.registerAddonTalents(MOD_ID);
  Aegis.registerAddonAegises(MOD_ID);
  SoulLinkCatalog.registerAddonSoulLinks(MOD_ID);

Call only the methods for files the addon actually ships. Each root uses the same `perks`,
`aegises`, or `soul_links` array shape as the corresponding bundled catalog; unrelated root
settings are ignored. Entry IDs must be namespaced to the registering mod, for example
`examplemod:starlight_guard`. Translation keys and icon ResourceLocations may point into
the addon's own assets. Talent and Aegis registrations implicitly require the owning mod;
Aegises may also declare `required_mods` for additional integrations.

The individual registration methods intentionally validate and commit one catalog at a
time. Consequently, an addon talent cannot use `pool_required_soul_links` to reference a
Soul Link introduced by that same addon: talents must exist before that Soul Link can
validate its required_perks. Use Soul Links as the dependent side of that relationship.
If bidirectional cross-catalog references are needed later, they should be introduced with
a single bundled registration transaction rather than by weakening either validator.

Registration is transactional per catalog and is rejected after a server starts because
player progression stores Perk and Aegis object identities in memory. Registered entries
are included automatically in the server-authoritative login snapshot. An addon Aegis is
selectable and persistent through this interface, but specialized gameplay behavior still
belongs to the addon, which can check its namespaced ID from its own event handlers.

Implementation map
------------------
- assets/aegis_ascension/talents.json: bundled default Perk config
- assets/aegis_ascension/soul_links.json: bundled default Soul Link config
- assets/aegis_ascension/aegises.json: bundled default catalog for 19 Aegises
- assets/aegis_ascension/lang/en_us.json: English talent/combination translations
- perk/TalentConstants.java: centralized perk, Soul-Link, and stat string constants
- perk/Perk.java: external JSON loader and R/SR/SSR catalog
- perk/TalentEffects.java: JSON-driven acquisition, Breakthrough, attribute, and combat events
- capability/PlayerPerkData.java: charges, milestone history, ranks, NBT, and offers
- event/ForgeEvents.java: capability attachment/copying, leveling, login, and respawn
- network/: C2S offer/selection packets and S2C state/UI packets
- client/: configurable key mapping, synchronized state, and responsive GUI

Build and run
-------------
Use a Java 17 JDK.

  ./gradlew build
  ./gradlew runClient

The distributable JAR is produced under build/libs/.

Configuration
-------------
Forge generates config/aegis_ascension-common.toml after the mod starts. Set:

  resetPerksOnDeath = true

to clear all saved perk data on player death. It defaults to false. Non-death player
clones, including returning from the End, always retain perk data.

To hide and disable selected talents server-wide, add their JSON IDs to:

  hiddenTalentIds = ["r_skill_damage_conversion", "sr_plana"]

Hidden talents do not appear in offers or the Talent Collection, provide effects, consume
visible talent slots, or satisfy unlock requirements. Their ranks remain saved, so removing
an ID restores the talent. A Soul Link that requires any hidden talent is shown as Disabled.
The list is synchronized from the server and can be reloaded while the server is running.

Set:

liveCustomStatsRefresh = true

to permit clients to request Custom Stats refreshes while that tab is open. It defaults to
false. The permission is synchronized to clients, and the server rate-limits both live and
manual stat-sync requests using the configurable packet cooldowns below.

The mod also generates config/aegis_ascension/talents.json on first launch. That file
contains editable rarity weights, perk stats, max ranks, and manual toggles. Soul Link
requirements and bonuses are generated separately in soul_links.json. Existing balance
edits are preserved.

On multiplayer login, the server sends authoritative snapshots of Perks, Aegises, Skill
Enhancements, Virtual Items, Soul Links, Mysterious Doll outcomes, and Shrine Maiden Dance
outcomes before normal progression state. Invalid snapshots are rejected client-side, and
old protocol versions are refused rather than decoded with the wrong packet layout.

The shop is configured in config/aegis_ascension/shopsetting.json. Common Shop uses the
configured item pools; Discovery Shop samples the live registry, including modded items.
Discovery equipment is automatically classified as R, SR, or SSR from attack-damage and
armor thresholds, with configurable rarity weights and a continuous high-power falloff.
Set autoClassifyEquipmentTier to false, or add an ordered rules entry with tier, to override
the automatic result. A rule's selectionWeightMultiplier can further raise, lower, or set to
zero an item's chance.

Quest rewards can select from those same shop pools without changing the player's current
stock. In questsetting.json, use for example:

  {"kind":"shop_item","source":"common","tier":"SR","count":4}
  {"kind":"shop_item","source":"discovery","tier":"SSR","count":1,
   "unique":true,"fallbackId":"minecraft:diamond"}

Common rewards use real randomItems and their configured weights. Discovery rewards use
the live item registry, including modded items, together with Discovery filters, tier rules,
equipment thresholds, and high-power weighting. A unique item is excluded before rolling
if it was already claimed or is reserved by another active quest. fallbackId is a guaranteed,
non-unique consolation item when the requested tier has no eligible candidate. The legacy
random_common and random_unique kinds remain supported as Common-tier and Discovery-SSR
aliases respectively.

Packet throttles live in the [packetCooldowns] section of
config/aegis_ascension-common.toml. Values are seconds and 0 disables that bucket:
storageMutationSeconds, storageViewSeconds, toggleAndPurchaseSeconds, refreshSeconds,
devourItemSeconds, devourDataSeconds, discardDevouredSeconds, perkDataSeconds,
livePerkDataSeconds, sharedFortuneSeconds, questViewSeconds, and
questProgressSyncIntervalSeconds. The quest progress interval batches ordinary counter
changes into compact delta packets; quest completion and structural changes still sync
immediately.

The apothic_attribute_mappings array maps stat keys to registered attributes when Apothic
Attributes is installed. Each mapped value includes the player's persisted custom stat,
matching stats from owned perks, and matching bonus_stats from active Soul Links. This
means a future JSON perk with stats.life_steal automatically contributes to
attributeslib:life_steal without new Java effect code. Decimal percentage values use 0.20
for +20%. Supported operations are addition, multiply_base, and multiply_total. Set
enabled to false to remove/disable a mapping. Older generated catalogs without this array
automatically use the mappings bundled in the mod, so existing worlds receive the fix.

Millennium Echo calculates overflow from the player's final Apothic Crit Chance, including
equipment and other mods. Its overflow step and Critical Damage per step remain editable
in the Soul Link's bonus_stats object.

Custom icon textures
--------------------
Perk icons are texture ResourceLocations, not Item instances. Talent icons are under
assets/aegis_ascension/textures/gui/talents/. A custom texture can be referenced with:

  GeneralClientMethods.fromNamespaceAndPath(
      "aegis_ascension", "textures/gui/talents/my_perk.png"
  )
