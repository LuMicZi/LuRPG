package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.LuRPGPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for the LuRPG GUI system.
 * <p>
 * Implements {@link Listener} to handle {@link InventoryClickEvent} and
 * {@link InventoryCloseEvent}. Maintains a map of active GUI sessions
 * keyed by player UUID.
 * </p>
 * <p>
 * Click events are delegated to the appropriate GUI page class based on
 * the session's {@link GUIType}. All clicks within a managed GUI inventory
 * are cancelled to prevent item movement.
 * </p>
 */
public class GUIManager implements Listener {

    private final LuRPGPlugin plugin;
    private final Map<UUID, GUISession> sessions = new ConcurrentHashMap<>();

    // Config values (loaded in initialize)
    private int itemsPerPage = 45;
    private boolean fillEmpty = true;
    private Material fillMaterial = Material.GRAY_STAINED_GLASS_PANE;
    private Material prevPageMaterial = Material.ARROW;
    private Material nextPageMaterial = Material.ARROW;
    private Material backMaterial = Material.BARRIER;
    private Material closeMaterial = Material.BARRIER;

    // Navigation slot constants (bottom row of a 54-slot inventory)
    public static final int SLOT_PREV_PAGE = 45;
    public static final int SLOT_BACK = 49;
    public static final int SLOT_NEXT_PAGE = 53;
    public static final int SLOT_CLOSE = 49;

    public GUIManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the GUI system by loading configuration values.
     */
    public void initialize() {
        loadConfig();
        plugin.getLogger().info("GUIManager initialized. items-per-page=" + itemsPerPage
                + ", fill-empty=" + fillEmpty);
    }

    /**
     * Loads GUI configuration from config.yml.
     */
    private void loadConfig() {
        FileConfiguration config = plugin.getConfigManager().getMainConfig();
        if (config == null) {
            return;
        }
        itemsPerPage = config.getInt("gui.items-per-page", 45);
        fillEmpty = config.getBoolean("gui.fill-empty", true);
        fillMaterial = parseMaterial(config.getString("gui.fill-material", "GRAY_STAINED_GLASS_PANE"),
                Material.GRAY_STAINED_GLASS_PANE);
        prevPageMaterial = parseMaterial(config.getString("gui.nav.prev-page", "ARROW"), Material.ARROW);
        nextPageMaterial = parseMaterial(config.getString("gui.nav.next-page", "ARROW"), Material.ARROW);
        backMaterial = parseMaterial(config.getString("gui.nav.back", "BARRIER"), Material.BARRIER);
        closeMaterial = parseMaterial(config.getString("gui.nav.close", "BARRIER"), Material.BARRIER);
    }

