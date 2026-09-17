package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.armor.ArmorSet;
import com.lumiczi.lurpg.armor.SetBonus;
import com.lumiczi.lurpg.item.RPGItem;
import com.lumiczi.lurpg.stat.StatMap;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Item detail GUI.
 * <p>
 * Displays comprehensive information about a single RPG item including
 * its display name, material icon, full stat list, elemental damage,
 * bound skills, level/class requirements, and armor set information.
 * </p>
 * <p>
 * For weapons and armor, a comparison with the player's currently
 * equipped item in the same slot is shown, highlighting stat differences.
 * </p>
 * <p>
 * The bottom row contains a back button that returns the player to
 * the previous GUI (weapon list, armor set list, etc.).
 * </p>
 */
public class ItemDetailGUI {

    // Slot constants
    private static final int SLOT_ITEM_ICON = 13;
    private static final int SLOT_ITEM_INFO = 22;
    private static final int SLOT_REQUIREMENTS = 31;
    private static final int SLOT_SET_INFO = 40;
    private static final int SLOT_COMPARISON = 36;
    private static final int SLOT_BACK = 49;

    /**
     * Opens the item detail GUI for the specified item.
     * <p>
     * This is the simple 3-argument version that returns to the main menu
     * when the back button is clicked.
     * </p>
     *
     * @param plugin the plugin instance
     * @param player the target player
     * @param itemId the RPG item ID to display
     */
    public static void open(LuRPGPlugin plugin, Player player, String itemId) {
        open(plugin, player, itemId, GUIType.MAIN_MENU, 0);
    }

