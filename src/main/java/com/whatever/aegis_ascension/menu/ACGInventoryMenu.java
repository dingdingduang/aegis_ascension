package com.whatever.aegis_ascension.menu;

import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.mojang.datafixers.util.Pair;
import com.whatever.aegis_ascension.compat.CuriosCompat;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Vanilla-compatible 3x3 crafting plus the player's real armor, offhand,
 * 27-slot inventory, and hotbar.
 * Virtual storage remains in PlayerStorage and is rendered as cards by the companion
 * screen; it cannot be represented by ordinary Slots because its counts are longs and
 * some rows are not real Minecraft items.
 */
public final class ACGInventoryMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 0;
    public static final int CRAFT_START = 1;
    public static final int CRAFT_END = 10;
    public static final int INVENTORY_START = 10;
    public static final int INVENTORY_END = 37;
    public static final int HOTBAR_START = 37;
    public static final int HOTBAR_END = 46;
    /** Four armor slots appended after the normal inventory slots. */
    public static final int ARMOR_START = HOTBAR_END;
    public static final int ARMOR_END = ARMOR_START + 4;
    /** Real player offhand slot, appended without changing the older slot ranges. */
    public static final int OFFHAND_SLOT = ARMOR_END;

    /** Curios slots are appended dynamically after the stable vanilla ranges. */
    private static final int CURIOS_ORIGIN_X = 205;
    private static final int CURIOS_ORIGIN_Y = 35;
    private static final int CURIOS_COLUMNS = 2;
    private static final int CURIOS_ROWS_PER_PAGE = 7;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final CraftingContainer crafting = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer result = new ResultContainer();
    private final Player player;
    private final List<CurioSlotInfo> curioSlotInfo = new ArrayList<>();
    private final int curiosStart;
    private final int curiosEnd;

    public ACGInventoryMenu(int containerId, Inventory inventory) {
        super(ModMenus.acgInventory(), containerId);
        this.player = inventory.player;

        // Coordinates are relative to ACGInventoryScreen's panel. The vanilla inventory
        // and crafting workspace occupy the left content pane; virtual storage is rendered
        // independently by the client in the right pane because its long counts and
        // virtual entries cannot be represented by ordinary Slots.
        addSlot(new ResultSlot(player, crafting, result, 0, 151, 53));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(crafting, column + row * 3,
                        49 + column * 18, 35 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        29 + column * 18, 120 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 29 + column * 18, 178));
        }
        // Armor slots use the player's real armor inventory indices (36..39), but are
        // appended after the existing slots so old inventory-slot packet indices remain
        // stable. They are arranged vertically in the open area beside the crafting grid.
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            EquipmentSlot equipmentSlot = ARMOR_SLOTS[index];
            addSlot(new ACGArmorSlot(
                    inventory, player, equipmentSlot, 39 - index,
                    10, 35 + index * 18,
                    emptyArmorIcon(equipmentSlot)
            ));
        }
        // The fifth equipment slot sits immediately below the four armor slots.
        // Its x coordinate remains outside the main inventory grid, so the two
        // columns can share vertical space without overlapping click targets.
        addSlot(new ACGOffhandSlot(inventory, player, 40, 10, 107));

        curiosStart = slots.size();
        for (CuriosCompat.MenuSlot entry : CuriosCompat.createMenuSlots(
                player, CURIOS_ORIGIN_X, CURIOS_ORIGIN_Y,
                CURIOS_COLUMNS, CURIOS_ROWS_PER_PAGE)) {
            addSlot(entry.slot());
            curioSlotInfo.add(new CurioSlotInfo(
                    slots.size() - 1,
                    entry.identifier(),
                    entry.handlerIndex(),
                    entry.pageIndex()
            ));
        }
        curiosEnd = slots.size();
    }

    public boolean hasCurios() {
        return curiosEnd > curiosStart;
    }

    public int curiosStart() {
        return curiosStart;
    }

    public int curiosEnd() {
        return curiosEnd;
    }

    public boolean isCuriosSlot(int slotIndex) {
        return slotIndex >= curiosStart && slotIndex < curiosEnd;
    }

    public List<CurioSlotInfo> curioSlotInfo() {
        return Collections.unmodifiableList(curioSlotInfo);
    }

    public int curioPageCount() {
        return curioSlotInfo.stream()
                .mapToInt(CurioSlotInfo::pageIndex)
                .max()
                .orElse(-1) + 1;
    }

    /**
     * Activates one visual page without removing any server-authoritative Curios slots.
     * The returned value is the clamped page that was actually selected.
     */
    public int setCurioPage(int requestedPage) {
        int pageCount = curioPageCount();
        int page = pageCount <= 0
                ? 0
                : Math.max(0, Math.min(requestedPage, pageCount - 1));
        for (CurioSlotInfo info : curioSlotInfo) {
            CuriosCompat.setPageActive(
                    slots.get(info.menuIndex()),
                    info.pageIndex() == page
            );
        }
        return page;
    }

    public CurioSlotInfo curioSlotInfo(int menuIndex) {
        for (CurioSlotInfo info : curioSlotInfo) {
            if (info.menuIndex() == menuIndex) {
                return info;
            }
        }
        return null;
    }

    public record CurioSlotInfo(
            int menuIndex,
            String identifier,
            int handlerIndex,
            int pageIndex
    ) {
    }

    public CraftingContainer crafting() {
        return crafting;
    }

    @Override
    public void slotsChanged(Container container) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ItemStack output = ItemStack.EMPTY;
        Optional<CraftingRecipe> match = serverPlayer.serverLevel().getServer()
                .getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, crafting, serverPlayer.serverLevel());
        if (match.isPresent()) {
            CraftingRecipe recipe = match.get();
            if (result.setRecipeUsed(serverPlayer.serverLevel(), serverPlayer, recipe)) {
                ItemStack assembled = recipe.assemble(
                        crafting,
                        serverPlayer.serverLevel().registryAccess()
                );
                if (assembled.isItemEnabled(serverPlayer.serverLevel().enabledFeatures())) {
                    output = assembled;
                }
            }
        }
        result.setItem(0, output);
        setRemoteSlot(RESULT_SLOT, output);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                containerId,
                incrementStateId(),
                RESULT_SLOT,
                output
        ));
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) {
            clearContainer(player, crafting);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // This is a player-owned virtual workbench, not a block that can become distant.
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot == null || !slot.hasItem()) {
            return original;
        }

        ItemStack moving = slot.getItem();
        original = moving.copy();
        EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(moving);
        if (slotIndex == RESULT_SLOT) {
            moving.getItem().onCraftedBy(moving, player.level(), player);
            if (!moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(moving, original);
        } else if (slotIndex >= ARMOR_START && slotIndex < ARMOR_END) {
            if (!moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex == OFFHAND_SLOT) {
            if (!moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (isCuriosSlot(slotIndex)) {
            if (!moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex >= INVENTORY_START && slotIndex < HOTBAR_END) {
            int armorSlot = armorMenuSlot(equipmentSlot);
            if (armorSlot >= 0 && !slots.get(armorSlot).hasItem()) {
                if (!moveItemStackTo(moving, armorSlot, armorSlot + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (equipmentSlot == EquipmentSlot.OFFHAND
                    && !slots.get(OFFHAND_SLOT).hasItem()) {
                if (!moveItemStackTo(moving, OFFHAND_SLOT, OFFHAND_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (curiosStart < curiosEnd
                    && !moveItemStackTo(moving, curiosStart, curiosEnd, false)) {
                if (slotIndex < HOTBAR_START) {
                    if (!moveItemStackTo(moving, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(moving, INVENTORY_START, HOTBAR_START, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(moving, CRAFT_START, CRAFT_END, false)) {
                if (slotIndex < HOTBAR_START) {
                    if (!moveItemStackTo(moving, HOTBAR_START, HOTBAR_END, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!moveItemStackTo(moving, INVENTORY_START, HOTBAR_START, false)) {
                    return ItemStack.EMPTY;
                }
            }
        } else if (!moveItemStackTo(moving, INVENTORY_START, HOTBAR_END, false)) {
            return ItemStack.EMPTY;
        }

        if (moving.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (moving.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, moving);
        if (slotIndex == RESULT_SLOT) {
            player.drop(moving, false);
        }
        return original;
    }

    private static int armorMenuSlot(EquipmentSlot slot) {
        if (slot == null || slot.getType() != EquipmentSlot.Type.ARMOR) {
            return -1;
        }
        return ARMOR_START + 3 - slot.getIndex();
    }

    private static ResourceLocation emptyArmorIcon(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            case FEET -> net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
            default -> null;
        };
    }

    /**
     * Vanilla's ArmorSlot is package-private, so the custom menu mirrors its behavior here:
     * correct equipment validation, one-item capacity, equip callbacks, binding-curse
     * protection, and the familiar empty armor-slot icons.
     */
    private static final class ACGArmorSlot extends Slot {
        private final Player owner;
        private final EquipmentSlot equipmentSlot;
        private final ResourceLocation emptyIcon;

        private ACGArmorSlot(Container container, Player owner, EquipmentSlot equipmentSlot,
                             int containerIndex, int x, int y, ResourceLocation emptyIcon) {
            super(container, containerIndex, x, y);
            this.owner = owner;
            this.equipmentSlot = equipmentSlot;
            this.emptyIcon = emptyIcon;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            owner.onEquipItem(equipmentSlot, getItem(), stack);
            super.setByPlayer(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.canEquip(equipmentSlot, owner);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = getItem();
            return !(!stack.isEmpty() && !player.isCreative()
                    && EnchantmentHelper.hasBindingCurse(stack))
                    && super.mayPickup(player);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS, emptyIcon);
        }
    }

    /** Mirrors the real offhand slot from vanilla's InventoryMenu. */
    private static final class ACGOffhandSlot extends Slot {
        private final Player owner;

        private ACGOffhandSlot(Container container, Player owner,
                               int containerIndex, int x, int y) {
            super(container, containerIndex, x, y);
            this.owner = owner;
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            owner.onEquipItem(EquipmentSlot.OFFHAND, getItem(), stack);
            super.setByPlayer(stack);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(
                    net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS,
                    net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD
            );
        }
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != result && super.canTakeItemForPickAll(stack, slot);
    }

    public void fillCraftingStackedContents(StackedContents contents) {
        crafting.fillStackedContents(contents);
    }
}
