package com.lumiczi.lurpg;

import com.lumiczi.lurpg.class_.ClassManager;
import com.lumiczi.lurpg.command.AdminCommand;
import com.lumiczi.lurpg.command.ClassCommand;
import com.lumiczi.lurpg.command.PlayerCommand;
import com.lumiczi.lurpg.command.SkillCommand;
import com.lumiczi.lurpg.combat.CombatListener;
import com.lumiczi.lurpg.core.config.ConfigManager;
import com.lumiczi.lurpg.core.database.DatabaseManager;
import com.lumiczi.lurpg.core.hook.HookManager;
import com.lumiczi.lurpg.core.message.MessageManager;
import com.lumiczi.lurpg.gui.GUIManager;
import com.lumiczi.lurpg.armor.ArmorSetManager;
import com.lumiczi.lurpg.exp.ExpManager;
import com.lumiczi.lurpg.item.ItemManager;
import com.lumiczi.lurpg.item.SealListener;
import com.lumiczi.lurpg.item.tier.TierUpgradeListener;
import com.lumiczi.lurpg.player.PlayerManager;
import com.lumiczi.lurpg.potion.PotionManager;
import com.lumiczi.lurpg.skill.SkillLearnManager;
import com.lumiczi.lurpg.skill.SkillListener;
import com.lumiczi.lurpg.skill.SkillManager;
import com.lumiczi.lurpg.stat.StatManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * LuRPG Plugin Main Class
 * Author: LuMicZi
 * Target: Paper 26.2
 */
public class LuRPGPlugin extends JavaPlugin {

    private static LuRPGPlugin instance;

    // Core managers
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private MessageManager messageManager;
    private HookManager hookManager;

    // System managers
    private PlayerManager playerManager;
    private ClassManager classManager;
    private StatManager statManager;
    private ItemManager itemManager;
    private SkillManager skillManager;
    private SkillLearnManager skillLearnManager;
    private ArmorSetManager armorSetManager;
    private PotionManager potionManager;
    private ExpManager expManager;
    private GUIManager guiManager;

    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();
        String step = "initialization";

