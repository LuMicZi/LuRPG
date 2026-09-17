package com.lumiczi.lurpg.potion;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.item.ItemManager;
import com.lumiczi.lurpg.player.PlayerData;
import com.lumiczi.lurpg.stat.StatType;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the application of RPG potion effects to players.
 * <p>
 * Supports five categories of potion effects:
 * <ul>
 *   <li><b>heal</b> - instant health restoration (fixed amount or percentage of max health)</li>
 *   <li><b>regen</b> - continuous health regeneration over time</li>
 *   <li><b>buff</b> - temporary stat multiplier (tracked via {@link ActiveBuff})</li>
 *   <li><b>cure</b> - removes specified negative potion effects</li>
 *   <li><b>resource</b> - restores the player's class resource (mana/rage/energy)</li>
 * </ul>
 * </p>
 * <p>
 * Buff effects are stored as temporary multipliers and can be queried by
 * {@link com.lumiczi.lurpg.combat.CombatStatCalculator} via
 * {@link #getActiveBuffs(UUID)}.
 * </p>
 */
public class PotionEffectHandler {

    private final LuRPGPlugin plugin;

    // Active buff tracking: UUID -> list of ActiveBuff
    private final ConcurrentHashMap<UUID, List<ActiveBuff>> activeBuffs = new ConcurrentHashMap<>();

    public PotionEffectHandler(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Applies all effects defined in the potion to the player.
     * <p>
     * This method reads the potion's effects configuration section and
     * dispatches each effect to the appropriate handler.
     * </p>
     *
     * @param player the player to apply effects to
     * @param potion the RPG potion definition
     */
    public void applyEffects(Player player, RPGPotion potion) {
        if (player == null || potion == null) {
            return;
        }

        ConfigurationSection effects = potion.getEffects();
        if (effects == null) {
            return;
        }

        // Process each effect type
        if (effects.isConfigurationSection("heal")) {
            applyHealEffect(player, effects.getConfigurationSection("heal"));
        }
        if (effects.isConfigurationSection("regen")) {
            applyRegenEffect(player, effects.getConfigurationSection("regen"));
        }
        if (effects.isConfigurationSection("buff")) {
            applyBuffEffect(player, effects.getConfigurationSection("buff"));
        }
        if (effects.isConfigurationSection("cure")) {
            applyCureEffect(player, effects.getConfigurationSection("cure"));
        }
        if (effects.isConfigurationSection("resource")) {
            applyResourceEffect(player, effects.getConfigurationSection("resource"));
        }
    }

    /**
     * Applies an instant heal effect.
     * <p>
     * Supports two heal types:
     * <ul>
     *   <li>{@code INSTANT} - heals a fixed amount</li>
     *   <li>{@code PERCENT} - heals a percentage of max health</li>
     * </ul>
     * </p>
     *
     * @param player     the player to heal
     * @param healConfig the heal effect configuration
     */
    private void applyHealEffect(Player player, ConfigurationSection healConfig) {
        if (healConfig == null || player.isDead()) {
            return;
        }

        String type = healConfig.getString("type", "INSTANT");
        double amount = healConfig.getDouble("amount", 0);
        double maxHealth = getMaxHealth(player);

        double healAmount;
        if ("PERCENT".equalsIgnoreCase(type)) {
            healAmount = maxHealth * (amount / 100.0);
        } else {
            healAmount = amount;
        }

        double currentHealth = player.getHealth();
        double newHealth = Math.min(currentHealth + healAmount, maxHealth);
        player.setHealth(newHealth);
    }

    /**
     * Applies a regeneration effect using Bukkit's PotionEffect API.
     * <p>
     * The regeneration effect heals the player over time at the configured
     * interval for the configured duration.
     * </p>
     *
     * @param player      the player to regenerate
     * @param regenConfig the regen effect configuration
     */
    private void applyRegenEffect(Player player, ConfigurationSection regenConfig) {
        if (regenConfig == null || player.isDead()) {
            return;
        }

        int duration = regenConfig.getInt("duration", 100);
        int amplifier = regenConfig.getInt("amplifier", 0);

        // If interval is specified, calculate the equivalent amplifier
        // Bukkit regen heals 1 HP per 50 ticks at amplifier 0
        // Higher amplifiers heal faster
        int interval = regenConfig.getInt("interval", 0);
        if (interval > 0) {
            // Approximate the amplifier based on desired interval
            amplifier = Math.max(0, (50 / interval) - 1);
        }

        PotionEffectType regenType = getPotionEffectType("regeneration");
        if (regenType != null) {
            player.addPotionEffect(new PotionEffect(regenType, duration, amplifier, false, true, true));
        }
    }

    /**
     * Applies a buff effect as a temporary stat multiplier.
     * <p>
     * The buff is stored in the active buffs map and will be applied by
     * {@link com.lumiczi.lurpg.combat.CombatStatCalculator} when computing
     * the player's combat stats. The buff expires after the configured duration.
     * </p>
     *
     * @param player     the player to buff
     * @param buffConfig the buff effect configuration
     */
    private void applyBuffEffect(Player player, ConfigurationSection buffConfig) {
        if (buffConfig == null) {
            return;
        }

        String statName = buffConfig.getString("stat", "");
        StatType statType = ItemManager.parseStatType(statName);
        if (statType == null) {
            plugin.getLogger().warning("Unknown stat type '" + statName + "' in potion buff effect.");
            return;
        }

        double multiplier = buffConfig.getDouble("multiplier", 1.0);
        int durationTicks = buffConfig.getInt("duration", 200);

        // Convert ticks to milliseconds for expiry time
        long durationMs = durationTicks * 50L; // 1 tick = 50ms
        long expiryTime = System.currentTimeMillis() + durationMs;

        ActiveBuff buff = new ActiveBuff(statType, multiplier, expiryTime);

        UUID uuid = player.getUniqueId();
        activeBuffs.computeIfAbsent(uuid, k -> new ArrayList<>()).add(buff);

        // Schedule buff removal
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            List<ActiveBuff> buffs = activeBuffs.get(uuid);
            if (buffs != null) {
                buffs.removeIf(b -> b.equals(buff));
                if (buffs.isEmpty()) {
                    activeBuffs.remove(uuid);
                }
            }
        }, durationTicks);
    }

    /**
     * Applies a cure (detox) effect, removing specified negative potion effects.
     *
     * @param player     the player to cure
     * @param cureConfig the cure effect configuration
     */
    private void applyCureEffect(Player player, ConfigurationSection cureConfig) {
        if (cureConfig == null) {
            return;
        }

        List<String> types = cureConfig.getStringList("types");
        for (String typeName : types) {
            PotionEffectType effectType = getPotionEffectType(typeName.toLowerCase());
            if (effectType != null) {
                player.removePotionEffect(effectType);
            }
        }

        // Also extinguish fire if configured
        if (cureConfig.getBoolean("extinguish", false)) {
            player.setFireTicks(0);
        }
    }

    /**
     * Applies a resource restoration effect.
     * <p>
     * Restores the player's class resource (mana, rage, or energy) by the
     * configured amount. The resource is capped at the player's maximum
     * resource value.
     * </p>
     *
     * @param player        the player
     * @param resourceConfig the resource effect configuration
     */
    private void applyResourceEffect(Player player, ConfigurationSection resourceConfig) {
        if (resourceConfig == null) {
            return;
        }

        double amount = resourceConfig.getDouble("amount", 0);
        if (amount <= 0) {
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null) {
            return;
        }

        double current = data.getCurrentResource();
        double newResource = current + amount;

        // Cap at resource max from stats (if available)
        var statManager = plugin.getStatManager();
        if (statManager != null && data.getGameClass() != null) {
            var baseStats = statManager.calculateBaseStats(data.getGameClass(), data.getLevel());
            double maxResource = baseStats.get(com.lumiczi.lurpg.stat.StatType.RESOURCE_MAX);
            if (maxResource > 0) {
                newResource = Math.min(newResource, maxResource);
            }
        }

        data.setCurrentResource(newResource);
        data.markDirty();
    }

    /**
     * Returns the list of active buffs for a player.
     * <p>
     * Expired buffs are filtered out before returning.
     * </p>
     *
     * @param uuid the player's UUID
     * @return a list of active ActiveBuff entries (may be empty, never null)
     */
    public List<ActiveBuff> getActiveBuffs(UUID uuid) {
        List<ActiveBuff> buffs = activeBuffs.get(uuid);
        if (buffs == null) {
            return List.of();
        }

        // Filter out expired buffs
        buffs.removeIf(ActiveBuff::isExpired);
        if (buffs.isEmpty()) {
            activeBuffs.remove(uuid);
            return List.of();
        }

        return new ArrayList<>(buffs);
    }

    /**
     * Clears all active buffs for a player.
     *
     * @param uuid the player's UUID
     */
    public void clearBuffs(UUID uuid) {
        activeBuffs.remove(uuid);
    }

    /**
     * Clears all active buffs for all players.
     */
    public void clearAllBuffs() {
        activeBuffs.clear();
    }

    /**
     * Gets the player's maximum health, considering both the Bukkit attribute
     * and the RPG MAX_HEALTH stat.
     *
     * @param player the player
     * @return the effective maximum health
     */
    private double getMaxHealth(Player player) {
        double maxHealth = 20.0;
        var maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealth = maxHealthAttr.getValue();
        }

        // Consider RPG MAX_HEALTH stat if higher
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data != null && data.getGameClass() != null) {
            var statManager = plugin.getStatManager();
            if (statManager != null) {
                var baseStats = statManager.calculateBaseStats(data.getGameClass(), data.getLevel());
                double rpgMaxHealth = baseStats.get(com.lumiczi.lurpg.stat.StatType.MAX_HEALTH);
                if (rpgMaxHealth > maxHealth) {
                    maxHealth = rpgMaxHealth;
                }
            }
        }

        return maxHealth;
    }

    /**
     * Resolves a PotionEffectType by its Minecraft key name.
     *
     * @param key the potion effect key (e.g. "regeneration", "poison", "wither")
     * @return the PotionEffectType, or null if not found
     */
    private PotionEffectType getPotionEffectType(String key) {
        try {
            return org.bukkit.Registry.EFFECT.get(org.bukkit.NamespacedKey.minecraft(key));
        } catch (Exception e) {
            return switch (key) {
                case "slowness", "slow" -> PotionEffectType.SLOWNESS;
                case "wither" -> PotionEffectType.WITHER;
                case "weakness" -> PotionEffectType.WEAKNESS;
                case "poison" -> PotionEffectType.POISON;
                case "regeneration" -> PotionEffectType.REGENERATION;
                case "speed" -> PotionEffectType.SPEED;
                default -> null;
            };
        }
    }

    /**
     * Represents a temporary stat buff applied by a potion.
     * <p>
     * Each buff has:
     * <ul>
     *   <li>A target {@link StatType} that the buff affects</li>
     *   <li>A multiplier (e.g. 1.3 = 130% of the base stat value)</li>
     *   <li>An expiry time (epoch milliseconds) after which the buff is no longer active</li>
     * </ul>
     * </p>
     *
     * @param stat       the stat type being buffed
     * @param multiplier the multiplier to apply to the stat (1.0 = no change)
     * @param expiryTime the epoch millisecond time when the buff expires
     */
    public record ActiveBuff(StatType stat, double multiplier, long expiryTime) {

        /**
         * Checks whether this buff has expired.
         *
         * @return true if the current time is past the expiry time
         */
        public boolean isExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }
    }
}
