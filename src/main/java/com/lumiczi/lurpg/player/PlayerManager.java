package com.lumiczi.lurpg.player;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages player RPG data lifecycle: loading, caching, saving, and event handling.
 * <p>
 * Uses a {@link ConcurrentHashMap} for thread-safe player data caching.
 * Database operations are performed asynchronously using Paper's
 * {@link io.papermc.paper.threadbox.AsyncScheduler}.
 * </p>
 * <p>
 * The {@code dirty} flag on {@link PlayerData} is used to avoid unnecessary
 * database writes. Data is only saved when it has been modified since the last save.
 * </p>
 */
public class PlayerManager implements Listener {

    private final LuRPGPlugin plugin;
    private final ConcurrentHashMap<UUID, PlayerData> playerCache = new ConcurrentHashMap<>();

    public PlayerManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the player cache. Clears any existing cached data.
     */
    public void initialize() {
        playerCache.clear();
        plugin.getLogger().info("PlayerManager initialized.");
    }

    /**
     * Asynchronously loads player data from the database and caches it.
     * If the player does not exist in the database, a new record is created
     * using the configured starting class (or null if players should choose).
     *
     * @param uuid the player's unique ID
     */
    public void loadPlayerData(UUID uuid) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                var dataProvider = plugin.getDatabaseManager().getDataProvider();

                // Create player record if it doesn't exist
                if (!dataProvider.playerExists(uuid)) {
                    FileConfiguration config = plugin.getConfigManager().getConfig("config");
                    String startingClass = config.getString("classes.starting-class");
                    String classId = startingClass; // null means player chooses later
                    dataProvider.createPlayer(uuid, classId);
                    plugin.getLogger().info("Created new player record for " + uuid);
                }

                // Load player data from database
                var record = dataProvider.loadPlayerData(uuid);
                if (record == null) {
                    plugin.getLogger().warning("Failed to load player data for " + uuid
                            + " (record is null after creation).");
                    return;
                }

                // Build PlayerData from record
                PlayerData data = new PlayerData(plugin, uuid);
                data.setGameClass(GameClass.fromString(record.classId()));
                data.setLevel(record.level());
                data.setXp(record.xp());
                data.setSkillPoints(record.skillPoints());
                data.setCurrentResource(record.currentResource());

                // Restore learned skills
                if (record.learnedSkills() != null) {
                    for (String skillId : record.learnedSkills()) {
                        data.learnSkill(skillId);
                    }
                }

                // Restore equipped skills (in order)
                if (record.equippedSkills() != null) {
                    for (String skillId : record.equippedSkills()) {
                        data.equipSkill(skillId);
                    }
                }

                // Clear dirty flag (data was just loaded, no need to save)
                data.setDirty(false);

