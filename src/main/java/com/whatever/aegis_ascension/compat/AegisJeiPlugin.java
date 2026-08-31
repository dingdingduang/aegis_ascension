package com.whatever.aegis_ascension.compat;

import com.whatever.aegis_ascension.AegisAscensionMod;
import com.whatever.aegis_ascension.client.screen.ACGInventoryScreen;
import com.whatever.aegis_ascension.util.GeneralClientMethods;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * Keeps JEI's ingredient list out of the custom ACG inventory screen.
 *
 * <p>This class is only loaded by JEI when JEI is installed. The rest of the
 * mod never references JEI classes, so JEI remains an optional client-side
 * integration.</p>
 */
@JeiPlugin
public final class AegisJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = GeneralClientMethods.fromNamespaceAndPath(AegisAscensionMod.MOD_ID, "jei");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(ACGInventoryScreen.class,
                screen -> fullViewportProperties());
    }

    private static IGuiProperties fullViewportProperties() {
        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        return new FullViewportProperties(screenWidth, screenHeight);
    }

    /**
     * Reports that the ACG inventory owns the complete viewport. JEI therefore
     * finds no room for its ingredient list while this screen is open, while
     * the normal JEI overlay remains available everywhere else.
     */
    private record FullViewportProperties(int width, int height) implements IGuiProperties {
        @Override
        public Class<? extends Screen> getScreenClass() {
            return ACGInventoryScreen.class;
        }

        @Override
        public int getGuiLeft() {
            return 0;
        }

        @Override
        public int getGuiTop() {
            return 0;
        }

        @Override
        public int getGuiXSize() {
            return width;
        }

        @Override
        public int getGuiYSize() {
            return height;
        }

        @Override
        public int getScreenWidth() {
            return width;
        }

        @Override
        public int getScreenHeight() {
            return height;
        }
    }
}
