package com.lumiczi.lurpg.combat;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.stat.StatManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles elemental damage and elemental status effects for the RPG combat system.
 * <p>
 * Each element type applies a different status effect to the target:
 * <ul>
 *   <li><b>fire</b> - burning damage over time (burn-duration, burn-damage-per-tick)</li>
 *   <li><b>ice</b> - slowness effect (slow-duration, slow-amplifier)</li>
 *   <li><b>thunder</b> - stun effect with a probability (stun-chance, stun-duration)</li>
 *   <li><b>dark</b> - wither effect (wither-duration, wither-amplifier)</li>
 *   <li><b>light</b> - heals the attacker (heal-amount)</li>
 * </ul>
 * </p>
 * <p>
 * Element configurations are loaded from {@code config.yml} under
 * {@code stats.combat.elements} and accessed via {@link StatManager#getElementConfig(String)}.
 * </p>
 */
public class ElementalDamage {

    private final LuRPGPlugin plugin;

    public ElementalDamage(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calculates the total elemental damage from a map of element damage values.
     *
     * @param elements a map of element name to damage value
     * @return the sum of all elemental damage values
     */
    public double calculateElementDamage(Map<String, Double> elements) {
        if (elements == null || elements.isEmpty()) {
            return 0;
        }
        double total = 0;
        for (Double dmg : elements.values()) {
            if (dmg != null && dmg > 0) {
                total += dmg;
            }
        }
        return total;
    }

    /**
     * Applies elemental status effects to the target.
     * <p>
     * This overload does not provide an attacker, so the {@code light} element's
     * healing effect is skipped. Use
     * {@link #applyElementalEffects(LivingEntity, Player, Map)} to include
     * attacker healing.
     * </p>
     *
     * @param target        the entity to apply effects to
     * @param elementDamage a map of element name to damage value
     */
    public void applyElementalEffects(LivingEntity target, Map<String, Double> elementDamage) {
        applyElementalEffects(target, null, elementDamage);
    }

    /**
     * Applies elemental status effects to the target, with optional attacker healing.
     * <p>
     * For each element present in the damage map:
     * <ul>
     *   <li><b>fire</b> - sets the target on fire for the configured burn duration</li>
     *   <li><b>ice</b> - applies slowness for the configured duration and amplifier</li>
     *   <li><b>thunder</b> - has a chance to apply a stun (slowness + weakness)</li>
     *   <li><b>dark</b> - applies wither for the configured duration and amplifier</li>
     *   <li><b>light</b> - heals the attacker by the configured amount</li>
     * </ul>
     * </p>
     *
     * @param target        the entity to apply effects to
     * @param attacker      the attacking player (may be null; required for light healing)
     * @param elementDamage a map of element name to damage value
     */
    public void applyElementalEffects(LivingEntity target, Player attacker, Map<String, Double> elementDamage) {
        if (elementDamage == null || elementDamage.isEmpty() || target == null) {
            return;
        }

        StatManager statManager = plugin.getStatManager();

        for (Map.Entry<String, Double> entry : elementDamage.entrySet()) {
            String element = entry.getKey().toLowerCase();
            double damage = entry.getValue();
            if (damage <= 0) {
                continue;
            }

            switch (element) {
                case "fire" -> applyFireEffect(target, statManager);
                case "ice" -> applyIceEffect(target, statManager);
                case "thunder" -> applyThunderEffect(target, statManager);
                case "dark" -> applyDarkEffect(target, statManager);
                case "light" -> applyLightEffect(attacker, statManager);
                default -> { /* unknown element, ignore */ }
            }
        }
    }

    /**
     * Applies the fire burning effect to the target.
     * Sets the target on fire for the configured burn duration.
     */
    private void applyFireEffect(LivingEntity target, StatManager statManager) {
        int burnDuration = statManager.getElementConfigInt("fire", "burn-duration", 40);
        double burnDamagePerTick = statManager.getElementConfigDouble("fire", "burn-damage-per-tick", 2.0);

        // Set the target on fire (burnDuration is in ticks)
        int fireTicks = burnDuration;
        if (target.getFireTicks() < fireTicks) {
            target.setFireTicks(fireTicks);
        }

        // Apply additional burn damage as a repeating task would be complex here;
        // instead we apply a portion of the burn damage immediately as poison-like effect.
        // For a proper DoT, a scheduled task should be used. Here we apply immediate bonus damage.
        double immediateBurnDamage = burnDamagePerTick * (burnDuration / 20.0);
        if (immediateBurnDamage > 0) {
            target.damage(immediateBurnDamage);
        }
    }

    /**
     * Applies the ice slowness effect to the target.
     */
    private void applyIceEffect(LivingEntity target, StatManager statManager) {
        int slowDuration = statManager.getElementConfigInt("ice", "slow-duration", 60);
        int slowAmplifier = statManager.getElementConfigInt("ice", "slow-amplifier", 1);

        PotionEffectType slowness = getPotionEffectType("slowness");
        if (slowness != null) {
            target.addPotionEffect(new PotionEffect(slowness, slowDuration, slowAmplifier, false, true, true));
        }
    }

    /**
     * Applies the thunder stun effect to the target (with a probability check).
     * Stun is simulated using high-level slowness and weakness.
     */
    private void applyThunderEffect(LivingEntity target, StatManager statManager) {
        double stunChance = statManager.getElementConfigDouble("thunder", "stun-chance", 20.0);
        int stunDuration = statManager.getElementConfigInt("thunder", "stun-duration", 20);

        if (ThreadLocalRandom.current().nextDouble(100.0) < stunChance) {
            // Stun = high slowness (prevents movement) + weakness (prevents effective attack)
            PotionEffectType slowness = getPotionEffectType("slowness");
            PotionEffectType weakness = getPotionEffectType("weakness");
            if (slowness != null) {
                target.addPotionEffect(new PotionEffect(slowness, stunDuration, 5, false, true, true));
            }
            if (weakness != null) {
                target.addPotionEffect(new PotionEffect(weakness, stunDuration, 5, false, true, true));
            }
        }
    }

    /**
     * Applies the dark wither effect to the target.
     */
    private void applyDarkEffect(LivingEntity target, StatManager statManager) {
        int witherDuration = statManager.getElementConfigInt("dark", "wither-duration", 60);
        int witherAmplifier = statManager.getElementConfigInt("dark", "wither-amplifier", 1);

        PotionEffectType wither = getPotionEffectType("wither");
        if (wither != null) {
            target.addPotionEffect(new PotionEffect(wither, witherDuration, witherAmplifier, false, true, true));
        }
    }

    /**
     * Applies the light healing effect to the attacker.
     */
    private void applyLightEffect(Player attacker, StatManager statManager) {
        if (attacker == null || attacker.isDead()) {
            return;
        }
        double healAmount = statManager.getElementConfigDouble("light", "heal-amount", 5.0);
        if (healAmount <= 0) {
            return;
        }

        double currentHealth = attacker.getHealth();
        var maxHealthAttr = attacker.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0;
        double newHealth = Math.min(currentHealth + healAmount, maxHealth);
        attacker.setHealth(newHealth);
    }

    /**
     * Resolves a PotionEffectType by its Minecraft key name.
     * Uses the Registry API for forward compatibility with newer Paper versions.
     *
     * @param key the potion effect key (e.g. "slowness", "wither", "weakness")
     * @return the PotionEffectType, or null if not found
     */
    private PotionEffectType getPotionEffectType(String key) {
        try {
            return org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(key));
        } catch (Exception e) {
            // Fallback to deprecated static fields for older versions
            return switch (key) {
                case "slowness" -> PotionEffectType.SLOWNESS;
                case "wither" -> PotionEffectType.WITHER;
                case "weakness" -> PotionEffectType.WEAKNESS;
                case "poison" -> PotionEffectType.POISON;
                case "regeneration" -> PotionEffectType.REGENERATION;
                case "speed" -> PotionEffectType.SPEED;
                default -> null;
            };
        }
    }
}
