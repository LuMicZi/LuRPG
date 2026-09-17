package com.lumiczi.lurpg.combat;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/**
 * Handles lifesteal logic for the RPG combat system.
 * <p>
 * Lifesteal restores the attacker's health by a percentage of the damage dealt.
 * The lifesteal percentage is capped by the configured lifesteal cap to prevent
 * excessive healing.
 * </p>
 */
public class LifestealHandler {

    private final LuRPGPlugin plugin;

    public LifestealHandler(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calculates the lifesteal amount based on the damage dealt and the attacker's
     * lifesteal stat.
     * <p>
     * The lifesteal percentage is capped by the configured {@code lifesteal-cap}
     * value from {@code config.yml}. The actual heal amount is:
     * {@code damage * (min(lifestealStat, lifestealCap) / 100)}
     * </p>
     *
     * @param damage        the damage dealt
     * @param attackerStats the attacker's stat map
     * @return the amount of health to restore
     */
    public double calculateLifesteal(double damage, StatMap attackerStats) {
        if (attackerStats == null || damage <= 0) {
            return 0;
        }
        double lifestealPercent = attackerStats.get(StatType.LIFESTEAL);
        if (lifestealPercent <= 0) {
            return 0;
        }
        double cap = plugin.getStatManager().getLifestealCap();
        lifestealPercent = Math.min(lifestealPercent, cap);
        return damage * (lifestealPercent / 100.0);
    }

    /**
     * Handles lifesteal for an attacker, restoring their health based on damage dealt.
     * <p>
     * The health restored is capped by the player's maximum health to prevent
     * overhealing. If the player is dead or the lifesteal amount is 0, no action
     * is taken.
     * </p>
     *
     * @param attacker      the attacking player
     * @param damageDealt   the damage dealt in the attack
     * @param attackerStats the attacker's stat map
     * @return the actual amount of health restored
     */
    public double handleLifesteal(Player attacker, double damageDealt, StatMap attackerStats) {
        double lifestealAmount = calculateLifesteal(damageDealt, attackerStats);
        if (lifestealAmount <= 0 || attacker == null || attacker.isDead()) {
            return 0;
        }

        double currentHealth = attacker.getHealth();
        double maxHealth = currentHealth; // fallback
        var maxHealthAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealth = maxHealthAttr.getValue();
        }

        // Also consider RPG MAX_HEALTH stat if it's higher than vanilla max
        if (attackerStats != null) {
            double rpgMaxHealth = attackerStats.get(StatType.MAX_HEALTH);
            if (rpgMaxHealth > maxHealth) {
                maxHealth = rpgMaxHealth;
            }
        }

        double newHealth = Math.min(currentHealth + lifestealAmount, maxHealth);
        double actualHeal = newHealth - currentHealth;

        if (actualHeal > 0) {
            attacker.setHealth(newHealth);
        }

        return actualHeal;
    }
}
