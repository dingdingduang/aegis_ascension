package com.whatever.aegis_ascension.client.screen.collectiontabs;

import com.whatever.aegis_ascension.aegis.Aegis;
import com.whatever.aegis_ascension.aegis.AegisConstants;
import com.whatever.aegis_ascension.client.ClientPerkState;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

/** Builds the cards shown by the Owned Aegis tab. */
public final class OwnedAegis {
    private OwnedAegis() {
    }

    public static List<TalentCollectionCard> cards() {
        return Aegis.values().stream()
                .filter(ClientPerkState::ownsAegis)
                .map(aegis -> {
                    boolean enabled = ClientPerkState.isAegisEnabled(aegis);
                    Component status = aegis.manuallyToggleable()
                            ? getTranslatableString(enabled
                            ? "screen.aegis_ascension.collection.toggle_on"
                            : "screen.aegis_ascension.collection.toggle_off")
                            : getTranslatableString(
                            "screen.aegis_ascension.collection.aegis_owned"
                    );
                    Component tooltip = aegis.title().copy()
                            .append("\n\n")
                            .append(aegis.description());
                    if (aegis.manuallyToggleable()) {
                        tooltip = tooltip.copy()
                                .append("\n\n")
                                .append(getTranslatableString(
                                        "screen.aegis_ascension.collection.aegis_toggle_hint"
                                ));
                    } else if (aegis.id().equals(AegisConstants.DEVOUR)) {
                        tooltip = tooltip.copy()
                                .append("\n\n")
                                .append(getTranslatableString(
                                        "screen.aegis_ascension.collection.devour_manage_hint"
                                ));
                    }
                    return new TalentCollectionCard(
                            aegis.iconTexture(),
                            128,
                            aegis.title(),
                            aegis.description(),
                            status,
                            tooltip,
                            0xFFFFD36A,
                            aegis.manuallyToggleable()
                                    ? (enabled ? 0xFF72E39A : 0xFFE07A7A)
                                    : 0xFFFFD36A,
                            true,
                            null,
                            aegis.manuallyToggleable()
                                    || aegis.id().equals(AegisConstants.DEVOUR)
                                    ? aegis.id() : null,
                            null,
                            null
                    );
                })
                .toList();
    }
}
