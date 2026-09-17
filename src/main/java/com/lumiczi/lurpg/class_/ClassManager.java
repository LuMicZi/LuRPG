package com.lumiczi.lurpg.class_;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages RPG class configuration and attributes.
 * <p>
 * Loads class attribute data from {@code classes.yml} (with fallback to
 * {@code config.yml}'s {@code stats} section) and class availability/change
 * settings from {@code config.yml}'s {@code classes} section.
 * </p>
 */
public class ClassManager {

    private final LuRPGPlugin plugin;
    private final Map<GameClass, ClassAttribute> classAttributes = new EnumMap<>(GameClass.class);
    private final Set<GameClass> availableClasses = new LinkedHashSet<>();

    private boolean classChangeAllowed = true;
    private double classChangeCost = 1000.0;

    public ClassManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads class configuration from classes.yml and config.yml.
     * Clears existing cache and reloads all data.
     */
    public void load() {
        classAttributes.clear();
        availableClasses.clear();

        FileConfiguration classConfig = plugin.getConfigManager().getConfig("classes");
        FileConfiguration mainConfig = plugin.getConfigManager().getConfig("config");

        // Load available classes list from config.yml
        List<String> classList = mainConfig.getStringList("classes.list");
        if (classList.isEmpty()) {
            // Default: all enum values are available
            for (GameClass gc : GameClass.values()) {
                availableClasses.add(gc);
            }
        } else {
            for (String className : classList) {
                GameClass gc = GameClass.fromString(className);
                if (gc != null) {
                    availableClasses.add(gc);
                } else {
                    plugin.getLogger().warning("Unknown class in classes.list: " + className);
                }
            }
        }

        // Load class change settings from config.yml
        classChangeAllowed = mainConfig.getBoolean("classes.allow-class-change", true);
        classChangeCost = mainConfig.getDouble("classes.class-change-cost", 1000.0);

        // Load class attributes for all enum values
        for (GameClass gc : GameClass.values()) {
            ClassAttribute attr = loadFromClassConfig(classConfig, gc);
            if (attr == null) {
                attr = loadFromMainConfig(mainConfig, gc);
            }
            if (attr == null) {
                attr = getDefaultAttribute(gc);
                plugin.getLogger().warning("No configuration found for class " + gc.name()
                        + ", using defaults.");
            }
            classAttributes.put(gc, attr);
        }

        plugin.getLogger().info("Loaded " + availableClasses.size() + " available classes.");
    }

    /**
     * Attempts to load class attributes from classes.yml.
     */
    private ClassAttribute loadFromClassConfig(FileConfiguration config, GameClass gc) {
        if (config == null) {
            return null;
        }
        ConfigurationSection section = config.getConfigurationSection("classes." + gc.name());
        if (section == null) {
            section = config.getConfigurationSection(gc.name());
        }
        if (section == null) {
            return null;
        }

        ConfigurationSection baseSection = section.getConfigurationSection("base");
        ConfigurationSection growthSection = section.getConfigurationSection("growth");
        if (baseSection == null) {
            baseSection = section;
        }
        return parseAttributeFromSections(baseSection, growthSection);
    }

    /**
     * Attempts to load class attributes from config.yml's stats section.
     */
    private ClassAttribute loadFromMainConfig(FileConfiguration config, GameClass gc) {
        if (config == null) {
            return null;
        }
        ConfigurationSection baseSection = config.getConfigurationSection("stats.base." + gc.name());
        if (baseSection == null) {
            return null;
        }
        ConfigurationSection growthSection = config.getConfigurationSection("stats.growth." + gc.name());
        return parseAttributeFromSections(baseSection, growthSection);
    }

    /**
     * Parses a ClassAttribute from base and growth configuration sections.
     */
    private ClassAttribute parseAttributeFromSections(ConfigurationSection base, ConfigurationSection growth) {
        ClassAttribute attr = new ClassAttribute();
        if (base != null) {
            attr.setMaxHealth(base.getDouble("max-health", 20.0));
            attr.setPhysicalAttack(base.getDouble("physical-attack", 0.0));
            attr.setMagicalAttack(base.getDouble("magical-attack", 0.0));
            attr.setPhysicalDefense(base.getDouble("physical-defense", 0.0));
            attr.setMagicalDefense(base.getDouble("magical-defense", 0.0));
            attr.setResourceMax(base.getDouble("resource-max", 100.0));
        }
        if (growth != null) {
            attr.setHealthGrowth(growth.getDouble("max-health", 0.0));
            attr.setPatkGrowth(growth.getDouble("physical-attack", 0.0));
            attr.setMatkGrowth(growth.getDouble("magical-attack", 0.0));
            attr.setPdefGrowth(growth.getDouble("physical-defense", 0.0));
            attr.setMdefGrowth(growth.getDouble("magical-defense", 0.0));
            attr.setResourceGrowth(growth.getDouble("resource-max", 0.0));
        }
        return attr;
    }

    /**
     * Returns hardcoded default attributes for a class.
     */
    private ClassAttribute getDefaultAttribute(GameClass gc) {
        return switch (gc) {
            case WARRIOR -> {
                ClassAttribute a = new ClassAttribute(30.0, 5.0, 0.0, 3.0, 0.0, 100.0);
                a.setHealthGrowth(2.0);
                a.setPatkGrowth(1.0);
                a.setPdefGrowth(0.5);
                a.setResourceGrowth(5.0);
                yield a;
            }
            case MAGE -> {
                ClassAttribute a = new ClassAttribute(20.0, 0.0, 8.0, 0.0, 5.0, 200.0);
                a.setHealthGrowth(1.0);
                a.setMatkGrowth(1.5);
                a.setMdefGrowth(0.8);
                a.setResourceGrowth(10.0);
                yield a;
            }
            case ASSASSIN -> {
                ClassAttribute a = new ClassAttribute(22.0, 7.0, 0.0, 2.0, 0.0, 150.0);
                a.setHealthGrowth(1.5);
                a.setPatkGrowth(1.2);
                a.setPdefGrowth(0.3);
                a.setResourceGrowth(8.0);
                yield a;
            }
        };
    }

    /**
     * Gets the cached ClassAttribute for the specified class.
     *
     * @param gc the game class
     * @return the ClassAttribute, or null if not loaded
     */
    public ClassAttribute getClass(GameClass gc) {
        return classAttributes.get(gc);
    }

    /**
     * Returns the set of available classes.
     *
     * @return an unmodifiable set of available GameClass values
     */
    public Set<GameClass> getAvailableClasses() {
        return Collections.unmodifiableSet(availableClasses);
    }

    /**
     * Checks whether class changing is allowed.
     *
     * @return true if class change is permitted
     */
    public boolean isClassChangeAllowed() {
        return classChangeAllowed;
    }

    /**
     * Returns the cost of changing classes (in gold, if Vault is enabled).
     *
     * @return the class change cost
     */
    public double getClassChangeCost() {
        return classChangeCost;
    }

    /**
     * Gets the starting class from config, or null if players should choose.
     *
     * @return the starting GameClass, or null
     */
    public GameClass getStartingClass() {
        FileConfiguration config = plugin.getConfigManager().getConfig("config");
        String startingClass = config.getString("classes.starting-class");
        return GameClass.fromString(startingClass);
    }
}
