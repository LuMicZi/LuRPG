package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.skill.SkillCooldown;
import com.lumiczi.lurpg.skill.api.ResourceCost;
import com.lumiczi.lurpg.skill.api.Skill;
import com.lumiczi.lurpg.skill.api.SkillCastMode;
import com.lumiczi.lurpg.skill.api.SkillTrigger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Skill list GUI showing the player's learned and equipped skills.
 * <p>
 * The top row (slots 0-8) displays the player's currently equipped skills
 * in their respective slot positions. The remaining rows display all
 * learned skills that are not currently equipped.
 * </p>
 * <p>
 * Clicking an equipped skill unequips it. Clicking a learned but unequipped
 * skill equips it (if the equipped limit has not been reached).
 * Cooldown information is shown in the lore.
 * </p>
 */
public class SkillListGUI {

    // Equipped skill slots: 0-4 (or up to max-equipped-skills)
    // Learned skills start at slot 9

    /**
     * Opens the skill list GUI for the specified player.
     *
     * @param plugin the plugin instance
     * @param player the target player
     * @param page   the page number (0-based, for learned skills)
     */
    public static void open(LuRPGPlugin plugin, Player player, int page) {
        GUIManager guiManager = plugin.getGuiManager();
        String titleStr = getTitle(plugin, "gui.title-skills", "<dark_aqua>技能列表");

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().send(player, "class.no-class");
            return;
        }

        Inventory inv = guiManager.createInventory(54, titleStr);

        // Section header for equipped skills
        inv.setItem(0, GUIHelper.createItem(Material.NETHER_STAR,
                "<gold>已装备技能", List.of(
                        "<gray>点击已装备的技能可卸下",
                        "<gray>当前已装备: <yellow>" + data.getEquippedSkillList().size()
                )));

        // Equipped skills in slots 1-8
        List<String> equippedList = data.getEquippedSkillList();
        Map<Integer, String> equippedSlotMap = new HashMap<>();
        for (int i = 0; i < equippedList.size() && i < 8; i++) {
            String skillId = equippedList.get(i);
            Skill skill = plugin.getSkillManager().getSkill(skillId);
            int slot = 1 + i;
            if (skill != null) {
                inv.setItem(slot, createEquippedSkillIcon(plugin, player, skill, i));
                equippedSlotMap.put(slot, skillId);
            }
        }

        // Separator
        for (int i = 9; i < 18; i++) {
            inv.setItem(i, GUIHelper.createFiller(Material.BLUE_STAINED_GLASS_PANE));
        }

        // Section header for learned skills
        inv.setItem(9, GUIHelper.createItem(Material.BOOK,
                "<aqua>已学技能", List.of(
                        "<gray>点击未装备的技能可装备"
                )));

        // Learned but unequipped skills
        Set<String> learnedSkills = data.getLearnedSkills();
        List<String> unequippedSkills = new ArrayList<>();
        for (String skillId : learnedSkills) {
            if (!data.hasSkillEquipped(skillId)) {
                unequippedSkills.add(skillId);
            }
        }

