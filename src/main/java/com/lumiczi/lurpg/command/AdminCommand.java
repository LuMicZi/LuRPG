package com.lumiczi.lurpg.command;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.core.message.Pair;
import com.lumiczi.lurpg.item.ItemType;
import com.lumiczi.lurpg.item.RPGItem;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.stat.StatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles the /rpgadmin admin command.
 * <p>
 * Subcommands: reload, give, setlevel, setclass, givexp, giveskillpoint, help
 * </p>
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public AdminCommand(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            return executeCommand(sender, command, label, args);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Error executing /rpgadmin command for " + sender.getName(), e);
            sender.sendMessage(miniMessage.deserialize("<red>命令执行出错，请查看控制台日志。"));
            return true;
        }
    }

    private boolean executeCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lurpg.admin")) {
            sender.sendMessage(plugin.getMessageManager().getPrefix().append(
                    plugin.getMessageManager().get("general.no-permission")));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(sender);
            case "give" -> handleGive(sender, args);
            case "setlevel" -> handleSetLevel(sender, args);
            case "setclass" -> handleSetClass(sender, args);
            case "givexp" -> handleGiveXp(sender, args);
            case "giveskillpoint" -> handleGiveSkillPoint(sender, args);
            case "item" -> handleItemCommand(sender, args);
            case "create" -> handleCreateCommand(sender, args);
            default -> msg(sender, "general.unknown-command");
        }
        return true;
    }

    // ==================== Subcommand Handlers ====================

    private void handleReload(CommandSender sender) {
        try {
            plugin.reload();
            msg(sender, "general.reload-success");
        } catch (Exception e) {
            plugin.getLogger().severe("Error during reload: " + e.getMessage());
            msg(sender, "general.reload-failed");
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendRaw(sender, "<red>用法: /rpgadmin give <玩家> <物品ID> [数量]");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "command.player-not-found");
            return;
        }

        String itemId = args[2];
        if (!plugin.getItemManager().itemExists(itemId)) {
            msg(sender, "command.item-not-found");
            return;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) amount = 1;
            } catch (NumberFormatException e) {
                sendRaw(sender, "<red>数量必须为正整数。");
                return;
            }
        }

        ItemStack item = plugin.getItemManager().createItem(itemId);
        if (item == null) {
            msg(sender, "command.item-not-found");
            return;
        }
        item.setAmount(amount);

        // Give item to player; drop overflow on the ground
        var leftover = target.getInventory().addItem(item);
        leftover.forEach((index, leftoverItem) ->
                target.getWorld().dropItemNaturally(target.getLocation(), leftoverItem));

        msg(sender, "command.give-item-success",
                Pair.of("player", target.getName()),
                Pair.of("amount", String.valueOf(amount)),
                Pair.of("item", itemId));
    }

    private void handleSetLevel(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendRaw(sender, "<red>用法: /rpgadmin setlevel <玩家> <等级>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "command.player-not-found");
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(sender, "<red>等级必须为整数。");
            return;
        }

        PlayerData data = plugin.getPlayerManager().getOrCreatePlayerData(target.getUniqueId());
        data.setLevel(level);
        data.markDirty();

        msg(sender, "command.set-level-success",
                Pair.of("player", target.getName()),
                Pair.of("level", String.valueOf(level)));
    }

    private void handleSetClass(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendRaw(sender, "<red>用法: /rpgadmin setclass <玩家> <职业>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "command.player-not-found");
            return;
        }

        GameClass gameClass = GameClass.fromString(args[2]);
        if (gameClass == null) {
            sendRaw(sender, "<red>无效的职业: " + args[2]
                    + "。可选: WARRIOR, MAGE, ASSASSIN");
            return;
        }

        PlayerData data = plugin.getPlayerManager().getOrCreatePlayerData(target.getUniqueId());
        data.setGameClass(gameClass);
        data.markDirty();

        msg(sender, "command.set-class-success",
                Pair.of("player", target.getName()),
                Pair.of("class", gameClass.getDisplayName()));
    }

    private void handleGiveXp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendRaw(sender, "<red>用法: /rpgadmin givexp <玩家> <经验值>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "command.player-not-found");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(sender, "<red>经验值必须为整数。");
            return;
        }

        if (amount <= 0) {
            sendRaw(sender, "<red>经验值必须为正数。");
            return;
        }

        plugin.getExpManager().giveXp(target, amount);

        msg(sender, "command.give-xp-success",
                Pair.of("player", target.getName()),
                Pair.of("xp", String.valueOf(amount)));
    }

    private void handleGiveSkillPoint(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendRaw(sender, "<red>用法: /rpgadmin giveskillpoint <玩家> <数量>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg(sender, "command.player-not-found");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(sender, "<red>数量必须为整数。");
            return;
        }

        if (amount <= 0) {
            sendRaw(sender, "<red>数量必须为正数。");
            return;
        }

        PlayerData data = plugin.getPlayerManager().getOrCreatePlayerData(target.getUniqueId());
        data.setSkillPoints(data.getSkillPoints() + amount);
        data.markDirty();

        msg(sender, "command.give-skillpoint-success",
                Pair.of("player", target.getName()),
                Pair.of("amount", String.valueOf(amount)));
    }

    // ==================== Item Subcommand Handlers ====================

    private void handleItemCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            sendItemHelp(player);
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "setstat" -> handleItemSetStat(player, args);
            case "addstat" -> handleItemAddStat(player, args);
            case "setid" -> handleItemSetId(player, args);
            case "setlevel" -> handleItemSetLevel(player, args);
            case "addskill" -> handleItemAddSkill(player, args);
            case "removeskill" -> handleItemRemoveSkill(player, args);
            case "addspecial" -> handleItemAddSpecial(player, args);
            case "removespecial" -> handleItemRemoveSpecial(player, args);
            case "clearspecial" -> handleItemClearSpecial(player);
            case "relore" -> handleItemRelore(player);
            case "info" -> handleItemInfo(player);
            case "settier" -> handleItemSetTier(player, args);
            case "randomtier" -> handleItemRandomTier(player);
            case "givetierupgrade" -> handleGiveTierUpgrade(player, args);
            case "setenhance" -> handleItemSetEnhance(player, args);
            case "givegem" -> handleGiveGem(player, args);
            case "socket" -> handleItemSocket(player, args);
            case "unsocket" -> handleItemUnsocket(player, args);
            case "seal" -> handleItemSeal(player);
            case "unseal" -> handleItemUnseal(player);
            case "givesealscroll" -> handleGiveSealScroll(player, args);
            case "rename" -> handleItemRename(player, args);
            default -> sendItemHelp(player);
        }
    }

    private void handleItemSetStat(Player player, String[] args) {
        if (args.length < 4) {
            sendRaw(player, "<red>用法: /rpgadmin item setstat <属性> <值>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        StatType statType = com.lumiczi.lurpg.item.ItemManager.parseStatType(args[2]);
        if (statType == null) {
            sendRaw(player, "<red>无效的属性名: " + args[2] + "。可用属性: "
                    + getStatNameList());
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendRaw(player, "<red>属性值必须是数字。");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            // Not an RPG item yet, give it a temporary ID
            plugin.getItemManager().setRPGItemId(item, "custom_item");
        }
        boolean success = plugin.getItemManager().modifyItemStat(item, statType, value, false);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已设置属性 <gold>" + statType.getDisplayName()
                    + " <green>为 <yellow>" + formatValue(value) + statType.getSuffix());
        } else {
            sendRaw(player, "<red>设置属性失败。");
        }
    }

    private void handleItemAddStat(Player player, String[] args) {
        if (args.length < 4) {
            sendRaw(player, "<red>用法: /rpgadmin item addstat <属性> <值>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        StatType statType = com.lumiczi.lurpg.item.ItemManager.parseStatType(args[2]);
        if (statType == null) {
            sendRaw(player, "<red>无效的属性名: " + args[2] + "。可用属性: "
                    + getStatNameList());
            return;
        }
        double value;
        try {
            value = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sendRaw(player, "<red>属性值必须是数字。");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            plugin.getItemManager().setRPGItemId(item, "custom_item");
        }
        boolean success = plugin.getItemManager().modifyItemStat(item, statType, value, true);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            double newVal = plugin.getItemManager().getItemStat(item, statType);
            sendRaw(player, "<green>已增加 <gold>" + statType.getDisplayName()
                    + " <green>+<yellow>" + formatValue(value) + statType.getSuffix()
                    + " <gray>(当前: " + formatValue(newVal) + statType.getSuffix() + ")");
        } else {
            sendRaw(player, "<red>增加属性失败。");
        }
    }

    private void handleItemSetId(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item setid <物品ID>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        String itemId = args[2];
        boolean success = plugin.getItemManager().setRPGItemId(item, itemId);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已设置物品 RPG ID 为 <gold>" + itemId + "<green>。");
        } else {
            sendRaw(player, "<red>设置物品 ID 失败。");
        }
    }

    private void handleItemSetLevel(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item setlevel <等级>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(player, "<red>等级必须为整数。");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            plugin.getItemManager().setRPGItemId(item, "custom_item");
        }
        boolean success = plugin.getItemManager().setItemLevelRequirement(item, level);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            if (level <= 0) {
                sendRaw(player, "<green>已移除物品的等级要求。");
            } else {
                sendRaw(player, "<green>已设置物品等级要求为 <yellow>Lv." + level + "<green>。");
            }
        } else {
            sendRaw(player, "<red>设置等级要求失败。");
        }
    }

    private void handleItemAddSkill(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item addskill <技能ID>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        String skillId = args[2];
        if (!plugin.getItemManager().isRPGItem(item)) {
            plugin.getItemManager().setRPGItemId(item, "custom_item");
        }
        boolean success = plugin.getItemManager().addSkillToItem(item, skillId);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已添加绑定技能: <gold>" + skillId + "<green>。");
        } else {
            sendRaw(player, "<red>添加技能失败（技能可能已存在）。");
        }
    }

    private void handleItemRemoveSkill(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item removeskill <技能ID>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        String skillId = args[2];
        boolean success = plugin.getItemManager().removeSkillFromItem(item, skillId);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已移除绑定技能: <gold>" + skillId + "<green>。");
        } else {
            sendRaw(player, "<red>移除技能失败（物品上没有此技能）。");
        }
    }

    private void handleItemAddSpecial(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item addspecial <名称|描述>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            plugin.getItemManager().setRPGItemId(item, "custom_item");
        }
        String effectText = joinArgsFrom(args, 2);
        boolean success = plugin.getItemManager().addSpecialEffect(item, effectText);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已添加特殊效果: <gold>" + effectText + "<green>。");
        } else {
            sendRaw(player, "<red>添加特殊效果失败。");
        }
    }

    private void handleItemRemoveSpecial(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item removespecial <序号>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(player, "<red>序号必须为整数。");
            return;
        }
        boolean success = plugin.getItemManager().removeSpecialEffect(item, index);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已移除第 <yellow>" + index + " <green>条特殊效果。");
        } else {
            sendRaw(player, "<red>移除失败（序号超出范围）。");
        }
    }

    private void handleItemClearSpecial(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        boolean success = plugin.getItemManager().clearSpecialEffects(item);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已清空所有特殊效果。");
        } else {
            sendRaw(player, "<red>清空特殊效果失败。");
        }
    }

    private void handleItemRelore(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        plugin.getItemManager().regenerateLore(item);
        sendRaw(player, "<green>物品 Lore 已重新生成。");
    }

    private void handleItemInfo(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }

        RPGItem rpgItem = plugin.getItemManager().buildRPGItemFromPDC(item);
        if (rpgItem == null) {
            msg(player, "item.not-rpg-item");
            return;
        }

        sendRaw(player, "<gold>===== 物品 RPG 数据 =====");
        sendRaw(player, "<yellow>ID: <white>" + rpgItem.getId());
        sendRaw(player, "<yellow>等级要求: <white>" + rpgItem.getLevelRequirement());

        Set<GameClass> classReq = plugin.getItemManager().getItemClassRequirement(item);
        if (!classReq.isEmpty()) {
            String classStr = classReq.stream()
                    .map(GameClass::getDisplayName)
                    .collect(Collectors.joining(", "));
            sendRaw(player, "<yellow>职业要求: <white>" + classStr);
        }

        if (!rpgItem.getStats().isEmpty()) {
            sendRaw(player, "<yellow>属性:");
            for (var entry : rpgItem.getStats().entrySet()) {
                sendRaw(player, "  <gray>• " + entry.getKey().getColorPrefix()
                        + entry.getKey().getDisplayName() + ": <white>"
                        + formatValue(entry.getValue()) + entry.getKey().getSuffix());
            }
        }

        Set<String> skills = plugin.getItemManager().getItemSkills(item);
        if (!skills.isEmpty()) {
            sendRaw(player, "<yellow>绑定技能:");
            for (String skill : skills) {
                sendRaw(player, "  <gray>• <aqua>" + skill);
            }
        }

        List<String> specialEffects = plugin.getItemManager().getItemSpecialEffects(item);
        if (!specialEffects.isEmpty()) {
            sendRaw(player, "<yellow>特殊效果:");
            for (int i = 0; i < specialEffects.size(); i++) {
                sendRaw(player, "  <gray>" + (i + 1) + ". <gold>" + specialEffects.get(i));
            }
        }

        if (rpgItem.getArmorSetId() != null) {
            sendRaw(player, "<yellow>套装: <white>" + rpgItem.getArmorSetId());
        }

        // Tier info
        String tierId = plugin.getItemManager().getItemTier(item);
        if (tierId != null && !tierId.isBlank()) {
            var tier = plugin.getItemManager().getTierManager().getTier(tierId);
            if (tier != null) {
                double pct = (tier.multiplier() - 1.0) * 100;
                String pctStr = (pct >= 0 ? "+" : "") + (int) pct + "%";
                sendRaw(player, "<yellow>品级: " + tier.color() + tier.name() + "品级"
                        + " <gray>(" + pctStr + " 属性)");
            } else {
                sendRaw(player, "<yellow>品级: <white>" + tierId);
            }
        }

        // Seal status
        if (plugin.getItemManager().isItemSealed(item)) {
            sendRaw(player, "<yellow>封印状态: <aqua>已封印（可交易）");
        } else {
            sendRaw(player, "<yellow>封印状态: <red>已绑定（不可交易）");
        }

        sendRaw(player, "<gold>=========================");
    }

    // ---- Tier command handlers ----

    private void handleItemSetTier(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item settier <品级ID>");
            sendRaw(player, "<gray>可用品级: " + getTierNameList());
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        String tierId = args[2].toLowerCase();
        var tierManager = plugin.getItemManager().getTierManager();
        if (!tierManager.isValidTier(tierId)) {
            sendRaw(player, "<red>无效的品级: " + args[2] + "。可用品级: " + getTierNameList());
            return;
        }
        boolean success = plugin.getItemManager().setItemTier(item, tierId);
        if (success) {
            var tier = tierManager.getTier(tierId);
            String tierName = tier != null ? tier.name() : tierId;
            String tierColor = tier != null ? tier.color() : "<white>";
            sendRaw(player, "<green>已设置物品品级为 " + tierColor + tierName + "品级<green>。");
        } else {
            sendRaw(player, "<red>设置品级失败。");
        }
    }

    private void handleItemRandomTier(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        String tierId = plugin.getItemManager().randomizeTier(item);
        if (tierId != null) {
            var tier = plugin.getItemManager().getTierManager().getTier(tierId);
            String tierName = tier != null ? tier.name() : tierId;
            String tierColor = tier != null ? tier.color() : "<white>";
            sendRaw(player, "<green>已随机物品品级为 " + tierColor + tierName + "品级<green>。");
        } else {
            sendRaw(player, "<red>随机品级失败（未配置品级）。");
        }
    }

    private void handleGiveTierUpgrade(Player player, String[] args) {
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
            } catch (NumberFormatException e) {
                sendRaw(player, "<red>数量必须为正整数。");
                return;
            }
        }
        ItemStack upgradeItem = plugin.getItemManager().createTierUpgradeItem();
        upgradeItem.setAmount(amount);
        var leftover = player.getInventory().addItem(upgradeItem);
        leftover.forEach((index, leftoverItem) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
        sendRaw(player, "<green>已给予 <yellow>" + amount + " <green>个品级修改符。");
    }

    // ---- Enhance command handlers ----

    private void handleItemSetEnhance(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item setenhance <等级>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(player, "<red>等级必须为整数。");
            return;
        }
        int maxLevel = plugin.getItemManager().getEnhanceManager().getMaxLevel();
        if (level < 0) level = 0;
        if (level > maxLevel) level = maxLevel;

        boolean success = plugin.getItemManager().getEnhanceManager().setEnhanceLevel(item, level);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            if (level <= 0) {
                sendRaw(player, "<green>已移除物品的强化等级。");
            } else {
                sendRaw(player, "<green>已设置物品强化等级为 <yellow>+" + level + "<green>。");
            }
        } else {
            sendRaw(player, "<red>设置强化等级失败。");
        }
    }

    // ---- Gem command handlers ----

    private void handleGiveGem(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item givegem <宝石ID> [数量]");
            sendRaw(player, "<gray>可用宝石: " + String.join(", ", plugin.getItemManager().getGemManager().getAllGemIds()));
            return;
        }
        String gemId = args[2];
        var gemManager = plugin.getItemManager().getGemManager();
        if (gemManager.getGem(gemId) == null) {
            sendRaw(player, "<red>无效的宝石ID: " + gemId);
            sendRaw(player, "<gray>可用宝石: " + String.join(", ", gemManager.getAllGemIds()));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1) amount = 1;
            } catch (NumberFormatException e) {
                sendRaw(player, "<red>数量必须为正整数。");
                return;
            }
        }
        ItemStack gemItem = gemManager.createGemItem(gemId, amount);
        if (gemItem == null) {
            sendRaw(player, "<red>创建宝石失败。");
            return;
        }
        var leftover = player.getInventory().addItem(gemItem);
        leftover.forEach((index, leftoverItem) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
        sendRaw(player, "<green>已给予 <yellow>" + amount + " <green>个 " + gemId + " 宝石。");
    }

    private void handleItemSocket(Player player, String[] args) {
        if (args.length < 4) {
            sendRaw(player, "<red>用法: /rpgadmin item socket <槽位> <宝石ID>");
            sendRaw(player, "<gray>槽位: 1-8");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(player, "<red>槽位必须是整数（1-8）。");
            return;
        }
        if (slot < 1 || slot > 8) {
            sendRaw(player, "<red>槽位必须在 1-8 之间。");
            return;
        }
        String gemId = args[3];
        var gemManager = plugin.getItemManager().getGemManager();
        if (gemManager.getGem(gemId) == null) {
            sendRaw(player, "<red>无效的宝石ID: " + gemId);
            return;
        }
        // First clear the slot if it has a gem
        plugin.getItemManager().unsocketGem(item, slot - 1);
        // Then socket the new gem
        boolean success = plugin.getItemManager().socketGem(item, slot - 1, gemId);
        if (success) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已在槽位 <yellow>" + slot + " <green>镶嵌宝石 <gold>" + gemId + "<green>。");
        } else {
            sendRaw(player, "<red>镶嵌宝石失败。");
        }
    }

    private void handleItemUnsocket(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item unsocket <槽位>");
            sendRaw(player, "<gray>槽位: 1-8");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        int slot;
        try {
            slot = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sendRaw(player, "<red>槽位必须是整数（1-8）。");
            return;
        }
        if (slot < 1 || slot > 8) {
            sendRaw(player, "<red>槽位必须在 1-8 之间。");
            return;
        }
        String removedGemId = plugin.getItemManager().unsocketGem(item, slot - 1);
        if (removedGemId != null) {
            plugin.getItemManager().regenerateLore(item);
            sendRaw(player, "<green>已从槽位 <yellow>" + slot + " <green>拆除宝石 <gold>" + removedGemId + "<green>。");
        } else {
            sendRaw(player, "<red>该槽位没有宝石。");
        }
    }

    // ---- Seal command handlers ----

    private void handleItemSeal(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        plugin.getItemManager().sealItem(item);
        sendRaw(player, "<green>物品已封印，恢复可交易状态。");
    }

    private void handleItemUnseal(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }
        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }
        plugin.getItemManager().unsealItem(item);
        sendRaw(player, "<red>物品已解封并绑定！");
    }

    private void handleGiveSealScroll(Player player, String[] args) {
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount < 1) amount = 1;
            } catch (NumberFormatException e) {
                sendRaw(player, "<red>数量必须为正整数。");
                return;
            }
        }
        ItemStack scroll = plugin.getItemManager().createSealScroll(amount);
        var leftover = player.getInventory().addItem(scroll);
        leftover.forEach((index, leftoverItem) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
        sendRaw(player, "<green>已给予 <yellow>" + amount + " <green>个封券。");
    }

    private void handleItemRename(Player player, String[] args) {
        if (args.length < 3) {
            sendRaw(player, "<red>用法: /rpgadmin item rename <新名称>");
            sendRaw(player, "<gray>支持 MiniMessage 颜色格式，例如 <red>红色</red>、<gold>金色</gold>");
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }

        // Join remaining args to support spaces in name
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) nameBuilder.append(" ");
            nameBuilder.append(args[i]);
        }
        String nameStr = nameBuilder.toString();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            sendRaw(player, "<red>无法修改该物品名称。");
            return;
        }

        meta.displayName(com.lumiczi.lurpg.core.text.TextUtil.noItalic(
                miniMessage.deserialize(nameStr)));
        item.setItemMeta(meta);

        sendRaw(player, "<green>物品名称已修改为：" + nameStr);
    }

    // ==================== Create Subcommand Handlers ====================

    private void handleCreateCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            msg(sender, "general.player-only");
            return;
        }

        if (args.length < 2) {
            sendCreateHelp(player);
            return;
        }

        String type = args[1].toLowerCase();
        switch (type) {
            case "weapon" -> handleCreateWeapon(player, args);
            case "armor" -> handleCreateArmor(player, args);
            case "consumable" -> handleCreateConsumable(player, args);
            case "save" -> handleCreateSave(player);
            default -> sendCreateHelp(player);
        }
    }

    private void handleCreateWeapon(Player player, String[] args) {
        if (args.length < 4) {
            sendRaw(player, "<red>用法: /rpgadmin create weapon <物品ID> <物品名>");
            return;
        }

        ItemStack baseItem = player.getInventory().getItemInMainHand();
        if (baseItem == null || baseItem.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }

        String itemId = args[2];
        if (plugin.getItemManager().itemExists(itemId)) {
            sendRaw(player, "<red>物品ID已存在: " + itemId);
            return;
        }

        String displayName = joinArgsFrom(args, 3);
        createCustomItem(player, baseItem, itemId, displayName, ItemType.WEAPON);
    }

    private void handleCreateArmor(Player player, String[] args) {
        if (args.length < 4) {
            sendRaw(player, "<red>用法: /rpgadmin create armor <物品ID> <物品名>");
            return;
        }

        ItemStack baseItem = player.getInventory().getItemInMainHand();
        if (baseItem == null || baseItem.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }

        String itemId = args[2];
        if (plugin.getItemManager().itemExists(itemId)) {
            sendRaw(player, "<red>物品ID已存在: " + itemId);
            return;
        }

        String displayName = joinArgsFrom(args, 3);
        createCustomItem(player, baseItem, itemId, displayName, ItemType.ARMOR);
    }

    private void handleCreateConsumable(Player player, String[] args) {
        if (args.length < 4) {
            sendRaw(player, "<red>用法: /rpgadmin create consumable <物品ID> <物品名>");
            return;
        }

        ItemStack baseItem = player.getInventory().getItemInMainHand();
        if (baseItem == null || baseItem.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }

        String itemId = args[2];
        if (plugin.getItemManager().itemExists(itemId)) {
            sendRaw(player, "<red>物品ID已存在: " + itemId);
            return;
        }

        String displayName = joinArgsFrom(args, 3);
        createCustomItem(player, baseItem, itemId, displayName, ItemType.POTION);
    }

    /**
     * Creates a custom item from the held item template.
     * Sets the RPG item ID, display name, and lore template (rare by default).
     * The item is saved to items.yml and given to the player.
     */
    private void createCustomItem(Player player, ItemStack baseItem, String itemId,
                                   String displayName, ItemType type) {
        var itemManager = plugin.getItemManager();

        // Create a copy of the base item
        ItemStack newItem = baseItem.clone();
        newItem.setAmount(1);

        // Set RPG item ID
        itemManager.setRPGItemId(newItem, itemId);

        // Set display name
        ItemMeta meta = newItem.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize(displayName));
            newItem.setItemMeta(meta);
        }

        // Set lore template (rare by default)
        itemManager.setLoreTemplate(newItem, "rare");

        // Build RPGItem from the item and save to config
        RPGItem rpgItem = itemManager.buildRPGItemFromPDC(newItem);
        if (rpgItem != null) {
            rpgItem.setType(type);
            rpgItem.setMaterial(baseItem.getType().name());
            rpgItem.setLoreTemplate("rare");
            rpgItem.setDisplayName(displayName);

            boolean saved = itemManager.saveCustomItem(rpgItem, baseItem);
            if (!saved) {
                sendRaw(player, "<red>保存物品到配置失败，请查看控制台日志。");
                return;
            }
        }

        // Regenerate lore
        itemManager.regenerateLore(newItem);

        // Give the item to the player
        var leftover = player.getInventory().addItem(newItem);
        leftover.forEach((index, leftoverItem) ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));

        sendRaw(player, "<green>已创建自定义" + type.getDisplayName() + ": <gold>" + itemId
                + " <green>(<white>" + displayName + "<green>)");
    }

    private void handleCreateSave(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            msg(player, "item.no-item-in-hand");
            return;
        }

        if (!plugin.getItemManager().isRPGItem(item)) {
            msg(player, "item.not-rpg-item");
            return;
        }

        RPGItem rpgItem = plugin.getItemManager().buildRPGItemFromPDC(item);
        if (rpgItem == null) {
            msg(player, "item.not-rpg-item");
            return;
        }

        boolean saved = plugin.getItemManager().saveCustomItem(rpgItem, item);
        if (saved) {
            sendRaw(player, "<green>物品 <gold>" + rpgItem.getId() + " <green>已保存到 items.yml。");
        } else {
            sendRaw(player, "<red>保存失败，请查看控制台日志。");
        }
    }

    private void sendCreateHelp(Player player) {
        sendRaw(player, "<gold>===== 物品创建命令 =====");
        sendRaw(player, "<yellow>/rpgadmin create weapon <物品ID> <物品名> <gray>- 创建自定义武器");
        sendRaw(player, "<yellow>/rpgadmin create armor <物品ID> <物品名> <gray>- 创建自定义装备");
        sendRaw(player, "<yellow>/rpgadmin create consumable <物品ID> <物品名> <gray>- 创建自定义消耗品");
        sendRaw(player, "<yellow>/rpgadmin create save <gray>- 保存手持物品到配置文件");
        sendRaw(player, "<gold>=========================");
    }

    private void sendItemHelp(Player player) {
        sendRaw(player, "<gold>===== 物品编辑命令 =====");
        sendRaw(player, "<yellow>/rpgadmin item setstat <属性> <值> <gray>- 设置属性值");
        sendRaw(player, "<yellow>/rpgadmin item addstat <属性> <值> <gray>- 增加属性值");
        sendRaw(player, "<yellow>/rpgadmin item setid <物品ID> <gray>- 设置 RPG 物品 ID");
        sendRaw(player, "<yellow>/rpgadmin item setlevel <等级> <gray>- 设置等级要求");
        sendRaw(player, "<yellow>/rpgadmin item addskill <技能ID> <gray>- 添加绑定技能");
        sendRaw(player, "<yellow>/rpgadmin item removeskill <技能ID> <gray>- 移除绑定技能");
        sendRaw(player, "<yellow>/rpgadmin item addspecial <名称|描述> <gray>- 添加特殊效果");
        sendRaw(player, "<yellow>/rpgadmin item removespecial <序号> <gray>- 移除特殊效果");
        sendRaw(player, "<yellow>/rpgadmin item clearspecial <gray>- 清空特殊效果");
        sendRaw(player, "<yellow>/rpgadmin item relore <gray>- 重新生成物品 Lore");
        sendRaw(player, "<yellow>/rpgadmin item info <gray>- 查看物品 RPG 数据");
        sendRaw(player, "<yellow>/rpgadmin item settier <品级ID> <gray>- 设置物品品级");
        sendRaw(player, "<yellow>/rpgadmin item randomtier <gray>- 随机物品品级");
        sendRaw(player, "<yellow>/rpgadmin item givetierupgrade [数量] <gray>- 给予品级修改符");
        sendRaw(player, "<yellow>/rpgadmin item setenhance <等级> <gray>- 设置物品强化等级");
        sendRaw(player, "<yellow>/rpgadmin item givegem <宝石ID> [数量] <gray>- 给予宝石");
        sendRaw(player, "<yellow>/rpgadmin item socket <槽位> <宝石ID> <gray>- 镶嵌宝石");
        sendRaw(player, "<yellow>/rpgadmin item unsocket <槽位> <gray>- 拆除宝石");
        sendRaw(player, "<yellow>/rpgadmin item seal <gray>- 封印手持物品（恢复可交易）");
        sendRaw(player, "<yellow>/rpgadmin item unseal <gray>- 解封手持物品（绑定）");
        sendRaw(player, "<yellow>/rpgadmin item givesealscroll [数量] <gray>- 给予封券");
        sendRaw(player, "<yellow>/rpgadmin item rename <名称> <gray>- 修改物品名称（支持颜色）");
        sendRaw(player, "<gold>=========================");
    }

    /**
     * Formats a double value for display: whole numbers shown as integers.
     */
    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * Returns a comma-separated list of available stat names (kebab-case).
     */
    private String getStatNameList() {
        return Arrays.stream(StatType.values())
                .map(s -> s.name().toLowerCase().replace("_", "-"))
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns a list of stat names (kebab-case) for tab completion.
     */
    private List<String> getStatNames() {
        return Arrays.stream(StatType.values())
                .map(s -> s.name().toLowerCase().replace("_", "-"))
                .toList();
    }

    /**
     * Returns a comma-separated list of available tier IDs.
     */
    private String getTierNameList() {
        var tierManager = plugin.getItemManager().getTierManager();
        return tierManager.getTiersOrdered().stream()
                .map(tier -> tier.id())
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns a list of tier IDs for tab completion.
     */
    private List<String> getTierIds() {
        var tierManager = plugin.getItemManager().getTierManager();
        return tierManager.getTiersOrdered().stream()
                .map(tier -> tier.id())
                .toList();
    }

    // ==================== Help ====================

    private void showHelp(CommandSender sender) {
        var mm = plugin.getMessageManager();
        sender.sendMessage(mm.get("command.help-header"));
        for (Component line : mm.getList("command.admin-help")) {
            sender.sendMessage(line);
        }
        sender.sendMessage(mm.get("command.help-footer"));
    }

    // ==================== Messaging Helpers ====================

    @SafeVarargs
    private void msg(CommandSender sender, String path, Pair<String, String>... replacements) {
        sender.sendMessage(plugin.getMessageManager().getPrefix().append(
                plugin.getMessageManager().get(path, replacements)));
    }

    private void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(plugin.getMessageManager().getPrefix().append(
                miniMessage.deserialize(message)));
    }

    // ==================== Tab Completion ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lurpg.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> options = List.of(
                    "reload", "give", "setlevel", "setclass", "givexp",
                    "giveskillpoint", "item", "create", "help");
            return filterByPrefix(options, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            // Subcommands that take a player name as the second argument
            if (sub.equals("give") || sub.equals("setlevel") || sub.equals("setclass")
                    || sub.equals("givexp") || sub.equals("giveskillpoint")) {
                return filterByPrefix(getOnlinePlayerNames(), args[1]);
            }
            // Item subcommand actions
            if (sub.equals("item")) {
                List<String> itemActions = List.of(
                        "setstat", "addstat", "setid", "setlevel",
                        "addskill", "removeskill",
                        "addspecial", "removespecial", "clearspecial",
                        "relore", "info",
                        "settier", "randomtier", "givetierupgrade",
                        "setenhance", "givegem", "socket", "unsocket",
                        "seal", "unseal", "givesealscroll", "rename");
                return filterByPrefix(itemActions, args[1]);
            }
            // Create subcommand actions
            if (sub.equals("create")) {
                List<String> createActions = List.of(
                        "weapon", "armor", "consumable", "save");
                return filterByPrefix(createActions, args[1]);
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                return filterByPrefix(
                        new ArrayList<>(plugin.getItemManager().getAllItemIds()), args[2]);
            }
            if (sub.equals("setclass")) {
                return filterByPrefix(
                        List.of("WARRIOR", "MAGE", "ASSASSIN"), args[2]);
            }
            // Item subcommand third argument
            if (sub.equals("item")) {
                String action = args[1].toLowerCase();
                if (action.equals("setstat") || action.equals("addstat")) {
                    return filterByPrefix(getStatNames(), args[2]);
                }
                if (action.equals("addskill") || action.equals("removeskill")) {
                    // Suggest skill IDs from SkillManager
                    List<String> skillIds = plugin.getSkillManager().getAllSkills().stream()
                            .map(com.lumiczi.lurpg.skill.api.Skill::getId)
                            .collect(Collectors.toList());
                    return filterByPrefix(skillIds, args[2]);
                }
                if (action.equals("settier")) {
                    return filterByPrefix(getTierIds(), args[2]);
                }
                if (action.equals("givegem") || action.equals("socket")) {
                    return filterByPrefix(
                            new ArrayList<>(plugin.getItemManager().getGemManager().getAllGemIds()),
                            args[2]);
                }
                if (action.equals("unsocket")) {
                    return filterByPrefix(
                            List.of("1", "2", "3", "4", "5", "6", "7", "8"),
                            args[2]);
                }
            }
        }

        return List.of();
    }

    // ==================== Utility ====================

    private List<String> getOnlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
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

    /**
     * Joins command arguments from the given index onward with spaces.
     *
     * @param args       the argument array
     * @param startIndex the index to start joining from (inclusive)
     * @return the joined string, or empty string if out of bounds
     */
    private String joinArgsFrom(String[] args, int startIndex) {
        if (args == null || startIndex >= args.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                sb.append(' ');
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
