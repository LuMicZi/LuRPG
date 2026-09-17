package com.lumiczi.lurpg.command;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.skill.api.ResourceCost;
import com.lumiczi.lurpg.skill.api.Skill;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /skill command.
 * <p>
 * Subcommands: list, cast, equip, unequip, learn, info, help
 * </p>
 */
public class SkillCommand implements CommandExecutor, TabCompleter {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SkillCommand(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return executeCommand(sender, command, label, args);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Error executing /skill command for " + sender.getName(), e);
            sender.sendMessage(miniMessage.deserialize("<red>命令执行出错，请查看控制台日志。"));
            return true;
        }
    }

    private boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().getPrefix().append(
                    plugin.getMessageManager().get("general.player-only")));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "list" -> handleList(player);
            case "cast" -> handleCast(player, args);
            case "equip" -> handleEquip(player, args);
            case "unequip" -> handleUnequip(player, args);
            case "learn" -> handleLearn(player, args);
            case "unlearn" -> handleUnlearn(player, args);
            case "info" -> handleInfo(player, args);
            default -> plugin.getMessageManager().send(player, "general.unknown-command");
        }
        return true;
    }

    // ==================== Subcommand Handlers ====================

    private void handleList(Player player) {
        // Try to open the skill list GUI first
        if (!tryOpenGUI(player, "SKILL_LIST")) {
            // GUI not available - list skills in chat
            listSkillsInChat(player);
        }
    }

    private void handleCast(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>用法: /skill cast <技能ID>"));
            return;
        }
        // SkillManager.castSkill handles all checks and messages
        plugin.getSkillManager().castSkill(player, args[1]);
    }

    private void handleEquip(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>用法: /skill equip <技能ID>"));
            return;
        }
        // SkillLearnManager.equipSkill handles all checks and messages
        plugin.getSkillLearnManager().equipSkill(player, args[1]);
    }

    private void handleUnequip(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>用法: /skill unequip <槽位号(1-5)>"));
            return;
        }
        try {
            int slot = Integer.parseInt(args[1]);
            if (slot < 1) {
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<red>槽位号必须大于 0。"));
                return;
            }
            // Convert 1-based user input to 0-based index
            boolean success = plugin.getSkillLearnManager().unequipSkill(player, slot - 1);
            if (!success) {
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<red>无效的技能槽位。"));
            }
        } catch (NumberFormatException e) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>请输入有效的槽位号 (1-5)。"));
        }
    }

    private void handleLearn(Player player, String[] args) {
        if (args.length < 2) {
            // No skill specified - try to open the skill learn GUI
            if (!tryOpenGUI(player, "SKILL_LEARN")) {
                // GUI not available - list learnable skills in chat
                listLearnableSkills(player);
            }
            return;
        }
        // SkillLearnManager.learnSkill handles all checks and messages
        plugin.getSkillLearnManager().learnSkill(player, args[1]);
    }

    private void handleUnlearn(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>用法: /skill unlearn <技能ID>"));
            return;
        }
        plugin.getSkillLearnManager().unlearnSkill(player, args[1]);
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>用法: /skill info <技能ID>"));
            return;
        }

        Skill skill = plugin.getSkillManager().getSkill(args[1]);
        if (skill == null) {
            plugin.getMessageManager().send(player, "command.skill-not-found");
            return;
        }
        showSkillInfo(player, skill);
    }

    // ==================== Chat-based Listings ====================

    private void listSkillsInChat(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>玩家数据加载中，请稍后再试。"));
            return;
        }

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<gold>===== 技能列表 ====="));

        // Equipped skills
        List<String> equipped = data.getEquippedSkillList();
        if (equipped.isEmpty()) {
            plugin.getMessageManager().send(player, "skill.no-equipped-skills");
        } else {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<yellow>已装备技能:"));
            for (int i = 0; i < equipped.size(); i++) {
                Skill skill = plugin.getSkillManager().getSkill(equipped.get(i));
                String name = skill != null ? skill.getDisplayName() : equipped.get(i);
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<gray>[" + (i + 1) + "] <white>" + name));
            }
        }

        // Learned skills
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>已学技能:"));
        if (data.getLearnedSkills().isEmpty()) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>暂无已学技能"));
        } else {
            for (String skillId : data.getLearnedSkills()) {
                Skill skill = plugin.getSkillManager().getSkill(skillId);
                String name = skill != null ? skill.getDisplayName() : skillId;
                String equippedMark = data.hasSkillEquipped(skillId)
                        ? "<green>[已装备]" : "<gray>[未装备]";
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<gray>- <white>" + name + " " + equippedMark));
            }
        }
    }

    private void listLearnableSkills(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>玩家数据加载中，请稍后再试。"));
            return;
        }
        if (data.getGameClass() == null) {
            plugin.getMessageManager().send(player, "class.no-class");
            return;
        }

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<gold>===== 可学技能 ====="));

        List<Skill> learnable = plugin.getSkillManager().getLearnableSkills(data);
        if (learnable.isEmpty()) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>暂无可学技能。"));
        } else {
            for (Skill skill : learnable) {
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<gray>- <white>" + skill.getDisplayName()
                                + " <gray>(Lv." + skill.getRequiredLevel() + ")"
                                + " - 使用 <yellow>/skill learn " + skill.getId() + " <gray>学习"));
            }
        }
    }

    // ==================== Info Display ====================

    private void showSkillInfo(Player player, Skill skill) {
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<gold>===== " + skill.getDisplayName() + " ====="));

        // Description
        for (String line : skill.getDescription()) {
            plugin.getMessageManager().sendMessage(player, miniMessage.deserialize(line));
        }

        // Requirements
        String className = skill.getRequiredClass() != null
                ? skill.getRequiredClass().getDisplayName()
                : "无限制";
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>职业要求: <white>" + className));
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>等级要求: <white>" + skill.getRequiredLevel()));

        // Trigger and cast mode
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>触发方式: <white>" + skill.getTrigger().name()));
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>施放模式: <white>" + skill.getCastMode().name()));

        // Cooldown
        long cooldownSeconds = skill.getCooldownTicks() / 20;
        if (cooldownSeconds > 0) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<yellow>冷却时间: <white>" + cooldownSeconds + " 秒"));
        }

        // Resource cost
        ResourceCost cost = skill.getResourceCost();
        if (cost != null && cost.hasCost()) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<yellow>消耗: <white>" + (int) cost.amount()
                            + " " + cost.type().getDisplayName()));
        }
    }

    // ==================== Help ====================

    private void showHelp(Player player) {
        var mm = plugin.getMessageManager();
        mm.sendMessage(player, mm.get("command.help-header"));
        sendClickable(player, "/skill list", "查看技能列表");
        sendClickable(player, "/skill cast <技能ID>", "施放技能");
        sendClickable(player, "/skill equip <技能ID>", "装备技能");
        sendClickable(player, "/skill unequip <槽位>", "卸下技能");
        sendClickable(player, "/skill learn [技能ID]", "学习技能");
        sendClickable(player, "/skill unlearn <技能ID>", "卸载技能（返还技能点）");
        sendClickable(player, "/skill info <技能ID>", "查看技能详情");
        mm.sendMessage(player, mm.get("command.help-footer"));
    }

    private void sendClickable(Player player, String command, String description) {
        String cmdText = "<click:suggest_command:'" + command.replace(" <", "").replace("[", "").replace(">", "").replace("]", "")
                + "'><hover:show_text:'<gray>点击填充命令'>"
                + "<gold>" + command + "</gold></hover></click> <gray>- " + description;
        player.sendMessage(miniMessage.deserialize(cmdText));
    }

    // ==================== GUI Helper ====================

    private boolean tryOpenGUI(Player player, String guiTypeName) {
        try {
            Method getGuiManager = LuRPGPlugin.class.getMethod("getGuiManager");
            Object guiManager = getGuiManager.invoke(plugin);
            if (guiManager == null) {
                return false;
            }
            Class<?> guiTypeClass = Class.forName("com.lumiczi.lurpg.gui.GUIType");
            Method valueOf = guiTypeClass.getMethod("valueOf", String.class);
            Object guiType = valueOf.invoke(null, guiTypeName);
            Method openGUI = guiManager.getClass().getMethod("openGUI",
                    Player.class, guiTypeClass);
            openGUI.invoke(guiManager, player, guiType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== Tab Completion ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterByPrefix(
                    List.of("list", "cast", "equip", "unequip", "learn", "unlearn", "info", "help"),
                    args[0]);
        }

        if (args.length == 2 && sender instanceof Player player) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();

            switch (sub) {
                case "cast", "equip", "unlearn" -> {
                    // Show learned skills
                    PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
                    if (data != null) {
                        List<String> result = new ArrayList<>();
                        for (String skillId : data.getLearnedSkills()) {
                            if (skillId.toLowerCase().startsWith(prefix)) {
                                result.add(skillId);
                            }
                        }
                        return result;
                    }
                }
                case "learn" -> {
                    // Show skills for the player's class
                    PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
                    if (data != null && data.getGameClass() != null) {
                        List<String> result = new ArrayList<>();
                        for (Skill skill : plugin.getSkillManager().getSkillsByClass(data.getGameClass())) {
                            if (skill.getId().toLowerCase().startsWith(prefix)) {
                                result.add(skill.getId());
                            }
                        }
                        return result;
                    }
                }
                case "info" -> {
                    // Show all skills
                    List<String> result = new ArrayList<>();
                    for (Skill skill : plugin.getSkillManager().getAllSkills()) {
                        if (skill.getId().toLowerCase().startsWith(prefix)) {
                            result.add(skill.getId());
                        }
                    }
                    return result;
                }
                case "unequip" -> {
                    // Show slot numbers 1-5
                    List<String> result = new ArrayList<>();
                    for (int i = 1; i <= 5; i++) {
                        String slot = String.valueOf(i);
                        if (slot.startsWith(prefix)) {
                            result.add(slot);
                        }
                    }
                    return result;
                }
            }
        }

        return List.of();
    }

    private List<String> filterByPrefix(List<String> options, String prefix) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) {
                result.add(opt);
            }
        }
        return result;
    }
}
