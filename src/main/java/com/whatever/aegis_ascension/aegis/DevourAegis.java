package com.whatever.aegis_ascension.aegis;

import com.whatever.aegis_ascension.platform.PlatformServices;
import com.whatever.aegis_ascension.capability.PlayerPerkData;
import com.whatever.aegis_ascension.platform.AttributeOperation;
import com.whatever.aegis_ascension.platform.ItemAttributeModifier;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

/**
 * Complete runtime implementation for Devour Aegis.
 *
 * <p>The server snapshots every attribute modifier exposed by the held stack,
 * stores that snapshot in the player's persisted data, and reapplies it without an
 * equipment-slot condition. Attribute operations are preserved exactly.</p>
 */
public final class DevourAegis {
    private static final String MODIFIER_NAME_PREFIX = "aegis_ascension:devour/";

    private DevourAegis() {
    }

    /** Attempts to consume one main-hand item and permanently inherit its attributes. */
    public static boolean tryDevour(ServerPlayer player, PlayerPerkData data) {
        if (!data.hasAegis(AegisConstants.DEVOUR)) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.devour.no_aegis"
            ));
            return false;
        }

        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.devour.empty_hand"
            ));
            return false;
        }

        ResourceLocation itemLocation = GeneralServerMethods.getItemKey(stack.getItem());
        if (itemLocation == null) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.devour.invalid_item"
            ));
            return false;
        }
        String itemId = itemLocation.toString();
        if (data.hasDevouredItem(itemId)) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.devour.already_devoured",
                    stack.getHoverName()
            ));
            return false;
        }

        List<InheritedAttribute> inheritedAttributes = snapshotAttributes(itemId, stack);
        double inheritance = inheritanceMultiplier();
        long appliedAttributeCount = inheritedAttributes.stream()
                .filter(attribute -> !PlatformServices.config().isDevourAttributeBlacklisted(
                        attribute.attributeId()
                ))
                .map(DevourAegis::effectiveModifier)
                .filter(modifier -> Math.abs(modifier.amount() * inheritance) >= 1.0E-9D)
                .count();
        if (appliedAttributeCount == 0L) {
            player.sendSystemMessage(getTranslatableString(
                    "message.aegis_ascension.devour.no_attributes",
                    stack.getHoverName()
            ));
            return false;
        }

        Component itemName = stack.getHoverName().copy();
        if (!data.recordDevouredItem(itemId, inheritedAttributes)) {
            return false;
        }
        stack.shrink(1);
        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(
                null,
                player.blockPosition(),
                SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS,
                1.0F,
                1.0F
        );
        data.applyChosenPerks(player);
        player.sendSystemMessage(getTranslatableString(
                "message.aegis_ascension.devour.success",
                itemName,
                appliedAttributeCount
        ));
        return true;
    }

    /** Removes old Devour modifiers, then reapplies the authoritative saved snapshot. */
    public static void applyModifiers(ServerPlayer player, PlayerPerkData data) {
        clearAppliedModifiers(player);
        if (!data.hasAegis(AegisConstants.DEVOUR)) {
            return;
        }

        double inheritance = inheritanceMultiplier();
        if (Math.abs(inheritance) < 1.0E-9D) {
            return;
        }
        for (InheritedAttribute inherited : data.getDevouredAttributes()) {
            if (PlatformServices.config().isDevourAttributeBlacklisted(inherited.attributeId())) {
                continue;
            }
            ResourceLocation attributeLocation = PlatformServices.resources().tryParse(
                    inherited.attributeId()
            );
            Attribute attribute = attributeLocation == null
                    ? null
                    : GeneralServerMethods.resolveAttribute(attributeLocation);
            EffectiveModifier effective = effectiveModifier(inherited);
            double amount = effective.amount() * inheritance;
            if (attribute == null
                    || GeneralServerMethods.getAttributeInstance(player, attribute) == null
                    || !Double.isFinite(amount)
                    || Math.abs(amount) < 1.0E-9D) {
                continue;
            }

            UUID modifierId = inheritedModifierId(inherited);
            if (GeneralServerMethods.getAttributeModifier(player, attribute, modifierId) != null) {
                GeneralServerMethods.removeAttributeModifier(player, attribute, modifierId);
            }
            GeneralServerMethods.addAttributeModifier(
                    player,
                    attribute,
                    modifierId,
                    MODIFIER_NAME_PREFIX + inherited.itemId() + "/" + inherited.attributeId(),
                    amount,
                    effective.operation()
            );
        }
    }

    /** Clears all currently attached Devour modifiers, including data from older saves. */
    public static void clearAppliedModifiers(ServerPlayer player) {
        for (Attribute attribute : GeneralServerMethods.getAllAttributes()) {
            if (GeneralServerMethods.getAttributeInstance(player, attribute) == null) {
                continue;
            }
            List<AttributeModifier> modifiers = new ArrayList<>();
            for (AttributeModifier modifier : GeneralServerMethods.getAttributeModifiers(
                    player, attribute
            )) {
                modifiers.add(modifier);
            }
            modifiers.stream()
                    .filter(modifier -> modifier.getName().startsWith(MODIFIER_NAME_PREFIX))
                    .forEach(modifier -> GeneralServerMethods.removeAttributeModifier(
                            player, attribute, modifier.getId()
                    ));
        }
    }

    private static List<InheritedAttribute> snapshotAttributes(String itemId, ItemStack stack) {
        Map<String, InheritedAttribute> unique = new LinkedHashMap<>();
        for (ItemAttributeModifier modifier
                : PlatformServices.attributes().itemModifiers(stack)) {
            String identity = modifier.attributeId() + "/" + modifier.modifierId()
                    + "/" + modifier.operation().wireValue()
                    + "/" + Double.toHexString(modifier.amount());
            unique.putIfAbsent(identity, new InheritedAttribute(
                    itemId,
                    modifier.attributeId().toString(),
                    modifier.modifierId(),
                    modifier.amount(),
                    modifier.operation()
            ));
        }
        return List.copyOf(unique.values());
    }

    private static double inheritanceMultiplier() {
        return Aegis.byId(AegisConstants.DEVOUR)
                .map(aegis -> aegis.stat(AegisConstants.DEVOUR_STAT_INHERITANCE))
                .orElse(1.0D);
    }

    /**
     * Resolves the modifier Devour should currently apply. Keeping this conversion at
     * application/sync time lets a config reload update items that were already saved.
     */
    public static EffectiveModifier effectiveModifier(InheritedAttribute inherited) {
        if (PlatformServices.config().convertFlatAttackSpeedToPercentage()
                && inherited.operation() == AttributeOperation.ADDITION
                && "minecraft:generic.attack_speed".equals(inherited.attributeId())) {
            double baseAttackSpeed = Attributes.ATTACK_SPEED.getDefaultValue();
            if (Double.isFinite(baseAttackSpeed)
                    && Math.abs(baseAttackSpeed) >= 1.0E-9D) {
                return new EffectiveModifier(
                        inherited.amount() / baseAttackSpeed,
                        AttributeOperation.MULTIPLY_TOTAL
                );
            }
        }
        return new EffectiveModifier(inherited.amount(), inherited.operation());
    }

    private static UUID inheritedModifierId(InheritedAttribute inherited) {
        String key = MODIFIER_NAME_PREFIX + inherited.itemId()
                + "/" + inherited.attributeId()
                + "/" + inherited.sourceModifierId()
                + "/" + inherited.operation().wireValue()
                + "/" + Double.toHexString(inherited.amount());
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    public record EffectiveModifier(
            double amount,
            AttributeOperation operation
    ) {
    }

    /** Serializable snapshot of one modifier supplied by a devoured item. */
    public record InheritedAttribute(
            String itemId,
            String attributeId,
            UUID sourceModifierId,
            double amount,
            AttributeOperation operation
    ) {
        private static final String ITEM_TAG = "Item";
        private static final String ATTRIBUTE_TAG = "Attribute";
        private static final String SOURCE_MODIFIER_TAG = "SourceModifier";
        private static final String AMOUNT_TAG = "Amount";
        private static final String OPERATION_TAG = "Operation";

        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString(ITEM_TAG, itemId);
            tag.putString(ATTRIBUTE_TAG, attributeId);
            tag.putUUID(SOURCE_MODIFIER_TAG, sourceModifierId);
            tag.putDouble(AMOUNT_TAG, amount);
            tag.putInt(OPERATION_TAG, operation.wireValue());
            return tag;
        }

        public static Optional<InheritedAttribute> deserializeNBT(CompoundTag tag) {
            try {
                String itemId = tag.getString(ITEM_TAG);
                String attributeId = tag.getString(ATTRIBUTE_TAG);
                double amount = tag.getDouble(AMOUNT_TAG);
                if (itemId.isBlank() || PlatformServices.resources().tryParse(itemId) == null
                        || attributeId.isBlank()
                        || PlatformServices.resources().tryParse(attributeId) == null
                        || !tag.hasUUID(SOURCE_MODIFIER_TAG)
                        || !Double.isFinite(amount)) {
                    return Optional.empty();
                }
                return Optional.of(new InheritedAttribute(
                        itemId,
                        attributeId,
                        tag.getUUID(SOURCE_MODIFIER_TAG),
                        amount,
                        AttributeOperation.fromWireValue(tag.getInt(OPERATION_TAG))
                ));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }
}
