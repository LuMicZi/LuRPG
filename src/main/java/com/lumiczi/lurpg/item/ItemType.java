package com.lumiczi.lurpg.item;

/**
 * Represents the category of an RPG item.
 * <p>
 * Each item type determines how the item is equipped and which
 * gameplay systems interact with it.
 */
public enum ItemType {
    WEAPON("武器"),
    ARMOR("护甲"),
    POTION("药水"),
    ACCESSORY("饰品");

    private final String displayName;

    ItemType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the Chinese display name for this item type.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a string into an ItemType.
     * Accepts both enum name (e.g. "WEAPON") and case-insensitive variants.
     *
     * @param name the string to parse
     * @return the matching ItemType, or {@code null} if no match is found
     */
    public static ItemType fromString(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (ItemType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
