package com.lumiczi.lurpg.skill;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.core.message.Pair;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.skill.api.Skill;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Manages skill learning, equipping, and unequipping for players.
 * <p>
 * Handles skill point spending, optional gold costs (via Vault), and
 * skill book items for learning skills through gameplay.
 * </p>
 */
public class SkillLearnManager {

    private final LuRPGPlugin plugin;

    // Namespaced key for skill book items
    private static final String SKILL_BOOK_KEY = "lurpg_skill_book";

    public SkillLearnManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks whether a player can learn a specific skill.
     * <p>
     * Verifies:
     * <ul>
     *   <li>The skill exists</li>
     *   <li>The player has not already learned it</li>
     *   <li>The player's class matches the skill's required class</li>
     *   <li>The player's level meets the skill's level requirement</li>
     *   <li>The player has at least one skill point</li>
     *   <li>If Vault is enabled and gold cost is configured, the player has enough gold</li>
     * </ul>
     * </p>
     *
     * @param data    the player's data
     * @param skillId the skill ID to check
     * @return true if the player can learn the skill
     */
    public boolean canLearn(PlayerData data, String skillId) {
        if (data == null || skillId == null) {
            return false;
        }

        Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            return false;
        }

        // Already learned
        if (data.hasSkill(skill.getId())) {
            return false;
        }

        // Class check
        if (skill.getRequiredClass() != null && data.getGameClass() != skill.getRequiredClass()) {
            return false;
        }

        // Level check
        if (data.getLevel() < skill.getRequiredLevel()) {
            return false;
        }

        // Skill point check
        if (data.getSkillPoints() <= 0) {
            return false;
        }

