package com.lumiczi.lurpg.combat;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles critical hit logic for the RPG combat system.
 * <p>
 * Determines whether an attack critical hits based on the attacker's
 * critical chance stat, and calculates the resulting critical damage
 * using the configured base multiplier and the attacker's critical
 * damage bonus stat.
 * </p>
 */
public class CritHandler {

    private final LuRPGPlugin plugin;

    public CritHandler(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks whether an attack critically hits.
     * <p>
     * Rolls a random number against the attacker's critical chance percentage.
     * A critical hit occurs if the random roll (0-99.99) is less than the
     * critical chance value.
     * </p>
     *
     * @param attackerStats the attacker's stat map
     * @return true if the attack is a critical hit
     */
    public boolean checkCrit(StatMap attackerStats) {
        if (attackerStats == null) {
            return false;
        }
        double critChance = attackerStats.get(StatType.CRITICAL_CHANCE);
        if (critChance <= 0) {
            return false;
        }
        return ThreadLocalRandom.current().nextDouble(100.0) < critChance;
    }

    /**
     * Calculates the damage after applying the critical hit multiplier.
     * <p>
     * The formula is: {@code baseDamage * (critBaseMultiplier + critDamageBonus / 100)}
     * where {@code critBaseMultiplier} is loaded from config (default 1.5 = 150%)
     * and {@code critDamageBonus} is the attacker's CRITICAL_DAMAGE stat.
     * </p>
     * <p>
     * For example, with a base multiplier of 1.5 and a critical damage stat of 50%,
     * the final multiplier is 2.0 (200% damage).
     * </p>
     *
     * @param baseDamage    the base damage before critical hit
     * @param attackerStats the attacker's stat map
     * @return the damage after critical hit multiplier
     */
    public double calculateCritDamage(double baseDamage, StatMap attackerStats) {
        double critBaseMultiplier = plugin.getStatManager().getCritBaseMultiplier();
        double critDamageBonus = 0;
        if (attackerStats != null) {
            critDamageBonus = attackerStats.get(StatType.CRITICAL_DAMAGE);
        }
        double finalMultiplier = critBaseMultiplier + (critDamageBonus / 100.0);
        return baseDamage * finalMultiplier;
    }
}
