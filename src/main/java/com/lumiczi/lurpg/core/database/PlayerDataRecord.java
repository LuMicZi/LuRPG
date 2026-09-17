package com.lumiczi.lurpg.core.database;

import java.util.Set;
import java.util.UUID;

/**
 * Immutable data record representing a player's persisted RPG data.
 * <p>
 * Used as the transfer object between the database layer and the rest of the plugin.
 */
public record PlayerDataRecord(
        UUID uuid,
        String classId,
        int level,
        long xp,
        int skillPoints,
        double currentResource,
        Set<String> learnedSkills,
        Set<String> equippedSkills
) {

    /**
     * Creates a default empty record for a new player.
     *
     * @param uuid the player's UUID
     * @return a record with default values (level 1, no class, no skills)
     */
    public static PlayerDataRecord empty(UUID uuid) {
        return new PlayerDataRecord(uuid, "", 1, 0, 0, 0.0, Set.of(), Set.of());
    }
}
