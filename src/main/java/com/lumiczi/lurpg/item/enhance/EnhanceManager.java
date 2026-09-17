package com.lumiczi.lurpg.item.enhance;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages the item enhancement system.
 * <p>
 * Handles loading enhancement configuration, calculating success rates,
 * stat bonuses, costs, and performing enhancement operations on items.
 * Enhancement levels are stored in the item's PersistentDataContainer
 * under the key {@code rpg_enhance_level}.
 * </p>
 */
public class EnhanceManager {

    private final LuRPGPlugin plugin;
    private final NamespacedKey enhanceLevelKey;

    // Config values
    private int maxLevel = 15;
    private double statBonusPercentPerLevel = 5.0;
    private final Map<Integer, Double> successRates = new HashMap<>();

    private boolean failureDowngrade = true;
    private int failureDowngradeAmount = 1;
    private boolean failureDestroy = false;
    private int destroyStartLevel = 10;

    private boolean costUseMoney = true;
    private double costMoneyBase = 100;
    private double costMoneyMultiplier = 1.5;
    private boolean costUseItem = false;
    private Material costItemMaterial = Material.DIAMOND;
    private int costItemAmountBase = 1;

    public EnhanceManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
        this.enhanceLevelKey = new NamespacedKey(plugin, "rpg_enhance_level");
    }

    /**
     * Reloads enhancement configuration from item_enhance.yml.
     */
    public void reload() {
        FileConfiguration config = plugin.getConfigManager().getConfig("item_enhance");
        if (config == null) {
            plugin.getLogger().warning("item_enhance.yml not found; using default values.");
            return;
        }

        maxLevel = config.getInt("max-level", 15);
        statBonusPercentPerLevel = config.getDouble("stat-bonus-percent-per-level", 5.0);

        // Success rates
        successRates.clear();
        ConfigurationSection ratesSection = config.getConfigurationSection("success-rates");
        if (ratesSection != null) {
            for (String key : ratesSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    double rate = ratesSection.getDouble(key);
                    successRates.put(level, rate);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Failure settings
        ConfigurationSection failureSection = config.getConfigurationSection("failure");
        if (failureSection != null) {
            failureDowngrade = failureSection.getBoolean("downgrade-level", true);
            failureDowngradeAmount = failureSection.getInt("downgrade-amount", 1);
            failureDestroy = failureSection.getBoolean("destroy-item", false);
            destroyStartLevel = failureSection.getInt("destroy-start-level", 10);
        }

        // Cost settings
        ConfigurationSection costSection = config.getConfigurationSection("cost");
        if (costSection != null) {
            costUseMoney = costSection.getBoolean("use-money", true);
            costMoneyBase = costSection.getDouble("money-base", 100);
            costMoneyMultiplier = costSection.getDouble("money-multiplier", 1.5);
            costUseItem = costSection.getBoolean("use-item", false);
            String matName = costSection.getString("item-material", "DIAMOND");
            try {
                costItemMaterial = Material.valueOf(matName.toUpperCase());
            } catch (IllegalArgumentException e) {
                costItemMaterial = Material.DIAMOND;
            }
            costItemAmountBase = costSection.getInt("item-amount-base", 1);
        }

        plugin.getLogger().info("Enhance system loaded: max-level=" + maxLevel);
    }

    // ==================== Config Getters ====================

    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * Gets the success rate for enhancing from the given level to level+1.
     *
     * @param currentLevel the current enhancement level (0-based)
     * @return the success rate (0.0 - 1.0), or 0 if at max level
     */
    public double getSuccessRate(int currentLevel) {
        if (currentLevel >= maxLevel) {
            return 0.0;
        }
        int nextLevel = currentLevel + 1;
        return successRates.getOrDefault(nextLevel, 0.0);
    }

    /**
     * Gets the total stat bonus percentage for the given enhancement level.
     * Formula: level * statBonusPercentPerLevel / 100
     *
     * @param level the enhancement level
     * @return the bonus multiplier (e.g. 0.25 for +25%)
     */
    public double getStatBonusPercent(int level) {
        if (level <= 0) {
            return 0.0;
        }
        return level * statBonusPercentPerLevel / 100.0;
    }

    /**
     * Gets the stat multiplier for the given enhancement level.
     * Formula: 1.0 + getStatBonusPercent(level)
     *
     * @param level the enhancement level
     * @return the multiplier (e.g. 1.25 for +25%)
     */
    public double getStatMultiplier(int level) {
        return 1.0 + getStatBonusPercent(level);
    }

    /**
     * Gets the money cost for enhancing from the given level to level+1.
     *
     * @param currentLevel the current enhancement level
     * @return the cost in economy currency
     */
    public double getCostMoney(int currentLevel) {
        if (currentLevel < 0) {
            currentLevel = 0;
        }
        return costMoneyBase * Math.pow(costMoneyMultiplier, currentLevel);
    }

    /**
     * Gets the item amount required for enhancing from the given level.
     *
     * @param currentLevel the current enhancement level
     * @return the required item amount
     */
    public int getCostItemAmount(int currentLevel) {
        if (currentLevel < 0) {
            currentLevel = 0;
        }
        return costItemAmountBase + currentLevel;
    }

    public boolean isCostUseMoney() {
        return costUseMoney;
    }

    public boolean isCostUseItem() {
        return costUseItem;
    }

    public Material getCostItemMaterial() {
        return costItemMaterial;
    }

    // ==================== Enhance Level PDC ====================

    /**
     * Reads the enhancement level from an item's PDC.
     *
     * @param item the item to check
     * @return the enhancement level, or 0 if not enhanced
     */
    public int getEnhanceLevel(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0;
        }
        Integer val = meta.getPersistentDataContainer().get(enhanceLevelKey, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    /**
     * Sets the enhancement level on an item.
     * Does not regenerate lore.
     *
     * @param item  the item to modify
     * @param level the enhancement level to set
     * @return true if successful
     */
    public boolean setEnhanceLevel(ItemStack item, int level) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (level <= 0) {
            meta.getPersistentDataContainer().remove(enhanceLevelKey);
        } else {
            meta.getPersistentDataContainer().set(enhanceLevelKey, PersistentDataType.INTEGER, level);
        }
        item.setItemMeta(meta);
        return true;
    }

    // ==================== Enhancement Operation ====================

    /**
     * Attempts to enhance an item.
     * <p>
     * Rolls for success based on the current level's success rate.
     * On success: level increases by 1.
     * On failure: may downgrade or destroy the item based on config.
     * </p>
     *
     * @param item the item to enhance
     * @return the result of the enhancement attempt
     */
    public EnhanceResult enhance(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return EnhanceResult.NOT_RPG_ITEM;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            return EnhanceResult.NOT_RPG_ITEM;
        }

        int currentLevel = getEnhanceLevel(item);
        if (currentLevel >= maxLevel) {
            return EnhanceResult.MAX_LEVEL;
        }

        double successRate = getSuccessRate(currentLevel);
        double roll = Math.random();

        if (roll < successRate) {
            // Success
            setEnhanceLevel(item, currentLevel + 1);
            return EnhanceResult.SUCCESS;
        } else {
            // Failure
            return handleFailure(item, currentLevel);
        }
    }

    /**
     * Handles the failure case of an enhancement attempt.
     * May downgrade the item or destroy it.
     */
    private EnhanceResult handleFailure(ItemStack item, int currentLevel) {
        // Check for destruction
        if (failureDestroy && currentLevel >= destroyStartLevel) {
            double destroyChance = 0.3; // 30% chance to destroy on failure at high levels
            if (Math.random() < destroyChance) {
                return EnhanceResult.DESTROYED;
            }
        }

        // Downgrade
        if (failureDowngrade && currentLevel > 0) {
            int newLevel = Math.max(0, currentLevel - failureDowngradeAmount);
            setEnhanceLevel(item, newLevel);
        }

        return EnhanceResult.FAILED;
    }

    /**
     * Result of an enhancement attempt.
     */
    public enum EnhanceResult {
        SUCCESS,      // 成功
        FAILED,       // 失败（掉级）
        DESTROYED,    // 失败（物品销毁）
        MAX_LEVEL,    // 已满级
        NOT_RPG_ITEM  // 非RPG物品
    }
}
