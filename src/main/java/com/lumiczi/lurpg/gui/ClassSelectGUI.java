package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.ClassAttribute;
import com.lumiczi.lurpg.class_.ClassManager;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.core.message.Pair;
import com.lumiczi.lurpg.player.PlayerData;
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
 * Class selection GUI.
 * <p>
 * Displays the three RPG classes (Warrior, Mage, Assassin) as icons
 * with detailed lore including class description, base stats, resource
 * type, and available skill count. Players without a class can select
 * one by clicking. Players who already have a class are shown a
 * notification instead.
 * </p>
 */
public class ClassSelectGUI {

    // Slot constants
    private static final int SLOT_WARRIOR = 11;
    private static final int SLOT_MAGE = 13;
    private static final int SLOT_ASSASSIN = 15;
    private static final int SLOT_INFO = 22;
    private static final int SLOT_CLOSE = 49;

    /**
     * Opens the class selection GUI.
     *
     * @param plugin the plugin instance
     * @param player the target player
     */
    public static void open(LuRPGPlugin plugin, Player player) {
        GUIManager guiManager = plugin.getGuiManager();
        String titleStr = getTitle(plugin, "gui.title-class-select", "<gold>职业选择");

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

        boolean hasClass = data.getGameClass() != null;

        // Warrior (slot 11)
        inv.setItem(SLOT_WARRIOR, createClassIcon(plugin, GameClass.WARRIOR, hasClass,
                data.getGameClass() == GameClass.WARRIOR));

        // Mage (slot 13)
        inv.setItem(SLOT_MAGE, createClassIcon(plugin, GameClass.MAGE, hasClass,
                data.getGameClass() == GameClass.MAGE));

        // Assassin (slot 15)
        inv.setItem(SLOT_ASSASSIN, createClassIcon(plugin, GameClass.ASSASSIN, hasClass,
                data.getGameClass() == GameClass.ASSASSIN));

        // Info / status (slot 22)
        if (hasClass) {
            GameClass gc = data.getGameClass();
            inv.setItem(SLOT_INFO, GUIHelper.createItem(Material.COMPASS,
                    "<yellow>当前职业", List.of(
                            "<gray>你已选择职业: " + gc.getColorPrefix() + gc.getDisplayName(),
                            "<gray>如需更换职业，请使用 <yellow>/class change",
                            "<dark_gray>━━━━━━━━━━━━━━━━━━",
                            "<gray>点击关闭"
                    )));
        } else {
            inv.setItem(SLOT_INFO, GUIHelper.createItem(Material.COMPASS,
                    "<yellow>请选择职业", List.of(
                            "<gray>选择职业后将获得对应的",
                            "<gray>基础属性和技能体系",
                            "<dark_gray>━━━━━━━━━━━━━━━━━━",
                            "<gold>点击左侧职业图标选择"
                    )));
        }

        // Close button (slot 49)
        inv.setItem(SLOT_CLOSE, GUIHelper.createItem(guiManager.getCloseMaterial(),
                "<red>关闭", null));

        // Register session
        UUID uuid = player.getUniqueId();
        GUISession session = new GUISession(uuid, GUIType.CLASS_SELECT);
        session.setInventory(inv);
        session.setParam("hasClass", hasClass);
        // Store slot -> GameClass mapping
        Map<Integer, String> slotMap = new HashMap<>();
        if (!hasClass) {
            slotMap.put(SLOT_WARRIOR, GameClass.WARRIOR.name());
            slotMap.put(SLOT_MAGE, GameClass.MAGE.name());
            slotMap.put(SLOT_ASSASSIN, GameClass.ASSASSIN.name());
        }
        session.setParam("slotMap", slotMap);
        guiManager.registerSession(uuid, session);

        player.openInventory(inv);
    }

