package com.whatever.aegis_ascension.client.screen;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Transfers the cursor position when Minecraft replaces one ACG screen with another.
 *
 * <p>{@link net.minecraft.client.MouseHandler#releaseMouse()} deliberately centers the
 * cursor every time {@code Minecraft#setScreen} opens a screen. The position is therefore
 * kept as GUI-scaled coordinates and converted back to physical window pixels only after
 * the destination screen has been initialized.</p>
 */
final class ACGCursorState {
    private static final long MAX_PENDING_AGE_MILLIS = 3_000L;

    private static double pendingGuiX;
    private static double pendingGuiY;
    private static long pendingSince;
    private static boolean pending;

    private ACGCursorState() {
    }

    static void remember(double guiX, double guiY) {
        if (!Double.isFinite(guiX) || !Double.isFinite(guiY)) {
            pending = false;
            return;
        }
        pendingGuiX = guiX;
        pendingGuiY = guiY;
        pendingSince = System.currentTimeMillis();
        pending = true;
    }

    /** Restores once, after the destination Screen has completed its {@code init()}. */
    static void restoreIfPending(Minecraft minecraft) {
        if (!pending) {
            return;
        }
        if (System.currentTimeMillis() - pendingSince > MAX_PENDING_AGE_MILLIS) {
            pending = false;
            return;
        }
        if (minecraft == null) {
            return;
        }
        Window window = minecraft.getWindow();
        int guiWidth = window.getGuiScaledWidth();
        int guiHeight = window.getGuiScaledHeight();
        int screenWidth = window.getScreenWidth();
        int screenHeight = window.getScreenHeight();
        if (guiWidth <= 0 || guiHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        double x = Math.max(0.0D, Math.min(guiWidth - 1.0D, pendingGuiX));
        double y = Math.max(0.0D, Math.min(guiHeight - 1.0D, pendingGuiY));
        pending = false;
        GLFW.glfwSetCursorPos(
                window.getWindow(),
                x * screenWidth / guiWidth,
                y * screenHeight / guiHeight
        );
    }
}