    /**
     * Opens the item detail GUI for the specified item with return navigation.
     *
     * @param plugin     the plugin instance
     * @param player     the target player
     * @param itemId     the RPG item ID to display
     * @param returnType the GUI type to return to when back is clicked
     * @param returnPage the page number to return to
     */
    public static void open(LuRPGPlugin plugin, Player player, String itemId,
                            GUIType returnType, int returnPage) {
        GUIManager guiManager = plugin.getGuiManager();

        RPGItem rpgItem = plugin.getItemManager().getItem(itemId);
        if (rpgItem == null) {
            plugin.getMessageManager().send(player, "command.item-not-found");
            return;
        }

        String titleStr = getTitle(plugin, "gui.title-item-detail",
                "<dark_gray>物品详情 - " + rpgItem.getDisplayName());

        Inventory inv = guiManager.createInventory(54, titleStr);

        // Fill all with filler first
        Material fillMat = guiManager.getFillMaterial();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, GUIHelper.createFiller(fillMat));
        }

        // === Item icon (slot 13) ===
        inv.setItem(SLOT_ITEM_ICON, createItemIcon(plugin, rpgItem));

        // === Item info: stats, elements, skills (slot 22) ===
        inv.setItem(SLOT_ITEM_INFO, createItemInfoIcon(plugin, rpgItem));

        // === Requirements (slot 31) ===
        inv.setItem(SLOT_REQUIREMENTS, createRequirementsIcon(rpgItem));

        // === Armor set info (slot 40) ===
        if (rpgItem.hasArmorSet()) {
            ArmorSet set = plugin.getArmorSetManager().getSet(rpgItem.getArmorSetId());
            if (set != null) {
                inv.setItem(SLOT_SET_INFO, createSetInfoIcon(set, rpgItem));
            }
        }

        // === Comparison with current equipment (slot 36) ===
        ItemStack comparisonItem = createComparisonIcon(plugin, player, rpgItem);
        if (comparisonItem != null) {
            inv.setItem(SLOT_COMPARISON, comparisonItem);
        }

        // === Back button (slot 49) ===
        inv.setItem(SLOT_BACK, GUIHelper.createItem(guiManager.getBackMaterial(),
                "<red>返回", null));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.ITEM_DETAIL);
        session.setInventory(inv);
        session.setParam("returnType", returnType);
        session.setParam("returnPage", returnPage);
        session.setParam("itemId", itemId);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the main item icon.
     */
    private static ItemStack createItemIcon(LuRPGPlugin plugin, RPGItem item) {
        Material material = parseMaterial(item.getMaterial(), Material.STONE);
        return GUIHelper.createItem(material, item.getCustomModelData(),
                item.getDisplayName(), new ArrayList<>());
    }

    /**
     * Creates the item info icon (stats, elements, skills).
     */
    private static ItemStack createItemInfoIcon(LuRPGPlugin plugin, RPGItem item) {
        List<String> lore = new ArrayList<>();

        // Stats
        if (!item.getStats().isEmpty()) {
            lore.add("<gold>属性加成:");
            for (var entry : item.getStats().entrySet()) {
                lore.add("  " + GUIHelper.formatStatLine(entry.getKey(), entry.getValue()));
            }
        }

        // Elements
        if (item.hasElements()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<dark_purple>元素伤害:");
            for (var entry : item.getElements().entrySet()) {
                String elemName = entry.getKey().substring(0, 1).toUpperCase()
                        + entry.getKey().substring(1);
                lore.add("  <dark_gray>+" + (int) entry.getValue().doubleValue()
                        + " " + elemName + " 伤害");
            }
        }

        // Bound skills
        if (item.hasSkills()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<aqua>绑定技能:");
            for (String skillId : item.getSkills()) {
                var skill = plugin.getSkillManager().getSkill(skillId);
                String name = skill != null ? skill.getDisplayName() : skillId;
                lore.add("  <dark_aqua>" + name);
            }
        }

        // Weapon / armor type
        if (item.getWeaponType() != null && !item.getWeaponType().isBlank()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<gray>武器类型: <yellow>" + item.getWeaponType());
        }
        if (item.getArmorType() != null && !item.getArmorType().isBlank()) {
            lore.add("<gray>防具类型: <yellow>" + item.getArmorType());
        }

        if (lore.isEmpty()) {
            lore.add("<gray>无特殊属性");
        }

        return GUIHelper.createItem(Material.WRITTEN_BOOK,
                "<yellow>物品信息", lore);
    }

    /**
     * Creates the requirements icon.
     */
    private static ItemStack createRequirementsIcon(RPGItem item) {
        List<String> lore = new ArrayList<>();

        // Level requirement
        if (item.getLevelRequirement() > 0) {
            lore.add("<gray>需要等级: <yellow>" + item.getLevelRequirement());
        } else {
            lore.add("<gray>等级要求: <green>无");
        }

        // Class requirement
        if (item.getClassRequirement() != null && !item.getClassRequirement().isEmpty()) {
            lore.add("<gray>职业要求: " + GUIHelper.formatClassNames(item.getClassRequirement()));
        } else {
            lore.add("<gray>职业要求: <green>无");
        }

        // Item type
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<gray>物品类型: <yellow>" + item.getType().name());

        // Lore template (rarity)
        lore.add("<gray>稀有度: <yellow>" + item.getLoreTemplate());

        return GUIHelper.createItem(Material.PAPER,
                "<gold>使用要求", lore);
    }

    /**
     * Creates the armor set info icon.
     */
    private static ItemStack createSetInfoIcon(ArmorSet set, RPGItem item) {
        List<String> lore = new ArrayList<>();

        lore.add("<gray>套装: <green>" + set.getDisplayName());

        // Set description
        if (set.getDescription() != null && !set.getDescription().isEmpty()) {
            for (String line : set.getDescription()) {
                lore.add("<gray>" + line);
            }
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<gray>套装部件 (" + set.getPieces().size() + " 件):");

        // Mark which pieces the player has equipped
        for (String pieceId : set.getPieces()) {
            String marker = pieceId.equals(item.getId()) ? "<green>" : "<dark_gray>";
            lore.add(marker + " - " + pieceId);
        }

        // Set bonuses
        if (!set.getBonuses().isEmpty()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<gold>套装加成:");
            for (var entry : set.getBonuses().entrySet()) {
                SetBonus bonus = entry.getValue();
                lore.add("<yellow>" + entry.getKey() + " 件套装:");
                if (!bonus.getStats().isEmpty()) {
                    for (var statEntry : bonus.getStats().entrySet()) {
                        lore.add("  " + GUIHelper.formatStatLine(statEntry.getKey(), statEntry.getValue()));
                    }
                }
                if (bonus.getDescription() != null && !bonus.getDescription().isBlank()) {
                    lore.add("  <aqua>" + bonus.getDescription());
                }
                if (bonus.hasSpecial()) {
                    lore.add("  <light_purple>特殊: " + bonus.getSpecial().getType().name());
                }
            }
        }

        return GUIHelper.createItem(Material.DIAMOND_CHESTPLATE,
                "<green>套装信息", lore);
    }

    /**
     * Creates a comparison icon showing stat differences with currently equipped item.
     *
     * @return the comparison icon, or null if no comparison is applicable
     */
    private static ItemStack createComparisonIcon(LuRPGPlugin plugin, Player player, RPGItem item) {
        // Only compare weapons and armor
        if (item.getType() == null) {
            return null;
        }

        PlayerInventory playerInv = player.getInventory();
        ItemStack currentItem = null;

        // Determine which equipment slot to compare
        switch (item.getType()) {
            case WEAPON -> currentItem = playerInv.getItemInMainHand();
            case ARMOR -> {
                // Determine armor slot based on armor type
                String armorType = item.getArmorType();
                if (armorType != null) {
                    switch (armorType.toUpperCase()) {
                        case "HELMET" -> currentItem = playerInv.getHelmet();
                        case "CHESTPLATE" -> currentItem = playerInv.getChestplate();
                        case "LEGGINGS" -> currentItem = playerInv.getLeggings();
                        case "BOOTS" -> currentItem = playerInv.getBoots();
                    }
                }
            }
            default -> {
                return null;
            }
        }

        List<String> lore = new ArrayList<>();

        if (currentItem == null || currentItem.getType().isAir()) {
            lore.add("<gray>当前装备: <red>无");
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<gray>装备此物品将获得:");
            if (!item.getStats().isEmpty()) {
                for (var entry : item.getStats().entrySet()) {
                    lore.add("  " + GUIHelper.formatStatLine(entry.getKey(), entry.getValue()));
                }
            }
        } else {
            // Get current item's stats
            String currentItemId = plugin.getItemManager().getRPGItemId(currentItem);
            StatMap currentStats = new StatMap();
            if (currentItemId != null) {
                currentStats = plugin.getItemManager().getItemStats(currentItemId);
            }

            StatMap newItemStats = item.getStats();

            lore.add("<gray>当前装备: <yellow>"
                    + (currentItemId != null ? currentItemId : currentItem.getType().name()));
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<gray>属性对比:");

            // Collect all stat types from both items
            java.util.Set<StatType> allStats = new java.util.TreeSet<>(java.util.Comparator
                    .comparingInt(Enum::ordinal));
            allStats.addAll(currentStats.entrySet().stream()
                    .map(java.util.Map.Entry::getKey).toList());
            allStats.addAll(newItemStats.entrySet().stream()
                    .map(java.util.Map.Entry::getKey).toList());

            for (StatType type : allStats) {
                double current = currentStats.get(type);
                double newVal = newItemStats.get(type);
                double diff = newVal - current;

                if (diff == 0) {
                    lore.add("  <gray>" + type.getDisplayName() + ": " + (int) current
                            + " -> " + (int) newVal + " <dark_gray>(不变)");
                } else if (diff > 0) {
                    lore.add("  <gray>" + type.getDisplayName() + ": " + (int) current
                            + " -> " + (int) newVal + " <green>(+" + (int) diff + ")");
                } else {
                    lore.add("  <gray>" + type.getDisplayName() + ": " + (int) current
                            + " -> " + (int) newVal + " <red>(" + (int) diff + ")");
                }
            }
        }

        return GUIHelper.createItem(Material.ENDER_EYE,
                "<gold>装备对比", lore);
    }

    /**
     * Handles click events in the item detail GUI.
     *
     * @param plugin  the plugin instance
     * @param player  the clicking player
     * @param session the player's GUI session
     * @param slot    the clicked slot
     * @param event   the click event
     */
    public static void handleClick(LuRPGPlugin plugin, Player player, GUISession session,
                                   int slot, InventoryClickEvent event) {
        if (slot == SLOT_BACK) {
            GUIType returnType = session.getParam("returnType", GUIType.MAIN_MENU);
            int returnPage = session.getParam("returnPage", 0);

            switch (returnType) {
                case WEAPON_LIST -> WeaponListGUI.open(plugin, player, returnPage);
                case ARMOR_SET_LIST -> ArmorSetListGUI.open(plugin, player, returnPage);
                case POTION_LIST -> PotionListGUI.open(plugin, player, returnPage);
                case SKILL_LIST -> SkillListGUI.open(plugin, player, returnPage);
                case SKILL_LEARN -> SkillLearnGUI.open(plugin, player, returnPage);
                default -> MainMenuGUI.open(plugin, player);
            }
        }
        // All other clicks do nothing (informational view)
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
