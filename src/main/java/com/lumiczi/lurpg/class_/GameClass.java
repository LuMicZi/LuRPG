package com.lumiczi.lurpg.class_;

import org.bukkit.Material;

/**
 * Represents the available RPG classes in LuRPG.
 * Each class has a display name, color prefix (MiniMessage format),
 * a resource name (e.g. rage, mana, energy), and an icon material.
 */
public enum GameClass {

    WARRIOR("战士", "<red>", "怒气", Material.IRON_SWORD),
    MAGE("法师", "<blue>", "法力", Material.BLAZE_ROD),
    ASSASSIN("刺客", "<dark_purple>", "能量", Material.SHEARS);

    private final String displayName;
    private final String colorPrefix;
    private final String resourceName;
    private final Material iconMaterial;

    GameClass(String displayName, String colorPrefix, String resourceName, Material iconMaterial) {
        this.displayName = displayName;
        this.colorPrefix = colorPrefix;
        this.resourceName = resourceName;
        this.iconMaterial = iconMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorPrefix() {
        return colorPrefix;
    }

    public String getResourceName() {
        return resourceName;
    }

    public Material getIcon() {
        return iconMaterial;
    }

    /**
     * Parses a string into a GameClass.
     * Accepts both enum name (e.g. "WARRIOR") and display name (e.g. "战士").
     *
     * @param name the string to parse
     * @return the matching GameClass, or null if no match found
     */
    public static GameClass fromString(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (GameClass gc : values()) {
            if (gc.name().equalsIgnoreCase(name) || gc.displayName.equals(name)) {
                return gc;
            }
        }
        return null;
    }
}
