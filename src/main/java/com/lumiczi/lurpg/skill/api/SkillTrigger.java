package com.lumiczi.lurpg.skill.api;

/**
 * Represents the conditions under which a skill is triggered.
 * <p>
 * Active skills are triggered by player input (right-click, sneak+click, etc.).
 * Passive skills are triggered automatically by game events (attack, damage, kill).
 * Custom triggers can be used by external plugins or custom logic.
 * </p>
 */
public enum SkillTrigger {

    ACTIVE("主动"),
    ON_ATTACK("攻击命中"),
    ON_DAMAGED("受击"),
    ON_RIGHT_CLICK("右键点击"),
    ON_KILL("击杀"),
    CUSTOM("自定义");

    private final String displayName;

    SkillTrigger(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a string into a SkillTrigger.
     *
     * @param name the string to parse
     * @return the matching SkillTrigger, or {@link #ACTIVE} if no match found
     */
    public static SkillTrigger fromString(String name) {
        if (name == null || name.isBlank()) {
            return ACTIVE;
        }
        for (SkillTrigger trigger : values()) {
            if (trigger.name().equalsIgnoreCase(name)) {
                return trigger;
            }
        }
        return ACTIVE;
    }

    /**
     * Checks whether this trigger represents a passive (event-driven) skill.
     *
     * @return true if the skill triggers automatically on events
     */
    public boolean isPassive() {
        return switch (this) {
            case ON_ATTACK, ON_DAMAGED, ON_KILL -> true;
            default -> false;
        };
    }
}
