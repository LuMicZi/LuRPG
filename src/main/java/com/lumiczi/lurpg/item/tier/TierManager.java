package com.lumiczi.lurpg.item.tier;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages item tiers (quality/grade system).
 * <p>
 * Each RPG item can have a tier that multiplies its stats.
 * Tiers are defined in {@code item_tiers.yml} and ordered from
 * lowest to highest quality.
 * </p>
 * <p>
 * Features:
 * <ul>
 *   <li>Weighted random tier selection for newly obtained items</li>
 *   <li>Tier upgrade with configurable success rates</li>
 *   <li>Stat multiplier applied to all item stats</li>
 * </ul>
 */
public class TierManager {

    private final LuRPGPlugin plugin;

    /** Ordered list of tiers from lowest to highest. */
    private final List<Tier> tiersOrdered = new ArrayList<>();

    /** Tier lookup by ID. */
    private final Map<String, Tier> tierMap = new LinkedHashMap<>();

    /** Upgrade success rates: currentTierId -> success chance (0.0 ~ 1.0). */
    private final Map<String, Double> upgradeChances = new LinkedHashMap<>();

    /** Total weight for weighted random selection. */
    private int totalWeight = 0;

    public TierManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads or reloads tier definitions from item_tiers.yml.
     */
    public void reload() {
        tiersOrdered.clear();
        tierMap.clear();
        upgradeChances.clear();
        totalWeight = 0;

        FileConfiguration config = plugin.getConfigManager().getConfig("item_tiers");
        if (config == null) {
            plugin.getLogger().warning("item_tiers.yml not found; TierManager loaded with no tiers.");
            return;
        }

        // Load tiers
        ConfigurationSection tiersSection = config.getConfigurationSection("tiers");
        if (tiersSection != null) {
            for (String tierId : tiersSection.getKeys(false)) {
                ConfigurationSection tierSection = tiersSection.getConfigurationSection(tierId);
                if (tierSection == null) continue;

                String name = tierSection.getString("name", tierId);
                String color = tierSection.getString("color", "<white>");
                double multiplier = tierSection.getDouble("multiplier", 1.0);
                int weight = tierSection.getInt("weight", 1);
                if (weight < 1) weight = 1;

                Tier tier = new Tier(tierId, name, color, multiplier, weight);
                tiersOrdered.add(tier);
                tierMap.put(tierId, tier);
                totalWeight += weight;
            }
        }

        // Load upgrade chances
        ConfigurationSection upgradeSection = config.getConfigurationSection("upgrade-chances");
        if (upgradeSection != null) {
            for (String tierId : upgradeSection.getKeys(false)) {
                double chance = upgradeSection.getDouble(tierId, 0.0);
                if (chance < 0.0) chance = 0.0;
                if (chance > 1.0) chance = 1.0;
                upgradeChances.put(tierId, chance);
            }
        }

        plugin.getLogger().info("Loaded " + tiersOrdered.size() + " item tiers.");
    }

    /**
     * Gets a tier by its ID.
     *
     * @param id the tier ID (e.g. "epic")
     * @return the Tier, or null if not found
     */
    public Tier getTier(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return tierMap.get(id.toLowerCase());
    }

    /**
     * Returns the ordered list of tiers from lowest to highest quality.
     *
     * @return unmodifiable list of tiers
     */
    public List<Tier> getTiersOrdered() {
        return List.copyOf(tiersOrdered);
    }

    /**
     * Randomly selects a tier based on weight.
     *
     * @return a randomly selected Tier, or null if no tiers are defined
     */
    public Tier randomTier() {
        if (tiersOrdered.isEmpty() || totalWeight <= 0) {
            return null;
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (Tier tier : tiersOrdered) {
            cumulative += tier.weight();
            if (roll < cumulative) {
                return tier;
            }
        }

        // Fallback to last tier
        return tiersOrdered.get(tiersOrdered.size() - 1);
    }

    /**
     * Gets the next tier up from the given tier (for upgrading).
     *
     * @param currentTierId the current tier ID
     * @return the next Tier, or null if already at max tier or not found
     */
    public Tier getNextTier(String currentTierId) {
        if (currentTierId == null || currentTierId.isBlank()) {
            return null;
        }
        int index = -1;
        for (int i = 0; i < tiersOrdered.size(); i++) {
            if (tiersOrdered.get(i).id().equalsIgnoreCase(currentTierId)) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= tiersOrdered.size() - 1) {
            return null;
        }
        return tiersOrdered.get(index + 1);
    }

    /**
     * Gets the upgrade success chance for the given tier.
     *
     * @param currentTierId the current tier ID
     * @return the success chance (0.0 ~ 1.0), or 0.0 if not configured
     */
    public double getUpgradeChance(String currentTierId) {
        if (currentTierId == null || currentTierId.isBlank()) {
            return 0.0;
        }
        Double chance = upgradeChances.get(currentTierId.toLowerCase());
        return chance != null ? chance : 0.0;
    }

    /**
     * Returns the default (base) tier with multiplier 1.0.
     * This is used when an item has no explicit tier.
     *
     * @return the fine tier (multiplier 1.0), or a synthetic default if not found
     */
    public Tier getDefaultTier() {
        Tier fine = tierMap.get("fine");
        if (fine != null) {
            return fine;
        }
        // Fallback: first tier with multiplier >= 1.0, else a synthetic default
        for (Tier tier : tiersOrdered) {
            if (tier.multiplier() >= 1.0) {
                return tier;
            }
        }
        return new Tier("fine", "精良", "<white>", 1.0, 0);
    }

    /**
     * Checks whether a tier ID is valid (exists in the configuration).
     *
     * @param tierId the tier ID to check
     * @return true if the tier exists
     */
    public boolean isValidTier(String tierId) {
        return tierId != null && tierMap.containsKey(tierId.toLowerCase());
    }

    // ---- Data model ----

    /**
     * Immutable data record representing an item tier.
     *
     * @param id         the unique tier identifier (e.g. "epic")
     * @param name       the display name (e.g. "史诗")
     * @param color      the MiniMessage color prefix (e.g. "<dark_purple>")
     * @param multiplier the stat multiplier (e.g. 1.3 for +30%)
     * @param weight     the random selection weight
     */
    public record Tier(
            String id,
            String name,
            String color,
            double multiplier,
            int weight
    ) {
        /**
         * Returns the formatted display string with color.
         *
         * @return color + name + reset
         */
        public String formattedName() {
            return color + name + "<reset>";
        }
    }
}
