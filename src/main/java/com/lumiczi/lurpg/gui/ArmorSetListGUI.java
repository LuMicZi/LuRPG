package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.armor.ArmorSet;
import com.lumiczi.lurpg.armor.SetBonus;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Paginated armor set list GUI.
 * <p>
 * Displays all armor sets defined in armor_sets.yml. Each set is shown
 * as an icon with its display name, description, piece count, and all
 * tiered set bonuses in the lore.
 * </p>
 */
public class ArmorSetListGUI {

    /**
     * Opens the armor set list GUI at the specified page.
     *
     * @param plugin the plugin instance
     * @param player the target player
     * @param page   the page number (0-based)
     */
    public static void open(LuRPGPlugin plugin, Player player, int page) {
        GUIManager guiManager = plugin.getGuiManager();
        String titleStr = getTitle(plugin, "gui.title-armor-sets", "<dark_green>套装列表");

        // Get all armor sets
        List<ArmorSet> sets = new ArrayList<>(plugin.getArmorSetManager().getAllSets().values());

        int itemsPerPage = guiManager.getItemsPerPage();
        int totalPages = guiManager.getTotalPages(sets.size());

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = guiManager.createInventory(54, titleStr);

        Map<Integer, String> slotMap = new HashMap<>();

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, sets.size());

        for (int i = startIndex; i < endIndex; i++) {
            ArmorSet set = sets.get(i);
            int slot = i - startIndex;
            if (slot >= 45) break;

            inv.setItem(slot, createSetIcon(set));
            slotMap.put(slot, set.getId());
        }

        // Fill empty slots
        if (guiManager.isFillEmpty()) {
            Material fillMat = guiManager.getFillMaterial();
            for (int i = 0; i < 45; i++) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, GUIHelper.createFiller(fillMat));
                }
            }
        }

        // Navigation
        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;
        guiManager.fillNavRow(inv, hasPrev, hasNext, true);

        // Page indicator
        inv.setItem(47, GUIHelper.createItem(Material.PAPER,
                "<yellow>第 " + (page + 1) + " / " + totalPages + " 页",
                List.of("<gray>共 " + sets.size() + " 套套装")));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.ARMOR_SET_LIST, page);
        session.setInventory(inv);
        session.setParam("slotMap", slotMap);
        session.setParam("totalPages", totalPages);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the icon ItemStack for an armor set.
     */
    private static ItemStack createSetIcon(ArmorSet set) {
        List<String> lore = new ArrayList<>();

        // Description
        if (set.getDescription() != null && !set.getDescription().isEmpty()) {
            for (String line : set.getDescription()) {
                lore.add("<gray>" + line);
            }
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        }

        // Pieces
        lore.add("<gray>套装部件 (" + set.getPieces().size() + " 件):");
        for (String pieceId : set.getPieces()) {
            lore.add("<dark_gray> - " + pieceId);
        }

        // Bonuses
        if (!set.getBonuses().isEmpty()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<gold>套装加成:");
            for (var entry : set.getBonuses().entrySet()) {
                SetBonus bonus = entry.getValue();
                lore.add("<yellow>" + entry.getKey() + " 件套装:");
                // Stats
                if (!bonus.getStats().isEmpty()) {
                    for (var statEntry : bonus.getStats().entrySet()) {
                        lore.add("  " + GUIHelper.formatStatLine(statEntry.getKey(), statEntry.getValue()));
                    }
                }
                // Description
                if (bonus.getDescription() != null && !bonus.getDescription().isBlank()) {
                    lore.add("  <aqua>" + bonus.getDescription());
                }
                // Special effect
                if (bonus.hasSpecial()) {
                    var special = bonus.getSpecial();
                    lore.add("  <light_purple>特殊效果: " + special.getType().name());
                    if (special.getDescription() != null && !special.getDescription().isBlank()) {
                        lore.add("   <dark_purple>" + special.getDescription());
                    }
                }
            }
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<yellow>点击查看部件详情");

        return GUIHelper.createItem(Material.DIAMOND_CHESTPLATE,
                set.getDisplayName(), lore);
    }

    /**
     * Handles click events in the armor set list GUI.
     *
     * @param plugin  the plugin instance
     * @param player  the clicking player
     * @param session the player's GUI session
     * @param slot    the clicked slot
     * @param event   the click event
     */
    public static void handleClick(LuRPGPlugin plugin, Player player, GUISession session,
                                   int slot, InventoryClickEvent event) {
        int currentPage = session.getCurrentPage();
        int totalPages = session.getParam("totalPages", 1);

        if (slot == GUIManager.SLOT_PREV_PAGE) {
            if (currentPage > 0) {
                open(plugin, player, currentPage - 1);
            }
            return;
        }
        if (slot == GUIManager.SLOT_NEXT_PAGE) {
            if (currentPage < totalPages - 1) {
                open(plugin, player, currentPage + 1);
            }
            return;
        }
        if (slot == GUIManager.SLOT_BACK) {
            MainMenuGUI.open(plugin, player);
            return;
        }

        // Item click - could open detail or show pieces
        // For now, we just show a message about the set pieces
        Map<Integer, String> slotMap = session.getParam("slotMap", new HashMap<>());
        String setId = slotMap.get(slot);
        if (setId != null) {
            ArmorSet set = plugin.getArmorSetManager().getSet(setId);
            if (set != null) {
                plugin.getMessageManager().send(player, "set.bonus-activated",
                        com.lumiczi.lurpg.core.message.Pair.of("set", set.getDisplayName()),
                        com.lumiczi.lurpg.core.message.Pair.of("count", String.valueOf(set.getPieces().size())));
            }
        }
    }

    private static String getTitle(LuRPGPlugin plugin, String path, String fallback) {
        try {
            var msgConfig = plugin.getConfigManager().getConfig("messages");
            if (msgConfig != null) {
                String title = msgConfig.getString(path);
                if (title != null && !title.isBlank()) {
                    return title;
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
