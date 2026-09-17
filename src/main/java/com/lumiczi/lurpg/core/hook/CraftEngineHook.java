package com.lumiczi.lurpg.core.hook;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.plugin.Plugin;

/**
 * Hook for the CraftEngine plugin.
 * <p>
 * CraftEngine is a required dependency (declared in paper-plugin.yml).
 * This hook stores a reference to the CraftEngine plugin instance for
 * future item integration features.
 */
public class CraftEngineHook {

    private final LuRPGPlugin plugin;
    private Plugin craftEngine;
    private boolean available;

    public CraftEngineHook(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects CraftEngine and stores the plugin reference.
     */
    public void initialize() {
        Plugin p = plugin.getServer().getPluginManager().getPlugin("CraftEngine");
        if (p != null) {
            this.craftEngine = p;
            this.available = true;
            plugin.getLogger().info("CraftEngine hooked (v" + p.getPluginMeta().getVersion() + ")");
        } else {
            this.available = false;
            plugin.getLogger().warning("CraftEngine not found! RPG item integration will be limited.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the CraftEngine plugin instance.
     *
     * @return the Plugin, or null if not available
     */
    public Plugin getCraftEngine() {
        return craftEngine;
    }
}
