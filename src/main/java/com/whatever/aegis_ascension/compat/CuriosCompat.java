package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.platform.PlatformServices;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Optional Curios bridge used by the ACG inventory menu.
 *
 * <p>Curios is intentionally kept behind this lazy bridge. The main menu and
 * screen can therefore still be loaded in a profile that does not install
 * Curios, while a profile that does install it receives real Curios-backed
 * slots with the normal server-authoritative container click handling.</p>
 */
public final class CuriosCompat {
    public static final String MOD_ID = "curios";

    private static final String BRIDGE_CLASS =
            "com.whatever.aegis_ascension.compat.CuriosCompat$Bridge";

    private static Boolean bridgeUsable;

    private CuriosCompat() {
    }

    /** Metadata kept by ACGInventoryMenu without exposing Curios API types. */
    public record MenuSlot(String identifier, int handlerIndex, int pageIndex, Slot slot) {
    }

    /** Implemented only by the lazily loaded Curios-backed slot subclass. */
    private interface PageAwareSlot {
        void aegisAscension$setPageActive(boolean active);
    }

    public static boolean isLoaded() {
        return PlatformServices.mods().isLoaded(MOD_ID);
    }

    /**
     * Creates visible Curios slots in deterministic slot-type order. The returned
     * {@link Slot}s are backed directly by Curios' dynamic item handlers, so
     * vanilla menu clicks, shift-clicks, and container synchronization all use
     * Curios' own validation and equip callbacks.
     */
    public static List<MenuSlot> createMenuSlots(Player player, int originX, int originY,
                                                  int columns, int rowsPerPage) {
        if (player == null || columns <= 0 || rowsPerPage <= 0 || !useBridge()) {
            return List.of();
        }
        try {
            return Bridge.createMenuSlots(
                    player, originX, originY, columns, rowsPerPage
            );
        } catch (LinkageError | RuntimeException exception) {
            AegisAscensionMod.getLogger().error(
                    "Curios is installed, but its inventory slots could not be created",
                    exception
            );
            return List.of();
        }
    }

    /** Changes only client presentation; every slot remains present in the menu. */
    public static void setPageActive(Slot slot, boolean active) {
        if (slot instanceof PageAwareSlot paged) {
            paged.aegisAscension$setPageActive(active);
        }
    }

    private static boolean useBridge() {
        Boolean resolved = bridgeUsable;
        if (resolved != null) {
            return resolved;
        }
        boolean usable = false;
        if (isLoaded()) {
            try {
                Class.forName(BRIDGE_CLASS, true, CuriosCompat.class.getClassLoader());
                usable = true;
                AegisAscensionMod.getLogger().info(
                        "Enabled optional Curios inventory compatibility"
                );
            } catch (ReflectiveOperationException | LinkageError exception) {
                AegisAscensionMod.getLogger().error(
                        "Curios is installed, but its inventory bridge could not load",
                        exception
                );
            }
        }
        bridgeUsable = usable;
        return usable;
    }

    /** All Curios API references are isolated in this lazily loaded class. */
    private static final class Bridge {
        private Bridge() {
        }

        private static List<MenuSlot> createMenuSlots(Player player, int originX,
                                                       int originY, int columns,
                                                       int rowsPerPage) {
            var optionalHandler = top.theillusivec4.curios.api.CuriosApi
                    .getCuriosInventory(player)
                    .resolve();
            if (optionalHandler.isEmpty()) {
                return List.of();
            }

            var handler = optionalHandler.orElseThrow();
            Map<String, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler>
                    curios = handler.getCurios();
            List<String> identifiers = new ArrayList<>(curios.keySet());
            identifiers.removeIf(identifier -> {
                var stacks = curios.get(identifier);
                return stacks == null || !stacks.isVisible();
            });
            identifiers.sort(Comparator
                    .comparingInt((String identifier) ->
                            top.theillusivec4.curios.api.CuriosApi
                                    .getSlot(identifier, player.level())
                                    .map(top.theillusivec4.curios.api.type.ISlotType::getOrder)
                                    .orElse(Integer.MAX_VALUE))
                    .thenComparing(String::compareTo));

            List<MenuSlot> result = new ArrayList<>();
            int safeColumns = Math.max(1, columns);
            int pageSize = safeColumns * Math.max(1, rowsPerPage);
            int visualIndex = 0;
            for (String identifier : identifiers) {
                var stacksHandler = curios.get(identifier);
                var stacks = stacksHandler.getStacks();
                int slotCount = Math.min(stacksHandler.getSlots(), stacks.getSlots());
                boolean canToggleRender = top.theillusivec4.curios.api.CuriosApi
                        .getSlot(identifier, player.level())
                        .map(top.theillusivec4.curios.api.type.ISlotType::canToggleRendering)
                        .orElse(false);
                for (int handlerIndex = 0; handlerIndex < slotCount; handlerIndex++) {
                    int pageIndex = visualIndex / pageSize;
                    int localIndex = visualIndex % pageSize;
                    int x = originX + (localIndex % safeColumns) * 18;
                    int y = originY + (localIndex / safeColumns) * 18;
                    Slot slot = new PagedCurioSlot(
                            player,
                            stacks,
                            handlerIndex,
                            identifier,
                            x,
                            y,
                            stacksHandler.getRenders(),
                            canToggleRender
                    );
                    result.add(new MenuSlot(
                            identifier, handlerIndex, pageIndex, slot
                    ));
                    visualIndex++;
                }
            }
            return result;
        }

        /**
         * Page visibility participates in vanilla's existing Slot#isActive checks,
         * which cover rendering, hover lookup, clicks, dragging, and hotbar keys.
         */
        private static final class PagedCurioSlot
                extends top.theillusivec4.curios.common.inventory.CurioSlot
                implements PageAwareSlot {
            private boolean pageActive = true;

            private PagedCurioSlot(
                    Player player,
                    top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler stacks,
                    int handlerIndex,
                    String identifier,
                    int x,
                    int y,
                    net.minecraft.core.NonNullList<Boolean> renderStatuses,
                    boolean canToggleRender
            ) {
                super(
                        player, stacks, handlerIndex, identifier, x, y,
                        renderStatuses, canToggleRender
                );
            }

            @Override
            public boolean isActive() {
                return pageActive && super.isActive();
            }

            @Override
            public void aegisAscension$setPageActive(boolean active) {
                pageActive = active;
            }
        }
    }
}
