package com.lumiczi.lurpg.skill;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player, per-skill cooldowns.
 * <p>
 * Uses a {@link ConcurrentHashMap} mapping player UUIDs to maps of skill IDs
 * to cooldown expiry timestamps (in milliseconds). All operations are thread-safe.
 * </p>
 * <p>
 * Cooldown durations are specified in ticks (20 ticks = 1 second), but internally
 * stored as millisecond timestamps for efficient expiry checking.
 * </p>
 */
public class SkillCooldown {

    private final ConcurrentHashMap<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Checks whether a player's skill is currently on cooldown.
     *
     * @param uuid    the player's UUID
     * @param skillId the skill ID
     * @return true if the skill is on cooldown (not yet expired)
     */
    public boolean isOnCooldown(UUID uuid, String skillId) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) {
            return false;
        }
        Long expiry = playerCooldowns.get(skillId);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            // Cooldown has expired; clean up
            playerCooldowns.remove(skillId);
            return false;
        }
        return true;
    }

    /**
     * Returns the remaining cooldown time in ticks.
     *
     * @param uuid    the player's UUID
     * @param skillId the skill ID
     * @return the remaining ticks, or 0 if not on cooldown
     */
    public long getRemainingCooldown(UUID uuid, String skillId) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) {
            return 0;
        }
        Long expiry = playerCooldowns.get(skillId);
        if (expiry == null) {
            return 0;
        }
        long remainingMs = expiry - System.currentTimeMillis();
        if (remainingMs <= 0) {
            playerCooldowns.remove(skillId);
            return 0;
        }
        // Convert milliseconds to ticks (20 ticks per second = 50ms per tick)
        return (remainingMs + 49) / 50;
    }

    /**
     * Returns the remaining cooldown time in seconds (rounded to one decimal place).
     *
     * @param uuid    the player's UUID
     * @param skillId the skill ID
     * @return the remaining seconds, or 0.0 if not on cooldown
     */
    public double getRemainingCooldownSeconds(UUID uuid, String skillId) {
        long ticks = getRemainingCooldown(uuid, skillId);
        return ticks / 20.0;
    }

    /**
     * Sets a cooldown for a player's skill.
     *
     * @param uuid    the player's UUID
     * @param skillId the skill ID
     * @param ticks   the cooldown duration in ticks (20 ticks = 1 second)
     */
    public void setCooldown(UUID uuid, String skillId, long ticks) {
        if (ticks <= 0) {
            clearCooldown(uuid, skillId);
            return;
        }
        // Convert ticks to milliseconds
        long expiryMs = System.currentTimeMillis() + (ticks * 50L);
        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(skillId, expiryMs);
    }

    /**
     * Clears the cooldown for a specific player's skill.
     *
     * @param uuid    the player's UUID
     * @param skillId the skill ID
     */
    public void clearCooldown(UUID uuid, String skillId) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns != null) {
            playerCooldowns.remove(skillId);
        }
    }

    /**
     * Clears all cooldowns for a player.
     *
     * @param uuid the player's UUID
     */
    public void clearAllCooldowns(UUID uuid) {
        cooldowns.remove(uuid);
    }

    /**
     * Clears all cooldowns for all players.
     */
    public void clearAll() {
        cooldowns.clear();
    }
}
