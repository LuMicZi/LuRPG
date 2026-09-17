package com.lumiczi.lurpg.core.database;

import java.util.Set;
import java.util.UUID;

/**
 * Abstraction layer for database operations.
 * <p>
 * Implementations include {@link SQLiteProvider} and {@link MySQLProvider}.
 * All methods are synchronous and blocking; callers should invoke them from
 * an asynchronous thread (see {@link DatabaseManager} for async wrappers).
 */
public interface DataProvider {

    /**
     * Initializes the database connection and creates tables if necessary.
     *
     * @throws Exception if initialization fails
     */
    void initialize() throws Exception;

    /**
     * Shuts down the database connection and releases resources.
     */
    void shutdown();

    /**
     * Saves (or upserts) player data to the database.
     *
     * @param uuid           the player's UUID
     * @param classId        the player's class identifier
     * @param level          the player's level
     * @param xp             the player's current XP
     * @param skillPoints    available skill points
     * @param currentResource current resource amount (mana, energy, etc.)
     * @param learnedSkills   set of learned skill IDs
     * @param equippedSkills  set of equipped skill IDs
     */
    void savePlayerData(UUID uuid, String classId, int level, long xp, int skillPoints,
                        double currentResource, Set<String> learnedSkills, Set<String> equippedSkills);

    /**
     * Loads player data from the database.
     *
     * @param uuid the player's UUID
     * @return the player data record, or null if not found
     */
    PlayerDataRecord loadPlayerData(UUID uuid);

    /**
     * Checks whether a player record exists in the database.
     *
     * @param uuid the player's UUID
     * @return true if the player exists
     */
    boolean playerExists(UUID uuid);

    /**
     * Creates a new player record with default values.
     *
     * @param uuid    the player's UUID
     * @param classId the player's initial class identifier
     */
    void createPlayer(UUID uuid, String classId);
}
