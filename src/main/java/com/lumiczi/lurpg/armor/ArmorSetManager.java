package com.lumiczi.lurpg.armor;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.item.ItemManager;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages armor set definitions and tracks active set bonuses for players.
 * <p>
 * Loads armor set configurations from {@code armor_sets.yml}, monitors player
 * equipment changes, and provides access to active stat bonuses and special
 * effects.
 * </p>
 * <p>
 * A periodic task (configured by {@code sets.check-interval} in config.yml)
 * scans online players' equipment and updates the cached set bonus state.
 * This caching avoids expensive inventory scanning on every combat event.
 * </p>
 */
public class ArmorSetManager {

    private final LuRPGPlugin plugin;

    // All loaded armor sets: setId -> ArmorSet
    private final Map<String, ArmorSet> armorSets = new HashMap<>();

    // Reverse lookup: itemId -> setId (for quick set identification)
    private final Map<String, String> itemToSetMap = new HashMap<>();

    // Cached player set state: UUID -> (ArmorSet -> equipped count)
    private final Map<UUID, Map<ArmorSet, Integer>> playerSetCache = new ConcurrentHashMap<>();

    // Periodic check task
    private BukkitTask checkTask;

    public ArmorSetManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads armor set definitions from armor_sets.yml.
     */
    public void load() {
        armorSets.clear();
        itemToSetMap.clear();

        FileConfiguration config = plugin.getConfigManager().getConfig("armor_sets");
        if (config == null) {
            plugin.getLogger().warning("armor_sets.yml not found; ArmorSetManager loaded with no sets.");
            startCheckTask();
            return;
        }

        ConfigurationSection setsSection = config.getConfigurationSection("sets");
        if (setsSection != null) {
            for (String setId : setsSection.getKeys(false)) {
                ConfigurationSection setSection = setsSection.getConfigurationSection(setId);
                if (setSection != null) {
                    ArmorSet armorSet = parseArmorSet(setId, setSection);
                    if (armorSet != null) {
                        armorSets.put(setId, armorSet);
                        // Build reverse lookup
                        for (String pieceId : armorSet.getPieces()) {
                            itemToSetMap.put(pieceId, setId);
                        }
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + armorSets.size() + " armor sets.");

        // Start periodic check task
        startCheckTask();
    }

    /**
     * Parses an ArmorSet from a configuration section.
     *
     * @param setId      the set ID
     * @param setSection the configuration section for this set
     * @return the parsed ArmorSet, or null if parsing failed
     */
    private ArmorSet parseArmorSet(String setId, ConfigurationSection setSection) {
        String displayName = setSection.getString("display-name", setId);
        List<String> description = setSection.getStringList("description");
        List<String> pieces = setSection.getStringList("pieces");

        Map<Integer, SetBonus> bonuses = new HashMap<>();
        ConfigurationSection bonusesSection = setSection.getConfigurationSection("bonuses");
        if (bonusesSection != null) {
            for (String countStr : bonusesSection.getKeys(false)) {
                try {
                    int pieceCount = Integer.parseInt(countStr);
                    ConfigurationSection bonusSection = bonusesSection.getConfigurationSection(countStr);
                    if (bonusSection != null) {
                        SetBonus bonus = parseSetBonus(pieceCount, bonusSection);
                        if (bonus != null) {
                            bonuses.put(pieceCount, bonus);
                        }
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid piece count '" + countStr
                            + "' in armor set '" + setId + "'. Skipping.");
                }
            }
        }

        return new ArmorSet(setId, displayName, description, pieces, bonuses);
    }

    /**
     * Parses a SetBonus from a configuration section.
     *
     * @param pieceCount   the piece count threshold
     * @param bonusSection the configuration section for this bonus
     * @return the parsed SetBonus, or null if parsing failed
     */
    private SetBonus parseSetBonus(int pieceCount, ConfigurationSection bonusSection) {
        StatMap stats = new StatMap();

        // Parse stats
        ConfigurationSection statsSection = bonusSection.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String statKey : statsSection.getKeys(false)) {
                StatType type = ItemManager.parseStatType(statKey);
                if (type != null) {
                    stats.set(type, statsSection.getDouble(statKey));
                } else {
                    plugin.getLogger().warning("Unknown stat type '" + statKey
                            + "' in set bonus (piece count " + pieceCount + "). Skipping.");
                }
            }
        }

        String description = bonusSection.getString("description", "");

        // Parse special effect
        SetBonus.SpecialEffect special = null;
        ConfigurationSection specialSection = bonusSection.getConfigurationSection("special");
        if (specialSection != null) {
            special = parseSpecialEffect(specialSection);
        }

        return new SetBonus(pieceCount, stats, description, special);
    }

    /**
     * Parses a SpecialEffect from a configuration section.
     *
     * @param specialSection the configuration section for the special effect
     * @return the parsed SpecialEffect, or null if parsing failed
     */
    private SetBonus.SpecialEffect parseSpecialEffect(ConfigurationSection specialSection) {
        String effectStr = specialSection.getString("effect", "");
        SetBonus.SpecialEffect.Type type;
        try {
            type = SetBonus.SpecialEffect.Type.valueOf(effectStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown special effect type '" + effectStr + "'. Skipping.");
            return null;
        }

        // Collect all parameters
        Map<String, Object> parameters = new HashMap<>();
        for (String key : specialSection.getKeys(true)) {
            if (!key.equals("effect") && !key.equals("description")) {
                parameters.put(key, specialSection.get(key));
            }
        }

        String description = specialSection.getString("description", "");
        return new SetBonus.SpecialEffect(type, parameters, description);
    }

    /**
     * Gets an armor set by its ID.
     *
     * @param id the set ID
     * @return the ArmorSet, or null if not found
     */
    public ArmorSet getSet(String id) {
        return armorSets.get(id);
    }

    /**
     * Returns all loaded armor sets.
     *
     * @return an unmodifiable map of set ID to ArmorSet
     */
    public Map<String, ArmorSet> getAllSets() {
        return Collections.unmodifiableMap(armorSets);
    }

    /**
     * Finds the armor set that an item belongs to, based on the item's RPG ID.
     *
     * @param itemId the RPG item ID
     * @return the ArmorSet, or null if the item does not belong to any set
     */
    public ArmorSet getSetFromItem(String itemId) {
        if (itemId == null) {
            return null;
        }
        String setId = itemToSetMap.get(itemId);
        if (setId != null) {
            return armorSets.get(setId);
        }
        return null;
    }

    /**
     * Checks the player's currently equipped armor and determines which sets
     * are active and how many pieces of each set are equipped.
     * <p>
     * This method scans the player's equipment slots (helmet, chestplate,
     * leggings, boots) and counts how many pieces of each armor set are
     * currently worn.
     * </p>
     *
     * @param player the player to check
     * @return a map of ArmorSet to the number of pieces equipped
     */
    public Map<ArmorSet, Integer> checkPlayerSet(Player player) {
        Map<ArmorSet, Integer> equippedSets = new HashMap<>();

        if (player == null) {
            return equippedSets;
        }

        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return equippedSets;
        }

        PlayerInventory inventory = player.getInventory();
        if (inventory == null) {
            return equippedSets;
        }

        // Check armor slots only (not weapons)
        ItemStack[] armorContents = {
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        };

        for (ItemStack item : armorContents) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            String itemId = itemManager.getRPGItemId(item);
            if (itemId == null) {
                continue;
            }

            ArmorSet set = getSetFromItem(itemId);
            if (set != null) {
                equippedSets.merge(set, 1, Integer::sum);
            }
        }

        return equippedSets;
    }

