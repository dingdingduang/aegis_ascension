package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.mechanic.TalentEffects;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.util.AegisModifiers;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Optional Iron's Spells bridge with all direct API references isolated elsewhere. */
public final class IronSpellsCompat {
    public static final String MOD_ID = "irons_spellbooks";
    private static final String SPELL_DAMAGE_SOURCE_CLASS =
            "io.redspace.ironsspellbooks.damage.SpellDamageSource";
    private static final String WISDOM_HANDLER_CLASS =
            "com.whatever.aegis_ascension.aegis.WisdomAegis";
    private static final String HOMURA_HANDLER_CLASS =
            "com.whatever.aegis_ascension.compat.HomuraBlessingHandler";
    private static final ResourceLocation MAX_MANA_ATTRIBUTE =
            PlatformServices.resources().create(MOD_ID, "max_mana");
    private static final ResourceLocation MANA_REGEN_ATTRIBUTE =
            PlatformServices.resources().create(MOD_ID, "mana_regen");
    private static final UUID MAGIC_CONVERSION_MAX_MANA_MODIFIER_ID =
            AegisModifiers.adopt("7d2d44d8-e7ce-4cbe-9194-fc366eff3546");
    private static final UUID FRIEREN_MAX_MANA_MODIFIER_ID =
            AegisModifiers.adopt("f9086697-6a6f-4a7b-8a1e-5c91961d03e1");
    private static final UUID NOELLE_MANA_REGEN_MODIFIER_ID =
            AegisModifiers.adopt("24abf786-68ab-481b-b2c9-49f7ce747b2c");

    private static boolean handlersRegistered;

    private IronSpellsCompat() {
    }

    public static boolean isLoaded() {
        return PlatformServices.mods().isLoaded(MOD_ID);
    }

    /** Registers the API-linked handler only when Iron's Spells is actually installed. */
    public static void registerOptionalHandlers() {
        if (!isLoaded() || handlersRegistered) {
            return;
        }
        try {
            registerHandler(WISDOM_HANDLER_CLASS);
            registerHandler(HOMURA_HANDLER_CLASS);
            handlersRegistered = true;
            AegisAscensionMod.getLogger().info(
                    "Enabled optional Iron's Spells 'n Spellbooks compatibility"
            );
        } catch (ReflectiveOperationException | LinkageError exception) {
            AegisAscensionMod.getLogger().error(
                    "Iron's Spells is installed, but its compatibility handler could not load",
                    exception
            );
        }
    }

    private static void registerHandler(String className)
            throws ReflectiveOperationException {
        Class<?> handler = Class.forName(
                className,
                true,
                IronSpellsCompat.class.getClassLoader()
        );
        PlatformServices.mods().registerGameEventHandler(handler);
    }

