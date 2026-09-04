package com.whatever.aegis_ascension.client.screen;

import static com.whatever.aegis_ascension.util.GeneralClientMethods.blitScaledRegion;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.client.screen.acg.ACGSectionHeader;
import com.whatever.aegis_ascension.client.screen.acg.ACGTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Collapsible, animated left navigation shared by every ACG page. */
final class ACGDrawer {
    enum Destination {
        AEGIS_SELECTION(ACGPerkSelectionScreen.UIMode.AEGIS_SELECTION),
        PERK_SELECTION(ACGPerkSelectionScreen.UIMode.PERK_SELECTION),
        SKILL_ENHANCEMENT(ACGPerkSelectionScreen.UIMode.SKILL_ENHANCEMENT),
        OWNED_AEGIS(ACGPerkSelectionScreen.UIMode.OWNED_AEGIS),
        OWNED_PERKS(ACGPerkSelectionScreen.UIMode.OWNED_PERKS),
        OWNED_SOUL_LINKS(ACGPerkSelectionScreen.UIMode.OWNED_SOUL_LINKS),
        DEVOURED(ACGPerkSelectionScreen.UIMode.DEVOURED),
        PLAYER_CUSTOM_STAT(ACGPerkSelectionScreen.UIMode.PLAYER_CUSTOM_STAT),
        CUSTOM_SHOP(ACGPerkSelectionScreen.UIMode.CUSTOM_SHOP),
        QUEST_CENTER(ACGPerkSelectionScreen.UIMode.QUEST_CENTER),
        STORAGE(ACGPerkSelectionScreen.UIMode.STORAGE),
        INVENTORY_AND_CRAFTING(null),
        SERVER_SETTINGS(ACGPerkSelectionScreen.UIMode.SERVER_SETTINGS),
        CLIENT_SETTINGS(ACGPerkSelectionScreen.UIMode.CLIENT_SETTINGS);

        private final ACGPerkSelectionScreen.UIMode pageMode;

        Destination(ACGPerkSelectionScreen.UIMode pageMode) {
            this.pageMode = pageMode;
        }

        ACGPerkSelectionScreen.UIMode pageMode() {
            return pageMode;
        }

        static Destination fromPageMode(ACGPerkSelectionScreen.UIMode mode) {
            for (Destination destination : values()) {
                if (destination.pageMode == mode) {
                    return destination;
                }
            }
            return AEGIS_SELECTION;
        }
    }

    static final int WIDTH = 172;
    private static final int ROW_HEIGHT = 24;
    private static final int ROW_GAP = 2;
    private static final int GROUP_GAP = 6;
    private static final float GROUP_EASING = 0.25F;

    private boolean selectionExpanded;
    private boolean collectionExpanded;
    private boolean miscellaneousExpanded;
    private float selectionAnim;
    private float collectionAnim;
    private float miscellaneousAnim;
    private int scroll;
    private int maxScroll;
    private int viewportTop;
    private int viewportBottom;
    private ACGSectionHeader selectionHeader;
    private ACGSectionHeader collectionHeader;
    private ACGSectionHeader miscellaneousHeader;
    private final List<NavButton> selectionItems = new ArrayList<>();
    private final List<NavButton> collectionItems = new ArrayList<>();
    private final List<NavButton> miscellaneousItems = new ArrayList<>();
    private long indicatorAnimStartMs = System.currentTimeMillis();
    private int screenHeight;
    private Consumer<AbstractButton> widgetAdder;
    private Supplier<Destination> currentDestination;
    private Consumer<Destination> navigator;
    private BooleanSupplier navigationEnabled;

    ACGDrawer() {
        ClientSettings settings = ClientSettings.get();
        if (settings.rememberCollapsedTabs) {
            selectionExpanded = settings.selectionExpanded;
            collectionExpanded = settings.collectionExpanded;
            miscellaneousExpanded = settings.miscellaneousExpanded;
        } else {
            selectionExpanded = true;
            collectionExpanded = true;
            miscellaneousExpanded = true;
        }
        selectionAnim = selectionExpanded ? 1.0F : 0.0F;
        collectionAnim = collectionExpanded ? 1.0F : 0.0F;
        miscellaneousAnim = miscellaneousExpanded ? 1.0F : 0.0F;
    }

