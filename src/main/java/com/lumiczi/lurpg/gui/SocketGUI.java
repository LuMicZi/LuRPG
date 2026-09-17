package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.item.gem.GemManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for the gem socketing system.
 * <p>
 * Layout (54 slots, 6 rows):
 * - Slot 13 (row 2, col 5): Equipment slot
 * - Slots 20,21,22,23 (row 3): Gem sockets 1-4
 * - Slots 29,30,31,32 (row 4): Gem sockets 5-8
 * - All other slots: decorative filler
 * </p>
 * <p>
 * Interaction:
 * - Drag a gem item from inventory onto an empty socket to socket it
 * - Left-click a socketed gem to unsocket it (with keep chance)
 * </p>
 */
public class SocketGUI implements InventoryHolder {

    private final LuRPGPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private static final int INVENTORY_SIZE = 54;
    private static final int SLOT_EQUIPMENT = 13;

    // 8 socket slot positions (0-7 index maps to these inventory slots)
    private static final int[] SOCKET_SLOTS = {
            20, 21, 22, 23,  // row 3, cols 3-6
            29, 30, 31, 32   // row 4, cols 3-6
    };

    public SocketGUI(LuRPGPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(this, INVENTORY_SIZE,
                GUIHelper.parse("<dark_purple>宝石镶嵌"));
    }

