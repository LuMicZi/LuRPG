package com.lumiczi.lurpg.item;

import com.lumiczi.lurpg.LuRPGPlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Set;

/**
 * Event listener for the seal/unseal binding system.
 * <p>
 * Handles:
 * <ul>
 *   <li>Auto-unseal when a player equips a sealed RPG item</li>
 *   <li>Preventing drops of unsealed (bound) items</li>
 *   <li>Preventing placement of bound items into containers</li>
 *   <li>Keeping bound items on death</li>
 *   <li>Seal scroll usage to re-seal unsealed items</li>
 * </ul>
 */
public class SealListener implements Listener {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SealListener(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== Auto-Unseal on Equip ====================

    /**
     * Handles main hand slot switching. If the newly held item is a sealed
     * RPG item, it is automatically unsealed (bound).
     *
     * @param event the player item held event
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (!isSealEnabled() || !isAutoUnsealOnEquip()) {
            return;
        }
        Player player = event.getPlayer();
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return;
        }

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem == null || newItem.getType().isAir()) {
            return;
        }
        if (!itemManager.isRPGItem(newItem)) {
            return;
        }
        if (itemManager.isItemSealed(newItem)) {
            itemManager.unsealItem(newItem);
            playUnsealEffects(player);
            player.sendMessage(miniMessage.deserialize("<red>装备已解封并绑定！"));
        }
    }

    /**
     * Handles inventory clicks on armor slots. If a sealed RPG item is placed
     * into an armor slot, it is automatically unsealed (bound).
     * <p>
     * Also handles the anti-container logic for bound items.
     *
     * @param event the inventory click event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isSealEnabled()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return;
        }

        // --- Anti-container: prevent moving bound items into containers ---
        if (isPreventContainer()) {
            ItemStack cursorItem = event.getCursor();
            ItemStack currentItem = event.getCurrentItem();

            // If the clicked inventory is a container type (not player's own inventory)
            if (isContainerInventory(event.getInventory().getType())) {
                // Player is trying to place a bound item into a container
                if (isBoundRPGItem(cursorItem, itemManager) || isBoundRPGItem(currentItem, itemManager)) {
                    event.setCancelled(true);
                    player.sendMessage(miniMessage.deserialize("<red>绑定物品无法放入容器！"));
                    return;
                }
            }

            // Shift-click: if player shift-clicks a bound item while a container is open
            if (event.getClick().isShiftClick()) {
                if (isContainerInventory(event.getInventory().getType())) {
                    ItemStack shifted = event.getCurrentItem();
                    if (isBoundRPGItem(shifted, itemManager)) {
                        event.setCancelled(true);
                        player.sendMessage(miniMessage.deserialize("<red>绑定物品无法放入容器！"));
                        return;
                    }
                }
            }
        }

        // --- Auto-unseal on armor slot equip ---
        if (isAutoUnsealOnEquip() && event.getSlotType() == InventoryType.SlotType.ARMOR) {
            // Check the item being placed into the armor slot
            ItemStack placedItem = event.getCursor();
            if (placedItem != null && !placedItem.getType().isAir()
                    && itemManager.isRPGItem(placedItem)
                    && itemManager.isItemSealed(placedItem)) {
                // Unseal after the click is processed
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        checkAndUnsealEquipment(player);
                    }
                });
            }
        }

        // --- Auto-unseal for off-hand slot (slot 40 in player inventory) ---
        if (isAutoUnsealOnEquip() && event.getSlotType() == InventoryType.SlotType.QUICKBAR
                && event.getSlot() == 40) {
            ItemStack placedItem = event.getCursor();
            if (placedItem != null && !placedItem.getType().isAir()
                    && itemManager.isRPGItem(placedItem)
                    && itemManager.isItemSealed(placedItem)) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        checkAndUnsealEquipment(player);
                    }
                });
            }
        }
    }

    // ==================== Prevent Drop ====================

    /**
     * Prevents players from dropping unsealed (bound) items.
     *
     * @param event the player drop item event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!isSealEnabled() || !isPreventDrop()) {
            return;
        }
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return;
        }
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        if (isBoundRPGItem(droppedItem, itemManager)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(miniMessage.deserialize("<red>绑定物品无法丢弃！"));
        }
    }

    // ==================== Keep on Death ====================

    /**
     * Ensures bound items are kept on death by removing them from the drop
     * list and adding them to the keep list.
     *
     * @param event the player death event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isSealEnabled() || !isKeepOnDeath()) {
            return;
        }
        if (!event.getKeepInventory()) {
            // Only process if keepInventory gamerule is off
            ItemManager itemManager = plugin.getItemManager();
            if (itemManager == null) {
                return;
            }

            // Remove bound items from drops and keep them
            var drops = event.getDrops();
            var toKeep = event.getItemsToKeep();
            var iterator = drops.iterator();
            while (iterator.hasNext()) {
                ItemStack drop = iterator.next();
                if (isBoundRPGItem(drop, itemManager)) {
                    toKeep.add(drop.clone());
                    iterator.remove();
                }
            }
        }
    }

    // ==================== Seal Scroll Usage ====================

    /**
     * Handles right-click usage of seal scrolls. The player must hold a seal
     * scroll in one hand and the target equipment in the other.
     *
     * @param event the player interact event
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!isSealEnabled()) {
            return;
        }
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

        // Determine which hand holds the seal scroll and which holds the target
        boolean mainHandIsScroll = itemManager.isSealScroll(mainHand);
        boolean offHandIsScroll = itemManager.isSealScroll(offHand);

        if (!mainHandIsScroll && !offHandIsScroll) {
            return;
        }

        if (mainHandIsScroll && offHandIsScroll) {
            // Both hands have scrolls — ambiguous
            return;
        }

        ItemStack targetItem;
        EquipmentSlot scrollSlot;

        if (mainHandIsScroll) {
            targetItem = offHand;
            scrollSlot = EquipmentSlot.HAND;
        } else {
            targetItem = mainHand;
            scrollSlot = EquipmentSlot.OFF_HAND;
        }

        // Cancel the event so the scroll isn't used normally
        event.setCancelled(true);

        // Validate target
        if (targetItem == null || targetItem.getType().isAir()) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>请将需要封印的装备放在另一只手上！"));
            return;
        }

        if (!itemManager.isRPGItem(targetItem)) {
            player.sendMessage(miniMessage.deserialize(
                    "<red>目标物品不是 RPG 装备，无法封印！"));
            return;
        }

        if (itemManager.isItemSealed(targetItem)) {
            player.sendMessage(miniMessage.deserialize(
                    "<gold>该装备已经是封印状态，无需使用封券。"));
            return;
        }

        // Seal the item
        itemManager.sealItem(targetItem);

        // Consume one seal scroll
        consumeOneItem(player, scrollSlot);

        // Visual and sound effects
        playSealEffects(player);
        player.sendMessage(miniMessage.deserialize(
                "<green>封印成功！物品已恢复可交易状态"));
    }

    // ==================== Helper Methods ====================

    /**
     * Checks all equipment slots of a player and unseals any sealed RPG items.
     *
     * @param player the player to check
     */
    private void checkAndUnsealEquipment(Player player) {
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) {
            return;
        }
        PlayerInventory inv = player.getInventory();
        ItemStack[] equipment = {
                inv.getItemInMainHand(),
                inv.getItemInOffHand(),
                inv.getHelmet(),
                inv.getChestplate(),
                inv.getLeggings(),
                inv.getBoots()
        };
        boolean unsealed = false;
        for (ItemStack item : equipment) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (itemManager.isRPGItem(item) && itemManager.isItemSealed(item)) {
                itemManager.unsealItem(item);
                unsealed = true;
            }
        }
        if (unsealed) {
            playUnsealEffects(player);
            player.sendMessage(miniMessage.deserialize("<red>装备已解封并绑定！"));
        }
    }

    /**
     * Checks whether an item is an unsealed (bound) RPG item.
     *
     * @param item        the item to check
     * @param itemManager the item manager
     * @return true if the item is a bound RPG item
     */
    private boolean isBoundRPGItem(ItemStack item, ItemManager itemManager) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return itemManager.isRPGItem(item) && itemManager.isItemUnsealed(item);
    }

    /**
     * Checks whether the given inventory type is a container that should
     * prevent bound item placement.
     *
     * @param type the inventory type
     * @return true if it is a container type
     */
    private boolean isContainerInventory(InventoryType type) {
        if (type == null) {
            return false;
        }
        return switch (type.name()) {
            case "CHEST", "BARREL", "HOPPER", "DISPENSER", "DROPPER", "SHULKER_BOX",
                 "ENDER_CHEST", "BLAST_FURNACE", "FURNACE", "SMOKER",
                 "BREWING_STAND", "BREWING",
                 "LECTERN", "CHISELED_BOOKSHELF", "DECORATED_POT" -> true;
            default -> false;
        };
    }

    /**
     * Consumes one item from the specified equipment slot.
     *
     * @param player the player
     * @param slot   the equipment slot
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

    // ==================== Visual Effects ====================

    private void playSealEffects(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        player.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
        player.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);

        for (int i = 0; i < 32; i++) {
            double angle = (Math.PI * 2 / 32) * i;
            double x = Math.cos(angle) * 1.5;
            double z = Math.sin(angle) * 1.5;
            player.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(x, -0.8, z), 5);
        }

        for (int i = 0; i < 4; i++) {
            final int delay = i * 4;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.getWorld().spawnParticle(Particle.END_ROD,
                        player.getLocation().add(0, 1, 0), 10, 0.5, 1.0, 0.5, 0.02);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
            }, delay);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.END_ROD,
                    player.getLocation().add(0, 1, 0), 20, 0.8, 1.2, 0.8, 0.05);
        }, 18L);
    }

    private void playUnsealEffects(Player player) {
        Location loc = player.getLocation().add(0, 1, 0);

        player.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 1.2f);
        player.getWorld().playSound(loc, Sound.BLOCK_SOUL_SAND_PLACE, 0.8f, 0.6f);

        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2 / 20) * i;
            double x = Math.cos(angle) * 1.2;
            double z = Math.sin(angle) * 1.2;
            player.getWorld().spawnParticle(Particle.WITCH, loc.clone().add(x, -0.5, z), 3);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.getWorld().spawnParticle(Particle.WITCH,
                    player.getLocation().add(0, 1, 0), 15, 0.6, 0.8, 0.6, 0.01);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.5f, 0.5f);
        }, 8L);
    }

    // ==================== Config Helpers ====================

    private boolean isSealEnabled() {
        FileConfiguration config = plugin.getConfig();
        return config.getBoolean("seal.enabled", true);
    }

    private boolean isAutoUnsealOnEquip() {
        return plugin.getConfig().getBoolean("seal.auto-unseal-on-equip", true);
    }

    private boolean isPreventDrop() {
        return plugin.getConfig().getBoolean("seal.prevent-drop", true);
    }

    private boolean isPreventContainer() {
        return plugin.getConfig().getBoolean("seal.prevent-container", true);
    }

    private boolean isKeepOnDeath() {
        return plugin.getConfig().getBoolean("seal.keep-on-death", true);
    }
}
