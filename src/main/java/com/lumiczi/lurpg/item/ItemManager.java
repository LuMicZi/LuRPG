package com.lumiczi.lurpg.item;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.core.text.TextUtil;
import com.lumiczi.lurpg.item.enhance.EnhanceManager;
import com.lumiczi.lurpg.item.gem.GemManager;
import com.lumiczi.lurpg.item.tier.TierManager;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages RPG items, including loading item definitions from items.yml,
 * creating ItemStacks with RPG data, and parsing RPG stats from items.
 * <p>
 * This is a foundational manager that provides the interface needed by
 * the combat, armor set, and potion systems to identify RPG items and
 * retrieve their stat bonuses.
 * </p>
 * <p>
 * Item definitions are loaded from {@code items.yml}. Each item can have:
 * <ul>
 *   <li>A display name (MiniMessage format)</li>
 *   <li>A material type</li>
 *   <li>Level and class requirements</li>
 *   <li>Stat bonuses (mapped to {@link StatType})</li>
 *   <li>Elemental damage bonuses</li>
 *   <li>An armor set ID (for set bonus tracking)</li>
 *   <li>Custom model data</li>
 * </ul>
 * </p>
 * <p>
 * RPG item IDs are stored in the item's {@link org.bukkit.persistence.PersistentDataContainer}
 * under the key {@code lurpg:rpg_item_id}.
 * </p>
 */
public class ItemManager {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey itemIdKey;
    private final NamespacedKey armorSetKey;
    private final NamespacedKey levelReqKey;
    private final NamespacedKey classReqKey;
    private final NamespacedKey skillsKey;
    private final NamespacedKey loreTemplateKey;
    private final NamespacedKey specialEffectsKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey tierUpgradeKey;
    private final NamespacedKey socketsKey;
    private final NamespacedKey enhanceLevelKey;
    private final NamespacedKey sealedKey;
    private final NamespacedKey sealScrollKey;

    /** Lore builder for regenerating item lore from PDC data. */
    private final ItemLoreBuilder loreBuilder;

    /** Tier manager for tier system. */
    private final TierManager tierManager;

    /** Enhance manager for enhancement system. */
    private final EnhanceManager enhanceManager;

    /** Gem manager for gem socketing system. */
    private final GemManager gemManager;

    // Cached item definitions: itemId -> item config section
    private final Map<String, ConfigurationSection> itemDefinitions = new HashMap<>();

    // Cached RPGItem objects: itemId -> RPGItem
    private final Map<String, RPGItem> rpgItems = new HashMap<>();

    /** Prefix for stat-specific PDC keys (e.g. "rpg_stat_physical_attack"). */
    private static final String STAT_KEY_PREFIX = "rpg_stat_";