    void init(ACGScreenContext context) {
        init(context.screenHeight(),
                widget -> context.add(widget),
                () -> Destination.fromPageMode(context.mode()),
                destination -> {
                    if (destination == Destination.INVENTORY_AND_CRAFTING) {
                        context.openIntegratedInventory();
                        return;
                    }
                    if (destination == Destination.STORAGE) {
                        ClientSettings settings = ClientSettings.get();
                        settings.inventoryMode = ClientSettings.InventoryMode.ORIGINAL;
                        settings.save();
                    }
                    context.switchMode(destination.pageMode());
                },
                () -> true);
    }

    void init(int screenHeight,
              Consumer<AbstractButton> widgetAdder,
              Supplier<Destination> currentDestination,
              Consumer<Destination> navigator,
              BooleanSupplier navigationEnabled) {
        this.screenHeight = screenHeight;
        this.widgetAdder = widgetAdder;
        this.currentDestination = currentDestination;
        this.navigator = navigator;
        this.navigationEnabled = navigationEnabled;
        selectionItems.clear();
        collectionItems.clear();
        miscellaneousItems.clear();
        int y = ACGPerkSelectionScreen.TOP_BAR_HEIGHT + 10;

        selectionHeader = new ACGSectionHeader(8, y, WIDTH - 16, ROW_HEIGHT,
                getTranslatableString(
                        "screen.aegis_ascension.acg.nav.selection_group"),
                () -> selectionExpanded, this::toggleSelection);
        addWidget(selectionHeader);
        addItem(selectionItems,
                "screen.aegis_ascension.acg.nav.aegis_selection",
                Destination.AEGIS_SELECTION);
        addItem(selectionItems,
                "screen.aegis_ascension.acg.nav.perk_selection",
                Destination.PERK_SELECTION);
        addItem(selectionItems,
                "screen.aegis_ascension.acg.nav.skill_enhancement",
                Destination.SKILL_ENHANCEMENT);

        collectionHeader = new ACGSectionHeader(8, y, WIDTH - 16, ROW_HEIGHT,
                getTranslatableString(
                        "screen.aegis_ascension.acg.nav.collection_group"),
                () -> collectionExpanded, this::toggleCollection);
        addWidget(collectionHeader);
        addItem(collectionItems,
                "screen.aegis_ascension.acg.nav.owned_aegis",
                Destination.OWNED_AEGIS);
        addItem(collectionItems,
                "screen.aegis_ascension.acg.nav.owned_perks",
                Destination.OWNED_PERKS);
        addItem(collectionItems,
                "screen.aegis_ascension.acg.nav.owned_soul_links",
                Destination.OWNED_SOUL_LINKS);
        if (ACGDevouredPage.isAvailable()) {
            addItem(collectionItems,
                    "screen.aegis_ascension.acg.nav.devoured",
                    Destination.DEVOURED);
        }
        addItem(collectionItems,
                "screen.aegis_ascension.acg.nav.custom_stat",
                Destination.PLAYER_CUSTOM_STAT);

        miscellaneousHeader = new ACGSectionHeader(8, y, WIDTH - 16, ROW_HEIGHT,
                getTranslatableString(
                        "screen.aegis_ascension.acg.nav.miscellaneous_group"),
                () -> miscellaneousExpanded, this::toggleMiscellaneous);
        addWidget(miscellaneousHeader);
        addItem(miscellaneousItems,
                "screen.aegis_ascension.acg.nav.custom_shop",
                Destination.CUSTOM_SHOP);
        addItem(miscellaneousItems,
                "screen.aegis_ascension.acg.nav.quest_center",
                Destination.QUEST_CENTER);
        addItem(miscellaneousItems,
                "screen.aegis_ascension.acg.nav.storage",
                Destination.STORAGE);
        addItem(miscellaneousItems,
                "screen.aegis_ascension.acg.nav.inventory_crafting",
                Destination.INVENTORY_AND_CRAFTING);
        addItem(miscellaneousItems,
                "screen.aegis_ascension.acg.nav.server_settings",
                Destination.SERVER_SETTINGS);
        addItem(miscellaneousItems,
                "screen.aegis_ascension.acg.nav.client_settings",
                Destination.CLIENT_SETTINGS);
        layout();
    }

