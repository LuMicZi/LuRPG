package com.lumiczi.lurpg.skill;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.skill.api.SkillCastContext;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Collection;

/**
 * Handles the processing of skill effects defined in the YAML configuration.
 * <p>
 * Each effect type is processed by a dedicated private method. The handler
 * reads the effect parameters from a {@link ConfigurationSection} and applies
 * the corresponding game effects (damage, healing, buffs, debuffs, etc.)
 * to the target entities.
 * </p>
 * <p>
 * All Bukkit API calls in this class must be invoked on the main server thread.
 * The caller is responsible for ensuring thread safety.
 * </p>
 */
public class SkillEffectHandler {

    private final LuRPGPlugin plugin;

    public SkillEffectHandler(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Processes all effects defined in the given configuration section.
     * <p>
     * This method iterates over the effect keys and dispatches each to the
     * appropriate handler method.
     * </p>
     *
     * @param context the cast context
     * @param effects the effects configuration section
     */
    public void handleEffects(SkillCastContext context, ConfigurationSection effects) {
        if (effects == null) {
            return;
        }

        Player caster = context.getCaster();
        StatMap stats = context.getCasterStats();
        LivingEntity target = context.getTarget();

        for (String key : effects.getKeys(false)) {
            // Skip non-section keys like "range" or "cone-angle" which are parameters
            // for other effects, not standalone effects
            if (isParameterKey(key)) {
                continue;
            }
            ConfigurationSection effectSection = effects.getConfigurationSection(key);
            if (effectSection == null) {
                continue;
            }

            switch (key.toLowerCase()) {
                case "damage" -> handleDamage(context, effectSection, stats, target);
                case "element" -> handleElement(context, effectSection, stats, target);
                case "heal" -> handleHeal(context, effectSection, caster);
                case "buff" -> handleBuff(context, effectSection, caster);
                case "debuff" -> handleDebuff(context, effectSection, target);
                case "stun" -> handleStun(context, effectSection, target);
                case "slow" -> handleSlow(context, effectSection, caster, target, effects);
                case "invisibility" -> handleInvisibility(context, effectSection, caster);
                case "shield" -> handleShield(context, effectSection, caster);
                case "dodge" -> handleDodge(context, effectSection, caster);
                case "dash" -> handleDash(context, effectSection, caster, target);
                case "projectile" -> handleProjectile(context, effectSection, caster);
                default -> {
                    // Unknown effect type - log in debug mode
                    if (plugin.getConfigManager().getMainConfig().getBoolean("general.debug", false)) {
                        plugin.getLogger().info("Unknown skill effect type: " + key);
                    }
                }
            }
        }
    }

    // ==================== Effect Handlers ====================

    /**
     * Handles damage effects (physical or magical).
     * <pre>
     * damage:
     *   type: PHYSICAL  # or MAGICAL
     *   multiplier: 1.5
     *   force-crit: true  # optional
     * </pre>
     */
    private void handleDamage(SkillCastContext context, ConfigurationSection section,
                              StatMap stats, LivingEntity target) {
        if (target == null || stats == null) {
            return;
        }

        String type = section.getString("type", "PHYSICAL");
        double multiplier = section.getDouble("multiplier", 1.0);
        boolean forceCrit = section.getBoolean("force-crit", false);

        double baseDamage;
        if (type.equalsIgnoreCase("MAGICAL")) {
            baseDamage = stats.get(StatType.MAGICAL_ATTACK);
        } else {
            baseDamage = stats.get(StatType.PHYSICAL_ATTACK);
        }

        double damage = baseDamage * multiplier;

        // Apply critical hit
        if (forceCrit) {
            double critMultiplier = plugin.getStatManager().getCritBaseMultiplier();
            double critDamage = stats.get(StatType.CRITICAL_DAMAGE) / 100.0;
            damage *= (critMultiplier + critDamage);
        } else {
            double critChance = stats.get(StatType.CRITICAL_CHANCE) / 100.0;
            if (Math.random() < critChance) {
                double critMultiplier = plugin.getStatManager().getCritBaseMultiplier();
                double critDamage = stats.get(StatType.CRITICAL_DAMAGE) / 100.0;
                damage *= (critMultiplier + critDamage);
            }
        }

        // Apply defense reduction
        double defense;
        if (type.equalsIgnoreCase("MAGICAL")) {
            defense = stats.get(StatType.MAGICAL_DEFENSE);
        } else {
            defense = stats.get(StatType.PHYSICAL_DEFENSE);
        }
        damage = Math.max(1.0, damage - defense * 0.5);

        target.damage(damage, context.getCaster());
    }

    /**
     * Handles elemental damage effects.
     * <pre>
     * element:
     *   type: FIRE  # FIRE, ICE, THUNDER, DARK, LIGHT
     *   amount: 25
     * </pre>
     */
    private void handleElement(SkillCastContext context, ConfigurationSection section,
                               StatMap stats, LivingEntity target) {
        if (target == null) {
            return;
        }

        String elementType = section.getString("type", "FIRE").toUpperCase();
        double amount = section.getDouble("amount", 0);

        // Add elemental stat bonus from caster's stats
        amount += getElementalStatBonus(stats, elementType);

        // Apply elemental damage
        target.damage(amount, context.getCaster());

        // Apply elemental side effects based on config
        applyElementalSideEffect(elementType, target, amount);
    }

    /**
     * Handles healing effects.
     * <pre>
     * heal:
     *   type: FIXED  # or PERCENT_MAX_HEALTH
     *   amount: 20   # fixed value or percentage (0.2 = 20%)
     * </pre>
     */
    private void handleHeal(SkillCastContext context, ConfigurationSection section, Player caster) {
        String type = section.getString("type", "FIXED");
        double amount = section.getDouble("amount", 0);

        double healAmount;
        if (type.equalsIgnoreCase("PERCENT_MAX_HEALTH")) {
            double maxHealth = caster.getAttribute(Attribute.MAX_HEALTH) != null
                    ? caster.getAttribute(Attribute.MAX_HEALTH).getValue()
                    : 20.0;
            healAmount = maxHealth * amount;
        } else {
            healAmount = amount;
        }

        double newHealth = Math.min(
                caster.getHealth() + healAmount,
                caster.getAttribute(Attribute.MAX_HEALTH) != null
                        ? caster.getAttribute(Attribute.MAX_HEALTH).getValue()
                        : 20.0
        );
        caster.setHealth(newHealth);
    }

    /**
     * Handles buff effects (temporary stat increases).
     * <pre>
     * buff:
     *   stat: PHYSICAL_ATTACK
     *   multiplier: 1.3
     *   duration: 200  # ticks
     *   next-hit-only: true  # optional
     * </pre>
     */
    private void handleBuff(SkillCastContext context, ConfigurationSection section, Player caster) {
        String statName = section.getString("stat", "");
        double multiplier = section.getDouble("multiplier", 1.0);
        int duration = section.getInt("duration", 100);

        // Apply as a potion effect or attribute modifier
        // For simplicity, we use speed/strength potions for common stats
        StatType statType = parseStatType(statName);
        if (statType == null) {
            return;
        }

        switch (statType) {
            case PHYSICAL_ATTACK -> {
                int amplifier = Math.max(0, (int) ((multiplier - 1.0) * 2) - 1);
                caster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, amplifier));
            }
            case MAGICAL_ATTACK -> {
                // No direct vanilla equivalent; use glowing as visual indicator
                caster.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0));
            }
            case MAX_HEALTH -> {
                caster.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, duration,
                        Math.max(0, (int) ((multiplier - 1.0) * 10) - 1)));
            }
            case MOVEMENT_SPEED -> {
                int amplifier = Math.max(0, (int) ((multiplier - 1.0) * 5) - 1);
                caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, amplifier));
            }
            default -> {
                // For other stats, no vanilla equivalent; could be handled by a custom buff system
            }
        }
    }

    /**
     * Handles debuff effects (temporary stat decreases on target).
     * <pre>
     * debuff:
     *   stat: PHYSICAL_DEFENSE
     *   multiplier: 0.9
     *   duration: 200
     * </pre>
     */
    private void handleDebuff(SkillCastContext context, ConfigurationSection section, LivingEntity target) {
        if (target == null) {
            return;
        }
        String statName = section.getString("stat", "");
        double multiplier = section.getDouble("multiplier", 1.0);
        int duration = section.getInt("duration", 100);

        StatType statType = parseStatType(statName);
        if (statType == null) {
            return;
        }

        switch (statType) {
            case PHYSICAL_DEFENSE, MAGICAL_DEFENSE -> {
                int amplifier = Math.max(0, (int) ((1.0 - multiplier) * 5));
                target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, amplifier));
            }
            case MOVEMENT_SPEED -> {
                int amplifier = Math.max(0, (int) ((1.0 - multiplier) * 5));
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
            }
            default -> {
                // Custom debuff handling would go here
            }
        }
    }

    /**
     * Handles stun effects.
     * <pre>
     * stun:
     *   chance: 50  # percentage
     *   duration: 40  # ticks
     * </pre>
     */
    private void handleStun(SkillCastContext context, ConfigurationSection section, LivingEntity target) {
        if (target == null) {
            return;
        }
        double chance = section.getDouble("chance", 100) / 100.0;
        int duration = section.getInt("duration", 40);

        if (Math.random() < chance) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 10));
            target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 128));
            target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 10));
        }
    }

    /**
     * Handles slow effects.
     * <pre>
     * slow:
     *   duration: 100
     *   amplifier: 2
     * </pre>
     */
    private void handleSlow(SkillCastContext context, ConfigurationSection section,
                            Player caster, LivingEntity target, ConfigurationSection parentEffects) {
        int duration = section.getInt("duration", 60);
        int amplifier = section.getInt("amplifier", 1);

        double range = parentEffects.getDouble("range", 0);
        if (range > 0) {
            // AoE slow
            Collection<LivingEntity> entities = getNearbyEntities(caster, range);
            for (LivingEntity entity : entities) {
                if (entity instanceof Player p && p.equals(caster)) {
                    continue;
                }
                entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
            }
        } else if (target != null) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
        }
    }

    /**
     * Handles invisibility effects.
     * <pre>
     * invisibility:
     *   duration: 100
     * </pre>
     */
    private void handleInvisibility(SkillCastContext context, ConfigurationSection section, Player caster) {
        int duration = section.getInt("duration", 100);
        caster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, duration, 0));
    }

    /**
     * Handles shield effects (damage absorption).
     * <pre>
     * shield:
     *   damage-reduction: 0.5
     *   duration: 200
     * </pre>
     */
    private void handleShield(SkillCastContext context, ConfigurationSection section, Player caster) {
        int duration = section.getInt("duration", 200);
        // Use resistance as a shield approximation
        caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 1));
    }

    /**
     * Handles dodge effects (chance to dodge and resource restore).
     * <pre>
     * dodge:
     *   chance: 20  # percentage
     *   resource-restore: 20
     * </pre>
     */
    private void handleDodge(SkillCastContext context, ConfigurationSection section, Player caster) {
        double chance = section.getDouble("chance", 0) / 100.0;
        double resourceRestore = section.getDouble("resource-restore", 0);

        if (Math.random() < chance) {
            if (resourceRestore > 0 && context.getCasterData() != null) {
                var data = context.getCasterData();
                double max = plugin.getConfigManager().getMainConfig()
                        .getDouble("stats.base." + (data.getGameClass() != null ? data.getGameClass().name() : "") + ".resource-max", 100);
                data.setCurrentResource(Math.min(max, data.getCurrentResource() + resourceRestore));
            }
            // Visual feedback for dodge
            caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 2));
        }
    }

    /**
     * Handles dash effects (quick movement toward target or direction).
     * <pre>
     * dash:
     *   distance: 8
     * </pre>
     */
    private void handleDash(SkillCastContext context, ConfigurationSection section,
                            Player caster, LivingEntity target) {
        double distance = section.getDouble("distance", 5.0);
        boolean behindTarget = section.getBoolean("behind-target", false);

        Vector direction;
        if (behindTarget && target != null) {
            // Teleport behind the target
            Location targetLoc = target.getLocation();
            Vector targetDir = target.getLocation().getDirection();
            Location behind = targetLoc.clone().subtract(targetDir.multiply(1.5));
            behind.setY(targetLoc.getY());
            caster.teleport(behind);
            return;
        }

        if (target != null) {
            direction = target.getLocation().toVector().subtract(caster.getLocation().toVector()).normalize();
        } else {
            direction = caster.getLocation().getDirection().normalize();
        }

        Vector velocity = direction.multiply(distance * 0.3);
        velocity.setY(0.3); // Slight upward arc
        caster.setVelocity(velocity);
    }

    /**
     * Handles projectile effects.
     * <pre>
     * projectile:
     *   type: FIREBALL
     *   speed: 1.5
     *   radius: 3
     * </pre>
     */
    private void handleProjectile(SkillCastContext context, ConfigurationSection section, Player caster) {
        String type = section.getString("type", "SNOWBALL").toUpperCase();
        double speed = section.getDouble("speed", 1.5);

        Location eyeLoc = caster.getEyeLocation();
        Vector direction = eyeLoc.getDirection().multiply(speed);

        org.bukkit.entity.Projectile projectile = switch (type) {
            case "FIREBALL" -> caster.launchProjectile(org.bukkit.entity.Fireball.class, direction);
            case "SNOWBALL" -> caster.launchProjectile(org.bukkit.entity.Snowball.class, direction);
            case "ARROW" -> caster.launchProjectile(org.bukkit.entity.Arrow.class, direction);
            case "ENDER_PEARL" -> caster.launchProjectile(org.bukkit.entity.EnderPearl.class, direction);
            case "TRIDENT" -> caster.launchProjectile(org.bukkit.entity.Trident.class, direction);
            default -> caster.launchProjectile(org.bukkit.entity.Snowball.class, direction);
        };

        // launchProjectile automatically sets the caster as the projectile source
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if a key is a parameter for another effect (not a standalone effect).
     */
    private boolean isParameterKey(String key) {
        return switch (key.toLowerCase()) {
            case "range", "cone-angle", "behind-target", "next-hit-only" -> true;
            default -> false;
        };
    }

    /**
     * Gets nearby living entities within the specified range of the caster.
     */
    private Collection<LivingEntity> getNearbyEntities(Player caster, double range) {
        return caster.getLocation().getNearbyLivingEntities(range).stream()
                .filter(e -> !e.equals(caster))
                .toList();
    }

    /**
     * Gets the elemental stat bonus from the caster's stats.
     */
    private double getElementalStatBonus(StatMap stats, String elementType) {
        return switch (elementType.toUpperCase()) {
            case "FIRE" -> stats.get(StatType.FIRE_DAMAGE);
            case "ICE" -> stats.get(StatType.ICE_DAMAGE);
            case "THUNDER" -> stats.get(StatType.THUNDER_DAMAGE);
            case "DARK" -> stats.get(StatType.DARK_DAMAGE);
            case "LIGHT" -> stats.get(StatType.LIGHT_DAMAGE);
            default -> 0;
        };
    }

    /**
     * Applies side effects based on element type (burning, slowness, etc.).
     */
    private void applyElementalSideEffect(String elementType, LivingEntity target, double amount) {
        var statManager = plugin.getStatManager();
        switch (elementType.toUpperCase()) {
            case "FIRE" -> {
                int burnDuration = statManager.getElementConfigInt("fire", "burn-duration", 40);
                double burnDamage = statManager.getElementConfigDouble("fire", "burn-damage-per-tick", 2.0);
                target.setFireTicks(burnDuration);
            }
            case "ICE" -> {
                int slowDuration = statManager.getElementConfigInt("ice", "slow-duration", 60);
                int slowAmplifier = statManager.getElementConfigInt("ice", "slow-amplifier", 1);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, slowAmplifier));
            }
            case "THUNDER" -> {
                double stunChance = statManager.getElementConfigDouble("thunder", "stun-chance", 20.0) / 100.0;
                int stunDuration = statManager.getElementConfigInt("thunder", "stun-duration", 20);
                if (Math.random() < stunChance) {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, stunDuration, 10));
                }
            }
            case "DARK" -> {
                int witherDuration = statManager.getElementConfigInt("dark", "wither-duration", 60);
                int witherAmplifier = statManager.getElementConfigInt("dark", "wither-amplifier", 1);
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherDuration, witherAmplifier));
            }
            case "LIGHT" -> {
                // Light heals the caster instead; handled separately
            }
            default -> {
            }
        }
    }

    /**
     * Parses a stat type from a string.
     */
    private StatType parseStatType(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return StatType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
