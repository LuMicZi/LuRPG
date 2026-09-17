package com.lumiczi.lurpg.potion;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.core.message.MessageManager;
import com.lumiczi.lurpg.core.message.Pair;
import com.lumiczi.lurpg.core.text.TextUtil;
import com.lumiczi.lurpg.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages RPG potion definitions and handles potion usage.
 * <p>
 * Loads potion configurations from {@code potions.yml}, creates potion
 * ItemStacks with RPG data stored in the PersistentDataContainer, and
 * handles potion consumption including cooldown tracking, requirement
 * checking, and effect application.
 * </p>
 * <p>
 * Potion IDs are stored in the item's PersistentDataContainer under the
 * key {@code lurpg:rpg_potion_id}.
 * </p>
 * <p>
 * Cooldowns are tracked per-player per-potion using a nested map structure:
 * {@code UUID -> (potionId -> expiry timestamp)}. A global cooldown
 * (configured by {@code potions.global-cooldown}) is also enforced.
 * </p>
 */
public class PotionManager implements Listener {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final NamespacedKey potionIdKey;

    private final Map<String, RPGPotion> potions = new ConcurrentHashMap<>();
    private final PotionEffectHandler effectHandler;

    // Cooldown tracking: UUID -> (potionId -> expiry epoch millis)
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Global cooldown tracking: UUID -> expiry epoch millis
    private final Map<UUID, Long> globalCooldowns = new ConcurrentHashMap<>();

