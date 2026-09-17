package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.UUID;

/**
 * Main menu GUI for the LuRPG plugin.
 * <p>
 * Displays a 6-row (54-slot) chest inventory with navigation icons
 * for each subsystem (weapons, armor sets, potions, skills, character
 * panel, skill learning) and a close button. All other slots are
 * filled with gray stained glass panes.
 * </p>
 */
public class MainMenuGUI {

    // Slot constants for menu items
    private static final int SLOT_WEAPON_LIST = 11;
    private static final int SLOT_ARMOR_SET_LIST = 13;
    private static final int SLOT_POTION_LIST = 15;
    private static final int SLOT_SKILL_LIST = 29;
    private static final int SLOT_PLAYER_PANEL = 31;
    private static final int SLOT_SKILL_LEARN = 33;
    private static final int SLOT_CLASS_SELECT = 22;
    private static final int SLOT_CLOSE = 49;

    /**
     * Opens the main menu GUI for the specified player.
     *
     * @param plugin the plugin instance
     * @param player the target player
     */
    public static void open(LuRPGPlugin plugin, Player player) {
        // Read title from messages.yml
        String titleStr = getMessagesTitle(plugin, "gui.title-main", "<dark_blue>LuRPG <gray>主菜单");

        GUIManager guiManager = plugin.getGuiManager();
        Inventory inv = guiManager.createInventory(54, titleStr);

        // Fill all slots with filler first
        Material fillMat = guiManager.getFillMaterial();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, GUIHelper.createFiller(fillMat));
        }

        // Weapon list
        inv.setItem(SLOT_WEAPON_LIST, GUIHelper.createItem(Material.IRON_SWORD,
                "<gold>武器列表", List.of(
                        "<gray>查看所有可用的武器",
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<yellow>点击查看"
                )));

        // Armor set list
        inv.setItem(SLOT_ARMOR_SET_LIST, GUIHelper.createItem(Material.DIAMOND_CHESTPLATE,
                "<green>套装列表", List.of(
                        "<gray>查看所有套装及加成",
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<yellow>点击查看"
                )));

        // Potion list
        inv.setItem(SLOT_POTION_LIST, GUIHelper.createItem(Material.POTION,
                "<light_purple>药水列表", List.of(
                        "<gray>查看所有药水及效果",
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<yellow>点击查看"
                )));

        // Skill list
        inv.setItem(SLOT_SKILL_LIST, GUIHelper.createItem(Material.ENCHANTED_BOOK,
                "<aqua>技能列表", List.of(
                        "<gray>查看已学和已装备的技能",
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<yellow>点击查看"
                )));

        // Player panel
        inv.setItem(SLOT_PLAYER_PANEL, GUIHelper.createItem(Material.PLAYER_HEAD,
                "<blue>角色面板", List.of(
                        "<gray>查看你的角色信息",
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<yellow>点击查看"
                )));

        // Skill learn
        inv.setItem(SLOT_SKILL_LEARN, GUIHelper.createItem(Material.BOOKSHELF,
                "<aqua>技能学习", List.of(
                        "<gray>学习新的技能",
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<yellow>点击查看"
                )));

        // Class select (only show if player has no class)
        var playerData = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (playerData != null && playerData.getGameClass() == null) {
            inv.setItem(SLOT_CLASS_SELECT, GUIHelper.createItem(Material.COMPASS,
                    "<gold>选择职业", List.of(
                            "<red>你还没有选择职业！",
                            "<yellow>点击选择你的职业"
                    )));
        }

        // Close button
        inv.setItem(SLOT_CLOSE, GUIHelper.createItem(guiManager.getCloseMaterial(),
                "<red>关闭", null));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.MAIN_MENU);
        session.setInventory(inv);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Handles click events in the main menu GUI.
     *
     * @param plugin  the plugin instance
     * @param player  the clicking player
     * @param session the player's GUI session
     * @param slot    the clicked slot
     * @param event   the click event
     */
    public static void handleClick(LuRPGPlugin plugin, Player player, GUISession session,
                                   int slot, InventoryClickEvent event) {
        switch (slot) {
            case SLOT_WEAPON_LIST -> plugin.getGuiManager().openGUI(player, GUIType.WEAPON_LIST);
            case SLOT_ARMOR_SET_LIST -> plugin.getGuiManager().openGUI(player, GUIType.ARMOR_SET_LIST);
            case SLOT_POTION_LIST -> plugin.getGuiManager().openGUI(player, GUIType.POTION_LIST);
            case SLOT_SKILL_LIST -> plugin.getGuiManager().openGUI(player, GUIType.SKILL_LIST);
            case SLOT_PLAYER_PANEL -> plugin.getGuiManager().openGUI(player, GUIType.PLAYER_PANEL);
            case SLOT_SKILL_LEARN -> plugin.getGuiManager().openGUI(player, GUIType.SKILL_LEARN);
            case SLOT_CLASS_SELECT -> plugin.getGuiManager().openGUI(player, GUIType.CLASS_SELECT);
            case SLOT_CLOSE -> player.closeInventory();
            default -> { /* No action for filler slots */ }
        }
    }

    /**
     * Reads a title string from messages.yml.
     */
    private static String getMessagesTitle(LuRPGPlugin plugin, String path, String fallback) {
        try {
            var msgConfig = plugin.getConfigManager().getConfig("messages");
            if (msgConfig != null) {
                String title = msgConfig.getString(path);
                if (title != null && !title.isBlank()) {
                    return title;
                }
            }
        } catch (Exception ignored) {
            // Fall through to fallback
        }
        return fallback;
    }
}
