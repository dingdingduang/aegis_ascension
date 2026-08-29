package com.whatever.aegis_ascension.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/** One composable content page hosted by {@link ACGPerkSelectionScreen}. */
interface ACGPage {
    void init(ACGScreenContext context);

    void render(ACGScreenContext context, GuiGraphics graphics,
                int mouseX, int mouseY, float partialTick);

    default void tick(ACGScreenContext context) {
    }

    default boolean keyPressed(ACGScreenContext context, int keyCode,
                               int scanCode, int modifiers) {
        return false;
    }

    default boolean mouseClicked(ACGScreenContext context, double mouseX,
                                 double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(ACGScreenContext context, double mouseX,
                                 double mouseY, int button,
                                 double dragX, double dragY) {
        return false;
    }

    default boolean mouseReleased(ACGScreenContext context, double mouseX,
                                  double mouseY, int button) {
        return false;
    }

    default boolean mouseScrolled(ACGScreenContext context, double mouseX,
                                  double mouseY, double delta) {
        return false;
    }

    default void onServerSync(ACGScreenContext context) {
    }

    default void onDeactivated(ACGScreenContext context) {
    }
}