    /** Uses the stable runtime type name so ordinary damage support has no hard dependency. */
    public static boolean isIronSpellDamage(DamageSource source) {
        if (!isLoaded()) {
            return false;
        }
        for (Class<?> type = source.getClass(); type != null; type = type.getSuperclass()) {
            if (SPELL_DAMAGE_SOURCE_CLASS.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the player's live Iron's Spells Max Mana without linking against
     * Iron's Java API. When the mod or attribute is unavailable, Magic
     * Conversion's internal Max Mana remains usable by data-driven effects.
     */
    public static double maximumMana(Player player, PlayerPerkData data) {
        double fallback = TalentEffects.magicConversionMaximumMana(data)
                + TalentEffects.frierenMaximumMana(data);
        if (!isLoaded()) {
            return fallback;
        }
        return Math.max(
                0.0D,
                GeneralServerMethods.getAttributeValue(player, MAX_MANA_ATTRIBUTE, fallback)
        );
    }

    /** Restores live Iron mana; direct API symbols remain isolated in the nested bridge. */
    public static void restoreMana(Player player, double amount) {
        if (!isLoaded() || amount <= 0.0D || !Double.isFinite(amount)) {
            return;
        }
        try {
            SpellBridge.restoreMana(player, amount);
        } catch (LinkageError | RuntimeException exception) {
            // Optional API missing or changed: leave the other supported pools usable.
        }
    }

    /** Publishes data-driven Max Mana bonuses as Iron's real attribute. */
    public static void updateAttributeModifiers(Player player, PlayerPerkData data) {
        if (!isLoaded()) {
            return;
        }
        if (GeneralServerMethods.getAttributeInstance(player, MAX_MANA_ATTRIBUTE) != null) {
            updateModifier(
                    player,
                    MAX_MANA_ATTRIBUTE,
                    MAGIC_CONVERSION_MAX_MANA_MODIFIER_ID,
                    "aegis_ascension:magic_conversion_max_mana",
                    TalentEffects.magicConversionMaximumMana(data),
                    AttributeOperation.ADDITION
            );
            updateModifier(
                    player,
                    MAX_MANA_ATTRIBUTE,
                    FRIEREN_MAX_MANA_MODIFIER_ID,
                    "aegis_ascension:frieren_max_mana",
                    TalentEffects.frierenMaximumMana(data),
                    AttributeOperation.ADDITION
            );
        }
        if (GeneralServerMethods.getAttributeInstance(player, MANA_REGEN_ATTRIBUTE) != null) {
            updateModifier(
                    player,
                    MANA_REGEN_ATTRIBUTE,
                    NOELLE_MANA_REGEN_MODIFIER_ID,
                    "aegis_ascension:noelle_mana_regeneration",
                    TalentEffects.manaRegenerationMultiplier(data),
                    AttributeOperation.MULTIPLY_TOTAL
            );
        }
    }

    private static void updateModifier(Player player, ResourceLocation attributeId, UUID id,
                                       String name, double amount,
                                       AttributeOperation operation) {
        AttributeModifier current = GeneralServerMethods.getAttributeModifier(player, attributeId, id);
        if (current != null
                && Math.abs(current.getAmount() - amount) < 1.0E-9D
                && GeneralServerMethods.getAttributeOperation(current) == operation) {
            return;
        }
        if (current != null) {
            GeneralServerMethods.removeAttributeModifier(player, attributeId, id);
        }
        if (Math.abs(amount) >= 1.0E-9D) {
            GeneralServerMethods.addAttributeModifier(
                    player, attributeId, id, name, amount, operation
            );
        }
    }

    /**
     * Casts an Iron's Spells spell by id from {@code caster}, at {@code level}
     * (clamped to the spell's range), returning {@code true} when the spell exists
     * and its effect ran.
     *
     * <p>Uses {@code onCast}, so the effect fires immediately without consuming
     * mana, cooldown, or recasts — the aegis, not the spellbook, drives it.</p>
     */
    public static boolean castSpell(net.minecraft.server.level.ServerPlayer caster,
                                    String spellId, int level) {
        if (!isLoaded() || caster == null || spellId == null || spellId.isBlank()) {
            return false;
        }
        try {
            return SpellBridge.cast(caster, spellId, level);
        } catch (LinkageError | RuntimeException exception) {
            return false;
        }
    }

    /**
     * Marks every Iron's Spells summon owned by {@code owner} within {@code radius}
     * blocks invulnerable — the "ward armor becomes invulnerable" aegis effect.
     */
    public static void makeOwnedSummonsInvulnerable(net.minecraft.server.level.ServerPlayer owner,
                                                    double radius) {
        if (!isLoaded() || owner == null || radius <= 0.0D) {
            return;
        }
        try {
            SpellBridge.invulnerableOwnedSummons(owner, radius);
        } catch (LinkageError | RuntimeException exception) {
            // Iron's Spells absent or its summon API changed: silently skip.
        }
    }

    /** Holds the Iron's spell/summon API symbols so they link only when present. */
    private static final class SpellBridge {
        private SpellBridge() {
        }

        private static boolean cast(net.minecraft.server.level.ServerPlayer caster,
                                    String spellId, int level) {
            io.redspace.ironsspellbooks.api.spells.AbstractSpell spell =
                    io.redspace.ironsspellbooks.api.registry.SpellRegistry.getSpell(spellId);
            if (spell == io.redspace.ironsspellbooks.api.registry.SpellRegistry.none()) {
                return false;
            }
            int clamped = net.minecraft.util.Mth.clamp(
                    level, spell.getMinLevel(), Math.max(spell.getMinLevel(), spell.getMaxLevel()));
            spell.onCast(
                    caster.serverLevel(),
                    clamped,
                    caster,
                    io.redspace.ironsspellbooks.api.spells.CastSource.SPELLBOOK,
                    io.redspace.ironsspellbooks.api.magic.MagicData.getPlayerMagicData(caster));
            return true;
        }

        private static void restoreMana(Player player, double amount) {
            io.redspace.ironsspellbooks.api.magic.MagicData data =
                    io.redspace.ironsspellbooks.api.magic.MagicData
                            .getPlayerMagicData(player);
            data.addMana((float) Math.min(Float.MAX_VALUE, amount));
        }

        private static void invulnerableOwnedSummons(
                net.minecraft.server.level.ServerPlayer owner, double radius) {
            net.minecraft.world.phys.AABB box = owner.getBoundingBox().inflate(radius);
            for (net.minecraft.world.entity.Entity entity : owner.serverLevel().getEntities(
                    owner, box,
                    candidate -> candidate instanceof io.redspace.ironsspellbooks
                            .entity.mobs.IMagicSummon)) {
                io.redspace.ironsspellbooks.entity.mobs.IMagicSummon summon =
                        (io.redspace.ironsspellbooks.entity.mobs.IMagicSummon) entity;
                if (summon.getSummoner() == owner) {
                    entity.setInvulnerable(true);
                }
            }
        }
    }
}
