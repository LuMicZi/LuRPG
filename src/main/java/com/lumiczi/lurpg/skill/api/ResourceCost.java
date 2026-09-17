package com.lumiczi.lurpg.skill.api;

/**
 * Immutable data class representing the resource cost of a skill.
 * <p>
 * Contains the {@link ResourceType} and the amount required to cast the skill.
 * A cost with type {@link ResourceType#NONE} and amount 0 means the skill is free.
 * </p>
 *
 * @param type   the resource type consumed
 * @param amount the amount of resource consumed
 */
public record ResourceCost(ResourceType type, double amount) {

    /**
     * Creates a "free" cost (no resource required).
     *
     * @return a ResourceCost with type NONE and amount 0
     */
    public static ResourceCost free() {
        return new ResourceCost(ResourceType.NONE, 0);
    }

    /**
     * Checks whether this cost requires any resource.
     *
     * @return true if the skill consumes a resource
     */
    public boolean hasCost() {
        return type != ResourceType.NONE && amount > 0;
    }

    /**
     * Parses a ResourceCost from a configuration section.
     * Expected YAML structure:
     * <pre>
     * cost:
     *   type: MANA
     *   amount: 20
     * </pre>
     *
     * @param section the configuration section, or null
     * @return the parsed ResourceCost, or {@link #free()} if section is null
     */
    public static ResourceCost fromConfig(org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            return free();
        }
        ResourceType type = ResourceType.fromString(section.getString("type", "NONE"));
        double amount = section.getDouble("amount", 0);
        return new ResourceCost(type, amount);
    }
}
