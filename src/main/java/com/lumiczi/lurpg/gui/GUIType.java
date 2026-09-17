package com.lumiczi.lurpg.gui;

/**
 * Enumerates all GUI types available in the LuRPG GUI system.
 * <p>
 * Each type corresponds to a specific GUI page class that handles
 * rendering and click events. The {@link GUIManager} uses this enum
 * to delegate click events to the appropriate handler.
 * </p>
 */
public enum GUIType {
    /** Main menu with navigation to all sub-GUIs. */
    MAIN_MENU,
    /** Paginated list of all weapons. */
    WEAPON_LIST,
    /** Paginated list of all armor sets. */
    ARMOR_SET_LIST,
    /** Paginated list of all potions. */
    POTION_LIST,
    /** Player's learned and equipped skills. */
    SKILL_LIST,
    /** Skill learning interface. */
    SKILL_LEARN,
    /** Player character panel with stats and equipment. */
    PLAYER_PANEL,
    /** Class selection interface for new players. */
    CLASS_SELECT,
    /** Detailed view of a single RPG item. */
    ITEM_DETAIL
}
