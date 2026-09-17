package com.lumiczi.lurpg.skill;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.item.ItemManager;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.skill.api.Skill;
import com.lumiczi.lurpg.skill.api.SkillCastMode;
import com.lumiczi.lurpg.skill.api.SkillTrigger;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Event listener for skill triggers.
 * <p>
 * Listens for player interactions (clicks) to trigger active skills, and for
 * combat events (damage, death) to trigger passive skills.
 * Also handles equipment-bound passive skills that trigger when worn.
 * </p>
 */
public class SkillListener implements Listener {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SkillListener(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handles player click interactions to trigger active skills.
     * <p>
     * Detects right-click, left-click, and their sneak variants, then dispatches
     * to the matching equipped active skill via {@link SkillManager#castByMode}.
     * </p>
     *
     * @param event the player interact event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Only handle click actions on air or blocks (not physical interactions)
        if (action == Action.PHYSICAL) {
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        SkillCastMode castMode = resolveCastMode(action, player.isSneaking());
        if (castMode == null) {
            return;
        }

        // 1. Try to cast item-bound skill first (right click only)
        if (castMode == SkillCastMode.RIGHT_CLICK || castMode == SkillCastMode.SNEAK_RIGHT_CLICK) {
            if (tryCastItemSkill(player)) {
                return;
            }
        }

        // 2. Fall back to equipped skills
        if (data.getEquippedSkillList().isEmpty()) {
            return;
        }

        plugin.getSkillManager().castByMode(player, castMode);
    }

    /**
     * Tries to cast the first active skill bound to the player's main hand item.
     *
     * @param player the player
     * @return true if a skill was cast (or attempted), false if no item skill found
     */
    private boolean tryCastItemSkill(Player player) {
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return false;
        }

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand == null || mainHand.getType().isAir()) {
            return false;
        }

        var itemSkills = itemManager.getItemSkills(mainHand);
        if (itemSkills == null || itemSkills.isEmpty()) {
            return false;
        }

        // Find the first active skill on the item
        for (String skillId : itemSkills) {
            var skill = plugin.getSkillManager().getSkill(skillId);
            if (skill != null && skill.getTrigger() == SkillTrigger.ACTIVE) {
                plugin.getSkillManager().castSkill(player, skillId);
                return true;
            }
        }

        return false;
    }

