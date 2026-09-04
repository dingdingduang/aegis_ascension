package com.whatever.aegis_ascension.client.screen.collectiontabs;

import com.whatever.aegis_ascension.client.SkillEnhancementClientSettings;
import com.whatever.aegis_ascension.client.ClientPerkState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;

import static com.whatever.aegis_ascension.util.GeneralTextMethods.getTranslatableString;

/** Builds primary-stat and random-offer cards for the Skill Enhancement tab. */
public final class SkillEnhancement {
    private SkillEnhancement() {
    }

    public static List<TalentCollectionCard> cards(
            boolean choosingPrimaryEnhancement,
            boolean awaitingSelection,
            boolean awaitingRefresh) {
        if (choosingPrimaryEnhancement) {
            return primaryCards();
        }
        return offerCards(awaitingSelection, awaitingRefresh);
    }

    private static List<TalentCollectionCard> primaryCards() {
        return com.whatever.aegis_ascension.perk.SkillEnhancement.values().stream()
                .map(enhancement -> {
                    boolean selected = ClientPerkState.hasChosenPrimarySkillEnhancement()
                            && enhancement.equals(
                            ClientPerkState.getPrimarySkillEnhancement()
                    );
                    Component status = getTranslatableString(selected
                            ? "screen.aegis_ascension.collection.primary.selected"
                            : "screen.aegis_ascension.collection.primary.select");
                    Component tooltip = SkillEnhancementClientSettings.title(enhancement).copy()
                            .append("\n\n")
                            .append(SkillEnhancementClientSettings.description(enhancement))
                            .append("\n\n")
                            .append(allSkillEnhancementAttributeStatus(enhancement))
                            .append("\n\n")
                            .append(getTranslatableString(
                                    "screen.aegis_ascension.collection.primary.select_hint"
                            ));
                    return new TalentCollectionCard(
                            SkillEnhancementClientSettings.icon(enhancement),
                            SkillEnhancementClientSettings.iconSize(enhancement),
                            SkillEnhancementClientSettings.title(enhancement),
                            SkillEnhancementClientSettings.description(enhancement),
                            status,
                            tooltip,
                            selected ? 0xFFFFD36A : 0xFF72E39A,
                            selected ? 0xFFFFD36A : 0xFF72E39A,
                            true,
                            null,
                            null,
                            enhancement.id(),
                            null
                    );
                }).toList();
    }

    private static List<TalentCollectionCard> offerCards(
            boolean awaitingSelection,
            boolean awaitingRefresh) {
        return ClientPerkState.getSkillEnhancementOffers().stream()
                .map(enhancement -> {
                    int currentRank = ClientPerkState.getSkillEnhancementRank(enhancement);
                    Component tooltip = SkillEnhancementClientSettings.title(enhancement).copy()
                            .append("\n\n")
                            .append(SkillEnhancementClientSettings.description(enhancement))
                            .append("\n\n")
                            .append(allSkillEnhancementAttributeStatus(enhancement))
                            .append("\n\n")
                            .append(getTranslatableString(
                                    "screen.aegis_ascension.collection.skill_enhancement.current_rank",
                                    currentRank
                            ))
                            .append("\n")
                            .append(getTranslatableString(
                                    "screen.aegis_ascension.collection.skill_enhancement.select_hint"
                            ));
                    return new TalentCollectionCard(
                            SkillEnhancementClientSettings.icon(enhancement),
                            SkillEnhancementClientSettings.iconSize(enhancement),
                            SkillEnhancementClientSettings.title(enhancement),
                            SkillEnhancementClientSettings.description(enhancement),
                            getTranslatableString(
                                    "screen.aegis_ascension.collection.skill_enhancement.next_rank",
                                    currentRank + 1
                            ),
                            tooltip,
                            0xFF72E39A,
                            0xFFE08A,
                            ClientPerkState.getSkillEnhancementCharges() > 0
                                    && !awaitingSelection
                                    && !awaitingRefresh,
                            null,
                            null,
                            enhancement.id(),
                            null
                    );
                }).toList();
    }

    private static Component allSkillEnhancementAttributeStatus(
            com.whatever.aegis_ascension.perk.SkillEnhancement enhancement) {
        boolean affected = enhancement.affectedByAllSkillEnhancementAttribute();
        return getTranslatableString(affected
                ? "screen.aegis_ascension.collection.skill_enhancement.all_attribute_affected"
                : "screen.aegis_ascension.collection.skill_enhancement.all_attribute_unaffected"
        ).withStyle(affected ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }
}
