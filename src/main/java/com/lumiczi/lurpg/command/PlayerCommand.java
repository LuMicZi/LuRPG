package com.lumiczi.lurpg.command;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.gui.EnhanceGUI;
import com.lumiczi.lurpg.gui.SocketGUI;
import com.lumiczi.lurpg.player.PlayerData;
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
 * Handles the /rpg player command.
 * <p>
 * Subcommands: help, menu, panel, info
 * </p>
 */
public class PlayerCommand implements CommandExecutor, TabCompleter {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerCommand(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return executeCommand(sender, command, label, args);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Error executing /rpg command for " + sender.getName(), e);
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

        // No args: open main menu GUI
        if (args.length == 0) {
            if (!tryOpenGUI(player, "MAIN_MENU")) {
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<red>GUI 系统暂不可用，请使用 /rpg help 查看命令列表。"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> showHelp(player);
            case "menu", "panel" -> {
                if (!tryOpenGUI(player, "MAIN_MENU")) {
                    plugin.getMessageManager().sendMessage(player,
                            miniMessage.deserialize("<red>GUI 系统暂不可用，请使用 /rpg help 查看命令列表。"));
                }
            }
            case "enhance" -> EnhanceGUI.open(plugin, player);
            case "socket", "gem" -> SocketGUI.open(plugin, player);
            case "info" -> showInfo(player);
            default -> plugin.getMessageManager().send(player, "general.unknown-command");
        }
        return true;
    }

    private void showHelp(Player player) {
        var mm = plugin.getMessageManager();
        mm.sendMessage(player, mm.get("command.help-header"));

        // Build clickable help lines
        sendClickableCommand(player, "/rpg", "打开主菜单");
        sendClickableCommand(player, "/rpg panel", "查看角色面板");
        sendClickableCommand(player, "/rpg enhance", "装备强化");
        sendClickableCommand(player, "/rpg socket", "宝石镶嵌");
        sendClickableCommand(player, "/class select", "选择职业");
        sendClickableCommand(player, "/class info", "查看职业信息");
        sendClickableCommand(player, "/skill list", "查看技能列表");
        sendClickableCommand(player, "/skill cast <技能>", "施放技能");
        sendClickableCommand(player, "/skill equip <技能>", "装备技能");
        sendClickableCommand(player, "/skill unequip <槽位>", "卸下技能");

        mm.sendMessage(player, mm.get("command.help-footer"));
    }

    private void sendClickableCommand(Player player, String command, String description) {
        // <gold>/rpg <gray>- 打开主菜单
        // The command part is clickable (run_command) and shows tooltip
        String cmdText = "<click:run_command:'" + command + "'><hover:show_text:'<gray>点击执行 " + command + "'>"
                + "<gold>" + command + "</gold></hover></click> <gray>- " + description;
        player.sendMessage(miniMessage.deserialize(cmdText));
    }

    private void showInfo(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>玩家数据加载中，请稍后再试。"));
            return;
        }

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<gold>===== 角色信息 ====="));

        String className = data.getGameClass() != null
                ? data.getGameClass().getDisplayName()
                : "未选择";
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>职业: <white>" + className));

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>等级: <white>" + data.getLevel()));

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>经验: <white>" + data.getXp()
                        + " / " + data.getXpToNextLevel()));

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>技能点: <white>" + data.getSkillPoints()));

        String resourceName = data.getGameClass() != null
                ? data.getGameClass().getResourceName()
                : "资源";
        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>" + resourceName + ": <white>"
                        + (int) data.getCurrentResource()));

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>已学技能: <white>" + data.getLearnedSkills().size()
                        + "  <yellow>已装备: <white>" + data.getEquippedSkills().size()));
    }

    /**
     * Attempts to open a GUI via reflection.
     * Returns false if the GUI system is not available.
     */
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = List.of("help", "menu", "panel", "info", "enhance", "socket", "gem");
            List<String> result = new ArrayList<>();
            String prefix = args[0].toLowerCase();
            for (String opt : options) {
                if (opt.startsWith(prefix)) {
                    result.add(opt);
                }
            }
            return result;
        }
        return List.of();
    }
}