                playerCache.put(uuid, data);
                plugin.getLogger().info("Loaded player data for " + uuid);
            } catch (Exception e) {
                plugin.getLogger().severe("Error loading player data for " + uuid
                        + ": " + e.getMessage());
            }
        });
    }

    /**
     * Asynchronously saves player data to the database if it has been modified.
     * <p>
     * The data values are snapshotted on the calling thread (main thread) before
     * the async task is scheduled. The dirty flag is cleared before the async
     * task runs, so any modifications made after the snapshot will be caught by
     * the next save cycle.
     * </p>
     *
     * @param uuid the player's unique ID
     */
    public void savePlayerData(UUID uuid) {
        PlayerData data = playerCache.get(uuid);
        if (data == null || !data.isDirty()) {
            return;
        }

        // Snapshot values on the main thread to avoid race conditions
        final String classId = data.getGameClass() != null ? data.getGameClass().name() : null;
        final int level = data.getLevel();
        final long xp = data.getXp();
        final int skillPoints = data.getSkillPoints();
        final double currentResource = data.getCurrentResource();
        final Set<String> learnedSkills = new HashSet<>(data.getLearnedSkills());
        final Set<String> equippedSkills = new LinkedHashSet<>(data.getEquippedSkills());

        // Clear dirty flag before async save.
        // If data is modified after this, the modification will set dirty=true again.
        data.setDirty(false);

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                var dataProvider = plugin.getDatabaseManager().getDataProvider();
                dataProvider.savePlayerData(uuid, classId, level, xp, skillPoints,
                        currentResource, learnedSkills, equippedSkills);
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving player data for " + uuid
                        + ": " + e.getMessage());
                // Re-mark as dirty so it will be retried on the next save cycle
                data.setDirty(true);
            }
        });
    }

    /**
     * Asynchronously saves all cached player data that has been modified.
     */
    public void saveAll() {
        for (UUID uuid : playerCache.keySet()) {
            savePlayerData(uuid);
        }
    }

    /**
     * Gets the cached PlayerData for the specified player.
     *
     * @param uuid the player's unique ID
     * @return the cached PlayerData, or null if not loaded
     */
    public PlayerData getPlayerData(UUID uuid) {
        return playerCache.get(uuid);
    }

    /**
     * Gets or creates player data for the specified player.
     * <p>
     * If the player's data is not in the cache, a new PlayerData is created
     * with default values (level 1, 0 XP) and the configured starting class.
     * The new data is marked dirty so it will be saved.
     * </p>
     *
     * @param uuid the player's unique ID
     * @return the PlayerData (never null)
     */
    public PlayerData getOrCreatePlayerData(UUID uuid) {
        return playerCache.computeIfAbsent(uuid, id -> {
            PlayerData data = new PlayerData(plugin, id);
            // Apply starting class from config if configured
            FileConfiguration config = plugin.getConfigManager().getConfig("config");
            String startingClass = config.getString("classes.starting-class");
            if (startingClass != null) {
                data.setGameClass(GameClass.fromString(startingClass));
            }
            data.markDirty();
            plugin.getLogger().info("Created default PlayerData for " + id
                    + " (not yet loaded from database).");
            return data;
        });
    }

    /**
     * Performs a synchronous save of all dirty player data, then clears the cache.
     * <p>
     * This method should be called during plugin disable to ensure all player
     * data is persisted before the database connection is closed.
     * </p>
     */
    public void shutdown() {
        int saved = 0;
        try {
            var dataProvider = plugin.getDatabaseManager().getDataProvider();
            for (Map.Entry<UUID, PlayerData> entry : playerCache.entrySet()) {
                PlayerData data = entry.getValue();
                if (data == null || !data.isDirty()) {
                    continue;
                }
                try {
                    String classId = data.getGameClass() != null ? data.getGameClass().name() : null;
                    dataProvider.savePlayerData(
                            entry.getKey(),
                            classId,
                            data.getLevel(),
                            data.getXp(),
                            data.getSkillPoints(),
                            data.getCurrentResource(),
                            data.getLearnedSkills(),
                            data.getEquippedSkills()
                    );
                    data.setDirty(false);
                    saved++;
                } catch (Exception e) {
                    plugin.getLogger().severe("Error saving player data for " + entry.getKey()
                            + " during shutdown: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error during PlayerManager shutdown: " + e.getMessage());
        }
        playerCache.clear();
        plugin.getLogger().info("PlayerManager shutdown complete. Saved " + saved + " player(s).");
    }

    // ==================== Event Handlers ====================

    /**
     * Handles player join: asynchronously loads player data from the database.
     *
     * @param event the player join event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer().getUniqueId());
    }

    /**
     * Handles player quit: asynchronously saves player data to the database.
     *
     * @param event the player quit event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        savePlayerData(event.getPlayer().getUniqueId());
    }

    /**
     * Handles player death: preserves RPG data (no XP loss, no reset).
     * Marks the data as dirty to ensure it is saved.
     *
     * @param event the player death event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = playerCache.get(player.getUniqueId());
        if (data != null) {
            // Preserve RPG data - just mark dirty to ensure save
            data.markDirty();
        }
    }
}
