package com.lumiczi.lurpg.skill;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.core.message.Pair;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.skill.api.Skill;
import com.lumiczi.lurpg.skill.api.SkillCastContext;
import com.lumiczi.lurpg.skill.api.SkillMetadata;
import com.lumiczi.lurpg.skill.api.SkillTrigger;
import com.lumiczi.lurpg.stat.StatMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Central manager for the LuRPG skill system.
 * <p>
 * Loads skill definitions from {@code skills.yml}, provides query methods
 * for retrieving skills by various criteria, and handles skill casting
 * (both active and passive triggers).
 * </p>
 */
public class SkillManager {

    private final LuRPGPlugin plugin;
    private final Map<String, Skill> skills = new HashMap<>();
    private final SkillCooldown cooldown;
    private final SkillEffectHandler effectHandler;

    // Config values
    private double cooldownMultiplier = 1.0;
    private double costMultiplier = 1.0;

    public SkillManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
        this.cooldown = new SkillCooldown();
        this.effectHandler = new SkillEffectHandler(plugin);
    }

    /**
     * Loads all skill definitions from skills.yml.
     */
    public void load() {
        skills.clear();

        // Load config values
        FileConfiguration mainConfig = plugin.getConfigManager().getMainConfig();
        if (mainConfig != null) {
            cooldownMultiplier = mainConfig.getDouble("skills.cooldown-multiplier", 1.0);
            costMultiplier = mainConfig.getDouble("skills.cost-multiplier", 1.0);
        }

        // Load skills.yml
        FileConfiguration skillsConfig = plugin.getConfigManager().getConfig("skills");
        if (skillsConfig == null) {
            plugin.getLogger().warning("skills.yml not found or failed to load. No skills loaded.");
            return;
        }

        ConfigurationSection skillsSection = skillsConfig.getConfigurationSection("skills");
        if (skillsSection == null) {
            plugin.getLogger().warning("No 'skills' section found in skills.yml.");
            return;
        }

        int count = 0;
        for (String skillId : skillsSection.getKeys(false)) {
            try {
                ConfigurationSection skillSection = skillsSection.getConfigurationSection(skillId);
                if (skillSection == null) {
                    continue;
                }
                SkillMetadata metadata = SkillMetadata.fromConfig(skillId, skillSection);
                ConfigurableSkill skill = new ConfigurableSkill(plugin, metadata);
                skills.put(skillId.toLowerCase(), skill);
                count++;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to load skill '" + skillId + "': " + e.getMessage(), e);
            }
        }

        plugin.getLogger().info("Loaded " + count + " skills from skills.yml.");
    }

    /**
     * Gets a skill by its ID (case-insensitive).
     *
     * @param id the skill ID
     * @return the Skill, or null if not found
     */
    public Skill getSkill(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return skills.get(id.toLowerCase());
    }

    /**
     * Gets all registered skills.
     *
     * @return an unmodifiable collection of all skills
     */
    public Collection<Skill> getAllSkills() {
        return Collections.unmodifiableCollection(skills.values());
    }

    /**
     * Gets all skills for a specific class.
     *
     * @param gameClass the class to filter by
     * @return a list of skills matching the class
     */
    public List<Skill> getSkillsByClass(GameClass gameClass) {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : skills.values()) {
            if (skill.getRequiredClass() == gameClass) {
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * Gets all skills with a specific trigger type.
     *
     * @param trigger the trigger to filter by
     * @return a list of skills matching the trigger
     */
    public List<Skill> getSkillsByTrigger(SkillTrigger trigger) {
        List<Skill> result = new ArrayList<>();
        for (Skill skill : skills.values()) {
            if (skill.getTrigger() == trigger) {
                result.add(skill);
            }
        }
        return result;
    }

    /**
     * Gets all skills that a player can learn (meets class and level requirements,
     * has not already learned them).
     *
     * @param data the player's data
     * @return a list of learnable skills
     */
    public List<Skill> getLearnableSkills(PlayerData data) {
        List<Skill> result = new ArrayList<>();
        if (data == null || data.getGameClass() == null) {
            return result;
        }
        for (Skill skill : skills.values()) {
            if (data.hasSkill(skill.getId())) {
                continue;
            }
            if (skill.getRequiredClass() != data.getGameClass()) {
                continue;
            }
            if (data.getLevel() < skill.getRequiredLevel()) {
                continue;
            }
            result.add(skill);
        }
        return result;
    }

    /**
     * Attempts to cast an active skill for a player.
     * <p>
     * This method checks whether the player has learned and equipped the skill,
     * verifies cast conditions (cooldown, resource), deducts resources, sets
     * the cooldown, and executes the skill.
     * </p>
     *
     * @param player  the casting player
     * @param skillId the skill ID to cast
     * @return true if the skill was successfully cast
     */
    public boolean castSkill(Player player, String skillId) {
        if (player == null || skillId == null) {
            return false;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return false;
        }

        Skill skill = getSkill(skillId);
        if (skill == null) {
            plugin.getMessageManager().send(player, "command.skill-not-found");
            return false;
        }

        // Check if learned
        if (!data.hasSkill(skill.getId())) {
            plugin.getMessageManager().send(player, "skill.not-learned");
            return false;
        }

        // Check if equipped (for active skills triggered by command)
        // Note: click-triggered skills check equipment in the listener
        if (skill.getTrigger() == SkillTrigger.ACTIVE
                && skill.getCastMode() != null
                && !data.hasSkillEquipped(skill.getId())) {
            plugin.getMessageManager().send(player, "skill.not-learned");
            return false;
        }

        // Build context
        SkillCastContext context = buildContext(player, data, skill, null);
        if (context == null) {
            return false;
        }

        // Check cooldown
        if (cooldown.isOnCooldown(player.getUniqueId(), skill.getId())) {
            double remaining = cooldown.getRemainingCooldownSeconds(player.getUniqueId(), skill.getId());
            plugin.getMessageManager().send(player, "skill.cast-cooldown",
                    Pair.of("time", String.format("%.1f", remaining)));
            return false;
        }

        // Get resource cost
        var cost = skill.getResourceCost();

        // Check cast conditions
        if (!skill.canCast(context)) {
            // canCast returns false for various reasons; check resource specifically
            if (cost.hasCost() && data.getCurrentResource() < cost.amount() * costMultiplier) {
                plugin.getMessageManager().send(player, "skill.cast-no-resource",
                        Pair.of("resource", cost.type().getDisplayName()),
                        Pair.of("cost", String.valueOf((int) (cost.amount() * costMultiplier))));
            }
            return false;
        }

        // Deduct resource
        if (cost.hasCost()) {
            double costAmount = cost.amount() * costMultiplier;
            data.setCurrentResource(Math.max(0, data.getCurrentResource() - costAmount));
        }

        // Set cooldown
        long effectiveCooldown = (long) (skill.getCooldownTicks() * cooldownMultiplier);
        if (effectiveCooldown > 0) {
            cooldown.setCooldown(player.getUniqueId(), skill.getId(), effectiveCooldown);
        }

        // Execute skill
        skill.onCast(context);

        // Send success message
        plugin.getMessageManager().send(player, "skill.cast-success",
                Pair.of("skill", skill.getDisplayName()));

        return true;
    }

    /**
     * Triggers passive skills for a player based on the given trigger type.
     * <p>
     * Iterates over the player's equipped skills, finds those matching the
     * trigger type, and executes them if conditions are met.
     * </p>
     *
     * @param player  the player
     * @param trigger the trigger type
     * @param target  the target entity (may be null)
     */
    public void triggerPassive(Player player, SkillTrigger trigger, LivingEntity target) {
        if (player == null || trigger == null) {
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        for (String skillId : data.getEquippedSkillList()) {
            Skill skill = getSkill(skillId);
            if (skill == null) {
                continue;
            }

            // Check trigger match
            if (skill.getTrigger() != trigger) {
                continue;
            }

            // Check cooldown
            if (cooldown.isOnCooldown(player.getUniqueId(), skill.getId())) {
                continue;
            }

            // Build context
            SkillCastContext context = buildContext(player, data, skill, target);
            if (context == null) {
                continue;
            }

            // Check cast conditions
            if (!skill.canCast(context)) {
                continue;
            }

            // Deduct resource
            var cost = skill.getResourceCost();
            if (cost.hasCost()) {
                double costAmount = cost.amount() * costMultiplier;
                if (data.getCurrentResource() < costAmount) {
                    continue;
                }
                data.setCurrentResource(Math.max(0, data.getCurrentResource() - costAmount));
            }

            // Set cooldown
            long effectiveCooldown = (long) (skill.getCooldownTicks() * cooldownMultiplier);
            if (effectiveCooldown > 0) {
                cooldown.setCooldown(player.getUniqueId(), skill.getId(), effectiveCooldown);
            }

            // Execute skill (silently for passive)
            try {
                skill.onCast(context);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error executing passive skill '" + skill.getId() + "': " + e.getMessage(), e);
            }
        }
    }

    /**
     * Triggers passive skills for a player, including both equipped skills and
     * extra skill IDs (e.g. from equipped items).
     * <p>
     * Skill IDs are deduplicated before processing so the same skill is never
     * triggered twice in one event.
     * </p>
     *
     * @param player        the player
     * @param trigger       the trigger type
     * @param target        the target entity (may be null)
     * @param extraSkillIds additional skill IDs to trigger (e.g. from equipment)
     */
    public void triggerPassive(Player player, SkillTrigger trigger, LivingEntity target,
                               Collection<String> extraSkillIds) {
        if (player == null || trigger == null) {
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        // Collect and deduplicate all skill IDs (equipped + extra)
        Set<String> allSkillIds = new HashSet<>(data.getEquippedSkillList());
        if (extraSkillIds != null) {
            allSkillIds.addAll(extraSkillIds);
        }

        if (allSkillIds.isEmpty()) {
            return;
        }

        for (String skillId : allSkillIds) {
            Skill skill = getSkill(skillId);
            if (skill == null) {
                continue;
            }

            // Check trigger match
            if (skill.getTrigger() != trigger) {
                continue;
            }

            // Check cooldown
            if (cooldown.isOnCooldown(player.getUniqueId(), skill.getId())) {
                continue;
            }

            // Build context
            SkillCastContext context = buildContext(player, data, skill, target);
            if (context == null) {
                continue;
            }

            // Check cast conditions
            if (!skill.canCast(context)) {
                continue;
            }

            // Deduct resource
            var cost = skill.getResourceCost();
            if (cost.hasCost()) {
                double costAmount = cost.amount() * costMultiplier;
                if (data.getCurrentResource() < costAmount) {
                    continue;
                }
                data.setCurrentResource(Math.max(0, data.getCurrentResource() - costAmount));
            }

            // Set cooldown
            long effectiveCooldown = (long) (skill.getCooldownTicks() * cooldownMultiplier);
            if (effectiveCooldown > 0) {
                cooldown.setCooldown(player.getUniqueId(), skill.getId(), effectiveCooldown);
            }

            // Execute skill (silently for passive)
            try {
                skill.onCast(context);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Error executing passive skill '" + skill.getId() + "': " + e.getMessage(), e);
            }
        }
    }

    /**
     * Triggers a skill cast based on the player's input action (click type).
     * <p>
     * This is called by {@link SkillListener} when a player performs a click action.
     * It finds the first equipped active skill matching the cast mode and casts it.
     * </p>
     *
     * @param player  the player
     * @param castMode the cast mode detected from the player's action
     */
    public void castByMode(Player player, com.lumiczi.lurpg.skill.api.SkillCastMode castMode) {
        if (player == null || castMode == null) {
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        for (String skillId : data.getEquippedSkillList()) {
            Skill skill = getSkill(skillId);
            if (skill == null) {
                continue;
            }
            if (skill.getTrigger() != SkillTrigger.ACTIVE) {
                continue;
            }
            if (skill.getCastMode() != castMode) {
                continue;
            }

            castSkill(player, skill.getId());
            return; // Only cast the first matching skill
        }
    }

    /**
     * Builds a SkillCastContext for a skill cast.
     *
     * @param player the casting player
     * @param data   the player's RPG data
     * @param skill  the skill being cast
     * @param target the target entity (may be null)
     * @return the context, or null if it cannot be built
     */
    private SkillCastContext buildContext(Player player, PlayerData data, Skill skill, LivingEntity target) {
        StatMap stats = plugin.getStatManager().calculateBaseStats(data.getGameClass(), data.getLevel());
        if (stats == null) {
            stats = new StatMap();
        }

        return new SkillCastContext.Builder()
                .caster(player)
                .casterData(data)
                .skill(skill)
                .target(target)
                .castLocation(player.getLocation())
                .casterStats(stats)
                .build();
    }

    // ==================== Getters ====================

    public SkillCooldown getCooldown() {
        return cooldown;
    }

    public SkillEffectHandler getEffectHandler() {
        return effectHandler;
    }

    public double getCooldownMultiplier() {
        return cooldownMultiplier;
    }

    public double getCostMultiplier() {
        return costMultiplier;
    }
}
