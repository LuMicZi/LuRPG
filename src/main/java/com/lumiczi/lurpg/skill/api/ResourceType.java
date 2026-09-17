package com.lumiczi.lurpg.skill.api;

/**
 * Represents the types of resources that skills can consume.
 * Each resource type has a display name (Chinese) and a MiniMessage
 * color prefix used for rendering in chat and GUIs.
 */
public enum ResourceType {

    MANA("法力", "<blue>"),
    RAGE("怒气", "<red>"),
    ENERGY("能量", "<green>"),
    NONE("", "");

    private final String displayName;
    private final String colorPrefix;

    ResourceType(String displayName, String colorPrefix) {
        this.displayName = displayName;
        this.colorPrefix = colorPrefix;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorPrefix() {
        return colorPrefix;
    }

    /**
     * Parses a string into a ResourceType.
     * Accepts both enum name (e.g. "MANA") and display name (e.g. "法力").
     *
     * @param name the string to parse
     * @return the matching ResourceType, or {@link #NONE} if no match found
     */
    public static ResourceType fromString(String name) {
        if (name == null || name.isBlank()) {
            return NONE;
        }
        for (ResourceType type : values()) {
            if (type.name().equalsIgnoreCase(name) || type.displayName.equals(name)) {
                return type;
            }
        }
        return NONE;
    }
}