    private void addItem(List<NavButton> group, String labelKey,
                         Destination target) {
        int indent = 6;
        NavButton button = new NavButton(
                8 + indent, 0, WIDTH - 16 - indent, ROW_HEIGHT,
                getTranslatableString(labelKey), target);
        group.add(button);
        addWidget(button);
    }

    private void addWidget(AbstractButton widget) {
        widgetAdder.accept(widget);
    }

    void render(GuiGraphics graphics) {
        layout();
        ACGTheme.drawPanel(graphics, 0, ACGPerkSelectionScreen.TOP_BAR_HEIGHT,
                WIDTH, screenHeight - ACGPerkSelectionScreen.TOP_BAR_HEIGHT,
                (float) ClientSettings.get().drawerOpacity);
        if (maxScroll <= 0) {
            return;
        }
        int trackHeight = viewportBottom - viewportTop;
        int contentHeight = trackHeight + maxScroll;
        int thumbHeight = Math.max(16, trackHeight * trackHeight / contentHeight);
        int thumbY = viewportTop + Math.round(
                (trackHeight - thumbHeight) * (scroll / (float) maxScroll));
        graphics.fill(WIDTH - 4, thumbY, WIDTH - 2,
                thumbY + thumbHeight, ACGTheme.GOLD_DIM);
    }

    private void layout() {
        selectionAnim = ease(selectionAnim, selectionExpanded);
        collectionAnim = ease(collectionAnim, collectionExpanded);
        miscellaneousAnim = ease(miscellaneousAnim, miscellaneousExpanded);
        int step = ROW_HEIGHT + ROW_GAP;
        int selectionY = ACGPerkSelectionScreen.TOP_BAR_HEIGHT + 10;
        int selectionBottom = selectionY + step
                + Math.round(selectionAnim * selectionItems.size() * step);
        int collectionY = selectionBottom + GROUP_GAP;
        int collectionBottom = collectionY + step
                + Math.round(collectionAnim * collectionItems.size() * step);
        int miscellaneousY = collectionBottom + GROUP_GAP;
        int miscellaneousBottom = miscellaneousY + step
                + Math.round(miscellaneousAnim * miscellaneousItems.size() * step);

        viewportTop = ACGPerkSelectionScreen.TOP_BAR_HEIGHT + 4;
        viewportBottom = screenHeight - 8;
        maxScroll = Math.max(0, miscellaneousBottom - viewportBottom);
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        placeHeader(selectionHeader, selectionY, selectionAnim);
        layoutItems(selectionItems, selectionY, step, selectionAnim);
        placeHeader(collectionHeader, collectionY, collectionAnim);
        layoutItems(collectionItems, collectionY, step, collectionAnim);
        placeHeader(miscellaneousHeader, miscellaneousY, miscellaneousAnim);
        layoutItems(miscellaneousItems, miscellaneousY, step, miscellaneousAnim);
    }

    private void placeHeader(ACGSectionHeader button, int naturalY, float animation) {
        if (button == null) {
            return;
        }
        int y = naturalY - scroll;
        button.setY(y);
        boolean fits = y >= viewportTop && y + ROW_HEIGHT <= viewportBottom;
        button.visible = fits;
        button.active = fits;
        button.chevronProgress(animation);
    }

