package com.lumiczi.lurpg.potion;

/**
 * Enumerates the types of RPG potions available in LuRPG.
 * <p>
 * Each potion type determines how the potion's effects are applied:
 * <ul>
 *   <li>{@link #INSTANT_HEAL} - immediately restores health</li>
 *   <li>{@link #REGEN} - applies continuous health regeneration over time</li>
 *   <li>{@link #BUFF} - grants a temporary stat multiplier</li>
 *   <li>{@link #DETOX} - removes negative status effects</li>
 *   <li>{@link #RESOURCE} - restores the player's class resource (mana/rage/energy)</li>
 * </ul>
 * </p>
 */
public enum PotionType {
    /** Immediately restores a fixed amount of health. */
    INSTANT_HEAL,
    /** Applies continuous health regeneration over a duration. */
    REGEN,
    /** Grants a temporary stat multiplier (e.g. increased attack power). */
    BUFF,
    /** Removes negative status effects (poison, wither, weakness, slowness). */
    DETOX,
    /** Restores the player's class resource (mana, rage, or energy). */
    RESOURCE
}