        // Gold check (if Vault is enabled and skill has a gold cost)
        double goldCost = getSkillGoldCost(skill);
        if (goldCost > 0 && plugin.getHookManager().isVaultEnabled()) {
            Player player = plugin.getServer().getPlayer(data.getUuid());
            if (player != null && !plugin.getHookManager().getVaultHook().hasMoney(player, goldCost)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Attempts to learn a skill for a player.
     * <p>
     * Checks all learning conditions, deducts skill points and gold (if applicable),
     * and adds the skill to the player's learned skills.
     * </p>
     *
     * @param player  the player
     * @param skillId the skill ID to learn
     * @return true if the skill was successfully learned
     */
    public boolean learnSkill(Player player, String skillId) {
        if (player == null || skillId == null) {
            return false;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return false;
        }

        Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            plugin.getMessageManager().send(player, "command.skill-not-found");
            return false;
        }

        // Already learned
        if (data.hasSkill(skill.getId())) {
            plugin.getMessageManager().send(player, "skill.already-learned");
            return false;
        }

        // Class check
        if (skill.getRequiredClass() != null && data.getGameClass() != skill.getRequiredClass()) {
            plugin.getMessageManager().send(player, "skill.class-locked");
            return false;
        }

        // Level check
        if (data.getLevel() < skill.getRequiredLevel()) {
            plugin.getMessageManager().send(player, "skill.level-locked",
                    Pair.of("level", String.valueOf(skill.getRequiredLevel())));
            return false;
        }

        // Skill point check
        if (data.getSkillPoints() <= 0) {
            plugin.getMessageManager().send(player, "skill.no-skill-points");
            return false;
        }

        // Gold check
        double goldCost = getSkillGoldCost(skill);
        if (goldCost > 0 && plugin.getHookManager().isVaultEnabled()) {
            if (!plugin.getHookManager().getVaultHook().hasMoney(player, goldCost)) {
                plugin.getMessageManager().send(player, "skill.no-money",
                        Pair.of("cost", String.valueOf((int) goldCost)));
                return false;
            }
            // Deduct gold
            plugin.getHookManager().getVaultHook().withdraw(player, goldCost);
        }

        // Spend skill point
        data.spendSkillPoint();

        // Learn skill
        data.learnSkill(skill.getId());

        // Send success message
        plugin.getMessageManager().send(player, "skill.learned",
                Pair.of("skill", skill.getDisplayName()));

        return true;
    }

    /**
     * Learns a skill through a skill book item.
     * <p>
     * Checks whether the given item is a valid skill book for the specified skill,
     * consumes the item, and learns the skill. Skill books bypass the skill point
     * and gold requirements but still enforce class and level requirements.
     * </p>
     *
     * @param player  the player
     * @param skillId the skill ID to learn
     * @param item    the skill book item
     * @return true if the skill was successfully learned
     */
    public boolean learnSkillByItem(Player player, String skillId, ItemStack item) {
        if (player == null || skillId == null || item == null || item.getType() == Material.AIR) {
            return false;
        }

        // Check if the item is a skill book
        if (!isSkillBook(item, skillId)) {
            return false;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return false;
        }

        Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            plugin.getMessageManager().send(player, "command.skill-not-found");
            return false;
        }

        // Already learned
        if (data.hasSkill(skill.getId())) {
            plugin.getMessageManager().send(player, "skill.already-learned");
            return false;
        }

        // Class check (still enforced for skill books)
        if (skill.getRequiredClass() != null && data.getGameClass() != skill.getRequiredClass()) {
            plugin.getMessageManager().send(player, "skill.class-locked");
            return false;
        }

        // Level check (still enforced for skill books)
        if (data.getLevel() < skill.getRequiredLevel()) {
            plugin.getMessageManager().send(player, "skill.level-locked",
                    Pair.of("level", String.valueOf(skill.getRequiredLevel())));
            return false;
        }

        // Consume the item
        int amount = item.getAmount();
        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amount - 1);
        }

        // Learn skill (no skill point or gold cost for skill books)
        data.learnSkill(skill.getId());

        // Send success message
        plugin.getMessageManager().send(player, "skill.learned",
                Pair.of("skill", skill.getDisplayName()));

        return true;
    }

    /**
     * Equips a learned skill for a player.
     *
     * @param player  the player
     * @param skillId the skill ID to equip
     * @return true if the skill was successfully equipped
     */
    public boolean equipSkill(Player player, String skillId) {
        if (player == null || skillId == null) {
            return false;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return false;
        }

        Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            plugin.getMessageManager().send(player, "command.skill-not-found");
            return false;
        }

        // Check if learned
        if (!data.hasSkill(skill.getId())) {
            plugin.getMessageManager().send(player, "skill.not-learned");
            return false;
        }

        // Check if already equipped
        if (data.hasSkillEquipped(skill.getId())) {
            return false;
        }

        // Check max equipped limit
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        int maxEquipped = config.getInt("skills.max-equipped-skills", 5);
        if (data.getEquippedSkillList().size() >= maxEquipped) {
            plugin.getMessageManager().send(player, "skill.max-equipped",
                    Pair.of("max", String.valueOf(maxEquipped)));
            return false;
        }

        // Equip
        data.equipSkill(skill.getId());

        plugin.getMessageManager().send(player, "skill.equipped",
                Pair.of("skill", skill.getDisplayName()));

        return true;
    }

    /**
     * Unequips a skill by its slot index.
     *
     * @param player the player
     * @param slot   the slot index (0-based)
     * @return true if the skill was successfully unequipped
     */
    public boolean unequipSkill(Player player, int slot) {
        if (player == null) {
            return false;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return false;
        }

        List<String> equipped = data.getEquippedSkillList();
        if (slot < 0 || slot >= equipped.size()) {
            return false;
        }

        String skillId = equipped.get(slot);
        Skill skill = plugin.getSkillManager().getSkill(skillId);

        data.unequipSkill(slot);

        String displayName = skill != null ? skill.getDisplayName() : skillId;
        plugin.getMessageManager().send(player, "skill.unequipped",
                Pair.of("skill", displayName));

        return true;
    }

    /**
     * Unlearns (forgets) a skill, refunding one skill point.
     * If the skill is currently equipped, it will be unequipped first.
     *
     * @param player  the player
     * @param skillId the skill ID to unlearn
     * @return true if the skill was successfully unlearned
     */
    public boolean unlearnSkill(Player player, String skillId) {
        if (player == null || skillId == null) {
            return false;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return false;
        }

        Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            plugin.getMessageManager().send(player, "command.skill-not-found");
            return false;
        }

        // Check if learned
        if (!data.hasSkill(skill.getId())) {
            plugin.getMessageManager().send(player, "skill.not-learned");
            return false;
        }

        // Unlearn
        data.unlearnSkill(skill.getId());

        // Send success message
        plugin.getMessageManager().sendMessage(player,
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(
                        "<green>已遗忘技能：<gold>" + skill.getDisplayName()
                                + "</gold>，返还 <yellow>1</yellow> 技能点。"));

        return true;
    }

    // ==================== Helpers ====================

    /**
     * Gets the gold cost for learning a skill.
     * <p>
     * The gold cost is calculated as:
     * {@code requiredLevel * gold-cost-multiplier * level-based-factor}
     * </p>
     *
     * @param skill the skill
     * @return the gold cost, or 0 if Vault is not enabled
     */
    private double getSkillGoldCost(Skill skill) {
        if (!plugin.getHookManager().isVaultEnabled()) {
            return 0;
        }
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        double multiplier = config.getDouble("skills.learning.gold-cost-multiplier", 1.0);
        // Base cost = level * 100 * multiplier
        return skill.getRequiredLevel() * 100.0 * multiplier;
    }

    /**
     * Checks whether an item is a valid skill book for the specified skill.
     * <p>
     * A skill book is identified by a PersistentDataContainer entry with the key
     * "lurpg_skill_book" containing the skill ID.
     * </p>
     *
     * @param item    the item to check
     * @param skillId the expected skill ID
     * @return true if the item is a skill book for the specified skill
     */
    public boolean isSkillBook(ItemStack item, String skillId) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String stored = pdc.get(
                new org.bukkit.NamespacedKey(plugin, SKILL_BOOK_KEY),
                PersistentDataType.STRING
        );
        return skillId.equalsIgnoreCase(stored);
    }

    /**
     * Creates a skill book ItemStack for the specified skill.
     *
     * @param skillId the skill ID
     * @return the skill book item, or null if the skill does not exist
     */
    public ItemStack createSkillBook(String skillId) {
        Skill skill = plugin.getSkillManager().getSkill(skillId);
        if (skill == null) {
            return null;
        }

        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(com.lumiczi.lurpg.core.text.TextUtil.noItalic(
                    net.kyori.adventure.text.Component.text("技能书: " + skill.getDisplayName())));
            meta.getPersistentDataContainer().set(
                    new org.bukkit.NamespacedKey(plugin, SKILL_BOOK_KEY),
                    PersistentDataType.STRING,
                    skillId.toLowerCase()
            );
            item.setItemMeta(meta);
        }
        return item;
    }
}
