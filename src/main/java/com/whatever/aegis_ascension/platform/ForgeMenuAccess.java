package com.whatever.aegis_ascension.platform;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.menu.ACGInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Forge 1.20.1 implementation of {@link MenuAccess}. */
public final class ForgeMenuAccess implements MenuAccess {
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, AegisAscensionMod.MOD_ID);

    private static final RegistryObject<MenuType<ACGInventoryMenu>> ACG_INVENTORY =
            MENUS.register("acg_inventory", () -> IForgeMenuType.create(
                    (containerId, inventory, buffer) ->
                            new ACGInventoryMenu(containerId, inventory)
            ));

    /** Registers this platform's menu types on the mod event bus. */
    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }

    @Override
    public MenuType<ACGInventoryMenu> acgInventory() {
        return ACG_INVENTORY.get();
    }
}
