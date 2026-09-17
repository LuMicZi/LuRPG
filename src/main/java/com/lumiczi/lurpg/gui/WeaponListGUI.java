package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.item.ItemType;
import com.lumiczi.lurpg.item.RPGItem;
import com.lumiczi.lurpg.stat.StatType;
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
 * Paginated weapon list GUI.
 * <p>
 * Displays all weapons defined in items.yml in a paginated 6-row chest
 * inventory. Each weapon is shown as an icon with its display name,
 * stats, level requirement, and class requirement in the lore.
 * Clicking a weapon opens the item detail GUI.
 * </p>
 * <p>
 * The bottom row contains navigation buttons (previous page, back to
 * main menu, next page). Items per page is configurable in config.yml.
 * </p>
 */
public class WeaponListGUI {

    /**
     * Opens the weapon list GUI at the specified page.
     *
     * @param plugin the plugin instance
     * @param player the target player
     * @param page   the page number (0-based)
     */
    public static void open(LuRPGPlugin plugin, Player player, int page) {
        GUIManager guiManager = plugin.getGuiManager();
        String titleStr = getTitle(plugin, "gui.title-weapons", "<dark_red>武器列表");

        // Get all weapons
        List<RPGItem> weapons = new ArrayList<>(plugin.getItemManager().getItemsByType(ItemType.WEAPON));

        int itemsPerPage = guiManager.getItemsPerPage();
        int totalPages = guiManager.getTotalPages(weapons.size());

        // Clamp page
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = guiManager.createInventory(54, titleStr);

        // Build slot -> itemId mapping for click handling
        Map<Integer, String> slotMap = new HashMap<>();

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, weapons.size());

        for (int i = startIndex; i < endIndex; i++) {
            RPGItem weapon = weapons.get(i);
            int slot = i - startIndex;
            if (slot >= 45) break; // Safety: don't overflow into nav row

            ItemStack icon = createWeaponIcon(plugin, weapon);
            inv.setItem(slot, icon);
            slotMap.put(slot, weapon.getId());
        }

        // Fill empty item slots with filler
        if (guiManager.isFillEmpty()) {
            Material fillMat = guiManager.getFillMaterial();
            for (int i = 0; i < 45; i++) {
                if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                    inv.setItem(i, GUIHelper.createFiller(fillMat));
                }
            }
        }

        // Navigation row
        boolean hasPrev = page > 0;
        boolean hasNext = page < totalPages - 1;
        guiManager.fillNavRow(inv, hasPrev, hasNext, true);

        // Page indicator
        inv.setItem(47, GUIHelper.createItem(Material.PAPER,
                "<yellow>第 " + (page + 1) + " / " + totalPages + " 页",
                List.of("<gray>共 " + weapons.size() + " 件武器")));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.WEAPON_LIST, page);
        session.setInventory(inv);
        session.setParam("slotMap", slotMap);
        session.setParam("totalPages", totalPages);
        session.setParam("itemCount", weapons.size());
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the icon ItemStack for a weapon.
     */
    private static ItemStack createWeaponIcon(LuRPGPlugin plugin, RPGItem weapon) {
        Material material = parseMaterial(weapon.getMaterial(), Material.IRON_SWORD);
        List<String> lore = new ArrayList<>();

        // Level requirement
        if (weapon.getLevelRequirement() > 0) {
            lore.add("<gray>需要等级: <yellow>" + weapon.getLevelRequirement());
        }

        // Class requirement
        if (weapon.getClassRequirement() != null && !weapon.getClassRequirement().isEmpty()) {
            lore.add("<gray>职业要求: " + GUIHelper.formatClassNames(weapon.getClassRequirement()));
        }

        // Stats
        if (!weapon.getStats().isEmpty()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            for (var entry : weapon.getStats().entrySet()) {
                lore.add(GUIHelper.formatStatLine(entry.getKey(), entry.getValue()));
            }
        }

        // Elements
        if (weapon.hasElements()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            for (var entry : weapon.getElements().entrySet()) {
                String elemName = entry.getKey().substring(0, 1).toUpperCase()
                        + entry.getKey().substring(1);
                lore.add("<dark_gray>+" + (int) entry.getValue().doubleValue() + " " + elemName + " 伤害");
            }
        }

        // Bound skills
        if (weapon.hasSkills()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<aqua>绑定技能:");
            for (String skillId : weapon.getSkills()) {
                var skill = plugin.getSkillManager().getSkill(skillId);
                String name = skill != null ? skill.getDisplayName() : skillId;
                lore.add("<dark_aqua> - " + name);
            }
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<yellow>点击查看详情");

        return GUIHelper.createItem(material, weapon.getCustomModelData(),
                weapon.getDisplayName(), lore);
    }

    /**
     * Handles click events in the weapon list GUI.
     *
     * @param plugin  the plugin instance
     * @param player  the clicking player
     * @param session the player's GUI session
     * @param slot    the clicked slot
     * @param event   the click event
     */
    @SuppressWarnings("unchecked")
    public static void handleClick(LuRPGPlugin plugin, Player player, GUISession session,
                                   int slot, InventoryClickEvent event) {
        int currentPage = session.getCurrentPage();
        int totalPages = session.getParam("totalPages", 1);

        // Navigation buttons
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

        // Item click - open detail
        Map<Integer, String> slotMap = session.getParam("slotMap", new HashMap<>());
        String itemId = slotMap.get(slot);
        if (itemId != null) {
            ItemDetailGUI.open(plugin, player, itemId, GUIType.WEAPON_LIST, currentPage);
        }
    }

    /**
     * Parses a material name, falling back to a default.
     */
    private static Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Reads a title string from messages.yml.
     */
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
