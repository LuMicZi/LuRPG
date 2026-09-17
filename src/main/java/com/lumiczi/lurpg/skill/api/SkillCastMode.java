package com.lumiczi.lurpg.skill.api;

/**
 * Represents the input method required to cast a skill.
 * <p>
 * Active skills use one of the click-based modes (RIGHT_CLICK, LEFT_CLICK,
 * or their sneak variants). Passive skills use {@link #PASSIVE}.
 * Command-castable skills use {@link #COMMAND}.
 * </p>
 */
public enum SkillCastMode {

    RIGHT_CLICK,
    SNEAK_RIGHT_CLICK,
    LEFT_CLICK,
    SNEAK_LEFT_CLICK,
    PASSIVE,
    COMMAND;

    /**
     * Parses a string into a SkillCastMode.
     *
     * @param name the string to parse
     * @return the matching SkillCastMode, or {@link #RIGHT_CLICK} if no match found
     */
    public static SkillCastMode fromString(String name) {
        if (name == null || name.isBlank()) {
            return RIGHT_CLICK;
        }
        for (SkillCastMode mode : values()) {
            if (mode.name().equalsIgnoreCase(name)) {
                return mode;
            }
        }
        return RIGHT_CLICK;
    }

    /**
     * Checks whether this cast mode requires sneaking.
     *
     * @return true if the player must be sneaking to cast
     */
    public boolean requiresSneak() {
        return this == SNEAK_RIGHT_CLICK || this == SNEAK_LEFT_CLICK;
    }

    /**
     * Checks whether this cast mode uses a right-click action.
     *
     * @return true if the cast uses right-click
     */
    public boolean isRightClick() {
        return this == RIGHT_CLICK || this == SNEAK_RIGHT_CLICK;
    }

    /**
     * Checks whether this cast mode uses a left-click action.
     *
     * @return true if the cast uses left-click
     */
    public boolean isLeftClick() {
        return this == LEFT_CLICK || this == SNEAK_LEFT_CLICK;
    }

    /**
     * Checks whether this is a passive (automatic) cast mode.
     *
     * @return true if passive
     */
    public boolean isPassive() {
        return this == PASSIVE;
    }
}
