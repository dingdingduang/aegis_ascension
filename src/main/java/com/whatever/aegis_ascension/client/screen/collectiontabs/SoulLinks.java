package com.whatever.aegis_ascension.client.screen.collectiontabs;

import com.whatever.aegis_ascension.client.ClientPerkState;
import com.whatever.aegis_ascension.client.ClientSettings;
import com.whatever.aegis_ascension.perk.Perk;
import com.whatever.aegis_ascension.perk.SoulLink;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;
import static com.whatever.aegis_ascension.perk.soullink.SoulLinkConstants.*;

/** Builds active, locked, and disabled cards for the Soul Links tab. */
public final class SoulLinks {
    private SoulLinks() {
    }

    public static List<TalentCollectionCard> cards() {
        boolean showUnformed = ClientSettings.get().showUnformedSoulLinks;
        List<TalentCollectionCard> cards = new ArrayList<>();
        for (SoulLink soulLink : Perk.soulLinks()) {
            boolean active = ClientPerkState.isSoulLinkActive(soulLink);
            boolean disabled = ClientPerkState.isSoulLinkDisabled(soulLink);
            // Neither active nor disabled means the required talents are not all
            // owned: the link is not formed, and is hidden unless asked for.
            if (!active && !disabled && !showUnformed) {
                continue;
            }
            Component status;
            int color;
            if (disabled) {
                status = getTranslatableString(
                        "screen.aegis_ascension.collection.disabled"
                );
                color = 0xFF888888;
            } else if (active) {
                int rank = soulLink.id().equals(SOUL_TEAM_RADIANCE)
                        ? Math.max(1, (int) Math.floor(ClientPerkState.getDisplayStat(
                        TEAM_RADIANCE_RANK
                ))) : soulLink.rank(ClientPerkState::owns);
                int maximumRank = soulLink.id().equals(SOUL_TEAM_RADIANCE)
                        ? Math.max(1, (int) Math.round(soulLink.bonusStat(
                        TEAM_RADIANCE_RANK_CAP
                ))) : soulLink.maxRank();
                status = maximumRank > 1
                        ? getTranslatableString(
                        "screen.aegis_ascension.collection.active_rank",
                        rank,
                        maximumRank
                )
                        : getTranslatableString(
                        "screen.aegis_ascension.collection.active"
                );
                color = 0xFF72E39A;
            } else {
                status = getTranslatableString(
                        "screen.aegis_ascension.collection.locked"
                );
                color = 0xFFE07A7A;
            }

            String requirements = soulLink.requirements().stream()
                    .map(requirement -> Perk.byId(requirement)
                            .map(perk -> (ClientPerkState.owns(requirement) ? "✓ " : "✗ ")
                                    + perk.title().getString())
                            .orElse(requirement))
                    .collect(Collectors.joining(", "));
            if (requirements.isBlank()) {
                requirements = getTranslatableString(
                        "screen.aegis_ascension.collection.unspecified"
                ).getString();
            }
            Component tooltip = soulLink.title().copy()
                    .append("\n\n")
                    .append(soulLink.description())
                    .append("\n\n")
                    .append(getTranslatableString(
                            "screen.aegis_ascension.collection.requires",
                            requirements
                    ));
            if (!soulLink.rankPerks().isEmpty()) {
                String rankPerks = soulLink.rankPerks().stream()
                        .map(perkId -> Perk.byId(perkId)
                                .map(perk -> (ClientPerkState.owns(perkId) ? "✓ " : "✗ ")
                                        + perk.title().getString())
                                .orElse(perkId))
                        .collect(Collectors.joining(", "));
                tooltip = tooltip.copy()
                        .append("\n")
                        .append(getTranslatableString(
                                "screen.aegis_ascension.collection.rank_perks",
                                rankPerks
                        ));
            }
            cards.add(new TalentCollectionCard(
                    soulLink.iconTexture(),
                    28,
                    soulLink.title(),
                    soulLink.description(),
                    status,
                    tooltip,
                    color,
                    color,
                    active,
                    null,
                    null,
                    null,
                    null
            ));
        }
        return List.copyOf(cards);
    }
}
