package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.potion.RPGPotion;
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
 * Paginated potion list GUI.
 * <p>
 * Displays all potions defined in potions.yml. Each potion is shown
 * as an icon with its display name, type, effects, cooldown, and
 * level/class requirements in the lore.
 * </p>
 */
public class PotionListGUI {

    /**
     * Opens the potion list GUI at the specified page.
     *
     * @param plugin the plugin instance
     * @param player the target player
     * @param page   the page number (0-based)
     */
    public static void open(LuRPGPlugin plugin, Player player, int page) {
        GUIManager guiManager = plugin.getGuiManager();
        String titleStr = getTitle(plugin, "gui.title-potions", "<dark_purple>药水列表");

        // Get all potions
        List<RPGPotion> potions = new ArrayList<>(plugin.getPotionManager().getAllPotions().values());

        int itemsPerPage = guiManager.getItemsPerPage();
        int totalPages = guiManager.getTotalPages(potions.size());

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Inventory inv = guiManager.createInventory(54, titleStr);

        Map<Integer, String> slotMap = new HashMap<>();

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, potions.size());

        for (int i = startIndex; i < endIndex; i++) {
            RPGPotion potion = potions.get(i);
            int slot = i - startIndex;
            if (slot >= 45) break;

            inv.setItem(slot, createPotionIcon(plugin, potion));
            slotMap.put(slot, potion.getId());
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
                List.of("<gray>共 " + potions.size() + " 种药水")));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.POTION_LIST, page);
        session.setInventory(inv);
        session.setParam("slotMap", slotMap);
        session.setParam("totalPages", totalPages);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the icon ItemStack for a potion.
     */
    private static ItemStack createPotionIcon(LuRPGPlugin plugin, RPGPotion potion) {
        Material material = potion.getBukkitMaterial();
        List<String> lore = new ArrayList<>();

        // Type
        lore.add("<gray>类型: <yellow>" + potion.getType().name());

        // Level requirement
        if (potion.getLevelRequirement() > 1) {
            lore.add("<gray>需要等级: <yellow>" + potion.getLevelRequirement());
        }

        // Class requirement
        if (!potion.getClassRequirement().isEmpty()) {
            lore.add("<gray>职业要求: " + GUIHelper.formatClassNames(potion.getClassRequirement()));
        }

        // Cooldown
        lore.add("<gray>冷却: <yellow>" + potion.getCooldown() + " 秒");

        // Effects
        var effects = potion.getEffects();
        if (effects != null) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            if (effects.isConfigurationSection("heal")) {
                var heal = effects.getConfigurationSection("heal");
                double amount = heal.getDouble("amount", 0);
                String type = heal.getString("type", "INSTANT");
                if ("PERCENT".equalsIgnoreCase(type)) {
                    lore.add("<red>恢复 " + (int) amount + "% 生命值");
                } else {
                    lore.add("<red>恢复 " + (int) amount + " 点生命值");
                }
            }
            if (effects.isConfigurationSection("regen")) {
                var regen = effects.getConfigurationSection("regen");
                double amount = regen.getDouble("amount", 0);
                int duration = regen.getInt("duration", 0);
                lore.add("<green>每 " + (int) amount + " 点恢复, 持续 " + duration / 20 + " 秒");
            }
            if (effects.isConfigurationSection("buff")) {
                var buff = effects.getConfigurationSection("buff");
                String stat = buff.getString("stat", "");
                double multiplier = buff.getDouble("multiplier", 1.0);
                int duration = buff.getInt("duration", 0);
                lore.add("<gold>" + stat + " x" + multiplier + ", 持续 " + duration / 20 + " 秒");
            }
            if (effects.isConfigurationSection("cure")) {
                lore.add("<aqua>解除负面效果");
            }
            if (effects.isConfigurationSection("resource")) {
                var res = effects.getConfigurationSection("resource");
                double amount = res.getDouble("amount", 0);
                lore.add("<blue>恢复 " + (int) amount + " 点资源");
            }
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<gray>最大叠加: <yellow>" + potion.getMaxStack());

        return GUIHelper.createItem(material, potion.getCustomModelData(),
                potion.getDisplayName(), lore);
    }

    /**
     * Handles click events in the potion list GUI.
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

        // Item click - give potion to player
        Map<Integer, String> slotMap = session.getParam("slotMap", new HashMap<>());
        String potionId = slotMap.get(slot);
        if (potionId != null) {
            // Give the player one of this potion
            var item = plugin.getPotionManager().createPotionItem(potionId);
            if (item != null) {
                player.getInventory().addItem(item);
                plugin.getMessageManager().send(player, "command.give-item-success",
                        com.lumiczi.lurpg.core.message.Pair.of("player", player.getName()),
                        com.lumiczi.lurpg.core.message.Pair.of("amount", "1"),
                        com.lumiczi.lurpg.core.message.Pair.of("item",
                                plugin.getPotionManager().getPotion(potionId) != null
                                        ? plugin.getPotionManager().getPotion(potionId).getDisplayName()
                                        : potionId));
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
