package com.lumiczi.lurpg.combat;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.armor.ArmorSetManager;
import com.lumiczi.lurpg.item.ItemManager;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.player.PlayerManager;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatManager;
import com.lumiczi.lurpg.class_.GameClass;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Calculates a player's complete combat stats by aggregating all stat sources.
 * <p>
 * The total combat stats are computed as:
 * <pre>
 *   totalStats = baseStats(class, level) + equipmentStats + setBonuses
 * </pre>
 * Where:
 * <ul>
 *   <li><b>baseStats</b> - calculated from the player's class and level via {@link StatManager}</li>
 *   <li><b>equipmentStats</b> - parsed from each equipped item via {@link ItemManager}</li>
 *   <li><b>setBonuses</b> - obtained from active armor set bonuses via {@link ArmorSetManager}</li>
 * </ul>
 * </p>
 */
public class CombatStatCalculator {

    private final LuRPGPlugin plugin;

    public CombatStatCalculator(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Calculates the player's complete combat stat map.
     * <p>
     * Aggregates base stats (from class and level), equipment stats (from
     * all equipped items), and active armor set bonuses into a single StatMap.
     * </p>
     *
     * @param player the player to calculate stats for
     * @return the combined StatMap, or an empty StatMap if the player has no class
     */
    public StatMap getPlayerCombatStats(Player player) {
        StatMap totalStats = new StatMap();

        if (player == null) {
            return totalStats;
        }

        PlayerManager playerManager = plugin.getPlayerManager();
        if (playerManager == null) {
            return totalStats;
        }

        PlayerData data = playerManager.getPlayerData(player.getUniqueId());
        if (data == null) {
            return totalStats;
        }

        GameClass gameClass = data.getGameClass();
        if (gameClass == null) {
            return totalStats;
        }

        // 1. Base stats from class and level
        StatManager statManager = plugin.getStatManager();
        StatMap baseStats = statManager.calculateBaseStats(gameClass, data.getLevel());
        totalStats.addAll(baseStats);

        // 2. Equipment stats
        StatMap equipmentStats = getEquipmentStats(player);
        totalStats.addAll(equipmentStats);

        // 3. Armor set bonuses
        ArmorSetManager armorSetManager = plugin.getArmorSetManager();
        if (armorSetManager != null) {
            StatMap setBonuses = armorSetManager.getActiveBonuses(player);
            if (setBonuses != null) {
                totalStats.addAll(setBonuses);
            }
        }

        // 4. Apply potion buff effects (temporary multipliers)
        applyPotionBuffs(player, totalStats);

        return totalStats;
    }

    /**
     * Calculates the total stats provided by the player's equipped items.
     * <p>
     * Iterates through all equipment slots (main hand, off hand, helmet,
     * chestplate, leggings, boots) and parses RPG stats from each item.
     * </p>
     *
     * @param player the player whose equipment to check
     * @return a StatMap containing the combined stats from all equipped items
     */
    public StatMap getEquipmentStats(Player player) {
        StatMap equipmentStats = new StatMap();

        if (player == null) {
            return equipmentStats;
        }

        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return equipmentStats;
        }

        PlayerInventory inventory = player.getInventory();
        if (inventory == null) {
            return equipmentStats;
        }

        // Check all equipment slots
        ItemStack[] equipment = {
                inventory.getItemInMainHand(),
                inventory.getItemInOffHand(),
                inventory.getHelmet(),
                inventory.getChestplate(),
                inventory.getLeggings(),
                inventory.getBoots()
        };

        for (ItemStack item : equipment) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            StatMap itemStats = itemManager.getItemStats(item);
            if (itemStats != null && !itemStats.isEmpty()) {
                equipmentStats.addAll(itemStats);
            }
        }

        return equipmentStats;
    }

    /**
     * Applies active potion buff effects to the player's stat map.
     * <p>
     * Buff effects are temporary multipliers applied to specific stats.
     * This method queries the PotionManager's effect handler for active
     * buffs and applies them as multipliers.
     * </p>
     *
     * @param player  the player
     * @param statMap the stat map to apply buffs to (modified in place)
     */
    private void applyPotionBuffs(Player player, StatMap statMap) {
        var potionManager = plugin.getPotionManager();
        if (potionManager == null) {
            return;
        }
        var effectHandler = potionManager.getEffectHandler();
        if (effectHandler == null) {
            return;
        }

        // Get active buffs and apply them as multipliers
        var activeBuffs = effectHandler.getActiveBuffs(player.getUniqueId());
        if (activeBuffs == null || activeBuffs.isEmpty()) {
            return;
        }

        for (var buff : activeBuffs) {
            if (buff.isExpired()) {
                continue;
            }
            double currentValue = statMap.get(buff.stat());
            double buffedValue = currentValue * buff.multiplier();
            statMap.set(buff.stat(), buffedValue);
        }
    }
}
