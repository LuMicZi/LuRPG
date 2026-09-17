package com.lumiczi.lurpg.combat;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.stat.StatMap;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The main combat event listener for the RPG system.
 * <p>
 * Listens for damage events and applies the RPG combat calculation pipeline:
 * <ul>
 *   <li>When a player attacks: calculates RPG damage (physical/magical + crit + elemental + lifesteal)
 *       and overrides the event damage</li>
 *   <li>When a player is attacked by a non-player: applies RPG defense reduction</li>
 *   <li>Handles equipment durability via {@link PlayerItemDamageEvent}</li>
 * </ul>
 * </p>
 * <p>
 * The listener delegates to specialized handlers:
 * {@link DamageCalculator}, {@link CritHandler}, {@link LifestealHandler},
 * {@link ElementalDamage}, and {@link CombatStatCalculator}.
 * </p>
 */
public class CombatListener implements Listener {

    private final LuRPGPlugin plugin;
    private final DamageCalculator damageCalculator;
    private final CritHandler critHandler;
    private final LifestealHandler lifestealHandler;
    private final ElementalDamage elementalDamage;
    private final CombatStatCalculator statCalculator;

    public CombatListener(LuRPGPlugin plugin) {
        this.plugin = plugin;
        this.damageCalculator = new DamageCalculator(plugin);
        this.critHandler = damageCalculator.getCritHandler();
        this.lifestealHandler = damageCalculator.getLifestealHandler();
        this.elementalDamage = damageCalculator.getElementalDamage();
        this.statCalculator = new CombatStatCalculator(plugin);
    }

    /**
     * Handles entity damage by entity events.
     * <p>
     * If the damager is a player (direct or via projectile), the full RPG damage
     * calculation is applied: base damage, defense, critical hits, elemental
     * effects, and lifesteal.
     * </p>
     * <p>
     * If the damager is not a player but the target is, only the defender's
     * RPG defense reduction is applied to the incoming damage.
     * </p>
     *
     * @param event the damage event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Determine the actual attacker (resolve projectile shooters)
        Player attacker = resolveAttacker(event);

        if (attacker != null) {
            // Attacker is a player - full RPG damage calculation
            handlePlayerAttack(attacker, event);
        } else if (event.getEntity() instanceof Player defender) {
            // Defender is a player, attacker is not - apply defense only
            handlePlayerDefend(defender, event);
        }
    }

    /**
     * Resolves the attacking player from the damage event.
     * Handles both direct melee attacks and projectile attacks.
     *
     * @param event the damage event
     * @return the attacking Player, or null if the attacker is not a player
     */
    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    /**
     * Handles a player attack: calculates RPG damage and applies all effects.
     *
     * @param attacker the attacking player
     * @param event    the damage event
     */
    private void handlePlayerAttack(Player attacker, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }

        // Skip if target is the same as attacker (self-damage)
        if (target.equals(attacker)) {
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(attacker.getUniqueId());
        if (data == null || data.getGameClass() == null) {
            return;
        }

        // Get attacker's full combat stats
        StatMap attackerStats = statCalculator.getPlayerCombatStats(attacker);
        if (attackerStats == null || attackerStats.isEmpty()) {
            return;
        }

        // Get defender's stats if the target is a player
        StatMap defenderStats = null;
        if (target instanceof Player defenderPlayer) {
            defenderStats = statCalculator.getPlayerCombatStats(defenderPlayer);
        }

        // Calculate damage
        DamageResult result = damageCalculator.calculateDamage(attacker, target, attackerStats, defenderStats);

        // Set the event damage to the calculated value
        event.setDamage(result.getFinalDamage());

        // Apply elemental effects
        if (!result.getElementDamage().isEmpty()) {
            elementalDamage.applyElementalEffects(target, attacker, result.getElementDamage());
        }

        // Handle lifesteal
        if (result.getLifestealAmount() > 0) {
            lifestealHandler.handleLifesteal(attacker, result.getFinalDamage(), attackerStats);
        }
    }

    /**
     * Handles a player being attacked by a non-player entity.
     * Applies the player's RPG defense to reduce incoming damage.
     *
     * @param defender the defending player
     * @param event    the damage event
     */
    private void handlePlayerDefend(Player defender, EntityDamageByEntityEvent event) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(defender.getUniqueId());
        if (data == null || data.getGameClass() == null) {
            return;
        }

        StatMap defenderStats = statCalculator.getPlayerCombatStats(defender);
        if (defenderStats == null || defenderStats.isEmpty()) {
            return;
        }

        double originalDamage = event.getDamage();
        if (originalDamage <= 0) {
            return;
        }

        // Mob attacks are treated as physical by default
        boolean isPhysical = true;

        // Apply RPG defense
        double reducedDamage = damageCalculator.calculateDefense(defenderStats, originalDamage, isPhysical);
        reducedDamage = Math.max(1.0, reducedDamage);

        event.setDamage(reducedDamage);
    }

    /**
     * Handles equipment durability damage events.
     * <p>
     * RPG items may have customized durability behavior. This method can
     * reduce or cancel durability loss for RPG items based on configuration.
     * Currently, it reduces durability loss for RPG items by 50% as a
     * baseline protection mechanic.
     * </p>
     *
     * @param event the item damage event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        // Check if this is an RPG item
        var itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return;
        }

        String rpgItemId = itemManager.getRPGItemId(item);
        if (rpgItemId == null) {
            return; // Not an RPG item, use vanilla durability
        }

        // Reduce durability loss for RPG items (50% reduction as baseline)
        int originalDamage = event.getDamage();
        int reducedDamage = Math.max(1, originalDamage / 2);
        event.setDamage(reducedDamage);
    }

    // --- Getters for testing/debugging ---

    public DamageCalculator getDamageCalculator() {
        return damageCalculator;
    }

    public CombatStatCalculator getStatCalculator() {
        return statCalculator;
    }
}
