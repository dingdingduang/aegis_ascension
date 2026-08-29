package com.whatever.aegis_ascension.event;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.compat.IronSpellsCompat;
import com.whatever.aegis_ascension.compat.SummonCompat;
import com.whatever.aegis_ascension.util.GeneralServerMethods;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds the generic attributes used by Blessing to optional spell-mod summons. */
@Mod.EventBusSubscriber(modid = AegisAscensionMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBusEvents {
    private ModBusEvents() {
    }

    @SubscribeEvent
    public static void addSummonAttributes(EntityAttributeModificationEvent event) {
        event.getTypes().forEach(type -> {
            ResourceLocation id = GeneralServerMethods.getEntityTypeKey(type);
            if (id == null || (!id.getNamespace().equals(IronSpellsCompat.MOD_ID)
                    && !id.getNamespace().equals(SummonCompat.ARS_NOUVEAU_MOD_ID))) {
                return;
            }
            if (!event.has(type, Attributes.ATTACK_SPEED)) {
                event.add(type, Attributes.ATTACK_SPEED);
            }
            if (!event.has(type, GeneralServerMethods.getEntityReachAttribute())) {
                event.add(type, GeneralServerMethods.getEntityReachAttribute());
            }
        });
    }
}
