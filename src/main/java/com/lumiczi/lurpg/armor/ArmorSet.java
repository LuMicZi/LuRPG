package com.lumiczi.lurpg.armor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents an RPG armor set definition.
 * <p>
 * An armor set is a collection of equipment pieces (typically 4: helmet,
 * chestplate, leggings, boots) that grant additional bonuses when multiple
 * pieces are worn simultaneously.
 * </p>
 * <p>
 * Bonuses are tiered by piece count (e.g. 2-piece bonus, 4-piece bonus)
 * and are stored in a map keyed by the minimum piece count required to
 * activate them. A {@link TreeMap} is used internally to ensure bonuses
 * are evaluated in ascending order of piece count.
 * </p>
 */
public class ArmorSet {

    private final String id;
    private final String displayName;
    private final List<String> description;
    private final List<String> pieces;
    private final TreeMap<Integer, SetBonus> bonuses;

    public ArmorSet(String id, String displayName, List<String> description,
                    List<String> pieces, Map<Integer, SetBonus> bonuses) {
        this.id = id;
        this.displayName = displayName;
        this.description = description != null ? description : Collections.emptyList();
        this.pieces = pieces != null ? pieces : Collections.emptyList();
        this.bonuses = new TreeMap<>();
        if (bonuses != null) {
            this.bonuses.putAll(bonuses);
        }
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getDescription() {
        return Collections.unmodifiableList(description);
    }

    public List<String> getPieces() {
        return Collections.unmodifiableList(pieces);
    }

    /**
     * Returns the bonuses map, keyed by piece count.
     * The map is sorted in ascending order of piece count.
     *
     * @return an unmodifiable view of the bonuses map
     */
    public Map<Integer, SetBonus> getBonuses() {
        return Collections.unmodifiableMap(bonuses);
    }

    /**
     * Gets the bonus for a specific piece count threshold.
     *
     * @param pieceCount the piece count threshold
     * @return the SetBonus for that threshold, or null if none exists
     */
    public SetBonus getBonus(int pieceCount) {
        return bonuses.get(pieceCount);
    }

    /**
     * Checks whether a given item ID is one of this set's pieces.
     *
     * @param itemId the item ID to check
     * @return true if the item is part of this set
     */
    public boolean isPiece(String itemId) {
        return itemId != null && pieces.contains(itemId);
    }

    /**
     * Gets the highest tier bonus that is active for the given equipped piece count.
     * <p>
     * For example, if the set has bonuses at 2 and 4 pieces, and the player
     * has 3 pieces equipped, the 2-piece bonus is returned (the highest tier
     * that is still active).
     * </p>
     *
     * @param equippedCount the number of pieces equipped
     * @return the highest active SetBonus, or null if no bonus is active
     */
    public SetBonus getHighestActiveBonus(int equippedCount) {
        SetBonus result = null;
        for (Map.Entry<Integer, SetBonus> entry : bonuses.entrySet()) {
            if (equippedCount >= entry.getKey()) {
                result = entry.getValue();
            } else {
                break; // TreeMap is sorted, so we can stop here
            }
        }
        return result;
    }

    /**
     * Returns all active bonuses for the given equipped piece count.
     * <p>
     * For example, if the set has bonuses at 2 and 4 pieces, and the player
     * has 4 pieces equipped, both the 2-piece and 4-piece bonuses are returned.
     * </p>
     *
     * @param equippedCount the number of pieces equipped
     * @return a list of all active SetBonus entries
     */
    public java.util.List<SetBonus> getActiveBonuses(int equippedCount) {
        java.util.List<SetBonus> active = new java.util.ArrayList<>();
        for (Map.Entry<Integer, SetBonus> entry : bonuses.entrySet()) {
            if (equippedCount >= entry.getKey()) {
                active.add(entry.getValue());
            } else {
                break;
            }
        }
        return active;
    }

    @Override
    public String toString() {
        return "ArmorSet{" +
                "id='" + id + '\'' +
                ", displayName='" + displayName + '\'' +
                ", pieces=" + pieces +
                ", bonuses=" + bonuses +
                '}';
    }
}
