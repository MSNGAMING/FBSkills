package com.fbskills.model;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates progression rewards: commands, money, and attribute modifiers.
 */
public class SkillReward {

    private final int level;
    private final String description;
    private final List<String> commands;
    private final String attributeName;
    private final double attributeValue;
    private final double money;

    public SkillReward(int level, String description, List<String> commands, String attributeName, double attributeValue, double money) {
        this.level = level;
        this.description = description != null ? description : "";
        this.commands = commands != null ? List.copyOf(commands) : Collections.emptyList();
        this.attributeName = attributeName;
        this.attributeValue = attributeValue;
        this.money = Math.max(0.0, money);
    }

    public SkillReward(int level, String description, List<String> commands, String attributeName, double attributeValue) {
        this(level, description, commands, attributeName, attributeValue, 0.0);
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getCommands() {
        return commands;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public double getAttributeValue() {
        return attributeValue;
    }

    public double getMoney() {
        return money;
    }

    public boolean hasAttributeModifier() {
        return attributeName != null && !attributeName.isBlank() && attributeValue != 0.0;
    }

    public boolean hasMoneyReward() {
        return money > 0.0;
    }
}
