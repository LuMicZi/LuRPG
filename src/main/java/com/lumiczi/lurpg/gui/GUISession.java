package com.lumiczi.lurpg.gui;

import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an active GUI session for a player.
 * <p>
 * Tracks the player's UUID, the current GUI type, the current page
 * (for paginated GUIs), arbitrary parameters, and the Bukkit inventory
 * instance associated with this session.
 * </p>
 * <p>
 * Parameters can be used to pass context between GUIs, such as the
 * item ID being viewed in an item detail GUI, or a filter class for
 * a weapon list GUI.
 * </p>
 */
public class GUISession {

    private final UUID playerUUID;
    private GUIType type;
    private int currentPage;
    private final Map<String, Object> params;
    private Inventory inventory;

    /**
     * Creates a new GUI session for the specified player and GUI type.
     *
     * @param playerUUID the player's unique ID
     * @param type       the GUI type
     */
    public GUISession(UUID playerUUID, GUIType type) {
        this.playerUUID = playerUUID;
        this.type = type;
        this.currentPage = 0;
        this.params = new HashMap<>();
        this.inventory = null;
    }

    /**
     * Creates a new GUI session with an initial page.
     *
     * @param playerUUID the player's unique ID
     * @param type       the GUI type
     * @param page       the initial page (0-based)
     */
    public GUISession(UUID playerUUID, GUIType type, int page) {
        this(playerUUID, type);
        this.currentPage = page;
    }

    // ==================== Getters and Setters ====================

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public GUIType getType() {
        return type;
    }

    public void setType(GUIType type) {
        this.type = type;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Map<String, Object> getParams() {
        return params;
    }

    // ==================== Parameter Helpers ====================

    /**
     * Gets a parameter value by key.
     *
     * @param key the parameter key
     * @return the parameter value, or null if not present
     */
    public Object getParam(String key) {
        return params.get(key);
    }

    /**
     * Gets a parameter value by key, returning the default value if not present.
     *
     * @param key          the parameter key
     * @param defaultValue the default value to return if the key is not present
     * @param <T>          the expected type
     * @return the parameter value cast to the expected type, or the default
     */
    @SuppressWarnings("unchecked")
    public <T> T getParam(String key, T defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return (T) value;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    /**
     * Sets a parameter value.
     *
     * @param key   the parameter key
     * @param value the parameter value
     */
    public void setParam(String key, Object value) {
        params.put(key, value);
    }

    /**
     * Checks whether a parameter is present.
     *
     * @param key the parameter key
     * @return true if the parameter exists
     */
    public boolean hasParam(String key) {
        return params.containsKey(key);
    }

    /**
     * Removes a parameter.
     *
     * @param key the parameter key to remove
     */
    public void removeParam(String key) {
        params.remove(key);
    }

    @Override
    public String toString() {
        return "GUISession{" +
                "playerUUID=" + playerUUID +
                ", type=" + type +
                ", currentPage=" + currentPage +
                ", params=" + params +
                '}';
    }
}
