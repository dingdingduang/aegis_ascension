package com.whatever.aegis_ascension.quest;

import java.util.ArrayList;
import java.util.List;

/**
 * Recipe for the procedurally composed side quests, expanded into ordinary templates
 * when the catalogue loads.
 *
 * <p>Expansion happens at load rather than at roll time, and that is the whole design.
 * Completion history, reputation, once-per-player and chain unlocking all key on a
 * template id, and the client resolves every quest's title, story and icon from the
 * catalogue snapshot it received at login. A quest invented during a roll would carry an
 * id nothing had ever heard of: the screen would render blank text and reputation could
 * not attribute the completion to anyone. Expanding here means a generated quest is a
 * template like any other, and every system downstream needs no idea it was generated.</p>
 *
 * <p>Text is written once per archetype and parameterised with the villager's name rather
 * than written per quest. Five archetypes across thirteen professions is sixty-five
 * quests from five sets of strings; writing them out longhand would be sixty-five sets.
 * The trade is honest and worth stating: composed prose reads flatter than authored
 * prose, which is why these carry a lower weight than the hand-written quests and are
 * meant as the ordinary contract work between them.</p>
 */
public final class GeneratedQuests {
    /** Master switch; false leaves the catalogue exactly as it was authored. */
    public boolean enabled = true;
    /**
     * Draw weight given to every generated quest. Deliberately below the authored
     * templates' weights so hand-written quests still surface most often.
     */
    public int weight = 4;
    public List<Archetype> archetypes = new ArrayList<>();
    public List<ProfessionSubjects> professions = new ArrayList<>();

    /** One kind of job, shared across every profession that has subjects for it. */
    public static final class Archetype {
        public String id = "";
        public QuestObjective objective = QuestObjective.KILL;
        public int targetMin = 1;
        public int targetMax = 1;
        public int experience;
        public long goldRewardMin;
        public long goldRewardMax;
        public int minimumRank;
        public List<String> tiers = new ArrayList<>();
        public List<QuestConfig.RewardSpec> rewards = new ArrayList<>();
        /** How many story variants exist for this archetype in the language file. */
        public int storyVariants = 1;
    }

    /** What one villager can be asked for, per archetype. */
    public static final class ProfessionSubjects {
        public String profession = "";
        public String icon = "";
        /** Archetype id to the target ids that villager's version of it uses. */
        public java.util.Map<String, List<String>> subjects = new java.util.LinkedHashMap<>();
    }

    /**
     * Builds the templates this recipe describes. Ids are deterministic, so a player's
     * completion history survives a reload and a restart.
     */
    public List<QuestConfig.Template> expand() {
        List<QuestConfig.Template> templates = new ArrayList<>();
        if (!enabled || archetypes == null || professions == null) return templates;
        for (ProfessionSubjects profession : professions) {
            if (profession == null || profession.profession == null
                    || profession.profession.isBlank() || profession.subjects == null) {
                continue;
            }
            for (Archetype archetype : archetypes) {
                if (archetype == null || archetype.id == null || archetype.id.isBlank()) {
                    continue;
                }
                List<String> subjects = profession.subjects.get(archetype.id);
                if (subjects == null || subjects.isEmpty()) continue;
                templates.add(build(archetype, profession, subjects));
            }
        }
        return templates;
    }

    private QuestConfig.Template build(Archetype archetype, ProfessionSubjects profession,
                                       List<String> subjects) {
        QuestConfig.Template template = new QuestConfig.Template();
        template.id = "gen_" + profession.profession + "_" + archetype.id;
        template.objective = archetype.objective;
        template.targetIds = new ArrayList<>(subjects);
        template.target = Math.max(1, archetype.targetMin);
        template.targetMin = archetype.targetMin;
        template.targetMax = archetype.targetMax;
        template.weight = Math.max(0, weight);
        template.minimumRank = archetype.minimumRank;
        template.profession = profession.profession;
        template.icon = profession.icon == null ? "" : profession.icon;
        template.experience = archetype.experience;
        template.goldRewardMin = archetype.goldRewardMin;
        template.goldRewardMax = archetype.goldRewardMax;
        template.tiers = archetype.tiers == null
                ? new ArrayList<>() : new ArrayList<>(archetype.tiers);
        template.rewards = archetype.rewards == null
                ? new ArrayList<>() : new ArrayList<>(archetype.rewards);

        String base = "quest.aegis_ascension.generated." + archetype.id;
        template.title = base + ".title";
        template.description = base + ".description";
        template.story = base + ".story." + storyVariant(archetype, profession.profession);
        return template;
    }

    /**
     * Picks which story variant a profession gets, from the profession's own name so the
     * choice is stable. A random pick would hand the same villager a different account of
     * the same job on every reload.
     */
    private static int storyVariant(Archetype archetype, String profession) {
        int variants = Math.max(1, archetype.storyVariants);
        return Math.floorMod(profession.hashCode(), variants) + 1;
    }
}
