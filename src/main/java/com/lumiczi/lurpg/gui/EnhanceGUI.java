package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.item.enhance.EnhanceManager;
import com.lumiczi.lurpg.core.hook.VaultHook;
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
 * GUI for the item enhancement system.
 * <p>
 * Layout (54 slots, 6 rows):
 * - Slot 13 (row 2, col 5): Equipment slot (place item to enhance)
 * - Slot 31 (row 4, col 5): Enhance button (anvil)
 * - All other slots: decorative glass filler
 * </p>
 * <p>
 * Uses InventoryHolder pattern to allow item placement (unlike the
 * standard GUIManager-based GUIs which cancel all clicks).
 * </p>
 */
public class EnhanceGUI implements InventoryHolder {

    private final LuRPGPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // Slot constants
    private static final int SLOT_EQUIPMENT = 13;
    private static final int SLOT_ENHANCE_BUTTON = 31;
    private static final int INVENTORY_SIZE = 54;

    public EnhanceGUI(LuRPGPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = plugin.getServer().createInventory(this, INVENTORY_SIZE,
                GUIHelper.parse("<dark_blue>装备强化"));
    }

    /**
     * Opens the enhancement GUI for the player.
     *
     * @param plugin the plugin instance
     * @param player the target player
     */
    public static void open(LuRPGPlugin plugin, Player player) {
        EnhanceGUI gui = new EnhanceGUI(plugin, player);
        gui.fillDecorations();
        gui.updateEnhanceButton();
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
        ItemStack filler = GUIHelper.createFiller(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < INVENTORY_SIZE; i++) {
            if (i != SLOT_EQUIPMENT && i != SLOT_ENHANCE_BUTTON) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Updates the enhance button display based on the current equipment.
     */
    private void updateEnhanceButton() {
        ItemStack equipment = inventory.getItem(SLOT_EQUIPMENT);
        EnhanceManager enhanceManager = plugin.getItemManager().getEnhanceManager();

        if (equipment == null || equipment.getType() == Material.AIR) {
            // No equipment - disabled button
            inventory.setItem(SLOT_ENHANCE_BUTTON, GUIHelper.createItem(
                    Material.ANVIL,
                    "<gray>强化",
                    List.of(
                            "<dark_gray>━━━━━━━━━━━━━━━━━━",
                            "<gray>请放入装备",
                            "<gray>进行强化"
                    )
            ));
            return;
        }

        if (!plugin.getItemManager().isRPGItem(equipment)) {
            // Not an RPG item
            inventory.setItem(SLOT_ENHANCE_BUTTON, GUIHelper.createItem(
                    Material.BARRIER,
                    "<red>无法强化",
                    List.of(
                            "<dark_gray>━━━━━━━━━━━━━━━━━━",
                            "<red>该物品不是 RPG 装备"
                    )
            ));
            return;
        }

        int currentLevel = enhanceManager.getEnhanceLevel(equipment);
        int maxLevel = enhanceManager.getMaxLevel();

        if (currentLevel >= maxLevel) {
            // Already max level
            inventory.setItem(SLOT_ENHANCE_BUTTON, GUIHelper.createItem(
                    Material.ENCHANTED_BOOK,
                    "<gold>已满级",
                    List.of(
                            "<dark_gray>━━━━━━━━━━━━━━━━━━",
                            "<yellow>当前等级: +" + currentLevel,
                            "<gold>已达到最高强化等级"
                    )
            ));
            return;
        }

        double successRate = enhanceManager.getSuccessRate(currentLevel);
        double cost = enhanceManager.getCostMoney(currentLevel);
        int nextLevel = currentLevel + 1;
        double bonusPercent = enhanceManager.getStatBonusPercent(nextLevel) * 100;

        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<yellow>当前等级: <white>+" + currentLevel);
        lore.add("<green>下级等级: <white>+" + nextLevel);
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<aqua>成功率: <white>" + (int)(successRate * 100) + "%");
        lore.add("<light_purple>属性加成: <white>+" + (int)bonusPercent + "%");
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");

        if (enhanceManager.isCostUseMoney()) {
            VaultHook vault = plugin.getHookManager().getVaultHook();
            boolean hasEnough = vault != null && vault.isAvailable()
                    && vault.hasMoney(player, cost);
            String costColor = hasEnough ? "<green>" : "<red>";
            lore.add(costColor + "消耗金币: " + (int)cost + " 金币");
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<yellow>点击强化");

        inventory.setItem(SLOT_ENHANCE_BUTTON, GUIHelper.createItem(
                Material.ANVIL,
                "<gold>强化装备",
                lore
        ));
    }

    /**
     * Handles inventory click events for this GUI.
     *
     * @param event the click event
     */
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // Allow shifts/clicks in player inventory
        if (slot >= INVENTORY_SIZE) {
            // Player inventory click - allow normal behavior
            // But we need to update the button after the click
            plugin.getServer().getScheduler().runTask(plugin, this::updateEnhanceButton);
            return;
        }

        // Equipment slot - allow placing/removing items
        if (slot == SLOT_EQUIPMENT) {
            // Allow the click to proceed normally
            // Schedule button update for next tick
            plugin.getServer().getScheduler().runTask(plugin, this::updateEnhanceButton);
            return;
        }

        // Enhance button slot
        if (slot == SLOT_ENHANCE_BUTTON) {
            event.setCancelled(true);
            tryEnhance();
            return;
        }

        // All other slots are decorative - cancel the click
        event.setCancelled(true);
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
            // Return item to player
            var leftover = player.getInventory().addItem(equipment);
            leftover.forEach((index, leftoverItem) ->
                    player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
            inventory.setItem(SLOT_EQUIPMENT, null);
        }
    }

    /**
     * Attempts to enhance the equipment in the slot.
     */
    private void tryEnhance() {
        ItemStack equipment = inventory.getItem(SLOT_EQUIPMENT);
        if (equipment == null || equipment.getType() == Material.AIR) {
            return;
        }
        if (!plugin.getItemManager().isRPGItem(equipment)) {
            return;
        }

        EnhanceManager enhanceManager = plugin.getItemManager().getEnhanceManager();
        int currentLevel = enhanceManager.getEnhanceLevel(equipment);

        if (currentLevel >= enhanceManager.getMaxLevel()) {
            return;
        }

        // Check money cost
        if (enhanceManager.isCostUseMoney()) {
            double cost = enhanceManager.getCostMoney(currentLevel);
            VaultHook vault = plugin.getHookManager().getVaultHook();
            if (vault == null || !vault.isAvailable()) {
                player.sendMessage(miniMessage.deserialize("<red>经济系统未启用，无法强化。"));
                return;
            }
            if (!vault.hasMoney(player, cost)) {
                player.sendMessage(miniMessage.deserialize("<red>金币不足！需要 " + (int)cost + " 金币。"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            // Withdraw money
            vault.withdraw(player, cost);
        }

        // Perform enhancement
        EnhanceManager.EnhanceResult result = enhanceManager.enhance(equipment);

        // Update the item in the slot
        if (result == EnhanceManager.EnhanceResult.DESTROYED) {
            inventory.setItem(SLOT_EQUIPMENT, null);
            player.sendMessage(miniMessage.deserialize("<red>装备强化失败，物品被摧毁了！"));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            // Regenerate lore
            plugin.getItemManager().regenerateLore(equipment);
            inventory.setItem(SLOT_EQUIPMENT, equipment);

            switch (result) {
                case SUCCESS -> {
                    int newLevel = enhanceManager.getEnhanceLevel(equipment);
                    player.sendMessage(miniMessage.deserialize(
                            "<green>强化成功！装备升级为 +" + newLevel + "！"));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
                case FAILED -> {
                    player.sendMessage(miniMessage.deserialize("<red>强化失败！装备等级下降了。"));
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
                }
                case MAX_LEVEL -> {
                    player.sendMessage(miniMessage.deserialize("<gold>装备已达最高强化等级。"));
                }
                default -> { /* should not happen */ }
            }
        }

        // Update button display
        updateEnhanceButton();
    }
}
