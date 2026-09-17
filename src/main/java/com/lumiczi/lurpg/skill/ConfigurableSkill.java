package com.lumiczi.lurpg.skill;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.skill.api.SkillMetadata;

/**
 * A concrete skill implementation that loads all of its behavior from
 * {@link SkillMetadata} parsed from YAML configuration.
 * <p>
 * This class extends {@link AbstractSkill} and inherits the default
 * {@code canCast()} and {@code onCast()} implementations, which delegate
 * effect processing to {@link SkillEffectHandler}.
 * </p>
 * <p>
 * For skills requiring custom Java logic, developers can create a subclass
 * of {@link AbstractSkill} and override {@code onCast()}.
 * </p>
 */
public class ConfigurableSkill extends AbstractSkill {

    /**
     * Creates a new ConfigurableSkill from parsed metadata.
     *
     * @param plugin   the plugin instance
     * @param metadata the skill metadata
     */
    public ConfigurableSkill(LuRPGPlugin plugin, SkillMetadata metadata) {
        super(plugin, metadata);
    }
}
