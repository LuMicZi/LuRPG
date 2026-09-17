package com.lumiczi.lurpg.stat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * A container for stat values, mapping StatType to double values.
 * Uses an EnumMap internally for efficient storage and access.
 * This class is mutable and supports cloning.
 */
public class StatMap implements Cloneable {

    private final EnumMap<StatType, Double> stats;

    public StatMap() {
        this.stats = new EnumMap<>(StatType.class);
    }

    /**
     * Sets the value for a specific stat type, overwriting any existing value.
     *
     * @param type  the stat type
     * @param value the value to set
     */
    public void set(StatType type, double value) {
        stats.put(type, value);
    }

    /**
     * Adds a value to the existing value for a stat type.
     * If the stat type is not present, it is initialized to 0 before adding.
     *
     * @param type  the stat type
     * @param value the value to add
     */
    public void add(StatType type, double value) {
        stats.merge(type, value, Double::sum);
    }

    /**
     * Gets the value for a stat type, defaulting to 0 if not present.
     *
     * @param type the stat type
     * @return the value, or 0 if not set
     */
    public double get(StatType type) {
        return stats.getOrDefault(type, 0.0);
    }

    /**
     * Gets the value for a stat type, returning the specified default if not present.
     *
     * @param type         the stat type
     * @param defaultValue the default value to return if not set
     * @return the value, or the default if not set
     */
    public double getOrDefault(StatType type, double defaultValue) {
        return stats.getOrDefault(type, defaultValue);
    }

    /**
     * Adds all values from another StatMap into this one.
     * Values for matching stat types are summed.
     *
     * @param other the StatMap to add from
     */
    public void addAll(StatMap other) {
        if (other == null) {
            return;
        }
        other.stats.forEach((type, value) -> stats.merge(type, value, Double::sum));
    }

    /**
     * Multiplies all stat values in this map by the given multiplier.
     *
     * @param multiplier the multiplier to apply
     */
    public void multiplyAll(double multiplier) {
        if (multiplier == 1.0 || stats.isEmpty()) {
            return;
        }
        stats.replaceAll((type, value) -> value * multiplier);
    }

    /**
     * Removes a stat type from this map.
     *
     * @param type the stat type to remove
     */
    public void remove(StatType type) {
        stats.remove(type);
    }

    /**
     * Checks if this StatMap contains a value for the given stat type.
     *
     * @param type the stat type to check
     * @return true if a value is present
     */
    public boolean contains(StatType type) {
        return stats.containsKey(type);
    }

    /**
     * Checks if this StatMap has no entries.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return stats.isEmpty();
    }

    /**
     * Returns the number of stat entries in this map.
     *
     * @return the entry count
     */
    public int size() {
        return stats.size();
    }

    /**
     * Returns a set view of the stat entries in this map.
     *
     * @return an unmodifiable set of entries
     */
    public Set<Map.Entry<StatType, Double>> entrySet() {
        return stats.entrySet();
    }

    /**
     * Clears all stat entries.
     */
    public void clear() {
        stats.clear();
    }

    /**
     * Creates a deep copy of this StatMap.
     *
     * @return a new StatMap with the same values
     */
    @Override
    public StatMap clone() {
        StatMap clone = new StatMap();
        clone.stats.putAll(this.stats);
        return clone;
    }

    @Override
    public String toString() {
        return stats.toString();
    }
}
