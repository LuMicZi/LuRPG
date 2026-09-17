package com.lumiczi.lurpg.skill;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.skill.api.ResourceCost;
import com.lumiczi.lurpg.skill.api.Skill;
import com.lumiczi.lurpg.skill.api.SkillCastContext;
import com.lumiczi.lurpg.skill.api.SkillCastMode;
import com.lumiczi.lurpg.skill.api.SkillMetadata;
import com.lumiczi.lurpg.skill.api.SkillTrigger;
import org.bukkit.Material;

import java.util.List;

/**
 * Abstract base class implementing {@link Skill} that encapsulates common
 * skill logic backed by {@link SkillMetadata}.
 * <p>
 * Subclasses only need to implement {@link #onCast(SkillCastContext)} to define
 * the skill's behavior. The {@link #canCast(SkillCastContext)} method provides
 * a default implementation that checks:
 * </p>
 * <ul>
 *   <li>Player has a class assigned</li>
 *   <li>Player's class matches the skill's required class (if specified)</li>
 *   <li>Player's level meets the skill's level requirement</li>
 *   <li>Player has enough resource to pay the cost</li>
 *   <li>Player is not on cooldown for this skill</li>
 * </ul>
 * <p>
 * Resource deduction and cooldown setting are NOT performed in canCast();
 * they are handled by {@link SkillManager} after canCast() returns true.
 * </p>
 */
public abstract class AbstractSkill implements Skill {

    protected final LuRPGPlugin plugin;
    protected final SkillMetadata metadata;

    /**
     * Creates a new AbstractSkill.
     *
     * @param plugin   the plugin instance
     * @param metadata the skill metadata parsed from YAML
     */
    protected AbstractSkill(LuRPGPlugin plugin, SkillMetadata metadata) {
        this.plugin = plugin;
        this.metadata = metadata;
    }

    @Override
    public String getId() {
        return metadata.getId();
    }

    @Override
    public String getDisplayName() {
        return metadata.getDisplayName();
    }

    @Override
    public List<String> getDescription() {
        return metadata.getDescription();
    }

    @Override
    public GameClass getRequiredClass() {
        return metadata.getRequiredClass();
    }

    @Override
    public int getRequiredLevel() {
        return metadata.getRequiredLevel();
    }

    @Override
    public SkillTrigger getTrigger() {
        return metadata.getTrigger();
    }

    @Override
    public SkillCastMode getCastMode() {
        return metadata.getCastMode();
    }

    @Override
    public ResourceCost getResourceCost() {
        return metadata.getResourceCost();
    }

    @Override
    public long getCooldownTicks() {
        return metadata.getCooldownTicks();
    }

    @Override
    public Material getIcon() {
        return metadata.getIcon();
    }

    /**
     * Returns the underlying skill metadata.
     *
     * @return the SkillMetadata
     */
    public SkillMetadata getMetadata() {
        return metadata;
    }

    /**
     * Default implementation of the cast check.
     * <p>
     * Verifies that the caster has a class, meets the class and level requirements,
     * has sufficient resources, and is not on cooldown.
     * </p>
     *
     * @param context the cast context
     * @return true if all checks pass
     */
    @Override
    public boolean canCast(SkillCastContext context) {
        var data = context.getCasterData();
        if (data == null) {
            return false;
        }

        // Check class requirement
        GameClass requiredClass = getRequiredClass();
        if (requiredClass != null && data.getGameClass() != requiredClass) {
            return false;
        }

        // Check level requirement
        if (data.getLevel() < getRequiredLevel()) {
            return false;
        }

        // Check resource
        ResourceCost cost = getResourceCost();
        if (cost.hasCost() && data.getCurrentResource() < cost.amount()) {
            return false;
        }

        // Check cooldown
        SkillCooldown cooldown = plugin.getSkillManager().getCooldown();
        if (cooldown.isOnCooldown(context.getCaster().getUniqueId(), getId())) {
            return false;
        }

        return true;
    }

    /**
     * Executes the skill's effects.
     * <p>
     * The default implementation delegates to the {@link SkillEffectHandler}
     * to process the effects section from the skill metadata. Subclasses can
     * override this to implement custom behavior.
     * </p>
     *
     * @param context the cast context
     */
    @Override
    public void onCast(SkillCastContext context) {
        if (metadata.getEffects() != null) {
            plugin.getSkillManager().getEffectHandler().handleEffects(context, metadata.getEffects());
        }
    }
}
