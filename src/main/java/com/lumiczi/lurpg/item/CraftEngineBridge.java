package com.lumiczi.lurpg.item;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Deep integration bridge between LuRPG and CraftEngine.
 * <p>
 * CraftEngine is a custom-item plugin whose API may not be available at
 * compile time. All interactions are therefore performed via reflection,
 * with every call wrapped in try-catch so that the plugin degrades
 * gracefully when CraftEngine is absent or its API changes.
 *
 * <h2>Capabilities</h2>
 * <ul>
 *   <li>{@link #isAvailable()} &ndash; checks whether CraftEngine is hooked</li>
 *   <li>{@link #getCraftEngineItem(String)} &ndash; retrieves a CraftEngine ItemStack by ID</li>
 *   <li>{@link #registerRPGFields()} &ndash; scans CraftEngine item configs for {@code rpg} sections and registers them as RPGItems</li>
 *   <li>{@link #applyCraftEngineAppearance(ItemStack, String)} &ndash; copies material / custom-model-data from a CraftEngine item onto an RPG ItemStack</li>
 * </ul>
 */
public class CraftEngineBridge {

    private final LuRPGPlugin plugin;

    /** Cached reference to the CraftEngine item adapter (resolved via reflection). */
    private Object cachedItemAdapter;

    public CraftEngineBridge(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks whether CraftEngine is available via the hook manager.
     *
     * @return true if CraftEngine is hooked and ready
     */
    public boolean isAvailable() {
        return plugin.getHookManager() != null
                && plugin.getHookManager().getCraftEngineHook() != null
                && plugin.getHookManager().getCraftEngineHook().isAvailable();
    }

    // ---- CraftEngine item retrieval ----

    /**
     * Retrieves a CraftEngine custom item as an ItemStack by its string ID.
     * <p>
     * Uses reflection to traverse the CraftEngine API:
     * <pre>
     * craftEngine.getAdapterManager().getAdapter(AdapterTypes.ITEM).getItem(itemId)
     * </pre>
     *
     * @param itemId the CraftEngine item ID
     * @return the ItemStack, or null if CraftEngine is unavailable or the item does not exist
     */
    public ItemStack getCraftEngineItem(String itemId) {
        if (!isAvailable() || itemId == null || itemId.isBlank()) {
            return null;
        }
        try {
            Object itemAdapter = getItemAdapter();
            if (itemAdapter == null) {
                return null;
            }
            // itemAdapter.getItem(itemId)
            Method getItem = findMethod(itemAdapter.getClass(), "getItem", String.class);
            if (getItem == null) {
                plugin.getLogger().warning("CraftEngine item adapter has no getItem(String) method.");
                return null;
            }
            Object result = getItem.invoke(itemAdapter, itemId);
            return result instanceof ItemStack is ? is : null;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get CraftEngine item '" + itemId + "': " + e.getMessage());
            return null;
        }
    }

    // ---- RPG field registration ----

    /**
     * Scans CraftEngine item configurations for {@code rpg} fields and
     * registers matching items into the {@link ItemManager}.
     * <p>
     * The primary strategy uses reflection to enumerate item IDs from the
     * CraftEngine item adapter and inspect their configurations. If the
     * reflection path fails, a file-based fallback scans the CraftEngine
     * data folder for YAML files containing {@code rpg} sections.
     *
     * @return the number of RPG items registered from CraftEngine
     */
    public int registerRPGFields() {
        if (!isAvailable()) {
            return 0;
        }

        int count = 0;

        // Strategy 1: reflection-based enumeration
        count += registerViaReflection();

        // Strategy 2: file-based fallback (always run to catch items reflection missed)
        count += registerViaFileScan();

        if (count > 0) {
            plugin.getLogger().info("Registered " + count + " RPG items from CraftEngine.");
        }
        return count;
    }

    /**
     * Attempts to enumerate CraftEngine items via reflection and register
     * those with an {@code rpg} field.
     */
    private int registerViaReflection() {
        int count = 0;
        try {
            Object itemAdapter = getItemAdapter();
            if (itemAdapter == null) {
                return 0;
            }

            // Try to enumerate item IDs
            Set<String> itemIds = enumerateItemIds(itemAdapter);
            if (itemIds == null || itemIds.isEmpty()) {
                return 0;
            }

            for (String itemId : itemIds) {
                try {
                    ConfigurationSection rpgSection = getRPGSectionViaReflection(itemAdapter, itemId);
                    if (rpgSection != null) {
                        RPGItem rpgItem = parseRPGItem(itemId, rpgSection, null);
                        if (rpgItem != null) {
                            plugin.getItemManager().registerItem(rpgItem);
                            count++;
                        }
                    }
                } catch (Exception e) {
                    // Skip individual item failures
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Reflection-based CraftEngine item enumeration failed: " + e.getMessage());
        }
        return count;
    }

    /**
     * Scans the CraftEngine data folder for YAML config files containing
     * item definitions with {@code rpg} sections.
     */
    private int registerViaFileScan() {
        Plugin ce = plugin.getHookManager().getCraftEngineHook().getCraftEngine();
        if (ce == null) {
            return 0;
        }
        File dataFolder = ce.getDataFolder();
        if (dataFolder == null || !dataFolder.exists()) {
            return 0;
        }

        int count = 0;
        // Scan common locations: items/, configs/, and root
        count += scanDirectory(new File(dataFolder, "items"));
        count += scanDirectory(new File(dataFolder, "configs"));
        // Only scan root if subdirectories yielded nothing
        if (count == 0) {
            count += scanDirectory(dataFolder);
        }
        return count;
    }

    /**
     * Recursively scans a directory for YAML files and parses them for RPG items.
     */
    private int scanDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return 0;
        }
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                count += scanDirectory(file);
            } else if (file.getName().endsWith(".yml") || file.getName().endsWith(".yaml")) {
                count += parseCraftEngineConfigFile(file);
            }
        }
        return count;
    }

    /**
     * Parses a single CraftEngine YAML config file, looking for items with rpg sections.
     */
    private int parseCraftEngineConfigFile(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection itemsSection = config.getConfigurationSection("items");
            if (itemsSection == null) {
                return 0;
            }
            int count = 0;
            for (String itemId : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemId);
                if (itemSection == null) continue;

                ConfigurationSection rpgSection = itemSection.getConfigurationSection("rpg");
                if (rpgSection != null) {
                    RPGItem rpgItem = parseRPGItem(itemId, rpgSection, itemSection);
                    if (rpgItem != null) {
                        plugin.getItemManager().registerItem(rpgItem);
                        count++;
                    }
                }
            }
            return count;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse CraftEngine config '" + file.getName() + "': " + e.getMessage());
            return 0;
        }
    }

    // ---- Appearance application ----

    /**
     Applies the visual appearance (material and custom-model-data) of a
     CraftEngine item onto an existing RPG ItemStack.
     * <p>
     * This preserves the RPG item's PersistentDataContainer, display name,
     * and lore &mdash; only the material and custom model data are copied.
     *
     * @param rpgItemStack     the RPG ItemStack to modify
     * @param craftEngineItemId the CraftEngine item ID to copy appearance from
     */
    public void applyCraftEngineAppearance(ItemStack rpgItemStack, String craftEngineItemId) {
        if (!isAvailable() || rpgItemStack == null || craftEngineItemId == null) {
            return;
        }
        ItemStack ceItem = getCraftEngineItem(craftEngineItemId);
        if (ceItem == null) {
            return;
        }

        // Copy material type (does not affect ItemMeta)
        rpgItemStack.setType(ceItem.getType());

        // Copy custom model data without losing existing meta (lore, name, PDC)
        ItemMeta ceMeta = ceItem.getItemMeta();
        ItemMeta rpgMeta = rpgItemStack.getItemMeta();
        if (ceMeta != null && rpgMeta != null && ceMeta.hasCustomModelData()) {
            rpgMeta.setCustomModelData(ceMeta.getCustomModelData());
            rpgItemStack.setItemMeta(rpgMeta);
        }
    }

    // ---- Reflection helpers ----

    /**
     * Resolves the CraftEngine item adapter via reflection:
     * <pre>
     * craftEngine.getAdapterManager().getAdapter(AdapterTypes.ITEM)
     * </pre>
     * The result is cached for subsequent calls.
     *
     * @return the item adapter object, or null if resolution fails
     */
    private Object getItemAdapter() {
        if (cachedItemAdapter != null) {
            return cachedItemAdapter;
        }
        try {
            Plugin ce = plugin.getHookManager().getCraftEngineHook().getCraftEngine();
            if (ce == null) {
                return null;
            }

            // ce.getAdapterManager()
            Method getAdapterManager = findMethod(ce.getClass(), "getAdapterManager");
            if (getAdapterManager == null) {
                plugin.getLogger().warning("CraftEngine plugin has no getAdapterManager() method.");
                return null;
            }
            Object adapterManager = getAdapterManager.invoke(ce);
            if (adapterManager == null) {
                return null;
            }

            // Find getAdapter method and determine the adapter-type parameter
            Method getAdapter = findMethod(adapterManager.getClass(), "getAdapter");
            if (getAdapter == null) {
                plugin.getLogger().warning("CraftEngine AdapterManager has no getAdapter() method.");
                return null;
            }

            Class<?>[] paramTypes = getAdapter.getParameterTypes();
            if (paramTypes.length != 1) {
                plugin.getLogger().warning("CraftEngine getAdapter() has unexpected parameter count: " + paramTypes.length);
                return null;
            }

            // Resolve the ITEM constant from the adapter-type parameter class
            Object itemAdapterType = resolveItemAdapterType(paramTypes[0]);
            if (itemAdapterType == null) {
                plugin.getLogger().warning("Could not resolve AdapterTypes.ITEM constant.");
                return null;
            }

            Object adapter = getAdapter.invoke(adapterManager, itemAdapterType);
            cachedItemAdapter = adapter;
            return adapter;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to resolve CraftEngine item adapter: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolves the {@code ITEM} constant from the adapter-type class.
     * Handles both enum constants and static fields.
     *
     * @param adapterTypeClass the class representing adapter types
     * @return the ITEM constant value, or null if not found
     */
    private Object resolveItemAdapterType(Class<?> adapterTypeClass) {
        // If it's an enum, look for an ITEM constant
        if (adapterTypeClass.isEnum()) {
            for (Object constant : adapterTypeClass.getEnumConstants()) {
                if (constant.toString().equalsIgnoreCase("ITEM")) {
                    return constant;
                }
            }
        }
        // Try static field named ITEM
        try {
            Field field = adapterTypeClass.getField("ITEM");
            return field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // Not a field-based type
        }
        return null;
    }

    /**
     * Attempts to enumerate item IDs from the CraftEngine item adapter
     * by trying common method names.
     */
    private Set<String> enumerateItemIds(Object itemAdapter) {
        String[] candidates = {"getItemIds", "getIds", "getAllItemIds", "keys", "getKeys", "getItemKeys"};
        for (String methodName : candidates) {
            try {
                Method m = findMethod(itemAdapter.getClass(), methodName);
                if (m != null) {
                    Object result = m.invoke(itemAdapter);
                    if (result instanceof Set<?> set) {
                        Set<String> ids = new HashSet<>();
                        for (Object o : set) {
                            if (o != null) ids.add(o.toString());
                        }
                        return ids;
                    } else if (result instanceof Iterable<?> iterable) {
                        Set<String> ids = new HashSet<>();
                        for (Object o : iterable) {
                            if (o != null) ids.add(o.toString());
                        }
                        return ids;
                    }
                }
            } catch (Exception ignored) {
                // Try next method name
            }
        }
        return null;
    }

    /**
     * Attempts to retrieve the {@code rpg} ConfigurationSection for a specific
     * item via reflection on the item adapter.
     */
    private ConfigurationSection getRPGSectionViaReflection(Object itemAdapter, String itemId) {
        String[] configCandidates = {"getConfig", "getItemConfig", "getConfiguration", "getItemConfiguration"};
        for (String methodName : configCandidates) {
            try {
                Method m = findMethod(itemAdapter.getClass(), methodName, String.class);
                if (m != null) {
                    Object result = m.invoke(itemAdapter, itemId);
                    if (result instanceof ConfigurationSection section) {
                        return section.getConfigurationSection("rpg");
                    }
                }
            } catch (Exception ignored) {
                // Try next
            }
        }
        return null;
    }

    // ---- RPGItem parsing from CraftEngine rpg section ----

    /**
     * Parses an RPGItem from a CraftEngine {@code rpg} configuration section.
     *
     * @param itemId      the item ID
     * @param rpgSection  the {@code rpg} subsection containing RPG metadata
     * @param itemSection the parent item section (may provide material / display-name), or null
     * @return the parsed RPGItem, or null if parsing fails
     */
    private RPGItem parseRPGItem(String itemId, ConfigurationSection rpgSection, ConfigurationSection itemSection) {
        try {
            RPGItem item = new RPGItem(itemId);

            // Display name: prefer rpg section, fall back to parent section
            String displayName = rpgSection.getString("display-name");
            if (displayName == null && itemSection != null) {
                displayName = itemSection.getString("display-name");
                if (displayName == null) {
                    // Try CraftEngine's nested name field
                    displayName = itemSection.getString("name");
                }
            }
            item.setDisplayName(displayName != null ? displayName : "");

            // Type
            item.setType(ItemType.fromString(rpgSection.getString("type", "ACCESSORY")));

            // Material: prefer rpg section, fall back to parent
            String material = rpgSection.getString("material");
            if (material == null && itemSection != null) {
                material = itemSection.getString("material");
                // CraftEngine may nest material under settings.item.material
                if (material == null) {
                    ConfigurationSection settings = itemSection.getConfigurationSection("settings");
                    if (settings != null) {
                        ConfigurationSection itemSettings = settings.getConfigurationSection("item");
                        if (itemSettings != null) {
                            material = itemSettings.getString("material");
                        }
                    }
                }
            }
            item.setMaterial(material != null ? material : "STONE");

            // Level requirement
            item.setLevelRequirement(rpgSection.getInt("level-requirement", 0));

            // Class requirement
            Set<GameClass> classes = new HashSet<>();
            for (String className : rpgSection.getStringList("class-requirement")) {
                GameClass gc = GameClass.fromString(className);
                if (gc != null) {
                    classes.add(gc);
                }
            }
            item.setClassRequirement(classes);

            // Stats
            StatMap statMap = new StatMap();
            ConfigurationSection statsSection = rpgSection.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String key : statsSection.getKeys(false)) {
                    try {
                        StatType statType = StatType.valueOf(key.toUpperCase().replace("-", "_"));
                        statMap.set(statType, statsSection.getDouble(key));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown stat, skip
                    }
                }
            }
            item.setStats(statMap);

            // Elements
            Map<String, Double> elements = new LinkedHashMap<>();
            ConfigurationSection elementSection = rpgSection.getConfigurationSection("element");
            if (elementSection != null) {
                for (String key : elementSection.getKeys(false)) {
                    elements.put(key.toLowerCase(), elementSection.getDouble(key));
                }
            }
            item.setElements(elements);

            // Skills
            item.setSkills(new ArrayList<>(rpgSection.getStringList("skills")));

            // Lore template
            item.setLoreTemplate(rpgSection.getString("lore-template", "common"));

            // Custom model data
            int cmd = rpgSection.getInt("custom-model-data", 0);
            if (cmd == 0 && itemSection != null) {
                cmd = itemSection.getInt("custom-model-data", 0);
                if (cmd == 0) {
                    ConfigurationSection settings = itemSection.getConfigurationSection("settings");
                    if (settings != null) {
                        ConfigurationSection itemSettings = settings.getConfigurationSection("item");
                        if (itemSettings != null) {
                            cmd = itemSettings.getInt("custom-model-data", 0);
                        }
                    }
                }
            }
            item.setCustomModelData(cmd);

            // Weapon / armor type
            item.setWeaponType(rpgSection.getString("weapon-type"));
            item.setArmorType(rpgSection.getString("armor-type"));
            item.setArmorSetId(rpgSection.getString("armor-set"));

            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse RPG fields for CraftEngine item '" + itemId + "': " + e.getMessage());
            return null;
        }
    }

    // ---- Generic reflection utility ----

    /**
     * Finds a method by name (case-insensitive) with the specified parameter types.
     * If parameter types are not specified, returns the first method with the given name.
     *
     * @param clazz     the class to search
     * @param name      the method name (case-insensitive)
     * @param paramTypes the expected parameter types (empty for any)
     * @return the matching Method, or null if not found
     */
    private Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        if (clazz == null || name == null) {
            return null;
        }
        // Try exact match first
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {
            // Fall through to case-insensitive search
        }
        // Search all public methods
        for (Method m : clazz.getMethods()) {
            if (!m.getName().equalsIgnoreCase(name)) continue;
            if (paramTypes.length == 0) {
                return m;
            }
            if (m.getParameterCount() != paramTypes.length) continue;
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                if (!m.getParameterTypes()[i].isAssignableFrom(paramTypes[i])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return m;
            }
        }
        return null;
    }

    /**
     * Clears the cached item adapter, forcing a fresh resolution on next access.
     */
    public void clearCache() {
        cachedItemAdapter = null;
    }
}
