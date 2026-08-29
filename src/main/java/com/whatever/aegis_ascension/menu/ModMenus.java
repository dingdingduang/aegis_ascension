package com.whatever.aegis_ascension.menu;

import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.world.inventory.MenuType;

/** Loader-neutral access to menu types used by server-backed ACG screens. */
public final class ModMenus {
    private ModMenus() {
    }

    public static MenuType<ACGInventoryMenu> acgInventory() {
        return PlatformServices.menus().acgInventory();
    }
}
