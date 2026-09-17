package com.lumiczi.lurpg.exp;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.core.message.Pair;
import com.lumiczi.lurpg.player.PlayerData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
// import org.bukkit.event.player.PlayerFishEvent; // Removed in Paper 26.2

/**
 * Manages XP gain from various sources (mob kills, PvP, gathering, quests).
 */
public class ExpManager implements Listener {

    private final LuRPGPlugin plugin;

    private boolean mobKillEnabled;
    private double mobKillMultiplier;
    private double mythicMobsMultiplier;
    private boolean pvpKillEnabled;
    private double pvpKillMultiplier;
    private boolean gatheringEnabled;
    private int miningXp;
    private int fishingXp;
    private int woodcuttingXp;
    private int farmingXp;
    private boolean questEnabled;

    public ExpManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        var config = plugin.getConfigManager().getConfig("config");
        if (config == null) return;

        var sources = config.getConfigurationSection("leveling.xp-sources");
        if (sources == null) return;

        var mobSection = sources.getConfigurationSection("mob-kill");
        if (mobSection != null) {
            mobKillEnabled = mobSection.getBoolean("enabled", true);
            mobKillMultiplier = mobSection.getDouble("multiplier", 1.0);
            mythicMobsMultiplier = mobSection.getDouble("mythicmobs-multiplier", 2.0);
        }

        var pvpSection = sources.getConfigurationSection("pvp-kill");
        if (pvpSection != null) {
            pvpKillEnabled = pvpSection.getBoolean("enabled", true);
            pvpKillMultiplier = pvpSection.getDouble("multiplier", 50.0);
        }

        var gatherSection = sources.getConfigurationSection("gathering");
        if (gatherSection != null) {
            gatheringEnabled = gatherSection.getBoolean("enabled", true);
            miningXp = gatherSection.getInt("mining-xp", 5);
            fishingXp = gatherSection.getInt("fishing-xp", 8);
            woodcuttingXp = gatherSection.getInt("woodcutting-xp", 5);
            farmingXp = gatherSection.getInt("farming-xp", 3);
        }

