package com.lumiczi.lurpg.skill.api;

import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.stat.StatMap;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Objects;

/**
 * Immutable context object passed to {@link Skill#canCast(SkillCastContext)} and
 * {@link Skill#onCast(SkillCastContext)}.
 * <p>
 * Contains all information needed to evaluate and execute a skill cast:
 * the casting player, their RPG data, the skill being cast, an optional target
 * entity, the cast location, and a snapshot of the caster's stats.
 * </p>
 * <p>
 * Use the {@link Builder} to create instances.
 * </p>
 */
public final class SkillCastContext {

    private final Player caster;
    private final PlayerData casterData;
    private final Skill skill;
    private final LivingEntity target;
    private final Location castLocation;
    private final StatMap casterStats;

    private SkillCastContext(Builder builder) {
        this.caster = builder.caster;
        this.casterData = builder.casterData;
        this.skill = builder.skill;
        this.target = builder.target;
        this.castLocation = builder.castLocation != null
                ? builder.castLocation.clone()
                : (caster != null ? caster.getLocation() : null);
        this.casterStats = builder.casterStats;
    }

    public Player getCaster() {
        return caster;
    }

    public PlayerData getCasterData() {
        return casterData;
    }

    public Skill getSkill() {
        return skill;
    }

    /**
     * Returns the target entity, or null if the skill has no specific target.
     *
     * @return the target, or null
     */
    public LivingEntity getTarget() {
        return target;
    }

    /**
     * Checks whether a target entity is present.
     *
     * @return true if a target exists
     */
    public boolean hasTarget() {
        return target != null;
    }

    public Location getCastLocation() {
        return castLocation != null ? castLocation.clone() : null;
    }

    public StatMap getCasterStats() {
        return casterStats;
    }

    /**
     * Builder for constructing SkillCastContext instances.
     */
    public static final class Builder {

        private Player caster;
        private PlayerData casterData;
        private Skill skill;
        private LivingEntity target;
        private Location castLocation;
        private StatMap casterStats;

        public Builder caster(Player caster) {
            this.caster = caster;
            return this;
        }

        public Builder casterData(PlayerData casterData) {
            this.casterData = casterData;
            return this;
        }

        public Builder skill(Skill skill) {
            this.skill = skill;
            return this;
        }

        public Builder target(LivingEntity target) {
            this.target = target;
            return this;
        }

        public Builder castLocation(Location castLocation) {
            this.castLocation = castLocation;
            return this;
        }

        public Builder casterStats(StatMap casterStats) {
            this.casterStats = casterStats;
            return this;
        }

        public SkillCastContext build() {
            Objects.requireNonNull(caster, "caster cannot be null");
            Objects.requireNonNull(skill, "skill cannot be null");
            return new SkillCastContext(this);
        }
    }
}
