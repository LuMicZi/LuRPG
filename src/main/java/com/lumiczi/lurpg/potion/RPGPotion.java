package com.lumiczi.lurpg.potion;

import com.lumiczi.lurpg.class_.GameClass;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents an RPG potion definition loaded from {@code potions.yml}.
 * <p>
 * Each potion has:
 * <ul>
 *   <li>A unique ID and display name</li>
 *   <li>A material type (POTION, SPLASH_POTION, LINGERING_POTION)</li>
 *   <li>A potion color for visual appearance</li>
 *   <li>Level and class requirements</li>
 *   <li>A potion type determining the effect category</li>
 *   <li>A configuration section containing the detailed effect parameters</li>
 *   <li>A cooldown and max stack limit</li>
 *   <li>A lore template and custom model data for visual customization</li>
 * </ul>
 * </p>
 */
public class RPGPotion {

    private final String id;
    private final String displayName;
    private final String material;
    private final Color potionColor;
    private final int levelRequirement;
    private final Set<GameClass> classRequirement;
    private final PotionType type;
    private final ConfigurationSection effects;
    private final int cooldown;
    private final int maxStack;
    private final String loreTemplate;
    private final int customModelData;

    public RPGPotion(String id, String displayName, String material, Color potionColor,
                     int levelRequirement, Set<GameClass> classRequirement, PotionType type,
                     ConfigurationSection effects, int cooldown, int maxStack,
                     String loreTemplate, int customModelData) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.potionColor = potionColor;
        this.levelRequirement = levelRequirement;
        this.classRequirement = classRequirement != null ? classRequirement : new HashSet<>();
        this.type = type;
        this.effects = effects;
        this.cooldown = cooldown;
        this.maxStack = maxStack;
        this.loreTemplate = loreTemplate;
        this.customModelData = customModelData;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getMaterial() {
        return material;
    }

    /**
     * Returns the Material for this potion.
     *
     * @return the Material, or POTION if the material name is invalid
     */
    public Material getBukkitMaterial() {
        if (material == null || material.isBlank()) {
            return Material.POTION;
        }
        try {
            return Material.valueOf(material.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Material.POTION;
        }
    }

    public Color getPotionColor() {
        return potionColor;
    }

    public int getLevelRequirement() {
        return levelRequirement;
    }

    /**
     * Returns the class requirements for this potion.
     *
     * @return an unmodifiable set of required GameClasses (empty = no restriction)
     */
    public Set<GameClass> getClassRequirement() {
        return Collections.unmodifiableSet(classRequirement);
    }

    /**
     * Checks whether a specific class can use this potion.
     *
     * @param gameClass the class to check
     * @return true if the class is allowed (or if there are no class restrictions)
     */
    public boolean canClassUse(GameClass gameClass) {
        if (classRequirement.isEmpty()) {
            return true;
        }
        return classRequirement.contains(gameClass);
    }

    public PotionType getType() {
        return type;
    }

    public ConfigurationSection getEffects() {
        return effects;
    }

    public int getCooldown() {
        return cooldown;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public String getLoreTemplate() {
        return loreTemplate;
    }

    public int getCustomModelData() {
        return customModelData;
    }

    @Override
    public String toString() {
        return "RPGPotion{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", type=" + type +
                ", levelRequirement=" + levelRequirement +
                ", cooldown=" + cooldown +
                ", maxStack=" + maxStack +
                '}';
    }
}