    /**
     * Creates the icon for a class.
     *
     * @param plugin       the plugin instance
     * @param gameClass    the class to display
     * @param hasClass     whether the player already has a class
     * @param isCurrent    whether this is the player's current class
     * @return the class icon ItemStack
     */
    private static ItemStack createClassIcon(LuRPGPlugin plugin, GameClass gameClass,
                                              boolean hasClass, boolean isCurrent) {
        List<String> lore = new ArrayList<>();

        // Class description
        lore.addAll(getClassDescription(gameClass));

        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");

        // Base stats (level 1)
        ClassManager classManager = plugin.getClassManager();
        ClassAttribute attr = classManager.getClass(gameClass);
        if (attr != null) {
            ClassAttribute base = attr.getStatAtLevel(1);
            lore.add("<gold>基础属性:");
            lore.add("  <red>生命: <yellow>" + (int) base.getMaxHealth());
            lore.add("  <gold>物攻: <yellow>" + (int) base.getPhysicalAttack());
            lore.add("  <dark_purple>魔攻: <yellow>" + (int) base.getMagicalAttack());
            lore.add("  <gray>物防: <yellow>" + (int) base.getPhysicalDefense());
            lore.add("  <dark_gray>魔防: <yellow>" + (int) base.getMagicalDefense());
        }

        // Resource type
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add("<gray>资源类型: <yellow>" + gameClass.getResourceName());

        // Available skills count
        int skillCount = plugin.getSkillManager().getSkillsByClass(gameClass).size();
        lore.add("<gray>可用技能: <yellow>" + skillCount + " 个");

        // Status / action
        lore.add("<dark_gray>━━━━━━━━━━━━━━━━━━");
        if (hasClass) {
            if (isCurrent) {
                lore.add("<dark_green>当前职业");
            } else {
                lore.add("<dark_gray>已选择其他职业");
            }
        } else {
            lore.add("<green>点击选择此职业");
        }

        return GUIHelper.createItem(gameClass.getIcon(),
                gameClass.getColorPrefix() + gameClass.getDisplayName(), lore);
    }

    /**
     * Returns the description lines for a class.
     */
    private static List<String> getClassDescription(GameClass gameClass) {
        List<String> desc = new ArrayList<>();
        switch (gameClass) {
            case WARRIOR -> {
                desc.add("<gray>勇猛的近战战士，擅长使用");
                desc.add("<gray>剑盾和重甲，拥有高生命值");
                desc.add("<gray>和强大的物理攻击能力。");
            }
            case MAGE -> {
                desc.add("<gray>掌握元素魔法的施法者，");
                desc.add("<gray>拥有强大的魔法攻击和");
                desc.add("<gray>范围伤害能力，但生命较低。");
            }
            case ASSASSIN -> {
                desc.add("<gray>敏捷的暗影杀手，擅长");
                desc.add("<gray>快速突进和暴击，拥有高");
                desc.add("<gray>机动性和爆发伤害。");
            }
        }
        return desc;
    }

    /**
     * Handles click events in the class selection GUI.
     *
     * @param plugin  the plugin instance
     * @param player  the clicking player
     * @param session the player's GUI session
     * @param slot    the clicked slot
     * @param event   the click event
     */
    public static void handleClick(LuRPGPlugin plugin, Player player, GUISession session,
                                   int slot, InventoryClickEvent event) {
        boolean hasClass = session.getParam("hasClass", false);

        if (slot == SLOT_CLOSE || slot == SLOT_INFO) {
            player.closeInventory();
            return;
        }

        // If player already has a class, don't allow selection
        if (hasClass) {
            if (slot == SLOT_INFO) {
                player.closeInventory();
            }
            return;
        }

        // Check class selection
        Map<Integer, String> slotMap = session.getParam("slotMap", new HashMap<>());
        String className = slotMap.get(slot);
        if (className != null) {
            GameClass selected = GameClass.fromString(className);
            if (selected != null) {
                // Select the class
                PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
                if (data != null) {
                    data.setGameClass(selected);
                    data.markDirty();

                    // Send success message
                    plugin.getMessageManager().send(player, "class.selected",
                            Pair.of("class", selected.getDisplayName()));

                    // Close the GUI
                    player.closeInventory();
                }
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
