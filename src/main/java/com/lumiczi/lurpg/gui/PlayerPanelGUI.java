package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.armor.ArmorSet;
import com.lumiczi.lurpg.armor.SetBonus;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.skill.api.Skill;
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
import java.util.Map;
import java.util.UUID;

/**
 * Player character panel GUI.
 * <p>
 * Displays comprehensive character information including class, level,
 * experience, resource, full stat breakdown (base + equipment + set bonuses),
 * equipment slot previews, and equipped skills.
 * </p>
 * <p>
 * Layout (54-slot inventory):
 * <ul>
 *   <li>Slot 4: Player head with class, level, and XP info</li>
 *   <li>Slots 10-16: Equipment preview (helmet, chestplate, leggings, boots, main hand, off hand)</li>
 *   <li>Slots 19-25: Base stats</li>
 *   <li>Slots 28-34: Equipment + set bonus stats</li>
 *   <li>Slots 37-43: Equipped skills</li>
 *   <li>Slot 49: Back to main menu</li>
 * </ul>
 * </p>
 */
public class PlayerPanelGUI {

    // Slot constants
    private static final int SLOT_PLAYER_INFO = 4;
    private static final int SLOT_HELMET = 10;
    private static final int SLOT_CHESTPLATE = 11;
    private static final int SLOT_LEGGINGS = 12;
    private static final int SLOT_BOOTS = 13;
    private static final int SLOT_MAIN_HAND = 15;
    private static final int SLOT_OFF_HAND = 16;
    private static final int SLOT_RESOURCE = 22;
    private static final int SLOT_BASE_STATS_HEADER = 19;
    private static final int SLOT_BONUS_STATS_HEADER = 28;
    private static final int SLOT_EQUIPPED_SKILLS_HEADER = 37;
    private static final int SLOT_BACK = 49;

    /**
     * Opens the player panel GUI.
     *
     * @param plugin the plugin instance
     * @param player the target player
     */
    public static void open(LuRPGPlugin plugin, Player player) {
        GUIManager guiManager = plugin.getGuiManager();
        String titleStr = getTitle(plugin, "gui.title-player-panel", "<dark_blue>角色面板");

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().send(player, "class.no-class");
            return;
        }

        Inventory inv = guiManager.createInventory(54, titleStr);

