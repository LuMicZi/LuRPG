package com.lumiczi.lurpg.armor;

import com.lumiczi.lurpg.stat.StatMap;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a bonus granted by wearing a specific number of pieces
 * from an armor set.
 * <p>
 * A set bonus consists of:
 * <ul>
 *   <li>A piece count threshold (e.g. 2 pieces, 4 pieces)</li>
 *   <li>A set of stat bonuses (added to the player's stats when active)</li>
 *   <li>An optional description for display purposes</li>
 *   <li>An optional special effect (e.g. damage reflection, mana regen)</li>
 * </ul>
 * </p>
 */
public class SetBonus {

    private final int pieceCount;
    private final StatMap stats;
    private final String description;
    private final SpecialEffect special;

    public SetBonus(int pieceCount, StatMap stats, String description, SpecialEffect special) {
        this.pieceCount = pieceCount;
        this.stats = stats != null ? stats : new StatMap();
        this.description = description != null ? description : "";
        this.special = special;
    }

    public int getPieceCount() {
        return pieceCount;
    }

    public StatMap getStats() {
        return stats;
    }

    public String getDescription() {
        return description;
    }

    public SpecialEffect getSpecial() {
        return special;
    }

    public boolean hasSpecial() {
        return special != null;
    }

    /**
     * Represents a special effect granted by an armor set bonus.
     * <p>
     * Special effects include:
     * <ul>
     *   <li>{@link Type#DAMAGE_REFLECTION} - reflects a percentage of incoming damage back to the attacker</li>
     *   <li>{@link Type#MANA_REGEN} - increases resource regeneration rate</li>
     *   <li>{@link Type#SHADOW_DASH} - grants a dash ability on a cooldown</li>
     * </ul>
     * </p>
     * <p>
     * Parameters are stored as a flexible key-value map, allowing each effect
     * type to define its own parameters (e.g. "chance", "multiplier", "cooldown").
     * </p>
     */
    public static class SpecialEffect {

        /**
         * Enumerates the types of special effects available for armor sets.
         */
        public enum Type {
            /** Reflects a percentage of incoming damage back to the attacker. */
            DAMAGE_REFLECTION,
            /** Increases the player's resource (mana/rage/energy) regeneration rate. */
            MANA_REGEN,
            /** Grants a dash ability that can be used on a cooldown. */
            SHADOW_DASH
        }

        private final Type type;
        private final Map<String, Object> parameters;
        private final String description;

        public SpecialEffect(Type type, Map<String, Object> parameters, String description) {
            this.type = type;
            this.parameters = parameters != null ? parameters : Collections.emptyMap();
            this.description = description != null ? description : "";
        }

        public Type getType() {
            return type;
        }

        public Map<String, Object> getParameters() {
            return Collections.unmodifiableMap(parameters);
        }

        public String getDescription() {
            return description;
        }

        /**
         * Gets a parameter value by key.
         *
         * @param key the parameter key
         * @return the parameter value, or null if not present
         */
        public Object getParameter(String key) {
            return parameters.get(key);
        }

        /**
         * Gets a parameter value as a double.
         *
         * @param key          the parameter key
         * @param defaultValue the default value if not found
         * @return the parameter value as double
         */
        public double getParameterDouble(String key, double defaultValue) {
            Object value = parameters.get(key);
            if (value instanceof Number num) {
                return num.doubleValue();
            }
            return defaultValue;
        }

        /**
         * Gets a parameter value as an int.
         *
         * @param key          the parameter key
         * @param defaultValue the default value if not found
         * @return the parameter value as int
         */
        public int getParameterInt(String key, int defaultValue) {
            Object value = parameters.get(key);
            if (value instanceof Number num) {
                return num.intValue();
            }
            return defaultValue;
        }
    }

    @Override
    public String toString() {
        return "SetBonus{" +
                "pieceCount=" + pieceCount +
                ", stats=" + stats +
                ", description='" + description + '\'' +
                ", special=" + special +
                '}';
    }
}
