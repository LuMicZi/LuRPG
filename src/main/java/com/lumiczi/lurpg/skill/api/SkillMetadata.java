package com.lumiczi.lurpg.skill.api;

import com.lumiczi.lurpg.class_.GameClass;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata for a skill, parsed from the skills.yml configuration.
 * <p>
 * This class holds all the static configuration data for a skill: its ID,
 * display name, description, class/level requirements, trigger, cast mode,
 * resource cost, cooldown, icon, and the raw effects configuration section.
 * </p>
 * <p>
 * Use {@link #fromConfig(String, ConfigurationSection)} to create an instance
 * from a YAML configuration section.
 * </p>
 */
public final class SkillMetadata {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final GameClass requiredClass;
    private final int requiredLevel;
    private final SkillTrigger trigger;
    private final SkillCastMode castMode;
    private final ResourceCost resourceCost;
    private final long cooldownTicks;
    private final Material icon;
    private final ConfigurationSection effects;

    private SkillMetadata(String id, String displayName, List<String> description,
                          GameClass requiredClass, int requiredLevel,
                          SkillTrigger trigger, SkillCastMode castMode,
                          ResourceCost resourceCost, long cooldownTicks,
                          Material icon, ConfigurationSection effects) {
        this.id = id;
        this.displayName = displayName;
        this.description = Collections.unmodifiableList(description);
        this.requiredClass = requiredClass;
        this.requiredLevel = requiredLevel;
        this.trigger = trigger;
        this.castMode = castMode;
        this.resourceCost = resourceCost;
        this.cooldownTicks = cooldownTicks;
        this.icon = icon;
        this.effects = effects;
    }

    /**
     * Parses a SkillMetadata from a configuration section.
     * <p>
     * Expected YAML structure:
     * <pre>
     * flame_slash:
     *   display-name: "<gold>烈焰斩"
     *   description:
     *     - "<gray>第一行描述"
     *     - "<gray>第二行描述"
     *   class: WARRIOR
     *   level: 5
     *   trigger: ACTIVE
     *   cast-mode: RIGHT_CLICK
     *   cost:
     *     type: RAGE
     *     amount: 30
     *   cooldown: 8
     *   icon: FLINT_AND_STEEL
     *   effects:
     *     damage:
     *       type: PHYSICAL
     *       multiplier: 1.5
     * </pre>
     * Note: {@code cooldown} in the YAML is in seconds; it is converted to ticks
     * (multiplied by 20) in the resulting metadata.
     *
     * @param id      the skill ID (the key name in the YAML)
     * @param section the configuration section for this skill
     * @return the parsed SkillMetadata
     */
    public static SkillMetadata fromConfig(String id, ConfigurationSection section) {
        Objects.requireNonNull(id, "skill id cannot be null");
        Objects.requireNonNull(section, "configuration section cannot be null for skill: " + id);

        String displayName = section.getString("display-name", id);
        List<String> description = new ArrayList<>(section.getStringList("description"));
        GameClass requiredClass = GameClass.fromString(section.getString("class"));
        int requiredLevel = section.getInt("level", 1);
        SkillTrigger trigger = SkillTrigger.fromString(section.getString("trigger", "ACTIVE"));
        SkillCastMode castMode = SkillCastMode.fromString(section.getString("cast-mode", "RIGHT_CLICK"));

        ConfigurationSection costSection = section.getConfigurationSection("cost");
        ResourceCost resourceCost = ResourceCost.fromConfig(costSection);

        // Cooldown in YAML is seconds; convert to ticks
        long cooldownSeconds = section.getLong("cooldown", 0);
        long cooldownTicks = cooldownSeconds * 20L;

        String iconStr = section.getString("icon", "PAPER");
        Material icon = Material.matchMaterial(iconStr);
        if (icon == null) {
            icon = Material.PAPER;
        }

        ConfigurationSection effects = section.getConfigurationSection("effects");

        return new SkillMetadata(id, displayName, description, requiredClass, requiredLevel,
                trigger, castMode, resourceCost, cooldownTicks, icon, effects);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return description;
    }

    public GameClass getRequiredClass() {
        return requiredClass;
    }

    public int getRequiredLevel() {
        return requiredLevel;
    }

    public SkillTrigger getTrigger() {
        return trigger;
    }

    public SkillCastMode getCastMode() {
        return castMode;
    }

    public ResourceCost getResourceCost() {
        return resourceCost;
    }

    public long getCooldownTicks() {
        return cooldownTicks;
    }

    public Material getIcon() {
        return icon;
    }

    /**
     * Returns the raw effects configuration section.
     * <p>
     * This may be null if the skill has no effects defined. The section is
     * passed to {@code SkillEffectHandler} for processing.
     * </p>
     *
     * @return the effects ConfigurationSection, or null
     */
    public ConfigurationSection getEffects() {
        return effects;
    }

    @Override
    public String toString() {
        return "SkillMetadata{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", requiredClass=" + requiredClass +
                ", requiredLevel=" + requiredLevel +
                ", trigger=" + trigger +
                ", castMode=" + castMode +
                ", resourceCost=" + resourceCost +
                ", cooldownTicks=" + cooldownTicks +
                ", icon=" + icon +
                '}';
    }
}