        // Fill all with filler first
        Material fillMat = guiManager.getFillMaterial();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, GUIHelper.createFiller(fillMat));
        }

        // === Player info (slot 4) ===
        inv.setItem(SLOT_PLAYER_INFO, createPlayerInfoIcon(plugin, player, data));

        // === Equipment preview ===
        // Header
        inv.setItem(SLOT_BASE_STATS_HEADER - 1, GUIHelper.createItem(Material.ARMOR_STAND,
                "<gold>装备预览", null));

        PlayerInventory playerInv = player.getInventory();
        setEquipmentSlot(inv, SLOT_HELMET, playerInv.getHelmet(), "<gray>头盔");
        setEquipmentSlot(inv, SLOT_CHESTPLATE, playerInv.getChestplate(), "<gray>胸甲");
        setEquipmentSlot(inv, SLOT_LEGGINGS, playerInv.getLeggings(), "<gray>护腿");
        setEquipmentSlot(inv, SLOT_BOOTS, playerInv.getBoots(), "<gray>靴子");
        setEquipmentSlot(inv, SLOT_MAIN_HAND, playerInv.getItemInMainHand(), "<gray>主手");
        setEquipmentSlot(inv, SLOT_OFF_HAND, playerInv.getItemInOffHand(), "<gray>副手");

        // === Resource info (slot 22) ===
        if (data.getGameClass() != null) {
            inv.setItem(SLOT_RESOURCE, createResourceIcon(plugin, data));
        }

        // === Base stats (slots 19-25) ===
        inv.setItem(SLOT_BASE_STATS_HEADER, GUIHelper.createItem(Material.BOOK,
                "<aqua>基础属性", List.of("<gray>来自职业和等级")));
        StatMap baseStats = plugin.getStatManager().calculateBaseStats(data.getGameClass(), data.getLevel());
        int statSlot = SLOT_BASE_STATS_HEADER + 1;
        for (var entry : baseStats.entrySet()) {
            if (statSlot >= SLOT_BASE_STATS_HEADER + 7) break;
            inv.setItem(statSlot, createStatIcon(entry.getKey(), entry.getValue(), "<aqua>"));
            statSlot++;
        }

        // === Equipment + set bonus stats (slots 28-34) ===
        inv.setItem(SLOT_BONUS_STATS_HEADER, GUIHelper.createItem(Material.DIAMOND_CHESTPLATE,
                "<green>装备加成", List.of("<gray>来自装备和套装")));
        StatMap equipStats = new StatMap();
        // Add item stats from equipped gear
        for (ItemStack item : new ItemStack[]{
                playerInv.getHelmet(), playerInv.getChestplate(),
                playerInv.getLeggings(), playerInv.getBoots(),
                playerInv.getItemInMainHand(), playerInv.getItemInOffHand()
        }) {
            if (item != null && !item.getType().isAir()) {
                StatMap itemStats = plugin.getItemManager().getItemStats(item);
                if (itemStats != null) {
                    equipStats.addAll(itemStats);
                }
            }
        }
        // Add set bonuses
        StatMap setBonuses = plugin.getArmorSetManager().getActiveBonuses(player);
        if (setBonuses != null) {
            equipStats.addAll(setBonuses);
        }

        statSlot = SLOT_BONUS_STATS_HEADER + 1;
        for (var entry : equipStats.entrySet()) {
            if (statSlot >= SLOT_BONUS_STATS_HEADER + 7) break;
            if (entry.getValue() != 0) {
                inv.setItem(statSlot, createStatIcon(entry.getKey(), entry.getValue(), "<green>"));
                statSlot++;
            }
        }

        // === Active armor sets ===
        Map<ArmorSet, Integer> equippedSets = plugin.getArmorSetManager().checkPlayerSet(player);
        if (!equippedSets.isEmpty()) {
            int setSlot = SLOT_BONUS_STATS_HEADER + 7; // slot 35
            for (var entry : equippedSets.entrySet()) {
                ArmorSet set = entry.getKey();
                int count = entry.getValue();
                inv.setItem(setSlot, createActiveSetIcon(set, count));
                setSlot++;
                if (setSlot > 36) break;
            }
        }

        // === Equipped skills (slots 37-43) ===
        inv.setItem(SLOT_EQUIPPED_SKILLS_HEADER, GUIHelper.createItem(Material.ENCHANTED_BOOK,
                "<light_purple>已装备技能", List.of(
                        "<gray>当前装备: <yellow>" + data.getEquippedSkillList().size()
                )));
        List<String> equippedSkills = data.getEquippedSkillList();
        for (int i = 0; i < equippedSkills.size() && i < 6; i++) {
            String skillId = equippedSkills.get(i);
            Skill skill = plugin.getSkillManager().getSkill(skillId);
            int slot = SLOT_EQUIPPED_SKILLS_HEADER + 1 + i;
            if (skill != null) {
                inv.setItem(slot, createEquippedSkillPanelIcon(skill, i));
            } else {
                inv.setItem(slot, GUIHelper.createItem(Material.BARRIER,
                        "<red>未知技能: " + skillId, null));
            }
        }

        // === Back button ===
        inv.setItem(SLOT_BACK, GUIHelper.createItem(guiManager.getBackMaterial(),
                "<red>返回主菜单", null));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.PLAYER_PANEL);
        session.setInventory(inv);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the player info icon (player head with class/level/XP).
     */
    private static ItemStack createPlayerInfoIcon(LuRPGPlugin plugin, Player player, PlayerData data) {
        List<String> lore = new ArrayList<>();

        // Class
        if (data.getGameClass() != null) {
            GameClass gc = data.getGameClass();
            lore.add("<gray>职业: " + gc.getColorPrefix() + gc.getDisplayName());
        } else {
            lore.add("<gray>职业: <red>未选择");
        }

        // Level
        lore.add("<gray>等级: <yellow>" + data.getLevel());

        // XP progress
        long xp = data.getXp();
        long xpToNext = data.getXpToNextLevel();
        lore.add("<gray>经验: <yellow>" + xp + "<gray>/<yellow>" + xpToNext);

        // XP bar
        lore.add(GUIHelper.formatProgressBar(xp, xpToNext, 20));

        // Skill points
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<gray>技能点: <gold>" + data.getSkillPoints());
        lore.add("<gray>已学技能: <yellow>" + data.getLearnedSkills().size());
        lore.add("<gray>已装备技能: <yellow>" + data.getEquippedSkills().size());

        return GUIHelper.createItem(Material.PLAYER_HEAD,
                "<gold>" + player.getName(), lore);
    }

    /**
     * Creates the resource icon.
     */
    private static ItemStack createResourceIcon(LuRPGPlugin plugin, PlayerData data) {
        GameClass gc = data.getGameClass();
        String resName = gc.getResourceName();
        double current = data.getCurrentResource();

        // Get max resource from base stats
        StatMap baseStats = plugin.getStatManager().calculateBaseStats(gc, data.getLevel());
        double maxResource = baseStats.get(StatType.RESOURCE_MAX);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + resName + ": <yellow>" + (int) current + "<gray>/<yellow>" + (int) maxResource);
        lore.add(GUIHelper.formatProgressBar(current, maxResource, 20));

        return GUIHelper.createItem(Material.GOLDEN_APPLE,
                gc.getColorPrefix() + resName, lore);
    }

    /**
     * Creates a stat display icon.
     */
    private static ItemStack createStatIcon(StatType type, double value, String colorPrefix) {
        List<String> lore = new ArrayList<>();
        lore.add(colorPrefix + "+" + formatValue(value) + " " + type.getDisplayName() + type.getSuffix());
        return GUIHelper.createItem(Material.PAPER,
                colorPrefix + type.getDisplayName(), lore);
    }

    /**
     * Creates an icon for an active armor set.
     */
    private static ItemStack createActiveSetIcon(ArmorSet set, int equippedCount) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>已装备: <yellow>" + equippedCount + "<gray>/<yellow>" + set.getPieces().size() + " 件");

        // Show active bonuses
        List<SetBonus> activeBonuses = set.getActiveBonuses(equippedCount);
        if (!activeBonuses.isEmpty()) {
            lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
            lore.add("<gold>激活加成:");
            for (SetBonus bonus : activeBonuses) {
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
        } else {
            lore.add("<gray>无激活加成");
        }

        return GUIHelper.createItem(Material.DIAMOND_CHESTPLATE,
                "<green>" + set.getDisplayName(), lore);
    }

    /**
     * Creates an equipped skill icon for the panel.
     */
    private static ItemStack createEquippedSkillPanelIcon(Skill skill, int slotIndex) {
        List<String> lore = new ArrayList<>();

        if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
            lore.add("<gray>" + skill.getDescription().get(0));
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<gold>装备槽位: <yellow>" + (slotIndex + 1));
        lore.add("<gray>触发: <yellow>" + skill.getTrigger().getDisplayName());

        return GUIHelper.createItem(skill.getIcon() != null ? skill.getIcon() : Material.PAPER,
                "<light_purple>" + skill.getDisplayName(), lore);
    }

    /**
     * Sets an equipment preview slot with the player's current equipment.
     */
    private static void setEquipmentSlot(Inventory inv, int slot, ItemStack item, String defaultName) {
        if (item == null || item.getType().isAir()) {
            inv.setItem(slot, GUIHelper.createItem(Material.GRAY_STAINED_GLASS_PANE,
                    defaultName, List.of("<dark_gray>空")));
        } else {
            // Clone the item to avoid modifying the original
            ItemStack display = item.clone();
            inv.setItem(slot, display);
        }
    }

    /**
     * Formats a double value for display.
     */
    private static String formatValue(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }

    /**
     * Handles click events in the player panel GUI.
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
            MainMenuGUI.open(plugin, player);
        }
        // All other clicks do nothing (informational panel)
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
