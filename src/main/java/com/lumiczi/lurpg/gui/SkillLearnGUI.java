package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.player.PlayerData;
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
import java.util.UUID;

/**
 * Skill learning GUI.
 * <p>
 * Displays all skills available to the player's class. Skills that the
 * player can learn are shown with their normal icon and a "click to learn"
 * prompt. Skills that cannot yet be learned (due to level, class, or skill
 * point requirements) are shown with a red stained glass pane overlay and
 * locked status text.
 * </p>
 * <p>
 * The current skill point count is displayed prominently. Clicking a
 * learnable skill calls {@link com.lumiczi.lurpg.skill.SkillLearnManager#learnSkill}
 * and refreshes the GUI.
 * </p>
 */
public class SkillLearnGUI {

    // Slot for skill points indicator
    private static final int SLOT_SKILL_POINTS = 4;

    /**
     * Opens the skill learning GUI at the specified page.
     *
     * @param plugin the plugin instance
     * @param player the target player
     * @param page   the page number (0-based)
     */
    public static void open(LuRPGPlugin plugin, Player player, int page) {
        GUIManager guiManager = plugin.getGuiManager();
        String titleStr = getTitle(plugin, "gui.title-skill-learn", "<dark_aqua>技能学习");

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().send(player, "class.no-class");
            return;
        }

        if (data.getGameClass() == null) {
            plugin.getMessageManager().send(player, "class.no-class");
            return;
        }

        // Get all skills for the player's class
        GameClass playerClass = data.getGameClass();
        List<Skill> classSkills = plugin.getSkillManager().getSkillsByClass(playerClass);

        // Sort by required level
        classSkills.sort((a, b) -> Integer.compare(a.getRequiredLevel(), b.getRequiredLevel()));

        Inventory inv = guiManager.createInventory(54, titleStr);

        // Top row: skill points indicator
        inv.setItem(SLOT_SKILL_POINTS, GUIHelper.createItem(Material.NETHER_STAR,
                "<gold>技能点", List.of(
                        "<gray>可用技能点: <yellow>" + data.getSkillPoints(),
                        "<gray>已学技能: <yellow>" + data.getLearnedSkills().size(),
                        "<dark_gray>━━━━━━━━━━━━━━━━━━",
                        "<gray>点击可学习的技能来学习"
                )));

        // Fill slots 0-3 and 5-8 with filler
        Material separator = Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        for (int i = 0; i < 9; i++) {
            if (i != SLOT_SKILL_POINTS) {
                inv.setItem(i, GUIHelper.createFiller(separator));
            }
        }

        // Skills start at slot 9, up to slot 44 (36 slots per page)
        int itemsPerPage = 36;
        int totalPages = Math.max(1, (int) Math.ceil((double) classSkills.size() / itemsPerPage));

        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        Map<Integer, String> slotMap = new HashMap<>();
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, classSkills.size());

        for (int i = startIndex; i < endIndex; i++) {
            Skill skill = classSkills.get(i);
            int slot = 9 + (i - startIndex);
            if (slot >= 45) break;

            boolean canLearn = plugin.getSkillLearnManager().canLearn(data, skill.getId());
            boolean alreadyLearned = data.hasSkill(skill.getId());

            if (alreadyLearned) {
                inv.setItem(slot, createLearnedIcon(plugin, skill));
            } else if (canLearn) {
                inv.setItem(slot, createLearnableIcon(plugin, skill, data));
                slotMap.put(slot, skill.getId());
            } else {
                inv.setItem(slot, createLockedIcon(plugin, skill, data));
            }
        }

        // Fill empty item slots
        if (guiManager.isFillEmpty()) {
            Material fillMat = guiManager.getFillMaterial();
            for (int i = 9; i < 45; i++) {
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
                List.of(
                        "<gray>" + playerClass.getColorPrefix() + playerClass.getDisplayName() + "<gray> 可学技能",
                        "<gray>共 " + classSkills.size() + " 个技能"
                )));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.SKILL_LEARN, page);
        session.setInventory(inv);
        session.setParam("slotMap", slotMap);
        session.setParam("totalPages", totalPages);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the icon for a learnable skill.
     */
    private static ItemStack createLearnableIcon(LuRPGPlugin plugin, Skill skill, PlayerData data) {
        List<String> lore = buildSkillLore(plugin, skill);
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<green>可以学习");
        lore.add("<yellow>点击学习");

        Material icon = skill.getIcon() != null ? skill.getIcon() : Material.PAPER;
        return GUIHelper.createItem(icon, "<green>" + skill.getDisplayName(), lore);
    }

    /**
     * Creates the icon for a locked skill (requirements not met).
     */
    private static ItemStack createLockedIcon(LuRPGPlugin plugin, Skill skill, PlayerData data) {
        List<String> lore = buildSkillLore(plugin, skill);
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<red>未解锁");

        // Show specific lock reasons
        if (data.getLevel() < skill.getRequiredLevel()) {
            lore.add("<red>需要等级 " + skill.getRequiredLevel() + " (当前: " + data.getLevel() + ")");
        }
        if (skill.getRequiredClass() != null && data.getGameClass() != skill.getRequiredClass()) {
            lore.add("<red>需要职业: " + skill.getRequiredClass().getColorPrefix()
                    + skill.getRequiredClass().getDisplayName());
        }
        if (data.getSkillPoints() <= 0) {
            lore.add("<red>技能点不足");
        }

        Material icon = skill.getIcon() != null ? skill.getIcon() : Material.PAPER;
        return GUIHelper.createItem(Material.RED_STAINED_GLASS_PANE,
                "<red>" + skill.getDisplayName(), lore);
    }

    /**
     * Creates the icon for an already-learned skill.
     */
    private static ItemStack createLearnedIcon(LuRPGPlugin plugin, Skill skill) {
        List<String> lore = buildSkillLore(plugin, skill);
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<dark_green>已学习");

        Material icon = skill.getIcon() != null ? skill.getIcon() : Material.PAPER;
        return GUIHelper.createItem(icon, "<dark_green>" + skill.getDisplayName(), lore);
    }

    /**
     * Builds the common lore for a skill (description, cost, cooldown, requirements).
     */
    private static List<String> buildSkillLore(LuRPGPlugin plugin, Skill skill) {
        List<String> lore = new ArrayList<>();

        // Description
        if (skill.getDescription() != null) {
            for (String line : skill.getDescription()) {
                lore.add("<gray>" + line);
            }
        }

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");

        // Requirements
        lore.add("<gray>需要等级: <yellow>" + skill.getRequiredLevel());
        if (skill.getRequiredClass() != null) {
            lore.add("<gray>需要职业: " + skill.getRequiredClass().getColorPrefix()
                    + skill.getRequiredClass().getDisplayName());
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

        return lore;
    }

    /**
     * Handles click events in the skill learning GUI.
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

        // Skill click - attempt to learn
        Map<Integer, String> slotMap = session.getParam("slotMap", new HashMap<>());
        String skillId = slotMap.get(slot);
        if (skillId != null) {
            boolean success = plugin.getSkillLearnManager().learnSkill(player, skillId);
            if (success) {
                // Refresh the GUI
                open(plugin, player, currentPage);
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
