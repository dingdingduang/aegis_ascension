package com.whatever.aegis_ascension.platform;

import com.whatever.aegis_ascension.menu.ACGInventoryMenu;
import net.minecraft.world.inventory.MenuType;

/** Boundary for menu-type lookup used by common menu and client screen code. */
public interface MenuAccess {
    MenuType<ACGInventoryMenu> acgInventory();
}
