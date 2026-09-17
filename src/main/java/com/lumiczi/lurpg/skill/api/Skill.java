package com.lumiczi.lurpg.skill.api;

import com.lumiczi.lurpg.class_.GameClass;
import org.bukkit.Material;

import java.util.List;

/**
 * Core skill interface for the LuRPG skill system.
 * <p>
 * Developers can implement this interface (or extend {@code AbstractSkill})
 * to create custom skills. Each skill defines its identity, requirements,
 * trigger conditions, resource cost, cooldown, and cast behavior.
 * </p>
 */
public interface Skill {

    /**
     * Returns the unique identifier of this skill (e.g. "fireball").
     *
     * @return the skill ID
     */
    String getId();

    /**
     * Returns the display name of this skill, which may contain MiniMessage tags.
     *
     * @return the display name
     */
    String getDisplayName();

    /**
     * Returns the description lines of this skill, which may contain MiniMessage tags.
     *
     * @return the description lines
     */
    List<String> getDescription();

    /**
     * Returns the class required to learn this skill, or null if any class can learn it.
     *
     * @return the required GameClass, or null
     */
    GameClass getRequiredClass();

    /**
     * Returns the minimum level required to learn this skill.
     *
     * @return the required level
     */
    int getRequiredLevel();

    /**
     * Returns the trigger type that activates this skill.
     *
     * @return the SkillTrigger
     */
    SkillTrigger getTrigger();

    /**
     * Returns the cast mode (input method) for this skill.
     *
     * @return the SkillCastMode
     */
    SkillCastMode getCastMode();

    /**
     * Returns the resource cost to cast this skill.
     *
     * @return the ResourceCost
     */
    ResourceCost getResourceCost();

    /**
     * Returns the cooldown duration in ticks (20 ticks = 1 second).
     *
     * @return the cooldown in ticks
     */
    long getCooldownTicks();

    /**
     * Returns the icon material used to represent this skill in GUIs.
     *
     * @return the icon Material
     */
    Material getIcon();

    /**
     * Checks whether the skill can currently be cast by the player in the given context.
     * <p>
     * This should verify level requirements, class requirements, resource availability,
     * and cooldown status. It should not actually consume resources or set cooldowns.
     * </p>
     *
     * @param context the cast context
     * @return true if the skill can be cast
     */
    boolean canCast(SkillCastContext context);

    /**
     * Executes the skill's effects.
     * <p>
     * This is called after resource deduction and cooldown setting have been
     * performed by the SkillManager. Implementations should apply the skill's
     * effects (damage, healing, buffs, etc.) to the target or area.
     * </p>
     *
     * @param context the cast context
     */
    void onCast(SkillCastContext context);
}