    /**
     * Resolves the SkillCastMode from the interact action and sneak state.
     *
     * @param action the interact action
     * @param sneaking whether the player is sneaking
     * @return the matching SkillCastMode, or null if no match
     */
    private SkillCastMode resolveCastMode(Action action, boolean sneaking) {
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            return sneaking ? SkillCastMode.SNEAK_RIGHT_CLICK : SkillCastMode.RIGHT_CLICK;
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            return sneaking ? SkillCastMode.SNEAK_LEFT_CLICK : SkillCastMode.LEFT_CLICK;
        }
        return null;
    }

    /**
     * Handles entity damage events to trigger ON_ATTACK and ON_DAMAGED passive skills.
     * <p>
     * When a player deals damage to an entity, ON_ATTACK skills are triggered.
     * When a player takes damage, ON_DAMAGED skills are triggered.
     * Both equipped skills and equipment-bound passive skills are triggered.
     * </p>
     *
     * @param event the entity damage by entity event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // ON_ATTACK: player is the damager
        if (event.getDamager() instanceof Player attacker) {
            PlayerData attackerData = plugin.getPlayerManager().getPlayerData(attacker.getUniqueId());
            if (attackerData != null) {
                LivingEntity target = (event.getEntity() instanceof LivingEntity le) ? le : null;
                List<String> equipSkills = getEquipmentPassiveSkills(attacker, SkillTrigger.ON_ATTACK);
                if (!attackerData.getEquippedSkillList().isEmpty() || !equipSkills.isEmpty()) {
                    plugin.getSkillManager().triggerPassive(attacker, SkillTrigger.ON_ATTACK, target, equipSkills);
                }
            }
        }

        // ON_DAMAGED: player is the victim
        if (event.getEntity() instanceof Player victim) {
            PlayerData victimData = plugin.getPlayerManager().getPlayerData(victim.getUniqueId());
            if (victimData != null) {
                LivingEntity attacker = (event.getDamager() instanceof LivingEntity le) ? le : null;
                List<String> equipSkills = getEquipmentPassiveSkills(victim, SkillTrigger.ON_DAMAGED);
                if (!victimData.getEquippedSkillList().isEmpty() || !equipSkills.isEmpty()) {
                    plugin.getSkillManager().triggerPassive(victim, SkillTrigger.ON_DAMAGED, attacker, equipSkills);
                }
            }
        }
    }

    /**
     * Handles entity death events to trigger ON_KILL passive skills.
     * <p>
     * When a player kills an entity, ON_KILL skills are triggered with the
     * killed entity as the target.
     * Both equipped skills and equipment-bound passive skills are triggered.
     * </p>
     *
     * @param event the entity death event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) {
            return;
        }

        PlayerData killerData = plugin.getPlayerManager().getPlayerData(killer.getUniqueId());
        if (killerData == null) {
            return;
        }

        List<String> equipSkills = getEquipmentPassiveSkills(killer, SkillTrigger.ON_KILL);
        if (killerData.getEquippedSkillList().isEmpty() && equipSkills.isEmpty()) {
            return;
        }

        plugin.getSkillManager().triggerPassive(killer, SkillTrigger.ON_KILL, entity, equipSkills);
    }

    /**
     * Handles main hand item switch events to notify the player about
     * passive skills on the newly equipped weapon.
     *
     * @param event the player item held event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());

        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return;
        }

        if (newItem == null || newItem.getType().isAir()) {
            return;
        }

        Set<String> itemSkills = itemManager.getItemSkills(newItem);
        if (itemSkills == null || itemSkills.isEmpty()) {
            return;
        }

        // Collect passive skill names on this item
        List<String> passiveSkillNames = new ArrayList<>();
        for (String skillId : itemSkills) {
            Skill skill = plugin.getSkillManager().getSkill(skillId);
            if (skill != null && skill.getTrigger().isPassive()) {
                passiveSkillNames.add(skill.getDisplayName());
            }
        }

        if (!passiveSkillNames.isEmpty()) {
            String skillList = String.join(", ", passiveSkillNames);
            player.sendMessage(miniMessage.deserialize(
                    "<green>[LuRPG] <gray>已激活装备被动技能：<aqua>" + skillList));
        }
    }

    /**
     * Gets all passive skills of the specified trigger type that are bound to
     * the player's equipment (main hand, off hand, helmet, chestplate, leggings, boots).
     * <p>
     * Skill IDs are deduplicated across all equipment slots.
     * </p>
     *
     * @param player  the player
     * @param trigger the trigger type to filter by
     * @return a list of unique skill IDs matching the trigger type
     */
    private List<String> getEquipmentPassiveSkills(Player player, SkillTrigger trigger) {
        List<String> result = new ArrayList<>();
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null || player == null || trigger == null) {
            return result;
        }

        Set<String> seen = new HashSet<>();
        PlayerInventory inv = player.getInventory();

        // Check all equipment slots
        ItemStack[] equipment = new ItemStack[] {
                inv.getItemInMainHand(),
                inv.getItemInOffHand(),
                inv.getHelmet(),
                inv.getChestplate(),
                inv.getLeggings(),
                inv.getBoots()
        };

        for (ItemStack item : equipment) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            Set<String> itemSkills = itemManager.getItemSkills(item);
            if (itemSkills == null || itemSkills.isEmpty()) {
                continue;
            }

            for (String skillId : itemSkills) {
                if (skillId == null || skillId.isBlank()) {
                    continue;
                }
                if (seen.contains(skillId)) {
                    continue;
                }

                Skill skill = plugin.getSkillManager().getSkill(skillId);
                if (skill != null && skill.getTrigger() == trigger) {
                    seen.add(skillId);
                    result.add(skillId);
                }
            }
        }

        return result;
    }
}