    private void layoutItems(List<NavButton> items, int headerY,
                             int step, float animation) {
        for (int i = 0; i < items.size(); i++) {
            NavButton item = items.get(i);
            int y = headerY + step + Math.round(animation * i * step) - scroll;
            item.setY(y);
            item.renderAlpha = animation;
            boolean fits = y >= viewportTop && y + ROW_HEIGHT <= viewportBottom;
            boolean shown = animation > 0.02F && fits;
            item.visible = shown;
            item.active = shown && navigationEnabled.getAsBoolean();
        }
    }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= WIDTH || mouseY <= ACGPerkSelectionScreen.TOP_BAR_HEIGHT
                || Math.abs(delta) <= 1.0E-9D) {
            return false;
        }
        scroll += Math.round((delta < 0.0D ? 1 : -1) * ROW_HEIGHT);
        return true;
    }

    void onModeChanged() {
        indicatorAnimStartMs = System.currentTimeMillis();
    }

    private void toggleSelection() {
        selectionExpanded = !selectionExpanded;
        persistExpanded();
    }

    private void toggleCollection() {
        collectionExpanded = !collectionExpanded;
        persistExpanded();
    }

    private void toggleMiscellaneous() {
        miscellaneousExpanded = !miscellaneousExpanded;
        persistExpanded();
    }

    private void persistExpanded() {
        ClientSettings settings = ClientSettings.get();
        if (!settings.rememberCollapsedTabs) {
            return;
        }
        settings.selectionExpanded = selectionExpanded;
        settings.collectionExpanded = collectionExpanded;
        settings.miscellaneousExpanded = miscellaneousExpanded;
        settings.save();
    }

    private static float ease(float current, boolean expanded) {
        float target = expanded ? 1.0F : 0.0F;
        float next = current + (target - current) * GROUP_EASING;
        return Math.abs(next - target) < 0.001F ? target : next;
    }

    private static int fadeColor(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF)
                * Math.max(0.0F, Math.min(1.0F, factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private final class NavButton extends AbstractButton {
        private final Destination target;
        private float renderAlpha = 1.0F;

        private NavButton(int x, int y, int width, int height,
                          Component label, Destination target) {
            super(x, y, width, height, label);
            this.target = target;
        }

        @Override
        public void onPress() {
            if (navigationEnabled.getAsBoolean()) {
                navigator.accept(target);
            }
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX,
                                    int mouseY, float partialTick) {
            boolean current = target == currentDestination.get();
            boolean hovered = isHoveredOrFocused();
            if (ACGTheme.hasAtlas()) {
                ACGTheme.Slice art = current || hovered
                        ? ACGTheme.TAB_BUTTON_ACTIVE : ACGTheme.TAB_BUTTON_NORMAL;
                blitScaledRegion(graphics, ACGTheme.ATLAS,
                        getX(), getY(), width, height,
                        art.u(), art.v(), art.w(), art.h(),
                        ACGTheme.ATLAS_WIDTH, ACGTheme.ATLAS_HEIGHT);
                if (current) {
                    graphics.fill(getX(), getY(), getX() + 2, getY() + height,
                            fadeColor(ACGTheme.GOLD, renderAlpha));
                }
            } else if (current) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height,
                        fadeColor(ACGTheme.DRAWER_ACTIVE_FILL, renderAlpha));
                graphics.fill(getX(), getY(), getX() + 2, getY() + height,
                        fadeColor(ACGTheme.GOLD, renderAlpha));
            } else if (hovered) {
                graphics.fill(getX(), getY(), getX() + width, getY() + height,
                        fadeColor(ACGTheme.DRAWER_HOVER_FILL, renderAlpha));
            }
            int textX = getX() + (current ? 20 : 12);
            int color = !navigationEnabled.getAsBoolean() ? ACGTheme.TEXT_MUTED
                    : current ? ACGTheme.TEXT_PRIMARY
                    : hovered ? ACGTheme.TEXT_SECONDARY : ACGTheme.TEXT_MUTED;
            var font = Minecraft.getInstance().font;
            graphics.drawString(font, getMessage(), textX,
                    getY() + (height - 8) / 2, fadeColor(color, renderAlpha), false);
            if (current) {
                int box = 14;
                ACGTheme.drawActiveIndicator(graphics, font, getX() + 5,
                        getY() + (height - box) / 2, box,
                        System.currentTimeMillis() - indicatorAnimStartMs);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            defaultButtonNarrationText(narration);
        }
    }
}
