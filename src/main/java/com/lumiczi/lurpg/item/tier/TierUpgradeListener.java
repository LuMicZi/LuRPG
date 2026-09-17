package com.lumiczi.lurpg.item.tier;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.item.ItemManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Listens for right-click interactions with tier upgrade items.
 * <p>
 * When a player right-clicks while holding a tier upgrade item,
 * it attempts to upgrade the tier of the item in the opposite hand.
 * <ul>
 *   <li>Main hand = upgrade item → target is offhand item</li>
 *   <li>Offhand = upgrade item → target is main hand item</li>
 * </ul>
 * One upgrade item is consumed on success or failure.
 * It is NOT consumed if the target is invalid or already at max tier.
 */
public class TierUpgradeListener implements Listener {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public TierUpgradeListener(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand = inv.getItemInOffHand();

        // Determine which hand has the upgrade item and which is the target
        ItemStack targetItem;
        EquipmentSlot upgradeSlot;

        boolean mainHandIsUpgrade = itemManager.isTierUpgradeItem(mainHand);
        boolean offHandIsUpgrade = itemManager.isTierUpgradeItem(offHand);

        if (mainHandIsUpgrade && offHandIsUpgrade) {
            // Both hands have upgrade items - ambiguous, do nothing
            return;
        }

        if (mainHandIsUpgrade) {
            targetItem = offHand;
            upgradeSlot = EquipmentSlot.HAND;
        } else if (offHandIsUpgrade) {
            targetItem = mainHand;
            upgradeSlot = EquipmentSlot.OFF_HAND;
        } else {
            return;
        }

        // Cancel the event so the upgrade item isn't used normally
        event.setCancelled(true);

        // Validate target
        if (targetItem == null || targetItem.getType().isAir()) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>请将需要升级的装备放在另一只手上！"));
            return;
        }

        if (!itemManager.isRPGItem(targetItem)) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>目标物品不是 RPG 装备，无法升级品级！"));
            return;
        }

        TierManager tierManager = itemManager.getTierManager();

        // Check if already at max tier
        String currentTierId = itemManager.getItemTier(targetItem);
        if (currentTierId != null && !currentTierId.isBlank()) {
            TierManager.Tier nextTier = tierManager.getNextTier(currentTierId);
            if (nextTier == null) {
                TierManager.Tier currentTier = tierManager.getTier(currentTierId);
                String tierName = currentTier != null ? currentTier.name() : "最高";
                String tierColor = currentTier != null ? currentTier.color() : "<gold>";
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                player.sendMessage(miniMessage.deserialize(
                        "<gold>装备已达到最高品级（" + tierColor + tierName + "品级<gold>），无法继续升级！"));
                return;
            }
        }

        // Consume one upgrade item before attempting (consumed regardless of success/fail)
        consumeOneItem(player, upgradeSlot);

        // Attempt upgrade
        ItemManager.TierUpgradeResult result = itemManager.upgradeItemTier(targetItem);

        // Play sound and send message based on result
        switch (result) {
            case SUCCESS -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                String newTierId = itemManager.getItemTier(targetItem);
                TierManager.Tier newTier = newTierId != null ? tierManager.getTier(newTierId) : null;
                String tierName = newTier != null ? newTier.name() : "未知";
                String tierColor = newTier != null ? newTier.color() : "<white>";
                player.sendMessage(miniMessage.deserialize(
                        "<green>品级升级成功！装备品级已提升为 " + tierColor + tierName + "品级<green>！"));
            }
            case FAILED -> {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                // Show chance info
                if (currentTierId != null) {
                    double chance = tierManager.getUpgradeChance(currentTierId) * 100;
                    player.sendMessage(miniMessage.deserialize(
                            "<red>品级升级失败，品级保持不变。<gray>（成功率: " + (int) chance + "%）"));
                } else {
                    player.sendMessage(miniMessage.deserialize(
                            "<red>品级升级失败，品级保持不变。"));
                }
            }
            case MAX_TIER -> {
                // Shouldn't happen since we checked above, but handle gracefully
                player.sendMessage(miniMessage.deserialize("<gold>装备已达到最高品级！"));
            }
            case NOT_RPG_ITEM -> {
                // Shouldn't happen since we checked above
                player.sendMessage(miniMessage.deserialize("<red>目标物品不是 RPG 装备！"));
            }
        }
    }

    /**
     * Consumes one item from the specified equipment slot.
     */
    private void consumeOneItem(Player player, EquipmentSlot slot) {
        PlayerInventory inv = player.getInventory();
        ItemStack item = switch (slot) {
            case HAND -> inv.getItemInMainHand();
            case OFF_HAND -> inv.getItemInOffHand();
            default -> null;
        };
        if (item == null || item.getType().isAir()) return;

        int newAmount = item.getAmount() - 1;
        if (newAmount <= 0) {
            if (slot == EquipmentSlot.HAND) {
                inv.setItemInMainHand(null);
            } else {
                inv.setItemInOffHand(null);
            }
        } else {
            item.setAmount(newAmount);
        }
    }
}
