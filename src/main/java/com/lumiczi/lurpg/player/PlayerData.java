package com.lumiczi.lurpg.player;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents the RPG data for a single player.
 * <p>
 * Stores the player's class, level, XP, skill points, resource amount,
 * and learned/equipped skills. Also handles leveling logic (XP accumulation,
 * level-up checks, skill point granting) by reading configuration values
 * from config.yml.
 * </p>
 * <p>
 * The {@code dirty} flag tracks whether the data has been modified since
 * the last save. The {@link #markDirty()} method should be called whenever
 * the data changes.
 * </p>
 */
public class PlayerData {

    private final LuRPGPlugin plugin;
    private final UUID uuid;

    private GameClass gameClass;
    private int level;
    private long xp;
    private int skillPoints;
    private double currentResource;
    private Set<String> learnedSkills;
    private Set<String> equippedSkills;

    private boolean dirty = false;

    /**
     * Creates a new PlayerData with default values.
     *
     * @param plugin the plugin instance for config access
     * @param uuid   the player's unique ID
     */
    public PlayerData(LuRPGPlugin plugin, UUID uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
        this.level = 1;
        this.xp = 0;
        this.skillPoints = 0;
        this.currentResource = 0;
        this.learnedSkills = new HashSet<>();
        this.equippedSkills = new LinkedHashSet<>();
    }

    // ==================== XP and Leveling ====================

    /**
     * Adds XP to the player and checks for level-ups.
     * Multiple level-ups can occur from a single XP gain.
     *
     * @param amount the amount of XP to add (must be positive)
     * @return true if at least one level-up occurred
     */
    public boolean addXp(long amount) {
        if (amount <= 0) {
            return false;
        }
        this.xp += amount;
        this.dirty = true;

        boolean leveledUp = false;
        while (canLevelUp() && this.xp >= getXpToNextLevel()) {
            long needed = getXpToNextLevel();
            this.xp -= needed;
            levelUp();
            leveledUp = true;
        }
        return leveledUp;
    }

    /**
     * Calculates the XP required to advance from the current level to the next.
     * Uses the formula: {@code base-xp * (xp-multiplier ^ (level - 1))}.
     *
     * @return the XP required for the next level
     */
    public long getXpToNextLevel() {
        FileConfiguration config = getConfig();
        double baseXp = config.getDouble("leveling.base-xp", 100.0);
        double multiplier = config.getDouble("leveling.xp-multiplier", 1.15);
        return (long) Math.floor(baseXp * Math.pow(multiplier, level - 1));
    }

    /**
     * Checks whether the player can level up further.
     *
     * @return true if the player has not reached the max level (or if there is no cap)
     */
    public boolean canLevelUp() {
        FileConfiguration config = getConfig();
        int maxLevel = config.getInt("leveling.max-level", 100);
        return maxLevel <= 0 || level < maxLevel;
    }

    /**
     * Performs a level-up: increments level and grants skill points.
     * The number of skill points granted is read from config
     * ({@code skills.learning.skill-points-per-level}).
     */
    public void levelUp() {
        FileConfiguration config = getConfig();
        int skillPointsPerLevel = config.getInt("skills.learning.skill-points-per-level", 1);
        this.level++;
        this.skillPoints += skillPointsPerLevel;
        this.dirty = true;
    }

    // ==================== Skill Points ====================

    /**
     * Spends one skill point.
     *
     * @return true if a skill point was successfully spent, false if none available
     */
    public boolean spendSkillPoint() {
        if (skillPoints <= 0) {
            return false;
        }
        skillPoints--;
        dirty = true;
        return true;
    }

    /**
     * Grants skill points to the player.
     *
     * @param amount the number of skill points to add; non-positive values are ignored
     */
    public void addSkillPoints(int amount) {
        if (amount <= 0) {
            return;
        }
        skillPoints += amount;
        dirty = true;
    }

    // ==================== Skills ====================

    /**
     * Learns a new skill.
     *
     * @param skillId the skill ID to learn
     * @return true if the skill was newly learned, false if already known or null
     */
    public boolean learnSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }
        if (learnedSkills.contains(skillId)) {
            return false;
        }
        learnedSkills.add(skillId);
        dirty = true;
        return true;
    }

    /**
     * Equips a learned skill, if the equipped limit has not been reached.
     *
     * @param skillId the skill ID to equip
     * @return true if the skill was newly equipped, false if not learned, already equipped, or limit reached
     */
    public boolean equipSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }
        if (!learnedSkills.contains(skillId)) {
            return false;
        }
        if (equippedSkills.contains(skillId)) {
            return false;
        }
        FileConfiguration config = getConfig();
        int maxEquipped = config.getInt("skills.max-equipped-skills", 5);
        if (equippedSkills.size() >= maxEquipped) {
            return false;
        }
        equippedSkills.add(skillId);
        dirty = true;
        return true;
    }

    /**
     * Unequips a skill by its slot index (0-based).
     *
     * @param slot the slot index of the equipped skill to remove
     * @return true if a skill was unequipped, false if the slot was invalid
     */
    public boolean unequipSkill(int slot) {
        if (slot < 0 || slot >= equippedSkills.size()) {
            return false;
        }
        List<String> list = new ArrayList<>(equippedSkills);
        String removed = list.remove(slot);
        equippedSkills.remove(removed);
        dirty = true;
        return true;
    }

    /**
     * Unequips a skill by its skill ID.
     *
     * @param skillId the skill ID to unequip
     * @return true if the skill was unequipped, false if it was not equipped
     */
    public boolean unequipSkill(String skillId) {
        if (skillId == null || !equippedSkills.contains(skillId)) {
            return false;
        }
        equippedSkills.remove(skillId);
        dirty = true;
        return true;
    }

    /**
     * Unlearns (forgets) a skill.
     * If the skill is currently equipped, it will be unequipped first.
     * Refunds one skill point.
     *
     * @param skillId the skill ID to unlearn
     * @return true if the skill was successfully unlearned, false if not learned
     */
    public boolean unlearnSkill(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return false;
        }
        if (!learnedSkills.contains(skillId)) {
            return false;
        }
        // Unequip first if equipped
        equippedSkills.remove(skillId);
        learnedSkills.remove(skillId);
        // Refund one skill point
        skillPoints++;
        dirty = true;
        return true;
    }

    /**
     * Checks whether the player has learned a skill.
     *
     * @param skillId the skill ID to check
     * @return true if the skill has been learned
     */
    public boolean hasSkill(String skillId) {
        return skillId != null && learnedSkills.contains(skillId);
    }

    /**
     * Checks whether the player has a skill equipped.
     *
     * @param skillId the skill ID to check
     * @return true if the skill is currently equipped
     */
    public boolean hasSkillEquipped(String skillId) {
        return skillId != null && equippedSkills.contains(skillId);
    }

    /**
     * Returns a list of equipped skill IDs in order.
     *
     * @return an unmodifiable list of equipped skill IDs
     */
    public List<String> getEquippedSkillList() {
        return Collections.unmodifiableList(new ArrayList<>(equippedSkills));
    }

    // ==================== Dirty Flag ====================

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    // ==================== Getters and Setters ====================

    public UUID getUuid() {
        return uuid;
    }

    public GameClass getGameClass() {
        return gameClass;
    }

    public void setGameClass(GameClass gameClass) {
        this.gameClass = gameClass;
        this.dirty = true;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public long getXp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0, xp);
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(int skillPoints) {
        this.skillPoints = Math.max(0, skillPoints);
    }

    public double getCurrentResource() {
        return currentResource;
    }

    public void setCurrentResource(double currentResource) {
        this.currentResource = currentResource;
    }

    public Set<String> getLearnedSkills() {
        return learnedSkills;
    }

    public void setLearnedSkills(Set<String> learnedSkills) {
        this.learnedSkills = learnedSkills != null ? learnedSkills : new HashSet<>();
    }

    public Set<String> getEquippedSkills() {
        return equippedSkills;
    }

    public void setEquippedSkills(Set<String> equippedSkills) {
        this.equippedSkills = equippedSkills != null ? new LinkedHashSet<>(equippedSkills) : new LinkedHashSet<>();
    }

    // ==================== Helper ====================

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("config");
    }

    @Override
    public String toString() {
        return "PlayerData{" +
                "uuid=" + uuid +
                ", gameClass=" + gameClass +
                ", level=" + level +
                ", xp=" + xp +
                ", skillPoints=" + skillPoints +
                ", currentResource=" + currentResource +
                ", learnedSkills=" + learnedSkills +
                ", equippedSkills=" + equippedSkills +
                ", dirty=" + dirty +
                '}';
    }
}
