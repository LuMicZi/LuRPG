package com.lumiczi.lurpg.stat;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.ClassAttribute;
import com.lumiczi.lurpg.class_.GameClass;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the RPG stat system.
 * <p>
 * Loads stat configuration from {@code config.yml}'s {@code stats} section,
 * including combat settings (critical hit multiplier, lifesteal cap) and
 * elemental damage configurations.
 * </p>
 * <p>
 * The {@link #calculateBaseStats(GameClass, int)} method computes the base
 * stat values for a given class and level by delegating to ClassManager.
 * </p>
 */
public class StatManager {

    private final LuRPGPlugin plugin;
    private double critBaseMultiplier = 1.5;
    private double lifestealCap = 50.0;
    private final Map<String, Map<String, Object>> elementConfigs = new HashMap<>();

    public StatManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads stat configuration from config.yml.
     */
    public void load() {
        FileConfiguration config = plugin.getConfigManager().getConfig("config");

        // Load combat settings
        critBaseMultiplier = config.getDouble("stats.combat.crit-base-multiplier", 1.5);
        lifestealCap = config.getDouble("stats.combat.lifesteal-cap", 50.0);

        // Load element configurations
        elementConfigs.clear();
        ConfigurationSection elementsSection = config.getConfigurationSection("stats.combat.elements");
        if (elementsSection != null) {
            for (String element : elementsSection.getKeys(false)) {
                ConfigurationSection elemSection = elementsSection.getConfigurationSection(element);
                if (elemSection != null) {
                    Map<String, Object> elemConfig = new HashMap<>();
                    for (String key : elemSection.getKeys(true)) {
                        elemConfig.put(key, elemSection.get(key));
                    }
                    elementConfigs.put(element.toLowerCase(), elemConfig);
                }
            }
        }

        plugin.getLogger().info("Loaded stat configuration: critMultiplier=" + critBaseMultiplier
                + ", lifestealCap=" + lifestealCap
                + ", elements=" + elementConfigs.size());
    }

    /**
     * Calculates the base stats for a given class and level.
     * <p>
     * This retrieves the ClassAttribute from ClassManager, computes the
     * effective stats at the given level, and maps them to a StatMap.
     * </p>
     *
     * @param gameClass the game class
     * @param level     the player level
     * @return a StatMap containing the base stats, or an empty StatMap if the class is null or not found
     */
    public StatMap calculateBaseStats(GameClass gameClass, int level) {
        StatMap statMap = new StatMap();
        if (gameClass == null) {
            return statMap;
        }

        ClassAttribute attr = plugin.getClassManager().getClass(gameClass);
        if (attr == null) {
            return statMap;
        }

        ClassAttribute leveled = attr.getStatAtLevel(level);
        statMap.set(StatType.MAX_HEALTH, leveled.getMaxHealth());
        statMap.set(StatType.PHYSICAL_ATTACK, leveled.getPhysicalAttack());
        statMap.set(StatType.MAGICAL_ATTACK, leveled.getMagicalAttack());
        statMap.set(StatType.PHYSICAL_DEFENSE, leveled.getPhysicalDefense());
        statMap.set(StatType.MAGICAL_DEFENSE, leveled.getMagicalDefense());
        statMap.set(StatType.RESOURCE_MAX, leveled.getResourceMax());

        return statMap;
    }

    /**
     * Returns the critical hit base multiplier from config.
     *
     * @return the crit base multiplier (e.g. 1.5 means 150% damage)
     */
    public double getCritBaseMultiplier() {
        return critBaseMultiplier;
    }

    /**
     * Returns the lifesteal cap from config.
     *
     * @return the maximum lifesteal percentage
     */
    public double getLifestealCap() {
        return lifestealCap;
    }

    /**
     * Gets the element configuration for the specified element.
     *
     * @param element the element name (e.g. "fire", "ice", "thunder", "dark", "light")
     * @return an unmodifiable map of element config key-value pairs, or null if not found
     */
    public Map<String, Object> getElementConfig(String element) {
        if (element == null) {
            return null;
        }
        Map<String, Object> config = elementConfigs.get(element.toLowerCase());
        if (config == null) {
            return null;
        }
        return Collections.unmodifiableMap(config);
    }

    /**
     * Gets a specific value from an element's configuration.
     *
     * @param element the element name
     * @param key     the config key
     * @return the config value, or null if not found
     */
    public Object getElementConfigValue(String element, String key) {
        Map<String, Object> config = getElementConfig(element);
        if (config == null) {
            return null;
        }
        return config.get(key);
    }

    /**
     * Gets a double value from an element's configuration.
     *
     * @param element      the element name
     * @param key          the config key
     * @param defaultValue the default value if not found
     * @return the config value as double, or the default
     */
    public double getElementConfigDouble(String element, String key, double defaultValue) {
        Object value = getElementConfigValue(element, key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return defaultValue;
    }

    /**
     * Gets an int value from an element's configuration.
     *
     * @param element      the element name
     * @param key          the config key
     * @param defaultValue the default value if not found
     * @return the config value as int, or the default
     */
    public int getElementConfigInt(String element, String key, int defaultValue) {
        Object value = getElementConfigValue(element, key);
        if (value instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }
}