    public PotionManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
        this.potionIdKey = new NamespacedKey(plugin, "rpg_potion_id");
        this.effectHandler = new PotionEffectHandler(plugin);
    }

    /**
     * Loads potion definitions from potions.yml.
     */
    public void load() {
        potions.clear();

        FileConfiguration config = plugin.getConfigManager().getConfig("potions");
        if (config == null) {
            plugin.getLogger().warning("potions.yml not found; PotionManager loaded with no potions.");
            return;
        }

        ConfigurationSection potionsSection = config.getConfigurationSection("potions");
        if (potionsSection != null) {
            for (String potionId : potionsSection.getKeys(false)) {
                ConfigurationSection potionSection = potionsSection.getConfigurationSection(potionId);
                if (potionSection != null) {
                    RPGPotion potion = parsePotion(potionId, potionSection);
                    if (potion != null) {
                        potions.put(potionId, potion);
                    }
                }
            }
        }

        plugin.getLogger().info("Loaded " + potions.size() + " RPG potions.");
    }

    /**
     * Parses an RPGPotion from a configuration section.
     *
     * @param potionId     the potion ID
     * @param potionSection the configuration section for this potion
     * @return the parsed RPGPotion, or null if parsing failed
     */
    private RPGPotion parsePotion(String potionId, ConfigurationSection potionSection) {
        String displayName = potionSection.getString("display-name", potionId);
        String material = potionSection.getString("material", "POTION");
        Color potionColor = parseColor(potionSection.getString("potion-color", "RED"));
        int levelRequirement = potionSection.getInt("level-requirement", 1);

        // Parse class requirements
        Set<GameClass> classRequirement = new HashSet<>();
        List<String> classList = potionSection.getStringList("class-requirement");
        for (String className : classList) {
            GameClass gc = GameClass.fromString(className);
            if (gc != null) {
                classRequirement.add(gc);
            }
        }

        // Parse potion type
        String typeStr = potionSection.getString("type", "INSTANT_HEAL");
        PotionType type;
        try {
            type = PotionType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown potion type '" + typeStr
                    + "' for potion '" + potionId + "'. Defaulting to INSTANT_HEAL.");
            type = PotionType.INSTANT_HEAL;
        }

        ConfigurationSection effects = potionSection.getConfigurationSection("effects");
        int cooldown = potionSection.getInt("cooldown", 30);
        int maxStack = potionSection.getInt("max-stack", 1);
        String loreTemplate = potionSection.getString("lore-template", "common");
        int customModelData = potionSection.getInt("custom-model-data", -1);

        return new RPGPotion(potionId, displayName, material, potionColor,
                levelRequirement, classRequirement, type, effects,
                cooldown, maxStack, loreTemplate, customModelData);
    }

    /**
     * Gets a potion by its ID.
     *
     * @param id the potion ID
     * @return the RPGPotion, or null if not found
     */
    public RPGPotion getPotion(String id) {
        return potions.get(id);
    }

    /**
     * Returns all loaded potion definitions.
     *
     * @return an unmodifiable map of potion ID to RPGPotion
     */
    public Map<String, RPGPotion> getAllPotions() {
        return Collections.unmodifiableMap(potions);
    }

    /**
     * Creates an ItemStack for the specified RPG potion.
     * <p>
     * The created item includes:
     * <ul>
     *   <li>The correct material (POTION, SPLASH_POTION, etc.)</li>
     *   <li>The potion color</li>
     *   <li>Custom model data</li>
     *   <li>Display name (MiniMessage format)</li>
     *   <li>Lore with potion information</li>
     *   <li>The potion ID stored in PersistentDataContainer</li>
     * </ul>
     * </p>
     *
     * @param potionId the potion ID
     * @return the created ItemStack, or null if the potion is not defined
     */
    public ItemStack createPotionItem(String potionId) {
        RPGPotion potion = potions.get(potionId);
        if (potion == null) {
            return null;
        }

        ItemStack item = new ItemStack(potion.getBukkitMaterial());
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return item;
        }

        // Set display name
        meta.displayName(TextUtil.noItalic(miniMessage.deserialize(potion.getDisplayName())));

        // Set potion color
        if (potion.getPotionColor() != null) {
            meta.setColor(potion.getPotionColor());
        }

        // Set custom model data
        if (potion.getCustomModelData() >= 0) {
            meta.setCustomModelData(potion.getCustomModelData());
        }

        // Store potion ID in PDC
        meta.getPersistentDataContainer().set(potionIdKey, PersistentDataType.STRING, potionId);

        // Build lore
        List<Component> lore = buildPotionLore(potion);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Builds the lore for a potion ItemStack.
     *
     * @param potion the potion definition
     * @return a list of Component lines for the lore
     */
    private List<Component> buildPotionLore(RPGPotion potion) {
        List<Component> lore = new ArrayList<>();

        // Potion type
        lore.add(miniMessage.deserialize("<gray>类型: <yellow>" + potion.getType().name()));

        // Level requirement
        if (potion.getLevelRequirement() > 1) {
            lore.add(miniMessage.deserialize("<gray>需要等级: <yellow>" + potion.getLevelRequirement()));
        }

        // Class requirement
        if (!potion.getClassRequirement().isEmpty()) {
            List<String> classNames = new ArrayList<>();
            for (GameClass gc : potion.getClassRequirement()) {
                classNames.add(gc.getDisplayName());
            }
            lore.add(miniMessage.deserialize("<gray>职业要求: <yellow>" + String.join(", ", classNames)));
        }

        // Cooldown
        lore.add(miniMessage.deserialize("<gray>冷却: <yellow>" + potion.getCooldown() + "秒"));

        // Effects description
        ConfigurationSection effects = potion.getEffects();
        if (effects != null) {
            lore.add(miniMessage.deserialize("<dark_gray>━━━━━━━━━━━━━━━━━━"));
            if (effects.isConfigurationSection("heal")) {
                var heal = effects.getConfigurationSection("heal");
                double amount = heal.getDouble("amount", 0);
                String type = heal.getString("type", "INSTANT");
                if ("PERCENT".equalsIgnoreCase(type)) {
                    lore.add(miniMessage.deserialize("<red>恢复 " + (int) amount + "% 生命值"));
                } else {
                    lore.add(miniMessage.deserialize("<red>恢复 " + (int) amount + " 点生命值"));
                }
            }
            if (effects.isConfigurationSection("regen")) {
                var regen = effects.getConfigurationSection("regen");
                double amount = regen.getDouble("amount", 0);
                int duration = regen.getInt("duration", 0);
                lore.add(miniMessage.deserialize("<green>每" + (int) amount + "点恢复, 持续" + duration / 20 + "秒"));
            }
            if (effects.isConfigurationSection("buff")) {
                var buff = effects.getConfigurationSection("buff");
                String stat = buff.getString("stat", "");
                double multiplier = buff.getDouble("multiplier", 1.0);
                int duration = buff.getInt("duration", 0);
                lore.add(miniMessage.deserialize("<gold>" + stat + " x" + multiplier
                        + ", 持续" + duration / 20 + "秒"));
            }
            if (effects.isConfigurationSection("cure")) {
                lore.add(miniMessage.deserialize("<aqua>解除负面效果"));
            }
            if (effects.isConfigurationSection("resource")) {
                var res = effects.getConfigurationSection("resource");
                double amount = res.getDouble("amount", 0);
                lore.add(miniMessage.deserialize("<blue>恢复 " + (int) amount + " 点资源"));
            }
        }

        // Disable Minecraft's default italic rendering on every lore line.
        lore.replaceAll(TextUtil::noItalic);
        return lore;
    }

    /**
     * Attempts to use a potion for the specified player.
     * <p>
     * This method checks:
     * <ol>
     *   <li>Whether the potion exists</li>
     *   <li>Whether the global cooldown is active</li>
     *   <li>Whether the potion-specific cooldown is active</li>
     *   <li>Whether the player meets the level requirement</li>
     *   <li>Whether the player meets the class requirement</li>
     *   <li>Whether the max stack limit has been reached</li>
     * </ol>
     * If all checks pass, the potion's effects are applied and cooldowns are set.
     * </p>
     *
     * @param player   the player using the potion
     * @param potionId the potion ID to use
     * @return true if the potion was successfully used, false otherwise
     */
    public boolean usePotion(Player player, String potionId) {
        if (player == null || potionId == null) {
            return false;
        }

        RPGPotion potion = potions.get(potionId);
        if (potion == null) {
            return false;
        }

        MessageManager msg = plugin.getMessageManager();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Check global cooldown
        Long globalExpiry = globalCooldowns.get(uuid);
        if (globalExpiry != null && globalExpiry > now) {
            long remaining = (globalExpiry - now) / 1000;
            if (msg != null) {
                msg.send(player, "potion.cooldown", Pair.of("time", String.valueOf(remaining + 1)));
            }
            return false;
        }
        // Clean up expired global cooldown
        if (globalExpiry != null) {
            globalCooldowns.remove(uuid);
        }

        // Check potion-specific cooldown
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns != null) {
            Long potionExpiry = playerCooldowns.get(potionId);
            if (potionExpiry != null && potionExpiry > now) {
                long remaining = (potionExpiry - now) / 1000;
                if (msg != null) {
                    msg.send(player, "potion.cooldown", Pair.of("time", String.valueOf(remaining + 1)));
                }
                return false;
            }
            // Clean up expired cooldown
            if (potionExpiry != null) {
                playerCooldowns.remove(potionId);
            }
        }

        // Check level requirement
        PlayerData data = plugin.getPlayerManager().getPlayerData(uuid);
        if (data != null) {
            if (data.getLevel() < potion.getLevelRequirement()) {
                if (msg != null) {
                    msg.send(player, "item.level-requirement",
                            Pair.of("level", String.valueOf(potion.getLevelRequirement())));
                }
                return false;
            }

            // Check class requirement
            if (!potion.canClassUse(data.getGameClass())) {
                if (msg != null) {
                    msg.send(player, "item.class-requirement",
                            Pair.of("class", data.getGameClass().getDisplayName()));
                }
                return false;
            }
        }

        // Check max stack limit
        int maxStack = getMaxStackLimit(potion);
        int activeBuffs = effectHandler.getActiveBuffs(uuid).size();
        if (activeBuffs >= maxStack) {
            if (msg != null) {
                msg.send(player, "potion.max-stack");
            }
            return false;
        }

        // Apply effects
        effectHandler.applyEffects(player, potion);

        // Set cooldowns
        long potionCooldownMs = potion.getCooldown() * 1000L;
        int globalCooldownTicks = plugin.getConfigManager().getMainConfig()
                .getInt("potions.global-cooldown", 40);
        long globalCooldownMs = globalCooldownTicks * 50L; // ticks to ms

        cooldowns.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(potionId, now + potionCooldownMs);
        globalCooldowns.put(uuid, now + globalCooldownMs);

        // Send success message
        if (msg != null) {
            msg.send(player, "potion.used", Pair.of("potion", potion.getDisplayName()));
        }

        return true;
    }

    /**
     * Checks whether an ItemStack is an RPG potion.
     *
     * @param item the ItemStack to check
     * @return true if the item is an RPG potion
     */
    public boolean isRPGPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(potionIdKey, PersistentDataType.STRING);
    }

    /**
     * Gets the RPG potion ID from an ItemStack.
     *
     * @param item the ItemStack to check
     * @return the potion ID, or null if the item is not an RPG potion
     */
    public String getPotionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(potionIdKey, PersistentDataType.STRING);
    }

    /**
     * Returns the PotionEffectHandler instance used by this manager.
     *
     * @return the effect handler
     */
    public PotionEffectHandler getEffectHandler() {
        return effectHandler;
    }

    /**
     * Clears all cooldowns for a player.
     * Should be called when a player quits.
     *
     * @param uuid the player's UUID
     */
    public void clearCooldowns(UUID uuid) {
        cooldowns.remove(uuid);
        globalCooldowns.remove(uuid);
        effectHandler.clearBuffs(uuid);
    }

    /**
     * Clears all cooldowns and buffs for all players.
     */
    public void clearAll() {
        cooldowns.clear();
        globalCooldowns.clear();
        effectHandler.clearAllBuffs();
    }

    /**
     * Event handler for right-clicking with an RPG potion.
     * Consumes the potion and applies its effects.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPotionUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isRPGPotion(item)) {
            return;
        }

        event.setCancelled(true);

        String potionId = getPotionId(item);
        if (potionId == null) return;

        if (usePotion(player, potionId)) {
            // Consume one potion
            item.setAmount(item.getAmount() - 1);
            if (item.getAmount() <= 0) {
                player.getInventory().setItemInMainHand(null);
            }
            // Play drink animation
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.0f);
        }
    }

    /**
     * Gets the effective max stack limit for a potion.
     * Uses the potion's configured max-stack, or the global default if not set.
     *
     * @param potion the potion
     * @return the max stack limit
     */
    private int getMaxStackLimit(RPGPotion potion) {
        int maxStack = potion.getMaxStack();
        if (maxStack <= 0) {
            maxStack = plugin.getConfigManager().getMainConfig()
                    .getInt("potions.max-stack", 3);
        }
        return maxStack;
    }

    /**
     * Parses a color name string into a Bukkit Color object.
     * <p>
     * Supports standard Bukkit color names (RED, GREEN, BLUE, etc.) and
     * maps DARK_RED to MAROON (Bukkit's equivalent).
     * </p>
     *
     * @param colorName the color name
     * @return the Color object, or Color.RED if the name is unrecognized
     */
    private Color parseColor(String colorName) {
        if (colorName == null || colorName.isBlank()) {
            return Color.RED;
        }
        return switch (colorName.toUpperCase()) {
            case "WHITE" -> Color.WHITE;
            case "SILVER", "LIGHT_GRAY" -> Color.SILVER;
            case "GRAY" -> Color.GRAY;
            case "DARK_GRAY" -> Color.fromRGB(0x40, 0x40, 0x40);
            case "BLACK" -> Color.BLACK;
            case "RED" -> Color.RED;
            case "DARK_RED", "MAROON" -> Color.MAROON;
            case "YELLOW" -> Color.YELLOW;
            case "OLIVE", "DARK_YELLOW" -> Color.OLIVE;
            case "LIME" -> Color.LIME;
            case "GREEN" -> Color.GREEN;
            case "DARK_GREEN" -> Color.fromRGB(0, 100, 0);
            case "AQUA" -> Color.AQUA;
            case "TEAL", "DARK_AQUA" -> Color.TEAL;
            case "BLUE" -> Color.BLUE;
            case "NAVY", "DARK_BLUE" -> Color.NAVY;
            case "FUCHSIA", "MAGENTA" -> Color.FUCHSIA;
            case "PURPLE" -> Color.PURPLE;
            case "ORANGE" -> Color.ORANGE;
            default -> {
                // Try parsing as an RGB hex value
                try {
                    int rgb = Integer.parseInt(colorName.replace("#", ""), 16);
                    yield Color.fromRGB(rgb);
                } catch (NumberFormatException e) {
                    yield Color.RED;
                }
            }
        };
    }
}