    /**
     * Parses a material name, falling back to a default if invalid.
     */
    private Material parseMaterial(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    // ==================== Session Management ====================

    /**
     * Registers a GUI session for a player.
     *
     * @param uuid    the player's UUID
     * @param session the session to register
     */
    public void registerSession(UUID uuid, GUISession session) {
        sessions.put(uuid, session);
    }

    /**
     * Gets the active GUI session for a player.
     *
     * @param uuid the player's UUID
     * @return the session, or null if no active session
     */
    public GUISession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    /**
     * Removes and returns the active GUI session for a player.
     *
     * @param uuid the player's UUID
     * @return the removed session, or null if none existed
     */
    public GUISession removeSession(UUID uuid) {
        return sessions.remove(uuid);
    }

    /**
     * Checks whether a player has an active GUI session.
     *
     * @param uuid the player's UUID
     * @return true if the player has an active session
     */
    public boolean hasSession(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    /**
     * Closes all active GUI sessions. Called on plugin disable.
     */
    public void closeAll() {
        for (UUID uuid : sessions.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.closeInventory();
            }
        }
        sessions.clear();
    }

    // ==================== GUI Opening ====================

    /**
     * Opens a GUI of the specified type for the player.
     *
     * @param player the target player
     * @param type   the GUI type to open
     */
    public void openGUI(Player player, GUIType type) {
        openGUI(player, type, 0);
    }

    /**
     * Opens a GUI of the specified type at the given page.
     *
     * @param player the target player
     * @param type   the GUI type to open
     * @param page   the page number (0-based)
     */
    public void openGUI(Player player, GUIType type, int page) {
        switch (type) {
            case MAIN_MENU -> MainMenuGUI.open(plugin, player);
            case WEAPON_LIST -> WeaponListGUI.open(plugin, player, page);
            case ARMOR_SET_LIST -> ArmorSetListGUI.open(plugin, player, page);
            case POTION_LIST -> PotionListGUI.open(plugin, player, page);
            case SKILL_LIST -> SkillListGUI.open(plugin, player, page);
            case SKILL_LEARN -> SkillLearnGUI.open(plugin, player, page);
            case PLAYER_PANEL -> PlayerPanelGUI.open(plugin, player);
            case CLASS_SELECT -> ClassSelectGUI.open(plugin, player);
            case ITEM_DETAIL -> {
                // ItemDetail requires a param; fall back to main menu
                MainMenuGUI.open(plugin, player);
            }
        }
    }

    /**
     * Opens a GUI of the specified type with additional parameters.
     *
     * @param player the target player
     * @param type   the GUI type to open
     * @param params the parameters map (e.g. "itemId", "page", "returnType")
     */
    public void openGUI(Player player, GUIType type, Map<String, Object> params) {
        switch (type) {
            case ITEM_DETAIL -> {
                String itemId = params != null ? (String) params.get("itemId") : null;
                if (itemId != null) {
                    ItemDetailGUI.open(plugin, player, itemId);
                } else {
                    MainMenuGUI.open(plugin, player);
                }
            }
            case WEAPON_LIST -> {
                int page = params != null && params.get("page") instanceof Number n ? n.intValue() : 0;
                WeaponListGUI.open(plugin, player, page);
            }
            case ARMOR_SET_LIST -> {
                int page = params != null && params.get("page") instanceof Number n ? n.intValue() : 0;
                ArmorSetListGUI.open(plugin, player, page);
            }
            case POTION_LIST -> {
                int page = params != null && params.get("page") instanceof Number n ? n.intValue() : 0;
                PotionListGUI.open(plugin, player, page);
            }
            case SKILL_LIST -> {
                int page = params != null && params.get("page") instanceof Number n ? n.intValue() : 0;
                SkillListGUI.open(plugin, player, page);
            }
            case SKILL_LEARN -> {
                int page = params != null && params.get("page") instanceof Number n ? n.intValue() : 0;
                SkillLearnGUI.open(plugin, player, page);
            }
            default -> openGUI(player, type);
        }
    }

    // ==================== Event Handlers ====================

    /**
     * Handles inventory click events.
     * <p>
     * If the player has an active GUI session and the clicked inventory
     * matches the session's inventory, the event is cancelled and the
     * click is delegated to the appropriate GUI page handler.
     * </p>
     *
     * @param event the inventory click event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        GUISession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        Inventory topInv = event.getView().getTopInventory();
        if (session.getInventory() == null || !topInv.equals(session.getInventory())) {
            return;
        }
        // Cancel all clicks in the GUI (both top and bottom inventory)
        event.setCancelled(true);

        // Only delegate clicks within the top inventory
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= topInv.getSize()) {
            return;
        }

        delegateClick(player, session, slot, event);
    }

    /**
     * Handles inventory close events.
     * <p>
     * Removes the player's GUI session when the inventory is closed.
     * </p>
     *
     * @param event the inventory close event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        GUISession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        // Only remove if the closed inventory matches the session's inventory
        if (session.getInventory() != null && event.getInventory().equals(session.getInventory())) {
            sessions.remove(player.getUniqueId());
        }
    }

    /**
     * Delegates a click event to the appropriate GUI page handler
     * based on the session's GUI type.
     */
    private void delegateClick(Player player, GUISession session, int slot, InventoryClickEvent event) {
        switch (session.getType()) {
            case MAIN_MENU -> MainMenuGUI.handleClick(plugin, player, session, slot, event);
            case WEAPON_LIST -> WeaponListGUI.handleClick(plugin, player, session, slot, event);
            case ARMOR_SET_LIST -> ArmorSetListGUI.handleClick(plugin, player, session, slot, event);
            case POTION_LIST -> PotionListGUI.handleClick(plugin, player, session, slot, event);
            case SKILL_LIST -> SkillListGUI.handleClick(plugin, player, session, slot, event);
            case SKILL_LEARN -> SkillLearnGUI.handleClick(plugin, player, session, slot, event);
            case PLAYER_PANEL -> PlayerPanelGUI.handleClick(plugin, player, session, slot, event);
            case CLASS_SELECT -> ClassSelectGUI.handleClick(plugin, player, session, slot, event);
            case ITEM_DETAIL -> ItemDetailGUI.handleClick(plugin, player, session, slot, event);
        }
    }

    // ==================== Layout Helpers ====================

    /**
     * Fills the border (outer edge) of a chest inventory with the specified material.
     *
     * @param inventory the inventory to fill
     * @param material  the border material
     */
    public void fillBorder(Inventory inventory, Material material) {
        if (inventory == null) {
            return;
        }
        int size = inventory.getSize();
        int cols = 9;
        int rows = size / cols;
        ItemStack filler = GUIHelper.createFiller(material);
        for (int col = 0; col < cols; col++) {
            inventory.setItem(col, filler); // Top row
            inventory.setItem((rows - 1) * cols + col, filler); // Bottom row
        }
        for (int row = 1; row < rows - 1; row++) {
            inventory.setItem(row * cols, filler); // Left column
            inventory.setItem(row * cols + cols - 1, filler); // Right column
        }
    }

    /**
     * Fills all empty slots in an inventory with the specified material.
     *
     * @param inventory the inventory to fill
     * @param material  the filler material
     */
    public void fillEmpty(Inventory inventory, Material material) {
        if (inventory == null) {
            return;
        }
        ItemStack filler = GUIHelper.createFiller(material);
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, filler);
            }
        }
    }

    /**
     * Creates a navigation item for the GUI.
     *
     * @param material    the button material
     * @param displayName the MiniMessage display name
     * @param slot        the intended slot position
     * @return the created navigation ItemStack
     */
    public ItemStack createNavItem(Material material, String displayName, int slot) {
        return GUIHelper.createNavButton(material, displayName, slot);
    }

    /**
     * Fills the bottom navigation row of a 54-slot inventory with
     * the standard navigation buttons.
     *
     * @param inventory   the 54-slot inventory
     * @param hasPrev     whether the previous page button should be active
     * @param hasNext     whether the next page button should be active
     * @param showBack    whether to show the back button (vs close)
     */
    public void fillNavRow(Inventory inventory, boolean hasPrev, boolean hasNext, boolean showBack) {
        if (inventory == null || inventory.getSize() < 54) {
            return;
        }
        ItemStack filler = GUIHelper.createFiller(fillMaterial);

        // Fill entire bottom row with filler first
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Previous page button (slot 45)
        if (hasPrev) {
            inventory.setItem(SLOT_PREV_PAGE,
                    createNavItem(prevPageMaterial, "<yellow>上一页", SLOT_PREV_PAGE));
        } else {
            inventory.setItem(SLOT_PREV_PAGE,
                    GUIHelper.createItem(Material.GRAY_STAINED_GLASS_PANE,
                            "<dark_gray>上一页", null));
        }

        // Back / Close button (slot 49)
        if (showBack) {
            inventory.setItem(SLOT_BACK,
                    createNavItem(backMaterial, "<red>返回主菜单", SLOT_BACK));
        } else {
            inventory.setItem(SLOT_CLOSE,
                    createNavItem(closeMaterial, "<red>关闭", SLOT_CLOSE));
        }

        // Next page button (slot 53)
        if (hasNext) {
            inventory.setItem(SLOT_NEXT_PAGE,
                    createNavItem(nextPageMaterial, "<yellow>下一页", SLOT_NEXT_PAGE));
        } else {
            inventory.setItem(SLOT_NEXT_PAGE,
                    GUIHelper.createItem(Material.GRAY_STAINED_GLASS_PANE,
                            "<dark_gray>下一页", null));
        }
    }

    // ==================== Config Getters ====================

    public int getItemsPerPage() {
        return itemsPerPage;
    }

    public boolean isFillEmpty() {
        return fillEmpty;
    }

    public Material getFillMaterial() {
        return fillMaterial;
    }

    public Material getPrevPageMaterial() {
        return prevPageMaterial;
    }

    public Material getNextPageMaterial() {
        return nextPageMaterial;
    }

    public Material getBackMaterial() {
        return backMaterial;
    }

    public Material getCloseMaterial() {
        return closeMaterial;
    }

    /**
     * Calculates the total number of pages for a given item count.
     *
     * @param itemCount the total number of items
     * @return the total page count (at least 1)
     */
    public int getTotalPages(int itemCount) {
        if (itemCount <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) itemCount / itemsPerPage);
    }

    /**
     * Creates a new Bukkit inventory with the specified size and title.
     *
     * @param size  the inventory size (must be a multiple of 9)
     * @param title the MiniMessage title string
     * @return the created inventory
     */
    public Inventory createInventory(int size, String title) {
        return Bukkit.createInventory(null, size, GUIHelper.parse(title));
    }
}
