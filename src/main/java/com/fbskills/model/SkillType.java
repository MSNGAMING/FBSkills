package com.fbskills.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Enumeration representing the 11 core FBSkills progression paths.
 */
public enum SkillType {
    FARMING("farming", "Farming"),
    FORGING("forging", "Forging"),
    MINING("mining", "Mining"),
    FISHING("fishing", "Fishing"),
    EXCAVATION("excavation", "Excavation"),
    ARCHERY("archery", "Archery"),
    DEFENSE("defense", "Defense"),
    FIGHTING("fighting", "Fighting"),
    AGILITY("agility", "Agility"),
    ENCHANTING("enchanting", "Enchanting"),
    ALCHEMY("alchemy", "Alchemy");

    private final String id;
    private final String defaultDisplayName;

    SkillType(String id, String defaultDisplayName) {
        this.id = id;
        this.defaultDisplayName = defaultDisplayName;
    }

    public String getId() {
        return id;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public static Optional<SkillType> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String clean = id.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.id.equals(clean) || type.name().toLowerCase(Locale.ROOT).equals(clean))
                .findFirst();
    }
}
