package com.whatever.aegis_ascension.client.screen.collectiontabs;

import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.TalentConstants;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getLiteralString;
import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

/** Builds the cards shown by the Owned Talents tab. */
public final class OwnedTalents {
    private OwnedTalents() {
    }

    public static List<TalentCollectionCard> cards() {
        return ClientPerkState.getOwnedPerks().stream().map(perk -> {
            int color = rarityColor(perk.tier());
            boolean enabled = ClientPerkState.isTalentEnabled(perk);
            Component status = perk.manuallyToggleable()
                    ? getTranslatableString(enabled
                    ? "screen.aegis_ascension.collection.toggle_on"
                    : "screen.aegis_ascension.collection.toggle_off")
                    : getTranslatableString(
                    "screen.aegis_ascension.collection.rank",
                    ClientPerkState.getRank(perk),
                    perk.maxRank()
            );
            boolean unlocksConstellations =
                    perk.id().equals(TalentConstants.PERK_DIVINE_SAKURA_POWER);
            Component tooltip = getLiteralString("[" + perk.tier().name() + "] ")
                    .append(perk.title())
                    .append("\n\n")
                    .append(perk.description());
            if (perk.manuallyToggleable()) {
                tooltip = tooltip.copy()
                        .append("\n\n")
                        .append(getTranslatableString(
                                "screen.aegis_ascension.collection.toggle_hint"
                        ));
            } else if (unlocksConstellations) {
                tooltip = tooltip.copy()
                        .append("\n\n")
                        .append(getTranslatableString(
                                "screen.aegis_ascension.collection.constellation_hint"
                        ));
            }
            int statusColor = perk.manuallyToggleable()
                    ? (enabled ? 0xFF72E39A : 0xFFE07A7A)
                    : color;
            return new TalentCollectionCard(
                    perk.iconTexture(),
                    28,
                    perk.title(),
                    perk.description(),
                    status,
                    tooltip,
                    color,
                    statusColor,
                    true,
                    perk.manuallyToggleable() || unlocksConstellations ? perk.id() : null,
                    null,
                    null,
                    null
            );
        }).toList();
    }

    private static int rarityColor(Perk.Tier tier) {
        return switch (tier) {
            case R -> 0xFF55C7E8;
            case SR -> 0xFFC277FF;
            case SSR -> 0xFFFFC857;
        };
    }
}