    /**
     * Opens the socket GUI for the player.
     *
     * @param plugin the plugin instance
     * @param player the target player
     */
    public static void open(LuRPGPlugin plugin, Player player) {
        SocketGUI gui = new SocketGUI(plugin, player);
        gui.fillDecorations();
        gui.refreshSockets();
        player.openInventory(gui.getInventory());
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Fills the GUI with decorative glass panes.
     */
    private void fillDecorations() {
        ItemStack filler = GUIHelper.createFiller(Material.PURPLE_STAINED_GLASS_PANE);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i != SLOT_EQUIPMENT && !isSocketSlot(i)) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Checks if a slot is one of the 8 gem socket slots.
     *
     * @param slot the inventory slot index
     * @return the socket index (0-7) or -1 if not a socket slot
     */
    private int getSocketIndex(int slot) {
        for (int i = 0; i < SOCKET_SLOTS.length; i++) {
            if (SOCKET_SLOTS[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private boolean isSocketSlot(int slot) {
        return getSocketIndex(slot) >= 0;
    }

    /**
     * Refreshes the gem socket display based on the current equipment.
     */
    private void refreshSockets() {
        ItemStack equipment = inventory.getItem(SLOT_EQUIPMENT);
        GemManager gemManager = plugin.getItemManager().getGemManager();

        if (equipment == null || equipment.getType() == Material.AIR
                || !plugin.getItemManager().isRPGItem(equipment)) {
            // No valid equipment - show locked sockets
            for (int i = 0; i < 8; i++) {
                inventory.setItem(SOCKET_SLOTS[i], createLockedSocketItem());
            }
            return;
        }

        List<String> sockets = plugin.getItemManager().getSockets(equipment);
        for (int i = 0; i < 8; i++) {
            String gemId = sockets.get(i);
            if (gemId != null && !gemId.isBlank()) {
                // Socket has a gem - show the gem item
                ItemStack gemItem = gemManager.createGemItem(gemId, 1);
                if (gemItem != null) {
                    // Add unsocket hint to lore
                    ItemMeta meta = gemItem.getItemMeta();
                    if (meta != null) {
                        List<net.kyori.adventure.text.Component> lore = meta.lore();
                        if (lore == null) {
                            lore = new ArrayList<>();
                        }
                        List<net.kyori.adventure.text.Component> newLore = new ArrayList<>(lore);
                        newLore.add(GUIHelper.parse(""));
                        newLore.add(GUIHelper.parse("<yellow>点击拆除宝石"));
                        double keepRate = gemManager.getUnsocketKeepRate() * 100;
                        newLore.add(GUIHelper.parse("<gray>保留率: " + (int)keepRate + "%"));
                        meta.lore(newLore);
                        gemItem.setItemMeta(meta);
                    }
                    inventory.setItem(SOCKET_SLOTS[i], gemItem);
                } else {
                    inventory.setItem(SOCKET_SLOTS[i], createEmptySocketItem());
                }
            } else {
                // Empty socket
                inventory.setItem(SOCKET_SLOTS[i], createEmptySocketItem());
            }
        }
    }

    /**
     * Creates an empty socket slot item.
     */
    private ItemStack createEmptySocketItem() {
        return GUIHelper.createItem(
                Material.GRAY_STAINED_GLASS_PANE,
                "<gray>空槽位",
                List.of(
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<gray>将宝石拖入此处",
                        "<gray>进行镶嵌"
                )
        );
    }

    /**
     * Creates a locked socket slot item (when no equipment is present).
     */
    private ItemStack createLockedSocketItem() {
        return GUIHelper.createItem(
                Material.BLACK_STAINED_GLASS_PANE,
                "<dark_gray>未解锁",
                List.of(
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<gray>放入装备后",
                        "<gray>可镶嵌宝石"
                )
        );
    }

    /**
     * Handles inventory click events for this GUI.
     *
     * @param event the click event
     */
    public void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        // Player inventory - allow normal behavior, refresh after
        if (rawSlot >= INVENTORY_SIZE) {
            plugin.getServer().getScheduler().runTask(plugin, this::refreshSockets);
            return;
        }

        // Equipment slot - allow placing/removing
        if (rawSlot == SLOT_EQUIPMENT) {
            // Only allow RPG items to be placed? Let's allow any item for now
            // and check in refreshSockets
            plugin.getServer().getScheduler().runTask(plugin, this::refreshSockets);
            return;
        }

        // Socket slots
        int socketIndex = getSocketIndex(rawSlot);
        if (socketIndex >= 0) {
            handleSocketClick(event, socketIndex);
            return;
        }

        // Decorative slots - cancel
        event.setCancelled(true);
    }

    /**
     * Handles a click on a gem socket slot.
     *
     * @param event       the click event
     * @param socketIndex the socket index (0-7)
     */
    private void handleSocketClick(InventoryClickEvent event, int socketIndex) {
        ItemStack equipment = inventory.getItem(SLOT_EQUIPMENT);
        if (equipment == null || equipment.getType() == Material.AIR
                || !plugin.getItemManager().isRPGItem(equipment)) {
            event.setCancelled(true);
            return;
        }

        List<String> sockets = plugin.getItemManager().getSockets(equipment);
        String currentGemId = sockets.get(socketIndex);

        // Get the item on cursor / being placed
        ItemStack cursor = event.getCursor();
        boolean hasCursorItem = cursor != null && cursor.getType() != Material.AIR;

        GemManager gemManager = plugin.getItemManager().getGemManager();

        if (currentGemId != null && !currentGemId.isBlank()) {
            // Socket is occupied - left click to unsocket
            event.setCancelled(true);

            // Only handle left clicks for unsocketing
            if (!event.isLeftClick()) {
                return;
            }

            // Try to unsocket
            String removedGemId = plugin.getItemManager().unsocketGem(equipment, socketIndex);
            if (removedGemId != null) {
                // Roll for keep
                double keepRate = gemManager.getUnsocketKeepRate();
                boolean kept = Math.random() < keepRate;

                if (kept) {
                    // Return gem to player
                    ItemStack gemItem = gemManager.createGemItem(removedGemId, 1);
                    if (gemItem != null) {
                        var leftover = player.getInventory().addItem(gemItem);
                        leftover.forEach((idx, leftoverItem) ->
                                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
                    }
                    player.sendMessage(miniMessage.deserialize(
                            "<green>宝石拆除成功！宝石已返还到背包。"));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } else {
                    player.sendMessage(miniMessage.deserialize(
                            "<red>宝石拆除失败，宝石碎裂了..."));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }

                // Regenerate equipment lore and update display
                plugin.getItemManager().regenerateLore(equipment);
                inventory.setItem(SLOT_EQUIPMENT, equipment);
                refreshSockets();
            }
        } else if (hasCursorItem && gemManager.isGem(cursor)) {
            // Socket is empty and cursor has a gem - socket the gem
            event.setCancelled(true);

            String gemId = gemManager.getGemId(cursor);
            if (gemId == null) {
                return;
            }

            boolean success = plugin.getItemManager().socketGem(equipment, socketIndex, gemId);
            if (success) {
                // Consume one gem from cursor
                cursor.setAmount(cursor.getAmount() - 1);
                if (cursor.getAmount() <= 0) {
                    event.setCursor(null);
                }

                // Regenerate equipment lore
                plugin.getItemManager().regenerateLore(equipment);
                inventory.setItem(SLOT_EQUIPMENT, equipment);

                GemManager.Gem gem = gemManager.getGem(gemId);
                String gemName = gem != null ? gem.name() : gemId;
                player.sendMessage(miniMessage.deserialize(
                        "<green>宝石镶嵌成功！" + gemName + " <green>已嵌入槽位 " + (socketIndex + 1) + "。"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

                refreshSockets();
            }
        } else {
            // Empty socket but no gem on cursor - cancel
            event.setCancelled(true);
        }
    }

    /**
     * Handles inventory close events.
     * Returns the equipment item to the player if present.
     *
     * @param event the close event
     */
    public void handleClose(InventoryCloseEvent event) {
        ItemStack equipment = inventory.getItem(SLOT_EQUIPMENT);
        if (equipment != null && equipment.getType() != Material.AIR) {
            var leftover = player.getInventory().addItem(equipment);
            leftover.forEach((index, leftoverItem) ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
            inventory.setItem(SLOT_EQUIPMENT, null);
        }
    }
}
