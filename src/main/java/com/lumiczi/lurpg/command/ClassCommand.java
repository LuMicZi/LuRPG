package com.lumiczi.lurpg.command;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.ClassAttribute;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.core.message.Pair;
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
 * Handles the /class command.
 * <p>
 * Subcommands: select, info, change, help
 * </p>
 */
public class ClassCommand implements CommandExecutor, TabCompleter {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public ClassCommand(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return executeCommand(sender, command, label, args);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Error executing /class command for " + sender.getName(), e);
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
            case "select" -> handleSelect(player);
            case "info" -> handleInfo(player, args);
            case "change" -> handleChange(player, args);
            default -> plugin.getMessageManager().send(player, "general.unknown-command");
        }
        return true;
    }

    // ==================== Subcommand Handlers ====================

    private void handleSelect(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>玩家数据加载中，请稍后再试。"));
            return;
        }

        if (data.getGameClass() != null) {
            plugin.getMessageManager().send(player, "class.already-selected",
                    Pair.of("class", data.getGameClass().getDisplayName()));
            if (plugin.getClassManager().isClassChangeAllowed()) {
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<gray>如需更换职业，请使用 <yellow>/class change <职业>"));
            }
            return;
        }

        // No class selected - try to open the class selection GUI
        if (!tryOpenGUI(player, "CLASS_SELECT")) {
            // GUI not available - list available classes in chat
            plugin.getMessageManager().send(player, "class.select-prompt");
            for (GameClass gc : plugin.getClassManager().getAvailableClasses()) {
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<gray>- <gold>" + gc.name()
                                + " <gray>(" + gc.getDisplayName() + ")"
                                + " - 使用 <yellow>/class change " + gc.name() + " <gray>选择"));
            }
        }
    }

    private void handleInfo(Player player, String[] args) {
        GameClass targetClass;
        if (args.length >= 2) {
            targetClass = GameClass.fromString(args[1]);
            if (targetClass == null) {
                plugin.getMessageManager().sendMessage(player,
                        miniMessage.deserialize("<red>无效的职业。可选: WARRIOR, MAGE, ASSASSIN"));
                return;
            }
        } else {
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
            targetClass = data.getGameClass();
        }
        showClassInfo(player, targetClass);
    }

    private void handleChange(Player player, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>用法: /class change <职业>"));
            return;
        }

        GameClass newClass = GameClass.fromString(args[1]);
        if (newClass == null) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>无效的职业。可选: WARRIOR, MAGE, ASSASSIN"));
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>玩家数据加载中，请稍后再试。"));
            return;
        }

        boolean hasClass = data.getGameClass() != null;

        // If player already has this class, no need to change
        if (hasClass && data.getGameClass() == newClass) {
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<red>你已经是该职业了。"));
            return;
        }

        // If player already has a class, check if change is allowed
        if (hasClass && !plugin.getClassManager().isClassChangeAllowed()) {
            plugin.getMessageManager().send(player, "class.change-disabled");
            return;
        }

        // Charge cost only when changing an existing class
        double cost = 0;
        if (hasClass) {
            cost = plugin.getClassManager().getClassChangeCost();
            if (plugin.getHookManager().isVaultEnabled() && cost > 0) {
                if (!plugin.getHookManager().getVaultHook().hasMoney(player, cost)) {
                    plugin.getMessageManager().send(player, "class.change-no-money",
                            Pair.of("cost", String.valueOf((int) cost)));
                    return;
                }
                plugin.getHookManager().getVaultHook().withdraw(player, cost);
            }
        }

        data.setGameClass(newClass);
        data.markDirty();

        if (hasClass) {
            plugin.getMessageManager().send(player, "class.change-success",
                    Pair.of("class", newClass.getDisplayName()),
                    Pair.of("cost", String.valueOf((int) cost)));
        } else {
            plugin.getMessageManager().send(player, "class.selected",
                    Pair.of("class", newClass.getDisplayName()));
        }
    }

    // ==================== Info Display ====================

    private void showClassInfo(Player player, GameClass gameClass) {
        ClassAttribute attr = plugin.getClassManager().getClass(gameClass);

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<gold>===== " + gameClass.getDisplayName() + " ====="));

        // Description (hardcoded since ClassAttribute has no description field)
        String description = switch (gameClass) {
            case WARRIOR -> "<gray>近战物理输出，拥有高生命值和防御力";
            case MAGE -> "<gray>远程魔法输出，拥有高魔法攻击力";
            case ASSASSIN -> "<gray>高机动性物理输出，拥有高爆发能力";
        };
        plugin.getMessageManager().sendMessage(player, miniMessage.deserialize(description));

        plugin.getMessageManager().sendMessage(player,
                miniMessage.deserialize("<yellow>资源类型: <white>" + gameClass.getResourceName()));

        if (attr != null) {
            // Base attributes
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gold>基础属性 (Lv.1):"));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  生命值: <white>" + (int) attr.getMaxHealth()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  物理攻击: <white>" + (int) attr.getPhysicalAttack()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  魔法攻击: <white>" + (int) attr.getMagicalAttack()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  物理防御: <white>" + (int) attr.getPhysicalDefense()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  魔法防御: <white>" + (int) attr.getMagicalDefense()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  资源上限: <white>" + (int) attr.getResourceMax()));

            // Growth attributes
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gold>成长属性 (每级):"));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  生命值: <green>+" + attr.getHealthGrowth()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  物理攻击: <green>+" + attr.getPatkGrowth()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  魔法攻击: <green>+" + attr.getMatkGrowth()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  物理防御: <green>+" + attr.getPdefGrowth()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  魔法防御: <green>+" + attr.getMdefGrowth()));
            plugin.getMessageManager().sendMessage(player,
                    miniMessage.deserialize("<gray>  资源上限: <green>+" + attr.getResourceGrowth()));
        }
    }

    // ==================== Help ====================

    private void showHelp(Player player) {
        var mm = plugin.getMessageManager();
        mm.sendMessage(player, mm.get("command.help-header"));
        sendClickable(player, "/class select", "选择职业");
        sendClickable(player, "/class info [职业]", "查看职业信息");
        sendClickable(player, "/class change <职业>", "更换职业");
        mm.sendMessage(player, mm.get("command.help-footer"));
    }

    private void sendClickable(Player player, String command, String description) {
        String baseCmd = command.replace(" <", "").replace("[", "").replace(">", "").replace("]", "");
        String cmdText = "<click:run_command:'" + baseCmd + "'><hover:show_text:'<gray>点击执行 " + baseCmd + "'>"
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
            return filterByPrefix(List.of("select", "info", "change", "help"), args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("info") || sub.equals("change")) {
                return filterByPrefix(
                        List.of("WARRIOR", "MAGE", "ASSASSIN"), args[1]);
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
