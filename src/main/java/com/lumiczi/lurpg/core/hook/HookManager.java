package com.lumiczi.lurpg.core.hook;

import com.lumiczi.lurpg.LuRPGPlugin;

/**
 * Manages integration hooks with external plugins.
 * <p>
 * Each hook detects whether the corresponding plugin is installed and
 * initializes the integration if available. Optional hooks (Vault,
 * PlaceholderAPI, MythicMobs) degrade gracefully when absent.
 * CraftEngine is a required dependency per paper-plugin.yml.
 */
public class HookManager {

    private final LuRPGPlugin plugin;
    private CraftEngineHook craftEngineHook;
    private VaultHook vaultHook;
    private PlaceholderAPIHook placeholderAPIHook;
    private MythicMobsHook mythicMobsHook;

    public HookManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Detects and initializes all hooks.
     */
    public void initialize() {
        // CraftEngine (required dependency)
        this.craftEngineHook = new CraftEngineHook(plugin);
        this.craftEngineHook.initialize();

        // Vault (optional)
        this.vaultHook = new VaultHook(plugin);
        this.vaultHook.initialize();

        // PlaceholderAPI (optional)
        this.placeholderAPIHook = new PlaceholderAPIHook(plugin);
        this.placeholderAPIHook.initialize();

        // MythicMobs (optional)
        this.mythicMobsHook = new MythicMobsHook(plugin);
        this.mythicMobsHook.initialize();
    }

    public boolean isCraftEngineEnabled() {
        return craftEngineHook != null && craftEngineHook.isAvailable();
    }

    public boolean isVaultEnabled() {
        return vaultHook != null && vaultHook.isAvailable();
    }

    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIHook != null && placeholderAPIHook.isAvailable();
    }

    public boolean isMythicMobsEnabled() {
        return mythicMobsHook != null && mythicMobsHook.isAvailable();
    }

    public CraftEngineHook getCraftEngineHook() {
        return craftEngineHook;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public PlaceholderAPIHook getPlaceholderAPIHook() {
        return placeholderAPIHook;
    }

    public MythicMobsHook getMythicMobsHook() {
        return mythicMobsHook;
    }
}
