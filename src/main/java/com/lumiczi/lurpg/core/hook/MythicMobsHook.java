package com.lumiczi.lurpg.core.hook;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.plugin.Plugin;

/**
 * Hook for the MythicMobs plugin.
 * <p>
 * Detects MythicMobs and stores a reference for future mob integration
 * features (custom XP drops, etc.).
 */
public class MythicMobsHook {

    private final LuRPGPlugin plugin;
    private Plugin mythicMobs;
    private boolean available;

    public MythicMobsHook(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects MythicMobs and stores the plugin reference.
     */
    public void initialize() {
        Plugin p = plugin.getServer().getPluginManager().getPlugin("MythicMobs");
        if (p != null) {
            this.mythicMobs = p;
            this.available = true;
            plugin.getLogger().info("MythicMobs hooked (v" + p.getPluginMeta().getVersion() + ")");
        } else {
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Returns the MythicMobs plugin instance.
     *
     * @return the Plugin, or null if not available
     */
    public Plugin getMythicMobs() {
        return mythicMobs;
    }

    /**
     * Checks if a living entity is a MythicMobs mob.
     * Uses reflection to avoid compile-time dependency on MythicMobs API.
     *
     * @param entity the entity to check
     * @return true if the entity is a MythicMobs mob
     */
    public boolean isMythicMob(org.bukkit.entity.LivingEntity entity) {
        if (!available || entity == null) return false;
        try {
            Class<?> mobClass = Class.forName("io.lumine.mythic.bukkit.BukkitAPIHelper");
            Object helper = mythicMobs.getClass().getMethod("getAPIHelper").invoke(mythicMobs);
            if (helper == null) return false;
            return (boolean) mobClass.getMethod("isMythicMob", org.bukkit.entity.Entity.class)
                    .invoke(helper, entity);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the MythicMobs internal name for an entity.
     *
     * @param entity the entity to check
     * @return the MythicMobs mob type name, or null if not a MythicMobs mob
     */
    public String getMythicMobName(org.bukkit.entity.LivingEntity entity) {
        if (!available || entity == null) return null;
        try {
            Class<?> mobClass = Class.forName("io.lumine.mythic.bukkit.BukkitAPIHelper");
            Object helper = mythicMobs.getClass().getMethod("getAPIHelper").invoke(mythicMobs);
            if (helper == null) return null;
            return (String) mobClass.getMethod("getMythicMobInstance", org.bukkit.entity.Entity.class)
                    .invoke(helper, entity);
        } catch (Exception e) {
            return null;
        }
    }
}
