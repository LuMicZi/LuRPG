package com.lumiczi.lurpg.combat;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * The core damage calculation engine for the RPG combat system.
 * <p>
 * Computes the final damage result by:
 * <ol>
 *   <li>Determining the attack type (physical or magical) based on which stat is higher</li>
 *   <li>Applying the defender's defense reduction (diminishing returns formula)</li>
 *   <li>Applying a minimum damage floor of 1</li>
 *   <li>Checking for and applying critical hit multiplier</li>
 *   <li>Adding elemental damage (which bypasses defense)</li>
 *   <li>Calculating lifesteal amount</li>
 * </ol>
 * </p>
 * <p>
 * The defense formula used is: {@code damage * (100 / (100 + defense))}
 * which provides diminishing returns - each point of defense is less effective
 * than the previous one.
 * </p>
 */
public class DamageCalculator {

    private final LuRPGPlugin plugin;
    private final CritHandler critHandler;
    private final LifestealHandler lifestealHandler;
    private final ElementalDamage elementalDamage;

    public DamageCalculator(LuRPGPlugin plugin) {
        this.plugin = plugin;
        this.critHandler = new CritHandler(plugin);
        this.lifestealHandler = new LifestealHandler(plugin);
        this.elementalDamage = new ElementalDamage(plugin);
    }

    /**
     * Calculates the complete damage result for an attack.
     * <p>
     * The method determines whether the attack is physical or magical based
     * on which attack stat is higher, applies defense reduction, checks for
     * critical hits, adds elemental damage, and computes lifesteal.
     * </p>
     *
     * @param attacker      the attacking player
     * @param target        the target entity
     * @param attackerStats the attacker's full stat map
     * @param defenderStats the defender's stat map (may be null for non-player targets)
     * @return a DamageResult containing the full damage breakdown
     */
    public DamageResult calculateDamage(Player attacker, LivingEntity target,
                                         StatMap attackerStats, StatMap defenderStats) {
        DamageResult result = new DamageResult();

        if (attackerStats == null) {
            result.setFinalDamage(1.0);
            return result;
        }

        // 1. Determine attack type: physical if physAtk >= magAtk, else magical
        double physAtk = attackerStats.get(StatType.PHYSICAL_ATTACK);
        double magAtk = attackerStats.get(StatType.MAGICAL_ATTACK);
        boolean isPhysical = physAtk >= magAtk;
        double baseDamage = isPhysical ? physAtk : magAtk;

        // 2. Apply defense reduction
        double damageAfterDef = calculateDefense(defenderStats, baseDamage, isPhysical);

        // 3. Minimum damage floor of 1
        damageAfterDef = Math.max(1.0, damageAfterDef);

        // 4. Check for critical hit
        if (critHandler.checkCrit(attackerStats)) {
            result.setCrit(true);
            damageAfterDef = critHandler.calculateCritDamage(damageAfterDef, attackerStats);
        }

        // Store physical or magical damage
        if (isPhysical) {
            result.setPhysicalDamage(damageAfterDef);
        } else {
            result.setMagicalDamage(damageAfterDef);
        }

        // 5. Calculate elemental damage (not affected by defense)
        Map<String, Double> elements = collectElementalDamage(attackerStats);
        result.setElementDamage(elements);

        // 6. Calculate lifesteal
        double totalDamage = result.getTotalDamage();
        double lifesteal = lifestealHandler.calculateLifesteal(totalDamage, attackerStats);
        result.setLifestealAmount(lifesteal);

        // Set final damage
        result.setFinalDamage(totalDamage);

        return result;
    }

    /**
     * Calculates the damage after applying the defender's defense.
     * <p>
     * Uses a diminishing returns formula: {@code damage * (100 / (100 + defense))}
     * This ensures that defense is always useful but never makes the target
     * completely immune to damage.
     * </p>
     *
     * @param defenderStats   the defender's stat map (may be null)
     * @param incomingDamage  the incoming damage before defense
     * @param isPhysical      true if the damage is physical, false if magical
     * @return the damage after defense reduction
     */
    public double calculateDefense(StatMap defenderStats, double incomingDamage, boolean isPhysical) {
        if (defenderStats == null || incomingDamage <= 0) {
            return incomingDamage;
        }

        double defense = isPhysical
                ? defenderStats.get(StatType.PHYSICAL_DEFENSE)
                : defenderStats.get(StatType.MAGICAL_DEFENSE);

        if (defense <= 0) {
            return incomingDamage;
        }

        // Diminishing returns: damage * (100 / (100 + defense))
        return incomingDamage * (100.0 / (100.0 + defense));
    }

    /**
     * Collects all elemental damage values from the attacker's stat map.
     *
     * @param stats the attacker's stat map
     * @return a map of element name to damage value
     */
    private Map<String, Double> collectElementalDamage(StatMap stats) {
        Map<String, Double> elements = new HashMap<>();
        addElementIfPositive(elements, "fire", stats.get(StatType.FIRE_DAMAGE));
        addElementIfPositive(elements, "ice", stats.get(StatType.ICE_DAMAGE));
        addElementIfPositive(elements, "thunder", stats.get(StatType.THUNDER_DAMAGE));
        addElementIfPositive(elements, "dark", stats.get(StatType.DARK_DAMAGE));
        addElementIfPositive(elements, "light", stats.get(StatType.LIGHT_DAMAGE));
        return elements;
    }

    private void addElementIfPositive(Map<String, Double> map, String name, double value) {
        if (value > 0) {
            map.put(name, value);
        }
    }

    /**
     * Returns the CritHandler instance used by this calculator.
     *
     * @return the crit handler
     */
    public CritHandler getCritHandler() {
        return critHandler;
    }

    /**
     * Returns the LifestealHandler instance used by this calculator.
     *
     * @return the lifesteal handler
     */
    public LifestealHandler getLifestealHandler() {
        return lifestealHandler;
    }

    /**
     * Returns the ElementalDamage instance used by this calculator.
     *
     * @return the elemental damage handler
     */
    public ElementalDamage getElementalDamage() {
        return elementalDamage;
    }
}
