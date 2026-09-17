package com.lumiczi.lurpg.core.database;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Central database manager that abstracts the underlying storage provider.
 * <p>
 * Reads the {@code database.type} setting from config.yml and instantiates
 * either {@link SQLiteProvider} or {@link MySQLProvider}. Provides both
 * synchronous and asynchronous variants of all database operations.
 * Asynchronous methods use Paper's {@code AsyncScheduler} to execute
 * database I/O off the main thread.
 */
public class DatabaseManager {

    private final LuRPGPlugin plugin;
    private DataProvider dataProvider;

    public DatabaseManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the database based on the configured storage type.
     * Falls back to SQLite if MySQL initialization fails.
     */
    public void initialize() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        String type = config.getString("database.type", "sqlite").toLowerCase();

        try {
            dataProvider = switch (type) {
                case "mysql" -> new MySQLProvider(plugin);
                default -> new SQLiteProvider(plugin);
            };
            dataProvider.initialize();
            plugin.getLogger().info("Database initialized (type: " + type + ")");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize database (type: " + type + "): " + e.getMessage());
            e.printStackTrace();

            // Fall back to SQLite if MySQL fails
            if (!"sqlite".equals(type)) {
                plugin.getLogger().warning("Falling back to SQLite...");
                try {
                    dataProvider = new SQLiteProvider(plugin);
                    dataProvider.initialize();
                    plugin.getLogger().info("SQLite fallback database initialized.");
                } catch (Exception e2) {
                    plugin.getLogger().severe("SQLite fallback also failed: " + e2.getMessage());
                    e2.printStackTrace();
                }
            }
        }
    }

    /**
     * Returns the active data provider.
     *
     * @return the DataProvider, or null if initialization failed
     */
    public DataProvider getDataProvider() {
        return dataProvider;
    }

    // ---- Synchronous operations (call from async thread only) ----

    public void savePlayerData(UUID uuid, String classId, int level, long xp, int skillPoints,
                               double currentResource, Set<String> learnedSkills,
                               Set<String> equippedSkills) {
        if (dataProvider != null) {
            dataProvider.savePlayerData(uuid, classId, level, xp, skillPoints,
                    currentResource, learnedSkills, equippedSkills);
        }
    }

    public PlayerDataRecord loadPlayerData(UUID uuid) {
        if (dataProvider != null) {
            return dataProvider.loadPlayerData(uuid);
        }
        return null;
    }

    public boolean playerExists(UUID uuid) {
        if (dataProvider != null) {
            return dataProvider.playerExists(uuid);
        }
        return false;
    }

    public void createPlayer(UUID uuid, String classId) {
        if (dataProvider != null) {
            dataProvider.createPlayer(uuid, classId);
        }
    }

    // ---- Asynchronous operations (safe to call from main thread) ----

    /**
     * Asynchronously saves player data. Fire-and-forget; errors are logged.
     */
    public void savePlayerDataAsync(UUID uuid, String classId, int level, long xp, int skillPoints,
                                    double currentResource, Set<String> learnedSkills,
                                    Set<String> equippedSkills) {
        if (dataProvider == null) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                dataProvider.savePlayerData(uuid, classId, level, xp, skillPoints,
                        currentResource, learnedSkills, equippedSkills));
    }

    /**
     * Asynchronously loads player data. The callback is executed on the main thread.
     *
     * @param uuid     the player's UUID
     * @param callback callback invoked on the main thread with the loaded record (may be null)
     */
    public void loadPlayerDataAsync(UUID uuid, Consumer<PlayerDataRecord> callback) {
        if (dataProvider == null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            PlayerDataRecord record = dataProvider.loadPlayerData(uuid);
            // Run callback on the main thread for safe Bukkit API access
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(record));
        });
    }

    /**
     * Asynchronously checks if a player exists. The callback is executed on the main thread.
     */
    public void playerExistsAsync(UUID uuid, Consumer<Boolean> callback) {
        if (dataProvider == null) {
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(false));
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            boolean exists = dataProvider.playerExists(uuid);
            plugin.getServer().getScheduler().runTask(plugin, () -> callback.accept(exists));
        });
    }

    /**
     * Asynchronously creates a new player record. Fire-and-forget.
     */
    public void createPlayerAsync(UUID uuid, String classId) {
        if (dataProvider == null) {
            return;
        }
        plugin.getServer().getAsyncScheduler().runNow(plugin, task ->
                dataProvider.createPlayer(uuid, classId));
    }

    /**
     * Shuts down the database provider and releases all resources.
     */
    public void shutdown() {
        if (dataProvider != null) {
            dataProvider.shutdown();
            plugin.getLogger().info("Database connection closed.");
        }
    }
}
