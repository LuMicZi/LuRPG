package com.lumiczi.lurpg.core.hook;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * Hook for the PlaceholderAPI plugin.
 * <p>
 * Detects PlaceholderAPI and registers a custom placeholder expansion
 * ({@code %lurpg_*%}). Since PlaceholderAPI is an optional dependency
 * that may not be present at runtime, the expansion class is defined as
 * a static inner class. The JVM loads inner classes lazily, so the
 * {@code PlaceholderExpansion} superclass is only resolved when the
 * expansion is actually instantiated (i.e., only when PAPI is detected).
 * All registration logic is wrapped in a try-catch to handle any
 * {@link NoClassDefFoundError} edge cases gracefully.
 */
public class PlaceholderAPIHook {

    private final LuRPGPlugin plugin;
    private boolean available;

    public PlaceholderAPIHook(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects PlaceholderAPI and registers the expansion if available.
     */
    public void initialize() {
        Plugin p = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (p == null) {
            this.available = false;
            return;
        }

        try {
            // This class reference triggers lazy loading of LuRPGExpansion,
            // which in turn requires PlaceholderExpansion on the classpath.
            // Since PAPI is confirmed present above, this should succeed.
            LuRPGExpansion expansion = new LuRPGExpansion(plugin);
            boolean registered = expansion.register();
            this.available = registered;
            if (registered) {
                plugin.getLogger().info("PlaceholderAPI hooked (expansion registered).");
            } else {
                plugin.getLogger().warning("PlaceholderAPI found but expansion registration failed.");
            }
        } catch (Throwable t) {
            // NoClassDefFoundError, NoSuchMethodError, etc. if PAPI version is incompatible
            this.available = false;
            plugin.getLogger().warning("Failed to register PlaceholderAPI expansion: " + t.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * PlaceholderExpansion for LuRPG.
     * <p>
     * This class is only loaded when {@link #initialize()} confirms that
     * PlaceholderAPI is installed. Supported placeholders:
     * <ul>
     *   <li>{@code %lurpg_class%} - player's class name</li>
     *   <li>{@code %lurpg_level%} - player's level</li>
     *   <li>{@code %lurpg_xp%} - player's current XP</li>
     *   <li>{@code %lurpg_skillpoints%} - available skill points</li>
     * </ul>
     * Additional placeholders can be added in {@link #onRequest(OfflinePlayer, String)}.
     */
    public static class LuRPGExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {

        private final LuRPGPlugin plugin;

        public LuRPGExpansion(LuRPGPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public String getIdentifier() {
            return "lurpg";
        }

        @Override
        public String getAuthor() {
            return "LuMicZi";
        }

        @Override
        public String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            // Keep the expansion registered across PAPI reloads
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null) {
                return "";
            }

            // TODO: Implement full placeholder resolution once PlayerManager is available.
            // Current placeholders are stubs that return placeholder text.
            return switch (params.toLowerCase()) {
                case "class" -> "";       // playerManager.getPlayerData(uuid).getClassId()
                case "level" -> "1";      // playerManager.getPlayerData(uuid).getLevel()
                case "xp" -> "0";         // playerManager.getPlayerData(uuid).getXp()
                case "skillpoints" -> "0"; // playerManager.getPlayerData(uuid).getSkillPoints()
                default -> null;
            };
        }
    }
}