    /**
     * Gets the active stat bonuses for a player from all equipped armor sets.
     * <p>
     * This method uses the cached set state if available, or performs a
     * fresh check if the cache is not populated.
     * </p>
     *
     * @param player the player
     * @return a StatMap containing the combined stat bonuses from all active sets
     */
    public StatMap getActiveBonuses(Player player) {
        StatMap totalBonuses = new StatMap();

        Map<ArmorSet, Integer> equippedSets = getCachedSets(player);
        if (equippedSets == null || equippedSets.isEmpty()) {
            return totalBonuses;
        }

        for (Map.Entry<ArmorSet, Integer> entry : equippedSets.entrySet()) {
            ArmorSet set = entry.getKey();
            int count = entry.getValue();

            // Get all active bonuses for this set
            List<SetBonus> activeBonuses = set.getActiveBonuses(count);
            for (SetBonus bonus : activeBonuses) {
                totalBonuses.addAll(bonus.getStats());
            }
        }

        return totalBonuses;
    }

    /**
     * Gets all active special effects for a player from equipped armor sets.
     *
     * @param player the player
     * @return a list of active SpecialEffect entries
     */
    public List<SetBonus.SpecialEffect> getActiveSpecialEffects(Player player) {
        List<SetBonus.SpecialEffect> effects = new ArrayList<>();

        Map<ArmorSet, Integer> equippedSets = getCachedSets(player);
        if (equippedSets == null || equippedSets.isEmpty()) {
            return effects;
        }

        for (Map.Entry<ArmorSet, Integer> entry : equippedSets.entrySet()) {
            ArmorSet set = entry.getKey();
            int count = entry.getValue();

            List<SetBonus> activeBonuses = set.getActiveBonuses(count);
            for (SetBonus bonus : activeBonuses) {
                if (bonus.hasSpecial()) {
                    effects.add(bonus.getSpecial());
                }
            }
        }

        return effects;
    }

    /**
     * Gets the cached set state for a player, or performs a fresh check
     * if the cache is not populated.
     *
     * @param player the player
     * @return a map of ArmorSet to equipped piece count
     */
    private Map<ArmorSet, Integer> getCachedSets(Player player) {
        if (player == null) {
            return Collections.emptyMap();
        }

        UUID uuid = player.getUniqueId();
        Map<ArmorSet, Integer> cached = playerSetCache.get(uuid);
        if (cached != null) {
            return cached;
        }

        // Cache miss - perform a fresh check
        Map<ArmorSet, Integer> fresh = checkPlayerSet(player);
        playerSetCache.put(uuid, fresh);
        return fresh;
    }

    /**
     * Starts the periodic set check task.
     * The interval is configured by {@code sets.check-interval} in config.yml.
     */
    private void startCheckTask() {
        // Cancel existing task
        if (checkTask != null) {
            checkTask.cancel();
        }

        int interval = plugin.getConfigManager().getMainConfig().getInt("sets.check-interval", 20);
        if (interval <= 0) {
            return;
        }

        checkTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                try {
                    Map<ArmorSet, Integer> sets = checkPlayerSet(player);
                    playerSetCache.put(player.getUniqueId(), sets);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error checking armor set for "
                            + player.getName() + ": " + e.getMessage());
                }
            }
        }, interval, interval);
    }

    /**
     * Clears the cached set state for a player.
     * Should be called when a player quits or changes equipment significantly.
     *
     * @param uuid the player's UUID
     */
    public void clearCache(UUID uuid) {
        playerSetCache.remove(uuid);
    }

    /**
     * Clears all cached set states.
     */
    public void clearAllCache() {
        playerSetCache.clear();
    }

    /**
     * Shuts down the manager, cancelling the periodic task and clearing caches.
     */
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
        playerSetCache.clear();
    }
}
