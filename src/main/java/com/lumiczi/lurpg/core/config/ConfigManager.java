package com.lumiczi.lurpg.core.config;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages all YAML configuration files for LuRPG.
 * <p>
 * Responsible for loading, caching, and providing access to the various
 * configuration files used by the plugin (config.yml, classes.yml, etc.).
 */
public class ConfigManager {

    private final LuRPGPlugin plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();

    public ConfigManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) all configuration files into memory.
     */
    public void loadAll() {
        configs.clear();
        loadConfig("config.yml");
        loadConfig("classes.yml");
        loadConfig("skills.yml");
        loadConfig("items.yml");
        loadConfig("armor_sets.yml");
        loadConfig("potions.yml");
        loadConfig("item_tiers.yml");
        loadConfig("item_enhance.yml");
        loadConfig("gems.yml");
    }

    /**
     * Loads a single YAML configuration file.
     *
     * @param fileName the file name relative to the plugin data folder
     */
    private void loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            try {
                plugin.saveResource(fileName, false);
            } catch (IllegalArgumentException ignored) {
                // Resource not bundled in jar - create empty config
            }
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        // Use file name without extension as the key
        String key = fileName.replace(".yml", "");
        configs.put(key, config);
    }

    /**
     * Returns the FileConfiguration for the given config name.
     *
     * @param name the config name without extension (e.g. "config", "classes")
     * @return the FileConfiguration, or null if not loaded
     */
    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    /**
     * Returns the main configuration (config.yml).
     *
     * @return the main FileConfiguration
     */
    public FileConfiguration getMainConfig() {
        return configs.get("config");
    }

    /**
     * Reloads all configuration files. Equivalent to {@link #loadAll()}.
     */
    public void reload() {
        loadAll();
    }

    /**
     * Saves a configuration file to disk.
     * <p>
     * Writes the in-memory FileConfiguration back to its corresponding YAML file
     * in the plugin data folder. The config name is the file name without extension.
     * </p>
     *
     * @param name the config name without extension (e.g. "items", "skills")
     * @return true if saved successfully, false if the config is not loaded
     */
    public boolean saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        if (config == null) {
            return false;
        }

        File file = new File(plugin.getDataFolder(), name + ".yml");
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save config file '" + name + ".yml': " + e.getMessage(), e);
            return false;
        }
    }
}