        var questSection = sources.getConfigurationSection("quest");
        if (questSection != null) {
            questEnabled = questSection.getBoolean("enabled", true);
        }
    }

    public void giveXp(Player player, long amount) {
        if (amount <= 0) return;

        var pm = plugin.getPlayerManager();
        var data = pm.getPlayerData(player.getUniqueId());
        if (data == null) return;

        int oldLevel = data.getLevel();
        data.addXp(amount);
        int newLevel = data.getLevel();
        data.markDirty();

        plugin.getMessageManager().send(player, "level.xp-gain",
                Pair.of("xp", String.valueOf(amount)));

        if (newLevel > oldLevel) {
            handleLevelUp(player, data, oldLevel, newLevel);
        }

        updateXpBar(player, data);
    }

    public void awardMobKillXp(Player player, LivingEntity entity) {
        if (!mobKillEnabled) return;
        double baseXp = getMobBaseXp(entity);
        double multiplier = mobKillMultiplier;

        if (plugin.getHookManager().isMythicMobsEnabled()) {
            try {
                var mythicHook = plugin.getHookManager().getMythicMobsHook();
                if (mythicHook != null && mythicHook.isMythicMob(entity)) {
                    multiplier = mythicMobsMultiplier;
                }
            } catch (Exception ignored) {}
        }

        long finalXp = Math.round(baseXp * multiplier);
        if (finalXp > 0) {
            giveXp(player, finalXp);
        }
    }

    public void awardPvpKillXp(Player killer, Player victim) {
        if (!pvpKillEnabled) return;
        var pm = plugin.getPlayerManager();
        var victimData = pm.getPlayerData(victim.getUniqueId());
        int victimLevel = victimData != null ? victimData.getLevel() : 1;
        long xp = Math.round(victimLevel * pvpKillMultiplier);
        if (xp > 0) {
            giveXp(killer, xp);
        }
    }

    public void awardGatheringXp(Player player, GatherType type) {
        if (!gatheringEnabled) return;
        int xp = switch (type) {
            case MINING -> miningXp;
            case FISHING -> fishingXp;
            case WOODCUTTING -> woodcuttingXp;
            case FARMING -> farmingXp;
        };
        if (xp > 0) {
            giveXp(player, xp);
        }
    }

    public void awardQuestXp(Player player, long amount) {
        if (!questEnabled) return;
        giveXp(player, amount);
    }

    private void handleLevelUp(Player player, PlayerData data, int oldLevel, int newLevel) {
        var config = plugin.getConfigManager().getConfig("config");
        int skillPointsPerLevel = 1;
        if (config != null) {
            skillPointsPerLevel = config.getInt("skills.learning.skill-points-per-level", 1);
        }

        int levelsGained = newLevel - oldLevel;
        int earnedSkillPoints = skillPointsPerLevel * levelsGained;
        data.addSkillPoints(earnedSkillPoints);

        for (int i = 0; i < levelsGained; i++) {
            int levelMsg = oldLevel + i + 1;
            plugin.getMessageManager().send(player, "level.level-up",
                    Pair.of("level", String.valueOf(levelMsg)));
        }

        updatePlayerStats(player, data);
        plugin.getLogger().info("Player " + player.getName() + " leveled up from " +
                oldLevel + " to " + newLevel);
    }

    private void updatePlayerStats(Player player, PlayerData data) {
        if (data.getGameClass() == null) return;
        var combatCalc = new com.lumiczi.lurpg.combat.CombatStatCalculator(plugin);
        var fullStats = combatCalc.getPlayerCombatStats(player);
        double fullHealth = fullStats.get(com.lumiczi.lurpg.stat.StatType.MAX_HEALTH);
        double vanillaBase = 20.0;
        double bonusHealth = fullHealth - vanillaBase;
        if (bonusHealth < 0) bonusHealth = 0;
        player.setMaxHealth(vanillaBase + bonusHealth);
        player.setHealth(player.getMaxHealth());
    }

    private void updateXpBar(Player player, PlayerData data) {
        long xp = data.getXp();
        long xpToNext = data.getXpToNextLevel();
        if (xpToNext <= 0) return;
        float progress = (float) xp / xpToNext;
        if (progress > 1.0f) progress = 1.0f;
        if (progress < 0f) progress = 0f;
        player.setLevel(data.getLevel());
        player.setExp(progress);
    }

    private double getMobBaseXp(LivingEntity entity) {
        String typeName = entity.getType().name();
        return switch (typeName) {
            case "ENDER_DRAGON" -> 500;
            case "WITHER" -> 300;
            case "ELDER_GUARDIAN" -> 200;
            case "WARDEN" -> 400;
            case "RAVAGER" -> 150;
            case "ENDERMAN", "BLAZE", "GHAST", "WITHER_SKELETON" -> 20;
            case "CREEPER", "SKELETON", "ZOMBIE", "SPIDER", "WITCH" -> 10;
            case "PIGLIN_BRUTE" -> 30;
            case "HOGLIN", "ZOGLIN" -> 15;
            case "IRON_GOLEM" -> 25;
            case "VINDICATOR", "EVOKER", "PILLAGER" -> 20;
            case "PHANTOM", "DROWNED", "HUSK", "STRAY" -> 12;
            case "SLIME", "MAGMA_CUBE" -> 8;
            case "SILVERFISH", "ENDERMITE" -> 3;
            case "COW", "PIG", "SHEEP", "CHICKEN", "RABBIT" -> 2;
            default -> 5;
        };
    }

    // ===== Event Handlers =====

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        var entity = event.getEntity();
        var killer = entity.getKiller();
        if (killer == null) return;

        var data = plugin.getPlayerManager().getPlayerData(killer.getUniqueId());
        if (data == null || data.getGameClass() == null) return;

        awardMobKillXp(killer, entity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        var victim = event.getEntity();
        var killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        var data = plugin.getPlayerManager().getPlayerData(killer.getUniqueId());
        if (data == null || data.getGameClass() == null) return;

        awardPvpKillXp(killer, victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        var player = event.getPlayer();
        var data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        if (data == null || data.getGameClass() == null) return;

        String blockType = event.getBlock().getType().name();

        if (isOre(blockType) || isStone(blockType)) {
            awardGatheringXp(player, GatherType.MINING);
        } else if (isLog(blockType)) {
            awardGatheringXp(player, GatherType.WOODCUTTING);
        } else if (isCrop(blockType)) {
            awardGatheringXp(player, GatherType.FARMING);
        }
    }

    // TODO: Re-enable fishing XP when Paper 26.2 adds back PlayerFishEvent
    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public void onFish(PlayerFishEvent event) {
    //     if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
    //     var player = event.getPlayer();
    //     var data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
    //     if (data == null || data.getGameClass() == null) return;
    //     awardGatheringXp(player, GatherType.FISHING);
    // }

    // ===== Helper Methods =====

    private boolean isOre(String type) {
        return type.contains("ORE") || type.equals("ANCIENT_DEBRIS");
    }

    private boolean isStone(String type) {
        return type.equals("STONE") || type.equals("COBBLESTONE") || type.equals("DEEPSLATE") ||
                type.equals("NETHERRACK") || type.equals("BLACKSTONE") || type.equals("BASALT") ||
                type.equals("TUFF") || type.equals("GRANITE") || type.equals("DIORITE") ||
                type.equals("ANDESITE");
    }

    private boolean isLog(String type) {
        return type.endsWith("_LOG") || type.endsWith("_STEM") ||
                type.equals("CRIMSON_STEM") || type.equals("WARPED_STEM");
    }

    private boolean isCrop(String type) {
        return type.equals("WHEAT") || type.equals("CARROTS") || type.equals("POTATOES") ||
                type.equals("BEETROOTS") || type.equals("MELON") || type.equals("PUMPKIN") ||
                type.equals("NETHER_WART") || type.equals("COCOA") || type.equals("SWEET_BERRY_BUSH") ||
                type.equals("SUGAR_CANE");
    }

    public enum GatherType {
        MINING, FISHING, WOODCUTTING, FARMING
    }
}