        try {
            // 1. Save default resources
            step = "save default resources";
            saveDefaultResources();
            getLogger().info("[1/10] Resources saved.");

            // 2. Initialize core infrastructure
            step = "config manager";
            this.configManager = new ConfigManager(this);
            this.configManager.loadAll();
            getLogger().info("[2/10] Config loaded.");

            step = "message manager";
            this.messageManager = new MessageManager(this);
            this.messageManager.load();
            getLogger().info("[2/10] Messages loaded.");

            // 3. Initialize database
            step = "database";
            this.databaseManager = new DatabaseManager(this);
            this.databaseManager.initialize();
            getLogger().info("[3/10] Database initialized.");

            // 4. Initialize hooks
            step = "hooks";
            this.hookManager = new HookManager(this);
            this.hookManager.initialize();
            getLogger().info("[4/10] Hooks initialized.");

            // 5. Initialize systems
            step = "class manager";
            this.classManager = new ClassManager(this);
            this.classManager.load();
            getLogger().info("[5/10] Class system loaded.");

            step = "stat manager";
            this.statManager = new StatManager(this);
            this.statManager.load();
            getLogger().info("[5/10] Stat system loaded.");

            step = "item manager";
            this.itemManager = new ItemManager(this);
            this.itemManager.load();
            getLogger().info("[5/10] Item system loaded.");

            step = "skill manager";
            this.skillManager = new SkillManager(this);
            this.skillManager.load();
            this.skillLearnManager = new SkillLearnManager(this);
            getLogger().info("[5/10] Skill system loaded.");

            step = "armor set manager";
            this.armorSetManager = new ArmorSetManager(this);
            this.armorSetManager.load();
            getLogger().info("[5/10] Armor set system loaded.");

            step = "potion manager";
            this.potionManager = new PotionManager(this);
            this.potionManager.load();
            getLogger().info("[5/10] Potion system loaded.");

            step = "exp manager";
            this.expManager = new ExpManager(this);
            this.expManager.load();
            getLogger().info("[5/10] Exp system loaded.");

            step = "player manager";
            this.playerManager = new PlayerManager(this);
            this.playerManager.initialize();
            getLogger().info("[5/10] Player system loaded.");

            // 6. Initialize GUI
            step = "GUI";
            this.guiManager = new GUIManager(this);
            this.guiManager.initialize();
            getLogger().info("[6/10] GUI system loaded.");

            // 7. Register commands
            step = "commands";
            registerCommand("rpg", new PlayerCommand(this));
            registerCommand("rpgadmin", new AdminCommand(this));
            registerCommand("class", new ClassCommand(this));
            registerCommand("skill", new SkillCommand(this));
            getLogger().info("[7/10] Commands registered.");

            // 8. Register listeners
            step = "listeners";
            getServer().getPluginManager().registerEvents(new CombatListener(this), this);
            getServer().getPluginManager().registerEvents(playerManager, this);
            getServer().getPluginManager().registerEvents(guiManager, this);
            getServer().getPluginManager().registerEvents(expManager, this);
            getServer().getPluginManager().registerEvents(new com.lumiczi.lurpg.skill.SkillListener(this), this);
            getServer().getPluginManager().registerEvents(potionManager, this);
            getServer().getPluginManager().registerEvents(new TierUpgradeListener(this), this);
            getServer().getPluginManager().registerEvents(new SealListener(this), this);
            getServer().getPluginManager().registerEvents(new com.lumiczi.lurpg.gui.CustomGUIListener(), this);
            getLogger().info("[8/10] Listeners registered.");

            // 9. Load online players
            step = "load online players";
            for (Player player : getServer().getOnlinePlayers()) {
                playerManager.loadPlayerData(player.getUniqueId());
            }
            getLogger().info("[9/10] Online players loaded.");

            // 10. Start auto-save task
            step = "auto-save task";
            startAutoSaveTask();
            getLogger().info("[10/10] Auto-save task started.");

            long duration = System.currentTimeMillis() - startTime;
            getLogger().info("LuRPG v" + getPluginMeta().getVersion() + " enabled in " + duration + "ms.");
            getLogger().info("Author: LuMicZi | Target: Paper 26.2");

        } catch (Exception e) {
            getLogger().severe("========================================");
            getLogger().severe("LuRPG failed to enable at step: " + step);
            getLogger().severe("Error: " + e.getMessage());
            getLogger().severe("========================================");
            getLogger().log(java.util.logging.Level.SEVERE, "Full stack trace:", e);
            getLogger().severe("Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Save all online players
        if (playerManager != null) {
            for (Player player : getServer().getOnlinePlayers()) {
                playerManager.savePlayerData(player.getUniqueId());
            }
            playerManager.shutdown();
        }

        // Close database
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("LuRPG disabled.");
    }

    private void saveDefaultResources() {
        saveResource("config.yml", false);
        saveResource("messages.yml", false);
        saveResource("classes.yml", false);
        saveResource("skills.yml", false);
        saveResource("items.yml", false);
        saveResource("armor_sets.yml", false);
        saveResource("potions.yml", false);
        saveResource("item_tiers.yml", false);
        saveResource("item_enhance.yml", false);
        saveResource("gems.yml", false);
        saveResource("lore_templates/legendary.yml", false);
        saveResource("lore_templates/epic.yml", false);
        saveResource("lore_templates/rare.yml", false);
        saveResource("lore_templates/common.yml", false);
    }

    /**
     * Registers a command's executor and (if applicable) tab completer.
     * <p>
     * Looks up the command declared in {@code plugin.yml}. If the command is
     * missing (typically a name mismatch between the code and {@code plugin.yml}),
     * a warning is logged instead of throwing a {@link NullPointerException},
     * so that one misconfigured command cannot abort {@code onEnable} and take
     * down every other command with it.
     *
     * @param name    the command name as declared in plugin.yml
     * @param handler the executor (also used as tab completer if it implements one)
     */
    private void registerCommand(String name, CommandExecutor handler) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is not defined in plugin.yml; skipping registration.");
            return;
        }
        command.setExecutor(handler);
        if (handler instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }

    private void startAutoSaveTask() {
        int interval = configManager.getMainConfig().getInt("general.auto-save-interval", 300);
        if (interval <= 0) return;
        getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (playerManager != null) {
                playerManager.saveAll();
            }
        }, interval * 20L, interval * 20L, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void reload() {
        configManager.loadAll();
        messageManager.load();
        classManager.load();
        statManager.load();
        itemManager.load();
        skillManager.load();
        armorSetManager.load();
        potionManager.load();
        expManager.load();
    }

    // Getters
    public static LuRPGPlugin getInstance() { return instance; }
    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public MessageManager getMessageManager() { return messageManager; }
    public HookManager getHookManager() { return hookManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public ClassManager getClassManager() { return classManager; }
    public StatManager getStatManager() { return statManager; }
    public ItemManager getItemManager() { return itemManager; }
    public SkillManager getSkillManager() { return skillManager; }
    public SkillLearnManager getSkillLearnManager() { return skillLearnManager; }
    public ArmorSetManager getArmorSetManager() { return armorSetManager; }
    public PotionManager getPotionManager() { return potionManager; }
    public ExpManager getExpManager() { return expManager; }
    public GUIManager getGuiManager() { return guiManager; }
}