        int itemsPerPage = guiManager.getItemsPerPage() - 9; // Reserve 9 for equipped + header
        int totalPages = Math.max(1, (int) Math.ceil((double) unequippedSkills.size() / itemsPerPage));

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Map<Integer, String> learnedSlotMap = new HashMap<>();
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, unequippedSkills.size());

        for (int i = startIndex; i < endIndex; i++) {
            String skillId = unequippedSkills.get(i);
            Skill skill = plugin.getSkillManager().getSkill(skillId);
            int slot = 18 + (i - startIndex);
            if (slot >= 45) break;

            if (skill != null) {
                inv.setItem(slot, createLearnedSkillIcon(plugin, player, skill));
                learnedSlotMap.put(slot, skillId);
            }
        }

        // Fill empty slots
        if (guiManager.isFillEmpty()) {
            Material fillMat = guiManager.getFillMaterial();
            for (int i = 18; i < 45; i++) {
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
                List.of("<gray>已学 " + learnedSkills.size() + " 个技能")));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.SKILL_LIST, page);
        session.setInventory(inv);
        session.setParam("equippedSlotMap", equippedSlotMap);
        session.setParam("learnedSlotMap", learnedSlotMap);
        session.setParam("totalPages", totalPages);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the icon for an equipped skill.
     */
    private static ItemStack createEquippedSkillIcon(LuRPGPlugin plugin, Player player,
                                                      Skill skill, int slotIndex) {
        List<String> lore = buildSkillLore(plugin, player, skill);
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<gold>装备槽位: <yellow>" + (slotIndex + 1));
        lore.add("<red>点击卸下");

        Material icon = skill.getIcon() != null ? skill.getIcon() : Material.PAPER;
        return GUIHelper.createItem(icon,
                "<green>" + skill.getDisplayName(), lore);
    }

    /**
     * Creates the icon for a learned but unequipped skill.
     */
    private static ItemStack createLearnedSkillIcon(LuRPGPlugin plugin, Player player,
                                                     Skill skill) {
        List<String> lore = buildSkillLore(plugin, player, skill);
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<green>点击装备");

        Material icon = skill.getIcon() != null ? skill.getIcon() : Material.PAPER;
        return GUIHelper.createItem(icon,
                "<aqua>" + skill.getDisplayName(), lore);
    }

    /**
     * Builds the common lore for a skill (description, cost, cooldown, etc.).
     */
    private static List<String> buildSkillLore(LuRPGPlugin plugin, Player player, Skill skill) {
        List<String> lore = new ArrayList<>();

        // Description
        if (skill.getDescription() != null) {
            for (String line : skill.getDescription()) {
                lore.add("<gray>" + line);
            }
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");

        // Trigger
        SkillTrigger trigger = skill.getTrigger();
        lore.add("<gray>触发: <yellow>" + trigger.getDisplayName());

        // Cast mode
        SkillCastMode castMode = skill.getCastMode();
        if (trigger == SkillTrigger.ACTIVE) {
            lore.add("<gray>施放方式: <yellow>" + castMode.name());
        }

        // Resource cost
        ResourceCost cost = skill.getResourceCost();
        if (cost != null && cost.hasCost()) {
            String resName = cost.type().getDisplayName();
            lore.add("<gray>消耗: " + GUIHelper.formatResourceCost(resName, cost.amount()));
        } else {
            lore.add("<gray>消耗: <green>无");
        }

        // Cooldown
        lore.add("<gray>冷却: " + GUIHelper.formatCooldown(skill.getCooldownTicks()));

        // Current cooldown status
        SkillCooldown cooldown = plugin.getSkillManager().getCooldown();
        if (cooldown.isOnCooldown(player.getUniqueId(), skill.getId())) {
            double remaining = cooldown.getRemainingCooldownSeconds(player.getUniqueId(), skill.getId());
            lore.add("<red>冷却中: <yellow>" + String.format("%.1f", remaining) + " 秒");
        }

        return lore;
    }

    /**
     * Handles click events in the skill list GUI.
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

        // Navigation
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

        // Check equipped skill slots (click to unequip)
        Map<Integer, String> equippedSlotMap = session.getParam("equippedSlotMap", new HashMap<>());
        String equippedSkillId = equippedSlotMap.get(slot);
        if (equippedSkillId != null) {
            // Find the slot index
            int slotIndex = -1;
            for (var entry : equippedSlotMap.entrySet()) {
                if (entry.getKey() == slot) {
                    slotIndex = entry.getKey() - 1; // slot 1 = index 0, etc.
                    break;
                }
            }
            plugin.getSkillLearnManager().unequipSkill(player, Math.max(0, slotIndex));
            open(plugin, player, currentPage);
            return;
        }

        // Check learned skill slots (click to equip)
        Map<Integer, String> learnedSlotMap = session.getParam("learnedSlotMap", new HashMap<>());
        String learnedSkillId = learnedSlotMap.get(slot);
        if (learnedSkillId != null) {
            plugin.getSkillLearnManager().equipSkill(player, learnedSkillId);
            open(plugin, player, currentPage);
            return;
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
