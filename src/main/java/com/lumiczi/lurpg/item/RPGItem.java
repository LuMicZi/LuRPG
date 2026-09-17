package com.lumiczi.lurpg.item;

import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the data model for an RPG item.
 * <p>
 * An RPGItem is a template definition loaded from configuration (items.yml
 * or CraftEngine rpg fields). It holds all RPG-specific metadata such as
 * stats, elemental affinities, bound skills, level/class requirements,
 * and lore template information.
 * <p>
 * The actual Bukkit {@link org.bukkit.inventory.ItemStack} is created by
 * {@link ItemManager#createItemStack(String)}, which injects this data
 * into the item's PersistentDataContainer.
 */
public class RPGItem {

    private String id;
    private String displayName;
    private ItemType type;
    private String material;
    private int levelRequirement;
    private Set<GameClass> classRequirement = new HashSet<>();
    private StatMap stats = new StatMap();
    private Map<String, Double> elements = new LinkedHashMap<>();
    private List<String> skills = new ArrayList<>();
    private String loreTemplate = "common";
    private int customModelData;
    private String weaponType;
    private String armorType;
    private String armorSetId;
    private List<String> description = new ArrayList<>();
    private List<String> specialEffects = new ArrayList<>();
    private String tier; // 品级ID，null表示无品级（默认1.0倍率）
    private int enhanceLevel; // 强化等级，0表示未强化
    private List<String> sockets = new ArrayList<>(); // 8个宝石槽位，每个为宝石ID或null/空字符串
    private boolean sealed = true; // 封印状态，true=已封印（可交易），false=已解封（绑定）

    /**
     * Default constructor.
     */
    public RPGItem() {
    }

    /**
     * Constructs an RPGItem with the given ID.
     *
     * @param id the unique item identifier
     */
    public RPGItem(String id) {
        this.id = id;
    }

    // ---- Getters and Setters ----

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public int getLevelRequirement() {
        return levelRequirement;
    }

    public void setLevelRequirement(int levelRequirement) {
        this.levelRequirement = levelRequirement;
    }

    public Set<GameClass> getClassRequirement() {
        return classRequirement;
    }

    public void setClassRequirement(Set<GameClass> classRequirement) {
        this.classRequirement = classRequirement != null ? classRequirement : new HashSet<>();
    }

    public StatMap getStats() {
        return stats;
    }

    public void setStats(StatMap stats) {
        this.stats = stats != null ? stats : new StatMap();
    }

    public Map<String, Double> getElements() {
        return elements;
    }

    public void setElements(Map<String, Double> elements) {
        this.elements = elements != null ? elements : new LinkedHashMap<>();
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills != null ? skills : new ArrayList<>();
    }

    public String getLoreTemplate() {
        return loreTemplate;
    }

    public void setLoreTemplate(String loreTemplate) {
        this.loreTemplate = loreTemplate;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    public void setCustomModelData(int customModelData) {
        this.customModelData = customModelData;
    }

    public String getWeaponType() {
        return weaponType;
    }

    public void setWeaponType(String weaponType) {
        this.weaponType = weaponType;
    }

    public String getArmorType() {
        return armorType;
    }

    public void setArmorType(String armorType) {
        this.armorType = armorType;
    }

    public String getArmorSetId() {
        return armorSetId;
    }

    public void setArmorSetId(String armorSetId) {
        this.armorSetId = armorSetId;
    }

    public List<String> getDescription() {
        return description;
    }

    public void setDescription(List<String> description) {
        this.description = description != null ? description : new ArrayList<>();
    }

    public List<String> getSpecialEffects() {
        return specialEffects;
    }

    public void setSpecialEffects(List<String> specialEffects) {
        this.specialEffects = specialEffects != null ? specialEffects : new ArrayList<>();
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public int getEnhanceLevel() {
        return enhanceLevel;
    }

    public void setEnhanceLevel(int enhanceLevel) {
        this.enhanceLevel = enhanceLevel;
    }

    public List<String> getSockets() {
        return sockets;
    }

    public void setSockets(List<String> sockets) {
        this.sockets = sockets != null ? sockets : new ArrayList<>();
    }

    public boolean isSealed() {
        return sealed;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    /**
     * Gets the number of filled (non-empty) gem sockets.
     *
     * @return the count of gems socketed
     */
    public int getFilledSocketCount() {
        int count = 0;
        for (String s : sockets) {
            if (s != null && !s.isBlank()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks whether the item has any socketed gems.
     *
     * @return true if at least one socket has a gem
     */
    public boolean hasSockets() {
        return getFilledSocketCount() > 0;
    }

    // ---- Convenience queries ----

    /**
     * Checks whether this item belongs to an armor set.
     *
     * @return true if an armor set ID is defined
     */
    public boolean hasArmorSet() {
        return armorSetId != null && !armorSetId.isBlank();
    }

    /**
     * Checks whether this item has any bound skills.
     *
     * @return true if the skills list is non-empty
     */
    public boolean hasSkills() {
        return skills != null && !skills.isEmpty();
    }

    /**
     * Checks whether this item has any elemental affinities.
     *
     * @return true if the elements map is non-empty
     */
    public boolean hasElements() {
        return elements != null && !elements.isEmpty();
    }

    /**
     * Checks whether this item has a description.
     *
     * @return true if the description list is non-empty
     */
    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }

    /**
     * Checks whether this item has any special effects.
     *
     * @return true if the special effects list is non-empty
     */
    public boolean hasSpecialEffects() {
        return specialEffects != null && !specialEffects.isEmpty();
    }

    // ---- Static factory ----

    /**
     * Parses an RPGItem from a YAML configuration section.
     * <p>
     * Expected structure (keys are kebab-case):
     * <pre>
     * display-name: "&lt;gradient:#ff6600:#ff0000&gt;烈焰之剑"
     * type: WEAPON
     * weapon-type: SWORD
     * material: NETHERITE_SWORD
     * level-requirement: 10
     * class-requirement:
     *   - WARRIOR
     *   - ASSASSIN
     * stats:
     *   physical-attack: 45
     *   critical-chance: 15
     * element:
     *   fire: 20
     * skills:
     *   - flame_slash
     * lore-template: legendary
     * custom-model-data: 10001
     * armor-set: guardian
     * </pre>
     *
     * @param id      the unique item identifier
     * @param section the configuration section for this item
     * @return the parsed RPGItem, never null
     */
    public static RPGItem fromConfig(String id, ConfigurationSection section) {
        RPGItem item = new RPGItem(id);
        item.setDisplayName(section.getString("display-name", ""));
        item.setType(ItemType.fromString(section.getString("type", "ACCESSORY")));
        item.setMaterial(section.getString("material", "STONE"));
        item.setLevelRequirement(section.getInt("level-requirement", 0));

        // Class requirement
        Set<GameClass> classes = new HashSet<>();
        for (String className : section.getStringList("class-requirement")) {
            GameClass gc = GameClass.fromString(className);
            if (gc != null) {
                classes.add(gc);
            }
        }
        item.setClassRequirement(classes);

        // Stats
        StatMap statMap = new StatMap();
        ConfigurationSection statsSection = section.getConfigurationSection("stats");
        if (statsSection != null) {
            for (String key : statsSection.getKeys(false)) {
                StatType statType = parseStatType(key);
                if (statType != null) {
                    statMap.set(statType, statsSection.getDouble(key));
                }
            }
        }
        item.setStats(statMap);

        // Elements
        Map<String, Double> elements = new LinkedHashMap<>();
        ConfigurationSection elementSection = section.getConfigurationSection("element");
        if (elementSection != null) {
            for (String key : elementSection.getKeys(false)) {
                elements.put(key.toLowerCase(), elementSection.getDouble(key));
            }
        }
        item.setElements(elements);

        // Skills
        item.setSkills(new ArrayList<>(section.getStringList("skills")));

        // Lore template
        item.setLoreTemplate(section.getString("lore-template", "common"));

        // Custom model data
        item.setCustomModelData(section.getInt("custom-model-data", 0));

        // Weapon / armor type
        item.setWeaponType(section.getString("weapon-type"));
        item.setArmorType(section.getString("armor-type"));
        item.setArmorSetId(section.getString("armor-set"));

        // Description
        item.setDescription(new ArrayList<>(section.getStringList("description")));

        // Special effects
        item.setSpecialEffects(new ArrayList<>(section.getStringList("special-effects")));

        // Seal state (new items are sealed by default)
        item.setSealed(section.getBoolean("sealed", true));

        return item;
    }

    /**
     * Converts a kebab-case config key (e.g. "physical-attack") into a
     * {@link StatType} enum constant (e.g. {@code PHYSICAL_ATTACK}).
     *
     * @param key the config key
     * @return the matching StatType, or null if no match is found
     */
    private static StatType parseStatType(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return StatType.valueOf(key.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "RPGItem{id='" + id + "', type=" + type + ", material='" + material
                + "', levelReq=" + levelRequirement + "}";
    }
}
