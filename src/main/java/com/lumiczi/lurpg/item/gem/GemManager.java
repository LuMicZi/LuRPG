package com.lumiczi.lurpg.item.gem;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.core.text.TextUtil;
import com.lumiczi.lurpg.item.ItemManager;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the gem socketing system.
 * <p>
 * Loads gem definitions from gems.yml, creates gem items,
 * and provides methods for working with gem items and socket data.
 * </p>
 */
public class GemManager {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey gemIdKey;

    /** Gem definitions: gemId -> Gem */
    private final Map<String, Gem> gems = new LinkedHashMap<>();

    /** Unsocket keep rate (probability that the gem is preserved when removed) */
    private double unsocketKeepRate = 0.8;

    public GemManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
        this.gemIdKey = new NamespacedKey(plugin, "rpg_gem_id");
    }

    /**
     * Reloads gem configuration from gems.yml.
     */
    public void reload() {
        gems.clear();
        FileConfiguration config = plugin.getConfigManager().getConfig("gems");
        if (config == null) {
            plugin.getLogger().warning("gems.yml not found; no gems loaded.");
            return;
        }

        unsocketKeepRate = config.getDouble("unsocket-keep-rate", 0.8);

        ConfigurationSection gemsSection = config.getConfigurationSection("gems");
        if (gemsSection != null) {
            for (String gemId : gemsSection.getKeys(false)) {
                ConfigurationSection gemSection = gemsSection.getConfigurationSection(gemId);
                if (gemSection != null) {
                    try {
                        Gem gem = parseGem(gemId, gemSection);
                        gems.put(gemId, gem);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse gem '" + gemId
                                + "': " + e.getMessage());
                    }
                }
            }
        }

        plugin.getLogger().info("Gem system loaded: " + gems.size() + " gems defined.");
    }

    /**
     * Parses a Gem from a configuration section.
     */
    private Gem parseGem(String id, ConfigurationSection section) {
        String name = section.getString("name", id);
        String materialName = section.getString("material", "RED_DYE");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.RED_DYE;
        }
        int customModelData = section.getInt("custom-model-data", 0);
        int tier = section.getInt("tier", 1);

        StatMap stats = new StatMap();
        ConfigurationSection statsSection = section.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String key : statsSection.getKeys(false)) {
                StatType statType = ItemManager.parseStatType(key);
                if (statType != null) {
                    stats.set(statType, statsSection.getDouble(key));
                }
            }
        }

        return new Gem(id, name, material, customModelData, tier, stats);
    }

    // ==================== Gem Query Methods ====================

    /**
     * Gets a gem definition by ID.
     *
     * @param gemId the gem ID
     * @return the Gem, or null if not found
     */
    public Gem getGem(String gemId) {
        if (gemId == null) {
            return null;
        }
        return gems.get(gemId);
    }

    /**
     * Checks whether an item is a gem item.
     *
     * @param item the item to check
     * @return true if the item has a gem ID in its PDC
     */
    public boolean isGem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String gemId = meta.getPersistentDataContainer().get(gemIdKey, PersistentDataType.STRING);
        return gemId != null && gems.containsKey(gemId);
    }

    /**
     * Gets the gem ID from an item's PDC.
     *
     * @param item the item to check
     * @return the gem ID, or null if not a gem
     */
    public String getGemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(gemIdKey, PersistentDataType.STRING);
    }

    /**
     * Returns all loaded gem IDs.
     *
     * @return an unmodifiable set of gem IDs
     */
    public Set<String> getAllGemIds() {
        return Collections.unmodifiableSet(gems.keySet());
    }

    public double getUnsocketKeepRate() {
        return unsocketKeepRate;
    }

    // ==================== Gem Item Creation ====================

    /**
     * Creates a gem ItemStack for the specified gem ID.
     *
     * @param gemId  the gem ID
     * @param amount the stack amount
     * @return the created ItemStack, or null if gem not found
     */
    public ItemStack createGemItem(String gemId, int amount) {
        Gem gem = gems.get(gemId);
        if (gem == null) {
            return null;
        }

        ItemStack item = new ItemStack(gem.material(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Display name
        meta.displayName(TextUtil.noItalic(miniMessage.deserialize(gem.name())));

        // Custom model data
        if (gem.customModelData() > 0) {
            meta.setCustomModelData(gem.customModelData());
        }

        // Lore with stats
        List<String> loreLines = new ArrayList<>();
        loreLines.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        loreLines.add("<yellow>宝石等级: <white>" + gem.tier() + " 级");
        loreLines.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        for (var entry : gem.stats().entrySet()) {
            StatType type = entry.getKey();
            double value = entry.getValue();
            String suffix = type.getSuffix();
            loreLines.add(type.getColorPrefix() + "+" + formatValue(value) + " "
                    + type.getDisplayName() + suffix);
        }
        loreLines.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        loreLines.add("<gray>可镶嵌到装备的宝石槽位");

        List<net.kyori.adventure.text.Component> loreComponents = new ArrayList<>();
        for (String line : loreLines) {
            loreComponents.add(TextUtil.noItalic(miniMessage.deserialize(line)));
        }
        meta.lore(loreComponents);

        // Store gem ID in PDC
        meta.getPersistentDataContainer().set(gemIdKey, PersistentDataType.STRING, gemId);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Formats a double value for display.
     */
    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    // ==================== Gem Record ====================

    /**
     * Represents a gem definition.
     *
     * @param id               the unique gem ID
     * @param name             the display name (MiniMessage format)
     * @param material         the Bukkit material
     * @param customModelData  custom model data value
     * @param tier             the gem tier/level
     * @param stats            the stat bonuses provided by this gem
     */
    public record Gem(
            String id,
            String name,
            Material material,
            int customModelData,
            int tier,
            StatMap stats
    ) {}
}