    public ItemManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
        this.itemIdKey = new NamespacedKey(plugin, "rpg_item_id");
        this.armorSetKey = new NamespacedKey(plugin, "rpg_armor_set");
        this.levelReqKey = new NamespacedKey(plugin, "rpg_level_req");
        this.classReqKey = new NamespacedKey(plugin, "rpg_class_req");
        this.skillsKey = new NamespacedKey(plugin, "rpg_skills");
        this.loreTemplateKey = new NamespacedKey(plugin, "rpg_lore_template");
        this.specialEffectsKey = new NamespacedKey(plugin, "rpg_special_effects");
        this.tierKey = new NamespacedKey(plugin, "rpg_tier");
        this.tierUpgradeKey = new NamespacedKey(plugin, "rpg_tier_upgrade");
        this.socketsKey = new NamespacedKey(plugin, "rpg_sockets");
        this.enhanceLevelKey = new NamespacedKey(plugin, "rpg_enhance_level");
        this.sealedKey = new NamespacedKey(plugin, "rpg_sealed");
        this.sealScrollKey = new NamespacedKey(plugin, "rpg_seal_scroll");
        this.loreBuilder = new ItemLoreBuilder(plugin);
        this.tierManager = new TierManager(plugin);
        this.enhanceManager = new EnhanceManager(plugin);
        this.gemManager = new GemManager(plugin);
    }

    /**
     * Loads item definitions from items.yml into memory.
     */
    public void load() {
        itemDefinitions.clear();
        rpgItems.clear();
        FileConfiguration config = plugin.getConfigManager().getConfig("items");
        if (config == null) {
            plugin.getLogger().warning("items.yml not found; ItemManager loaded with no definitions.");
            return;
        }

        ConfigurationSection itemsSection = config.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemId : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                if (itemSection != null) {
                    itemDefinitions.put(itemId, itemSection);
                    // Parse and cache RPGItem object
                    try {
                        RPGItem rpgItem = RPGItem.fromConfig(itemId, itemSection);
                        rpgItems.put(itemId, rpgItem);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to parse RPGItem '" + itemId
                                + "': " + e.getMessage());
                    }
                }
            }
        }

        // Load tier system
        tierManager.reload();

        // Load enhance system
        enhanceManager.reload();

        // Load gem system
        gemManager.reload();

        plugin.getLogger().info("Loaded " + itemDefinitions.size() + " item definitions.");
    }

    /**
     * Registers an RPG item at runtime (e.g. items discovered from CraftEngine
     * configurations). The item is added to the in-memory cache keyed by its ID.
     *
     * @param item the RPG item to register; ignored if null or missing an ID
     */
    public void registerItem(RPGItem item) {
        if (item == null || item.getId() == null || item.getId().isBlank()) {
            return;
        }
        rpgItems.put(item.getId(), item);
    }

    /**
     * Retrieves the RPG item ID from an ItemStack's PersistentDataContainer.
     *
     * @param item the ItemStack to check
     * @return the RPG item ID, or null if the item is not an RPG item
     */
    public String getRPGItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    /**
     * Retrieves the armor set ID from an ItemStack's PersistentDataContainer.
     *
     * @param item the ItemStack to check
     * @return the armor set ID, or null if the item does not belong to a set
     */
    public String getArmorSetId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        // First check PDC
        String setId = meta.getPersistentDataContainer().get(armorSetKey, PersistentDataType.STRING);
        if (setId != null) {
            return setId;
        }
        // Fallback: look up from item definition
        String itemId = getRPGItemId(item);
        if (itemId != null) {
            ConfigurationSection itemSection = itemDefinitions.get(itemId);
            if (itemSection != null) {
                return itemSection.getString("armor-set");
            }
        }
        return null;
    }

    /**
     * Calculates the total stat bonuses for an RPG item.
     * <p>
     * Combines stats from multiple sources:
     * <ol>
     *   <li>Config-defined stats (from items.yml, looked up by item ID)</li>
     *   <li>PDC-stored stats (added via commands like /rpgadmin item setstat)</li>
     *   <li>Tier multiplier (percentage bonus)</li>
     *   <li>Enhancement multiplier (percentage bonus)</li>
     *   <li>Socketed gem stats (flat bonus added after multipliers)</li>
     * </ol>
     * Calculation order: (base * tier * enhance) + gems
     * </p>
     *
     * @param item the ItemStack to parse
     * @return a StatMap containing the item's stat bonuses (empty if not an RPG item)
     */
    public StatMap getItemStats(ItemStack item) {
        StatMap stats = new StatMap();
        if (item == null || item.getType() == Material.AIR) {
            return stats;
        }

        // 1. Config-defined stats (if item has an ID in items.yml)
        String itemId = getRPGItemId(item);
        if (itemId != null) {
            StatMap configStats = getItemStats(itemId);
            stats.addAll(configStats);
        }

        // 2. PDC-stored stats (added via commands)
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                var pdc = meta.getPersistentDataContainer();
                for (StatType statType : StatType.values()) {
                    Double val = pdc.get(getStatKey(statType), PersistentDataType.DOUBLE);
                    if (val != null) {
                        stats.add(statType, val);
                    }
                }
            }
        }

        // 3. Apply tier multiplier
        double tierMultiplier = getTierMultiplier(item);
        if (tierMultiplier != 1.0 && !stats.isEmpty()) {
            stats.multiplyAll(tierMultiplier);
        }

        // 4. Apply enhancement multiplier
        int enhanceLevel = enhanceManager.getEnhanceLevel(item);
        double enhanceMultiplier = enhanceManager.getStatMultiplier(enhanceLevel);
        if (enhanceMultiplier != 1.0 && !stats.isEmpty()) {
            stats.multiplyAll(enhanceMultiplier);
        }

        // 5. Add socketed gem stats (flat bonus, added after multipliers)
        StatMap gemStats = getSocketStats(item);
        if (!gemStats.isEmpty()) {
            stats.addAll(gemStats);
        }

        return stats;
    }

    /**
     * Retrieves the stat bonuses for a specific item ID from the loaded definitions.
     *
     * @param itemId the RPG item ID
     * @return a StatMap containing the item's stat bonuses (empty if not found)
     */
    public StatMap getItemStats(String itemId) {
        StatMap stats = new StatMap();
        if (itemId == null) {
            return stats;
        }
        ConfigurationSection itemSection = itemDefinitions.get(itemId);
        if (itemSection == null) {
            return stats;
        }

        // Parse stats section
        ConfigurationSection statsSection = itemSection.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String key : statsSection.getKeys(false)) {
                StatType type = parseStatType(key);
                if (type != null) {
                    stats.set(type, statsSection.getDouble(key));
                }
            }
        }

        // Parse element section
        ConfigurationSection elementSection = itemSection.getConfigurationSection("element");
        if (elementSection != null) {
            for (String element : elementSection.getKeys(false)) {
                StatType type = parseStatType(element + "-damage");
                if (type != null) {
                    stats.set(type, elementSection.getDouble(element));
                }
            }
        }

        return stats;
    }

    /**
     * Returns the item definition section for the given item ID.
     *
     * @param itemId the RPG item ID
     * @return the ConfigurationSection, or null if not found
     */
    public ConfigurationSection getItemDefinition(String itemId) {
        return itemDefinitions.get(itemId);
    }

    /**
     * Checks whether the given item ID exists in the loaded definitions.
     *
     * @param itemId the RPG item ID
     * @return true if the item is defined
     */
    public boolean itemExists(String itemId) {
        return itemDefinitions.containsKey(itemId);
    }

    /**
     * Creates an ItemStack for the specified RPG item, with all RPG data
     * stored in the PersistentDataContainer.
     *
     * @param itemId the RPG item ID
     * @return the created ItemStack, or null if the item is not defined
     */
    public ItemStack createItem(String itemId) {
        ConfigurationSection itemSection = itemDefinitions.get(itemId);
        if (itemSection == null) {
            return null;
        }

        String materialName = itemSection.getString("material", "STONE");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Set display name
        String displayName = itemSection.getString("display-name", itemId);
        meta.displayName(TextUtil.noItalic(miniMessage.deserialize(displayName)));

        // Set custom model data
        int customModelData = itemSection.getInt("custom-model-data", -1);
        if (customModelData >= 0) {
            meta.setCustomModelData(customModelData);
        }

        // Store RPG item ID in PDC
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);

        // Store sealed state (new items are sealed by default)
        meta.getPersistentDataContainer().set(sealedKey, PersistentDataType.STRING, "SEALED");

        // Store armor set ID if present
        String armorSet = itemSection.getString("armor-set");
        if (armorSet != null) {
            meta.getPersistentDataContainer().set(armorSetKey, PersistentDataType.STRING, armorSet);
        }

        // Build lore from stats
        List<Component> lore = buildItemLore(itemSection);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Builds the lore (description) for an item from its definition.
     *
     * @param itemSection the item's configuration section
     * @return a list of Component lines for the item lore
     */
    private List<Component> buildItemLore(ConfigurationSection itemSection) {
        List<Component> lore = new ArrayList<>();

        // Level requirement
        int levelReq = itemSection.getInt("level-requirement", 0);
        if (levelReq > 0) {
            lore.add(miniMessage.deserialize("<gray>需要等级: <yellow>" + levelReq));
        }

        // Class requirement
        List<String> classReqs = itemSection.getStringList("class-requirement");
        if (!classReqs.isEmpty()) {
            lore.add(miniMessage.deserialize("<gray>职业要求: <yellow>" + String.join(", ", classReqs)));
        }

        // Stats
        ConfigurationSection statsSection = itemSection.getConfigurationSection("stats");
        if (statsSection != null && !statsSection.getKeys(false).isEmpty()) {
            lore.add(miniMessage.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━"));
            for (String key : statsSection.getKeys(false)) {
                StatType type = parseStatType(key);
                if (type != null) {
                    double value = statsSection.getDouble(key);
                    String suffix = type.isPercentage() ? "%" : "";
                    lore.add(miniMessage.deserialize(type.getColorPrefix() + "+"
                            + (int) value + " " + type.getDisplayName() + suffix));
                }
            }
        }

        // Element damage
        ConfigurationSection elementSection = itemSection.getConfigurationSection("element");
        if (elementSection != null && !elementSection.getKeys(false).isEmpty()) {
            for (String element : elementSection.getKeys(false)) {
                double value = elementSection.getDouble(element);
                lore.add(miniMessage.deserialize("<dark_gray>+" + (int) value + " "
                        + element.substring(0, 1).toUpperCase() + element.substring(1) + " Damage"));
            }
        }

        // Disable Minecraft's default italic rendering on every lore line.
        lore.replaceAll(TextUtil::noItalic);
        return lore;
    }

    /**
     * Returns all loaded RPGItem objects.
     *
     * @return an unmodifiable collection of all RPGItems
     */
    public Collection<RPGItem> getAllItems() {
        return Collections.unmodifiableCollection(rpgItems.values());
    }

    /**
     * Returns all loaded item IDs.
     *
     * @return an unmodifiable set of all item IDs
     */
    public Set<String> getAllItemIds() {
        return Collections.unmodifiableSet(rpgItems.keySet());
    }

    /**
     * Gets the RPGItem for the specified item ID.
     *
     * @param itemId the RPG item ID
     * @return the RPGItem, or null if not found
     */
    public RPGItem getItem(String itemId) {
        if (itemId == null) {
            return null;
        }
        return rpgItems.get(itemId);
    }

    /**
     * Gets all items of a specific type.
     *
     * @param type the ItemType to filter by
     * @return a list of RPGItems matching the type
     */
    public List<RPGItem> getItemsByType(ItemType type) {
        List<RPGItem> result = new ArrayList<>();
        if (type == null) {
            return result;
        }
        for (RPGItem item : rpgItems.values()) {
            if (item.getType() == type) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Gets all items that can be used by the specified class.
     * <p>
     * An item matches if its class requirement is empty (usable by all)
     * or contains the specified class.
     * </p>
     *
     * @param gameClass the class to filter by
     * @return a list of RPGItems usable by the class
     */
    public List<RPGItem> getItemsByClass(GameClass gameClass) {
        List<RPGItem> result = new ArrayList<>();
        if (gameClass == null) {
            return result;
        }
        for (RPGItem item : rpgItems.values()) {
            Set<GameClass> req = item.getClassRequirement();
            if (req == null || req.isEmpty() || req.contains(gameClass)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Creates an ItemStack for the specified RPG item.
     * <p>
     * This is an alias for {@link #createItem(String)}.
     * </p>
     *
     * @param itemId the RPG item ID
     * @return the created ItemStack, or null if the item is not defined
     */
    public ItemStack createItemStack(String itemId) {
        return createItem(itemId);
    }

    /**
     * Checks whether the given ItemStack is an RPG item.
     *
     * @param item the ItemStack to check
     * @return true if the item has an RPG item ID in its PersistentDataContainer
     */
    public boolean isRPGItem(ItemStack item) {
        return getRPGItemId(item) != null;
    }

    /**
     * Parses a config key string into a StatType.
     * <p>
     * Converts kebab-case names (e.g. "physical-attack") to the enum name
     * format (e.g. "PHYSICAL_ATTACK") and resolves the StatType.
     * </p>
     *
     * @param configName the config key (e.g. "physical-attack", "fire-damage")
     * @return the matching StatType, or null if no match
     */
    public static StatType parseStatType(String configName) {
        if (configName == null || configName.isBlank()) {
            return null;
        }
        try {
            return StatType.valueOf(configName.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ==================== Tier System Methods ====================

    /**
     * Gets the tier ID stored in an item's PDC.
     *
     * @param item the item to check
     * @return the tier ID, or null if the item has no tier
     */
    public String getItemTier(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
    }

    /**
     * Gets the tier multiplier for an item.
     * If the item has no tier, returns 1.0 (default/fine tier).
     *
     * @param item the item to check
     * @return the tier multiplier (default 1.0)
     */
    public double getTierMultiplier(ItemStack item) {
        String tierId = getItemTier(item);
        if (tierId == null || tierId.isBlank()) {
            return 1.0;
        }
        TierManager.Tier tier = tierManager.getTier(tierId);
        return tier != null ? tier.multiplier() : 1.0;
    }

    /**
     * Sets the tier on an item and regenerates its lore.
     *
     * @param item   the item to modify
     * @param tierId the tier ID to set, or null to remove tier
     * @return true if successful
     */
    public boolean setItemTier(ItemStack item, String tierId) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (tierId == null || tierId.isBlank()) {
            meta.getPersistentDataContainer().remove(tierKey);
        } else {
            meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tierId.toLowerCase());
        }
        item.setItemMeta(meta);
        regenerateLore(item);
        return true;
    }

    /**
     * Randomizes the tier on an item based on weight and regenerates its lore.
     * If the item already has a tier, it will be replaced.
     *
     * @param item the item to randomize
     * @return the tier ID that was assigned, or null if no tiers defined
     */
    public String randomizeTier(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        TierManager.Tier tier = tierManager.randomTier();
        if (tier == null) {
            return null;
        }
        setItemTier(item, tier.id());
        return tier.id();
    }

    /**
     * Returns the tier manager instance.
     *
     * @return the TierManager
     */
    public TierManager getTierManager() {
        return tierManager;
    }

    // ---- Tier Upgrade Item Methods ----

    /**
     * Checks whether an item is a tier upgrade consumable.
     *
     * @param item the item to check
     * @return true if the item is a tier upgrade item
     */
    public boolean isTierUpgradeItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String val = meta.getPersistentDataContainer().get(tierUpgradeKey, PersistentDataType.STRING);
        return val != null && val.equals("true");
    }

    /**
     * Creates a tier upgrade consumable item.
     * Right-clicking with this item attempts to upgrade the tier
     * of the item in the main hand.
     *
     * @return the created tier upgrade ItemStack
     */
    public ItemStack createTierUpgradeItem() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.displayName(TextUtil.noItalic(miniMessage.deserialize("<gradient:#ffaa00:#ff6600>品级修改符</gradient>")));

        List<Component> lore = new ArrayList<>();
        lore.add(miniMessage.deserialize("<gray>右键使用，提升主手装备的品级"));
        lore.add(miniMessage.deserialize("<gray>有一定概率成功升级"));
        lore.add(miniMessage.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━"));
        lore.add(miniMessage.deserialize("<yellow>✦ 品级提升道具"));
        lore.replaceAll(TextUtil::noItalic);
        meta.lore(lore);

        meta.getPersistentDataContainer().set(tierUpgradeKey, PersistentDataType.STRING, "true");
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Attempts to upgrade the tier of the target item using a tier upgrade consumable.
     * Consumes one upgrade item regardless of success or failure.
     *
     * @param targetItem the item whose tier should be upgraded
     * @return the upgrade result (success, max tier, not rpg item, etc.)
     */
    public TierUpgradeResult upgradeItemTier(ItemStack targetItem) {
        if (targetItem == null || targetItem.getType() == Material.AIR) {
            return TierUpgradeResult.NOT_RPG_ITEM;
        }
        if (!isRPGItem(targetItem)) {
            return TierUpgradeResult.NOT_RPG_ITEM;
        }

        String currentTierId = getItemTier(targetItem);
        if (currentTierId == null || currentTierId.isBlank()) {
            // No tier yet, set to the first tier (rough) as a starting point
            TierManager.Tier firstTier = tierManager.getTiersOrdered().isEmpty()
                    ? null : tierManager.getTiersOrdered().get(0);
            if (firstTier != null) {
                setItemTier(targetItem, firstTier.id());
                return TierUpgradeResult.SUCCESS;
            }
            return TierUpgradeResult.FAILED;
        }

        // Check if already at max tier
        TierManager.Tier nextTier = tierManager.getNextTier(currentTierId);
        if (nextTier == null) {
            return TierUpgradeResult.MAX_TIER;
        }

        // Get upgrade chance
        double chance = tierManager.getUpgradeChance(currentTierId);

        // Roll for success
        double roll = Math.random();
        if (roll < chance) {
            // Success: upgrade tier
            setItemTier(targetItem, nextTier.id());
            return TierUpgradeResult.SUCCESS;
        } else {
            return TierUpgradeResult.FAILED;
        }
    }

    /**
     * Result of a tier upgrade attempt.
     */
    public enum TierUpgradeResult {
        SUCCESS,
        FAILED,
        MAX_TIER,
        NOT_RPG_ITEM
    }

    // ==================== Enhance System Methods ====================

    /**
     * Returns the enhance manager instance.
     *
     * @return the EnhanceManager
     */
    public EnhanceManager getEnhanceManager() {
        return enhanceManager;
    }

    // ==================== Gem Socket System Methods ====================

    /**
     * Returns the gem manager instance.
     *
     * @return the GemManager
     */
    public GemManager getGemManager() {
        return gemManager;
    }

    /**
     * Gets the list of socketed gem IDs from an item.
     * Always returns a list of 8 elements (null/empty string for empty slots).
     *
     * @param item the item to check
     * @return a list of 8 gem IDs (or empty strings for empty slots)
     */
    public List<String> getSockets(ItemStack item) {
        List<String> result = new ArrayList<>();
        // Initialize with 8 empty slots
        for (int i = 0; i < 8; i++) {
            result.add("");
        }
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return result;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return result;
        }
        String socketsStr = meta.getPersistentDataContainer().get(socketsKey, PersistentDataType.STRING);
        if (socketsStr == null || socketsStr.isBlank()) {
            return result;
        }
        String[] parts = socketsStr.split(";", -1);
        for (int i = 0; i < Math.min(parts.length, 8); i++) {
            result.set(i, parts[i]);
        }
        return result;
    }

    /**
     * Sets the socketed gem IDs on an item.
     *
     * @param item    the item to modify
     * @param sockets the list of gem IDs (should have 8 elements)
     * @return true if successful
     */
    public boolean setSockets(ItemStack item, List<String> sockets) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (sockets == null || sockets.isEmpty()) {
            meta.getPersistentDataContainer().remove(socketsKey);
            item.setItemMeta(meta);
            return true;
        }
        // Build semicolon-separated string, ensure exactly 8 slots
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(";");
            }
            if (i < sockets.size() && sockets.get(i) != null) {
                sb.append(sockets.get(i));
            }
        }
        String socketsStr = sb.toString();
        if (socketsStr.replace(";", "").isBlank()) {
            meta.getPersistentDataContainer().remove(socketsKey);
        } else {
            meta.getPersistentDataContainer().set(socketsKey, PersistentDataType.STRING, socketsStr);
        }
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Gets the total stat bonuses from all socketed gems on an item.
     *
     * @param item the item to check
     * @return a StatMap with the combined gem stats
     */
    public StatMap getSocketStats(ItemStack item) {
        StatMap stats = new StatMap();
        List<String> sockets = getSockets(item);
        for (String gemId : sockets) {
            if (gemId == null || gemId.isBlank()) {
                continue;
            }
            GemManager.Gem gem = gemManager.getGem(gemId);
            if (gem != null) {
                stats.addAll(gem.stats());
            }
        }
        return stats;
    }

    /**
     * Sockets a gem into the specified slot.
     *
     * @param item      the item to socket into
     * @param slotIndex the slot index (0-7)
     * @param gemId     the gem ID to socket
     * @return true if successful, false if slot is occupied or invalid
     */
    public boolean socketGem(ItemStack item, int slotIndex, String gemId) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (slotIndex < 0 || slotIndex >= 8) {
            return false;
        }
        if (gemId == null || gemId.isBlank()) {
            return false;
        }
        if (!isRPGItem(item)) {
            return false;
        }
        List<String> sockets = getSockets(item);
        // Check if slot is already occupied
        String current = sockets.get(slotIndex);
        if (current != null && !current.isBlank()) {
            return false; // Slot already has a gem
        }
        sockets.set(slotIndex, gemId);
        return setSockets(item, sockets);
    }

    /**
     * Removes a gem from the specified slot.
     *
     * @param item      the item to unsocket from
     * @param slotIndex the slot index (0-7)
     * @return the removed gem ID, or null if slot was empty or invalid
     */
    public String unsocketGem(ItemStack item, int slotIndex) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        if (slotIndex < 0 || slotIndex >= 8) {
            return null;
        }
        List<String> sockets = getSockets(item);
        String gemId = sockets.get(slotIndex);
        if (gemId == null || gemId.isBlank()) {
            return null;
        }
        sockets.set(slotIndex, "");
        setSockets(item, sockets);
        return gemId;
    }

    // ==================== Item Modification Methods ====================

    /**
     * Gives the held item an RPG item ID and adds a stat value.
     * This effectively converts a CE (or any) item into an RPG item.
     *
     * @param player   the player holding the item
     * @param itemId   the new RPG item ID
     * @param statType the stat type to add
     * @param value    the stat value
     * @return the modified ItemStack, or null if the player has no item in hand
     */
    public ItemStack addStatToHeldItem(Player player, String itemId, StatType statType, double value) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }
        // Set the RPG item ID
        setRPGItemId(item, itemId);
        // Add the stat
        modifyItemStat(item, statType, value, true);
        return item;
    }

    /**
     * Sets the RPG item ID on an ItemStack via PersistentDataContainer.
     *
     * @param item   the item to modify
     * @param itemId the RPG item ID to set
     * @return true if successful
     */
    public boolean setRPGItemId(ItemStack item, String itemId) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, itemId);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Modifies a single stat on an item.
     *
     * @param item     the item to modify
     * @param statType the stat type
     * @param value    the value to set or add
     * @param add      true to add to existing value, false to set
     * @return true if successful
     */
    public boolean modifyItemStat(ItemStack item, StatType statType, double value, boolean add) {
        if (item == null || item.getType() == Material.AIR || statType == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        NamespacedKey statKey = getStatKey(statType);
        var pdc = meta.getPersistentDataContainer();
        if (add) {
            double current = pdc.getOrDefault(statKey, PersistentDataType.DOUBLE, 0.0);
            pdc.set(statKey, PersistentDataType.DOUBLE, current + value);
        } else {
            pdc.set(statKey, PersistentDataType.DOUBLE, value);
        }
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Adds a bound skill to an item.
     *
     * @param item    the item to modify
     * @param skillId the skill ID to add
     * @return true if successful (false if already present)
     */
    public boolean addSkillToItem(ItemStack item, String skillId) {
        if (item == null || item.getType() == Material.AIR || skillId == null || skillId.isBlank()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Set<String> skills = getSkillsFromPDC(meta);
        if (skills.contains(skillId)) {
            return false;
        }
        skills.add(skillId);
        saveSkillsToPDC(meta, skills);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Removes a bound skill from an item.
     *
     * @param item    the item to modify
     * @param skillId the skill ID to remove
     * @return true if successful (false if not present)
     */
    public boolean removeSkillFromItem(ItemStack item, String skillId) {
        if (item == null || item.getType() == Material.AIR || skillId == null || skillId.isBlank()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Set<String> skills = getSkillsFromPDC(meta);
        if (!skills.contains(skillId)) {
            return false;
        }
        skills.remove(skillId);
        saveSkillsToPDC(meta, skills);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Adds a special effect to an item.
     *
     * @param item         the item to modify
     * @param effectText   the special effect text (name|description format)
     * @return true if successful
     */
    public boolean addSpecialEffect(ItemStack item, String effectText) {
        if (item == null || item.getType() == Material.AIR || effectText == null || effectText.isBlank()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        List<String> effects = getSpecialEffectsFromPDC(meta);
        effects.add(effectText);
        saveSpecialEffectsToPDC(meta, effects);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Removes a special effect from an item by index (1-based).
     *
     * @param item   the item to modify
     * @param index  the 1-based index of the effect to remove
     * @return true if successful (false if index out of range)
     */
    public boolean removeSpecialEffect(ItemStack item, int index) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        List<String> effects = getSpecialEffectsFromPDC(meta);
        if (index < 1 || index > effects.size()) {
            return false;
        }
        effects.remove(index - 1);
        saveSpecialEffectsToPDC(meta, effects);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Clears all special effects from an item.
     *
     * @param item the item to modify
     * @return true if successful
     */
    public boolean clearSpecialEffects(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        meta.getPersistentDataContainer().remove(specialEffectsKey);
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Regenerates the item's lore based on RPG data stored in the
     * PersistentDataContainer. Builds an ephemeral RPGItem from the PDC
     * data and uses ItemLoreBuilder to generate the lore lines.
     * Also updates the display name with enhancement level prefix.
     *
     * @param item the item whose lore should be regenerated
     */
    public void regenerateLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        RPGItem rpgItem = buildRPGItemFromPDC(item);
        if (rpgItem == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Update display name with enhancement prefix
        updateItemDisplayName(item, rpgItem);

        // Rebuild lore
        List<Component> lore = loreBuilder.buildLore(rpgItem);
        if (!lore.isEmpty()) {
            lore.replaceAll(TextUtil::noItalic);
            meta.lore(lore);
        }
        item.setItemMeta(meta);
    }

    /**
     * Updates the item's display name to include the enhancement level prefix.
     * The original name is derived from the current display name (stripping
     * any existing enhancement prefix) or from the item definition.
     *
     * @param item    the item to update
     * @param rpgItem the RPGItem data for this item
     */
    private void updateItemDisplayName(ItemStack item, RPGItem rpgItem) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        int enhanceLevel = enhanceManager.getEnhanceLevel(item);
        String originalName = getOriginalDisplayName(item, rpgItem);

        if (enhanceLevel > 0) {
            String prefix = "<yellow>[+" + enhanceLevel + "] ";
            meta.displayName(TextUtil.noItalic(miniMessage.deserialize(prefix + originalName)));
        } else {
            meta.displayName(TextUtil.noItalic(miniMessage.deserialize(originalName)));
        }
        item.setItemMeta(meta);
    }

    /**
     * Gets the original display name of an item without the enhancement prefix.
     * Tries to strip existing "+N" prefix first, then falls back to the
     * config definition name.
     *
     * @param item    the ItemStack
     * @param rpgItem the RPGItem data
     * @return the original display name string (may contain MiniMessage tags)
     */
    private String getOriginalDisplayName(ItemStack item, RPGItem rpgItem) {
        // Try to get from item definition first
        String itemId = getRPGItemId(item);
        if (itemId != null) {
            ConfigurationSection def = itemDefinitions.get(itemId);
            if (def != null) {
                String defName = def.getString("display-name");
                if (defName != null && !defName.isBlank()) {
                    return defName;
                }
            }
        }

        // Fall back to current display name, stripping enhancement prefix
        if (item.hasItemMeta() && item.getItemMeta() != null
                && item.getItemMeta().hasDisplayName()) {
            Component displayName = item.getItemMeta().displayName();
            if (displayName != null) {
                // Convert component to plain string and strip prefix pattern
                String plain = MiniMessage.miniMessage().serialize(displayName);
                // Remove "[+N] " prefix pattern
                String stripped = plain.replaceFirst("^\\[\\+\\d+\\]\\s*", "");
                if (!stripped.isBlank()) {
                    return stripped;
                }
            }
        }

        // Last fallback
        return rpgItem != null && rpgItem.getDisplayName() != null
                ? rpgItem.getDisplayName() : item.getType().name();
    }

    /**
     * Sets the level requirement on an item.
     *
     * @param item  the item to modify
     * @param level the required level (0 or negative to remove)
     * @return true if successful
     */
    public boolean setItemLevelRequirement(ItemStack item, int level) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (level <= 0) {
            meta.getPersistentDataContainer().remove(levelReqKey);
        } else {
            meta.getPersistentDataContainer().set(levelReqKey, PersistentDataType.INTEGER, level);
        }
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Sets the class requirement on an item.
     *
     * @param item    the item to modify
     * @param classes the set of allowed classes (empty set to remove requirement)
     * @return true if successful
     */
    public boolean setItemClassRequirement(ItemStack item, Set<GameClass> classes) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (classes == null || classes.isEmpty()) {
            meta.getPersistentDataContainer().remove(classReqKey);
        } else {
            String classStr = classes.stream()
                    .map(GameClass::name)
                    .collect(Collectors.joining(","));
            meta.getPersistentDataContainer().set(classReqKey, PersistentDataType.STRING, classStr);
        }
        item.setItemMeta(meta);
        return true;
    }

    // ==================== Seal System Methods ====================

    /**
     * Checks whether the given item is sealed (tradeable).
     * <p>
     * An item is considered sealed if it is an RPG item and its
     * {@code rpg_sealed} PDC value is either missing or equals "SEALED".
     *
     * @param item the item to check
     * @return true if the item is sealed (or not an RPG item)
     */
    public boolean isItemSealed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return true;
        }
        String state = meta.getPersistentDataContainer().get(sealedKey, PersistentDataType.STRING);
        return state == null || state.equalsIgnoreCase("SEALED");
    }

    /**
     * Checks whether the given item is unsealed (bound).
     * <p>
     * An item is considered unsealed/bound if it is an RPG item and its
     * {@code rpg_sealed} PDC value equals "UNSEALED".
     *
     * @param item the item to check
     * @return true if the item is unsealed (bound)
     */
    public boolean isItemUnsealed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        String state = meta.getPersistentDataContainer().get(sealedKey, PersistentDataType.STRING);
        return state != null && state.equalsIgnoreCase("UNSEALED");
    }

    /**
     * Unseals (binds) an item. Sets the {@code rpg_sealed} PDC value to
     * "UNSEALED" and regenerates the item's lore.
     *
     * @param item the item to unseal
     */
    public void unsealItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(sealedKey, PersistentDataType.STRING, "UNSEALED");
        item.setItemMeta(meta);
        regenerateLore(item);
    }

    /**
     * Seals (unbinds) an item. Sets the {@code rpg_sealed} PDC value to
     * "SEALED" and regenerates the item's lore.
     *
     * @param item the item to seal
     */
    public void sealItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(sealedKey, PersistentDataType.STRING, "SEALED");
        item.setItemMeta(meta);
        regenerateLore(item);
    }

    /**
     * Checks whether the given item is a seal scroll consumable.
     *
     * @param item the item to check
     * @return true if the item is a seal scroll
     */
    public boolean isSealScroll(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        Byte val = meta.getPersistentDataContainer().get(sealScrollKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    /**
     * Creates a seal scroll consumable item.
     * <p>
     * The seal scroll is used to re-seal an unsealed (bound) RPG item,
     * restoring its tradeability.
     *
     * @param amount the stack size
     * @return the created seal scroll ItemStack
     */
    public ItemStack createSealScroll(int amount) {
        FileConfiguration mainConfig = plugin.getConfig();
        String materialName = mainConfig.getString("seal.seal-scroll.material", "PAPER");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            material = Material.PAPER;
        }

        ItemStack item = new ItemStack(material);
        item.setAmount(Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        // Display name
        String displayName = mainConfig.getString("seal.seal-scroll.display-name",
                "<light_purple>封券</light_purple>");
        meta.displayName(TextUtil.noItalic(miniMessage.deserialize(displayName)));

        // Custom model data
        int customModelData = mainConfig.getInt("seal.seal-scroll.custom-model-data", 1001);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        }

        // Lore
        List<String> loreLines = mainConfig.getStringList("seal.seal-scroll.lore");
        if (loreLines.isEmpty()) {
            loreLines.add("<gray>用于重新封印已解封的装备</gray>");
            loreLines.add("<gray>使用方法：手持封券，另一只手持装备，右键使用</gray>");
        }
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(TextUtil.noItalic(miniMessage.deserialize(line)));
        }
        meta.lore(lore);

        // PDC marker
        meta.getPersistentDataContainer().set(sealScrollKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    // ==================== PDC Helper Methods ====================


    /**
     * Builds a NamespacedKey for a specific stat type.
     *
     * @param statType the stat type
     * @return the NamespacedKey for storing this stat's value in PDC
     */
    private NamespacedKey getStatKey(StatType statType) {
        return new NamespacedKey(plugin, STAT_KEY_PREFIX + statType.name().toLowerCase());
    }

    /**
     * Reads the set of bound skills from an ItemMeta's PDC.
     *
     * @param meta the item meta
     * @return a mutable set of skill IDs (may be empty)
     */
    private Set<String> getSkillsFromPDC(ItemMeta meta) {
        String skillsStr = meta.getPersistentDataContainer().get(skillsKey, PersistentDataType.STRING);
        if (skillsStr == null || skillsStr.isBlank()) {
            return new HashSet<>();
        }
        return new HashSet<>(Arrays.asList(skillsStr.split(",")));
    }

    /**
     * Saves a set of skill IDs to an ItemMeta's PDC as a comma-separated string.
     *
     * @param meta   the item meta to modify
     * @param skills the set of skill IDs
     */
    private void saveSkillsToPDC(ItemMeta meta, Set<String> skills) {
        if (skills == null || skills.isEmpty()) {
            meta.getPersistentDataContainer().remove(skillsKey);
            return;
        }
        String skillsStr = String.join(",", skills);
        meta.getPersistentDataContainer().set(skillsKey, PersistentDataType.STRING, skillsStr);
    }

    /**
     * Reads the list of special effects from an ItemMeta's PDC.
     * Effects are stored as a semicolon-separated string.
     *
     * @param meta the item meta
     * @return a mutable list of special effect strings (may be empty)
     */
    private List<String> getSpecialEffectsFromPDC(ItemMeta meta) {
        String effectsStr = meta.getPersistentDataContainer().get(specialEffectsKey, PersistentDataType.STRING);
        if (effectsStr == null || effectsStr.isBlank()) {
            return new ArrayList<>();
        }
        List<String> effects = new ArrayList<>();
        for (String part : effectsStr.split(";")) {
            if (!part.isBlank()) {
                effects.add(part);
            }
        }
        return effects;
    }

    /**
     * Saves a list of special effects to an ItemMeta's PDC as a
     * semicolon-separated string.
     *
     * @param meta    the item meta to modify
     * @param effects the list of special effect strings
     */
    private void saveSpecialEffectsToPDC(ItemMeta meta, List<String> effects) {
        if (effects == null || effects.isEmpty()) {
            meta.getPersistentDataContainer().remove(specialEffectsKey);
            return;
        }
        String effectsStr = String.join(";", effects);
        meta.getPersistentDataContainer().set(specialEffectsKey, PersistentDataType.STRING, effectsStr);
    }

    /**
     * Builds an RPGItem object from the RPG data stored in an item's
     * PersistentDataContainer. This is used for lore regeneration and
     * for reading item stats that may have been modified at runtime.
     *
     * @param item the ItemStack to read from
     * @return an RPGItem populated from PDC data, or null if not an RPG item
     */
    public RPGItem buildRPGItemFromPDC(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        var pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(itemIdKey, PersistentDataType.STRING);
        if (itemId == null) {
            return null;
        }

        RPGItem rpgItem = new RPGItem(itemId);

        // Display name
        if (meta.hasDisplayName()) {
            rpgItem.setDisplayName(meta.displayName().toString());
        }

        // Armor set
        String armorSet = pdc.get(armorSetKey, PersistentDataType.STRING);
        if (armorSet != null) {
            rpgItem.setArmorSetId(armorSet);
        }

        // Level requirement
        Integer levelReq = pdc.get(levelReqKey, PersistentDataType.INTEGER);
        if (levelReq != null) {
            rpgItem.setLevelRequirement(levelReq);
        }

        // Class requirement
        String classStr = pdc.get(classReqKey, PersistentDataType.STRING);
        if (classStr != null && !classStr.isBlank()) {
            Set<GameClass> classes = new HashSet<>();
            for (String className : classStr.split(",")) {
                GameClass gc = GameClass.fromString(className.trim());
                if (gc != null) {
                    classes.add(gc);
                }
            }
            rpgItem.setClassRequirement(classes);
        }

        // Skills
        Set<String> skills = getSkillsFromPDC(meta);
        if (!skills.isEmpty()) {
            rpgItem.setSkills(new ArrayList<>(skills));
        }

        // Special effects
        List<String> specialEffects = getSpecialEffectsFromPDC(meta);
        if (!specialEffects.isEmpty()) {
            rpgItem.setSpecialEffects(specialEffects);
        }

        // Lore template
        String loreTemplate = pdc.get(loreTemplateKey, PersistentDataType.STRING);
        if (loreTemplate != null && !loreTemplate.isBlank()) {
            rpgItem.setLoreTemplate(loreTemplate);
        }

        // Tier
        String tierId = pdc.get(tierKey, PersistentDataType.STRING);
        if (tierId != null && !tierId.isBlank()) {
            rpgItem.setTier(tierId);
        }

        // Enhancement level
        Integer enhanceLvl = pdc.get(enhanceLevelKey, PersistentDataType.INTEGER);
        if (enhanceLvl != null) {
            rpgItem.setEnhanceLevel(enhanceLvl);
        }

        // Sealed state (default SEALED if key missing)
        String sealedState = pdc.get(sealedKey, PersistentDataType.STRING);
        rpgItem.setSealed(sealedState == null || sealedState.equalsIgnoreCase("SEALED"));

        // Sockets
        String socketsStr = pdc.get(socketsKey, PersistentDataType.STRING);
        if (socketsStr != null && !socketsStr.isBlank()) {
            List<String> sockets = new ArrayList<>();
            String[] parts = socketsStr.split(";", -1);
            for (int i = 0; i < 8; i++) {
                if (i < parts.length) {
                    sockets.add(parts[i]);
                } else {
                    sockets.add("");
                }
            }
            rpgItem.setSockets(sockets);
        }

        // Stats - iterate over all StatType values and check PDC
        StatMap statMap = new StatMap();
        for (StatType statType : StatType.values()) {
            NamespacedKey statKey = getStatKey(statType);
            Double value = pdc.get(statKey, PersistentDataType.DOUBLE);
            if (value != null) {
                statMap.set(statType, value);
            }
        }
        rpgItem.setStats(statMap);

        // If the item also exists in definitions, merge in data that may
        // not be stored in PDC (e.g. elements from config)
        RPGItem defined = rpgItems.get(itemId);
        if (defined != null) {
            if (rpgItem.getStats().isEmpty() && !defined.getStats().isEmpty()) {
                rpgItem.setStats(defined.getStats().clone());
            }
            if (!defined.getElements().isEmpty()) {
                rpgItem.setElements(new HashMap<>(defined.getElements()));
            }
            if (rpgItem.getLevelRequirement() == 0 && defined.getLevelRequirement() > 0) {
                rpgItem.setLevelRequirement(defined.getLevelRequirement());
            }
            if (rpgItem.getClassRequirement().isEmpty() && !defined.getClassRequirement().isEmpty()) {
                rpgItem.setClassRequirement(new HashSet<>(defined.getClassRequirement()));
            }
            if (rpgItem.getSkills().isEmpty() && !defined.getSkills().isEmpty()) {
                rpgItem.setSkills(new ArrayList<>(defined.getSkills()));
            }
            if (rpgItem.getSpecialEffects().isEmpty() && defined.hasSpecialEffects()) {
                rpgItem.setSpecialEffects(new ArrayList<>(defined.getSpecialEffects()));
            }
            if (rpgItem.getArmorSetId() == null && defined.getArmorSetId() != null) {
                rpgItem.setArmorSetId(defined.getArmorSetId());
            }
            if ("common".equals(rpgItem.getLoreTemplate())
                    && !"common".equals(defined.getLoreTemplate())) {
                rpgItem.setLoreTemplate(defined.getLoreTemplate());
            }
        }

        return rpgItem;
    }

    /**
     * Gets the level requirement stored in an item's PDC.
     *
     * @param item the item to check
     * @return the level requirement, or 0 if not set
     */
    public int getItemLevelRequirement(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer val = meta.getPersistentDataContainer().get(levelReqKey, PersistentDataType.INTEGER);
        return val != null ? val : 0;
    }

    /**
     * Gets the class requirement stored in an item's PDC.
     *
     * @param item the item to check
     * @return the set of required classes (empty if none)
     */
    public Set<GameClass> getItemClassRequirement(ItemStack item) {
        Set<GameClass> result = new HashSet<>();
        if (item == null || !item.hasItemMeta()) {
            return result;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return result;
        String classStr = meta.getPersistentDataContainer().get(classReqKey, PersistentDataType.STRING);
        if (classStr == null || classStr.isBlank()) {
            return result;
        }
        for (String className : classStr.split(",")) {
            GameClass gc = GameClass.fromString(className.trim());
            if (gc != null) {
                result.add(gc);
            }
        }
        return result;
    }

    /**
     * Gets the set of bound skill IDs from an item's PDC.
     *
     * @param item the item to check
     * @return the set of skill IDs (empty if none)
     */
    public Set<String> getItemSkills(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new HashSet<>();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new HashSet<>();
        return getSkillsFromPDC(meta);
    }

    /**
     * Gets the list of special effects from an item's PDC.
     *
     * @param item the item to check
     * @return the list of special effect strings (empty if none)
     */
    public List<String> getItemSpecialEffects(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new ArrayList<>();
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return new ArrayList<>();
        return getSpecialEffectsFromPDC(meta);
    }

    /**
     * Gets a specific stat value from an item's PDC.
     *
     * @param item     the item to check
     * @param statType the stat type
     * @return the stat value, or 0 if not set
     */
    public double getItemStat(ItemStack item, StatType statType) {
        if (item == null || !item.hasItemMeta() || statType == null) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Double val = meta.getPersistentDataContainer().get(getStatKey(statType), PersistentDataType.DOUBLE);
        return val != null ? val : 0;
    }

    /**
     * Sets the lore template on an item's PDC.
     *
     * @param item     the item to modify
     * @param template the lore template name (e.g. "rare", "legendary")
     * @return true if successful
     */
    public boolean setLoreTemplate(ItemStack item, String template) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        if (template == null || template.isBlank()) {
            meta.getPersistentDataContainer().remove(loreTemplateKey);
        } else {
            meta.getPersistentDataContainer().set(loreTemplateKey, PersistentDataType.STRING, template);
        }
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Saves a custom item to the items.yml configuration file.
     * <p>
     * Writes the item's display name, type, material, lore template,
     * stats, skills, level requirement, and class requirement to the
     * {@code items.<id>} section in items.yml, then saves the file to disk
     * and reloads the ItemManager cache.
     * </p>
     *
     * @param item     the RPGItem containing the item definition
     * @param baseItem the base ItemStack (used to get the material)
     * @return true if saved successfully
     */
    public boolean saveCustomItem(RPGItem item, ItemStack baseItem) {
        if (item == null || item.getId() == null || item.getId().isBlank()) {
            return false;
        }

        var configManager = plugin.getConfigManager();
        if (configManager == null) {
            return false;
        }

        var itemsConfig = configManager.getConfig("items");
        if (itemsConfig == null) {
            plugin.getLogger().warning("Cannot save custom item: items config not loaded.");
            return false;
        }

        String path = "items." + item.getId();

        // Display name
        if (item.getDisplayName() != null && !item.getDisplayName().isBlank()) {
            itemsConfig.set(path + ".display-name", item.getDisplayName());
        }

        // Type
        if (item.getType() != null) {
            itemsConfig.set(path + ".type", item.getType().name());
        }

        // Material
        String material = item.getMaterial();
        if ((material == null || material.isBlank()) && baseItem != null) {
            material = baseItem.getType().name();
        }
        if (material != null && !material.isBlank()) {
            itemsConfig.set(path + ".material", material);
        }

        // Lore template
        if (item.getLoreTemplate() != null && !item.getLoreTemplate().isBlank()) {
            itemsConfig.set(path + ".lore-template", item.getLoreTemplate());
        }

        // Level requirement
        if (item.getLevelRequirement() > 0) {
            itemsConfig.set(path + ".level-requirement", item.getLevelRequirement());
        }

        // Class requirement
        if (item.getClassRequirement() != null && !item.getClassRequirement().isEmpty()) {
            List<String> classNames = item.getClassRequirement().stream()
                    .map(GameClass::name)
                    .toList();
            itemsConfig.set(path + ".class-requirement", classNames);
        }

        // Stats
        if (item.getStats() != null && !item.getStats().isEmpty()) {
            for (var entry : item.getStats().entrySet()) {
                String statKey = entry.getKey().name().toLowerCase().replace("_", "-");
                itemsConfig.set(path + ".stats." + statKey, entry.getValue());
            }
        }

        // Skills
        if (item.getSkills() != null && !item.getSkills().isEmpty()) {
            itemsConfig.set(path + ".skills", item.getSkills());
        }

        // Armor set
        if (item.getArmorSetId() != null && !item.getArmorSetId().isBlank()) {
            itemsConfig.set(path + ".armor-set", item.getArmorSetId());
        }

        // Custom model data
        if (item.getCustomModelData() > 0) {
            itemsConfig.set(path + ".custom-model-data", item.getCustomModelData());
        }

        // Weapon type / armor type
        if (item.getWeaponType() != null && !item.getWeaponType().isBlank()) {
            itemsConfig.set(path + ".weapon-type", item.getWeaponType());
        }
        if (item.getArmorType() != null && !item.getArmorType().isBlank()) {
            itemsConfig.set(path + ".armor-type", item.getArmorType());
        }

        // Description
        if (item.getDescription() != null && !item.getDescription().isEmpty()) {
            itemsConfig.set(path + ".description", item.getDescription());
        }

        // Special effects
        if (item.getSpecialEffects() != null && !item.getSpecialEffects().isEmpty()) {
            itemsConfig.set(path + ".special-effects", item.getSpecialEffects());
        }

        // Save to disk
        boolean saved = configManager.saveConfig("items");
        if (!saved) {
            plugin.getLogger().warning("Failed to save custom item '" + item.getId() + "' to disk.");
            return false;
        }

        // Reload item definitions
        load();

        plugin.getLogger().info("Saved custom item '" + item.getId() + "' to items.yml.");
        return true;
    }
}
