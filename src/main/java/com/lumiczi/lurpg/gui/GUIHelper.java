package com.lumiczi.lurpg.gui;

import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.stat.StatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Static utility class for creating and formatting GUI items.
 * <p>
 * Provides helper methods for creating ItemStacks with MiniMessage-formatted
 * display names and lore, filler items, navigation buttons, separators,
 * and formatting stat lines, class names, and cooldown durations.
 * </p>
 * <p>
 * All methods are static and stateless. The class cannot be instantiated.
 * </p>
 */
public final class GUIHelper {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private GUIHelper() {
        // Utility class - no instantiation
    }

    // ==================== Item Creation ====================

    /**
     * Creates an ItemStack with the specified material, display name, and lore.
     * <p>
     * The display name and lore lines are parsed as MiniMessage strings.
     * </p>
     *
     * @param material    the Bukkit material
     * @param displayName the MiniMessage display name (or null for default)
     * @param lore        the list of MiniMessage lore lines (or null for no lore)
     * @return the created ItemStack
     */
    public static ItemStack createItem(Material material, String displayName, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (displayName != null && !displayName.isBlank()) {
            meta.displayName(noItalic(MINI_MESSAGE.deserialize(displayName)));
        }
        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(noItalic(MINI_MESSAGE.deserialize(line)));
            }
            meta.lore(loreComponents);
        }
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates an ItemStack with CustomModelData, display name, and lore.
     *
     * @param material       the Bukkit material
     * @param customModelData the custom model data value (use -1 or 0 for none)
     * @param displayName    the MiniMessage display name
     * @param lore           the list of MiniMessage lore lines
     * @return the created ItemStack
     */
    public static ItemStack createItem(Material material, int customModelData,
                                       String displayName, List<String> lore) {
        ItemStack item = createItem(material, displayName, lore);
        if (customModelData > 0) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(customModelData);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    /**
     * Creates a filler item (gray glass pane with no visible name).
     *
     * @param material the material to use for the filler
     * @return the created filler ItemStack
     */
    public static ItemStack createFiller(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates a navigation button item.
     *
     * @param material    the button material
     * @param displayName the MiniMessage display name
     * @param slot        the intended slot (stored in lore for reference)
     * @return the created navigation button ItemStack
     */
    public static ItemStack createNavButton(Material material, String displayName, int slot) {
        return createItem(material, displayName, List.of(
                "<dark_gray>Slot: " + slot
        ));
    }

    /**
     * Creates a separator item (a horizontal line of material).
     *
     * @param material the material for the separator
     * @return the created separator ItemStack
     */
    public static ItemStack createSeparator(Material material) {
        return createFiller(material);
    }

    // ==================== Formatting Helpers ====================

    /**
     * Formats a stat line for display in item lore.
     * <p>
     * Example output: {@code "<gold>+45 物理攻击"}
     * </p>
     *
     * @param type  the stat type
     * @param value the stat value
     * @return the formatted MiniMessage string
     */
    public static String formatStatLine(StatType type, double value) {
        String color = type.getColorPrefix();
        String name = type.getDisplayName();
        String suffix = type.getSuffix();
        String valueStr;
        if (suffix.equals("%")) {
            valueStr = String.valueOf((int) value);
        } else if (value == (long) value) {
            valueStr = String.valueOf((long) value);
        } else {
            valueStr = String.format("%.1f", value);
        }
        return color + "+" + valueStr + " " + name + suffix;
    }

    /**
     * Formats a set of class names for display.
     * <p>
     * If the set is empty, returns "全职业" (all classes).
     * Otherwise, returns a comma-separated list of class display names
     * with their color prefixes.
     * </p>
     *
     * @param classes the set of required classes
     * @return the formatted MiniMessage string
     */
    public static String formatClassNames(Set<GameClass> classes) {
        if (classes == null || classes.isEmpty()) {
            return "<green>全职业";
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (GameClass gc : classes) {
            if (!first) {
                sb.append("<gray>, ");
            }
            sb.append(gc.getColorPrefix()).append(gc.getDisplayName());
            first = false;
        }
        return sb.toString();
    }

    /**
     * Formats a cooldown duration (in ticks) as a human-readable string.
     * <p>
     * Converts ticks to seconds. Values under 1 second are shown in ticks.
     * </p>
     *
     * @param ticks the cooldown in ticks (20 ticks = 1 second)
     * @return the formatted MiniMessage string
     */
    public static String formatCooldown(long ticks) {
        if (ticks <= 0) {
            return "<green>无冷却";
        }
        if (ticks < 20) {
            return "<yellow>" + ticks + " tick";
        }
        double seconds = ticks / 20.0;
        if (seconds < 60) {
            return "<yellow>" + String.format("%.1f", seconds) + " 秒";
        }
        long minutes = (long) (seconds / 60);
        long remainingSeconds = (long) (seconds % 60);
        if (remainingSeconds == 0) {
            return "<yellow>" + minutes + " 分钟";
        }
        return "<yellow>" + minutes + " 分 " + remainingSeconds + " 秒";
    }

    /**
     * Formats a resource cost for display.
     *
     * @param resourceName the resource name (e.g. "怒气", "法力")
     * @param amount       the resource amount
     * @return the formatted MiniMessage string
     */
    public static String formatResourceCost(String resourceName, double amount) {
        if (amount <= 0) {
            return "<green>无消耗";
        }
        return "<red>" + (int) amount + " " + resourceName;
    }

    /**
     * Creates a progress bar string using MiniMessage formatting.
     *
     * @param current the current value
     * @param max     the maximum value
     * @param length  the number of characters in the bar
     * @return the formatted MiniMessage string
     */
    public static String formatProgressBar(double current, double max, int length) {
        if (max <= 0) {
            return "<gray>[" + " ".repeat(length) + "]";
        }
        double ratio = Math.min(1.0, Math.max(0.0, current / max));
        int filled = (int) Math.round(ratio * length);
        int empty = length - filled;
        return "<green>" + "=".repeat(filled) + "<dark_gray>" + "-".repeat(empty);
    }

    /**
     * Parses a MiniMessage string into a Component.
     *
     * @param input the MiniMessage string
     * @return the parsed Component
     */
    public static Component parse(String input) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }
        return noItalic(MINI_MESSAGE.deserialize(input));
    }

    /**
     * Parses a list of MiniMessage strings into Components.
     *
     * @param lines the MiniMessage strings
     * @return the list of parsed Components
     */
    public static List<Component> parseList(List<String> lines) {
        List<Component> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(parse(line));
        }
        return result;
    }

    /**
     * Disables the default italic decoration that Minecraft applies to custom
     * item names and lore.
     * <p>
     * When an item has a custom display name or lore, the vanilla client renders
     * the text in italics unless the italic decoration is explicitly turned off.
     * Adventure components do not do this automatically, so every component used
     * for GUI item names/lore must pass through this method.
     * </p>
     * <p>
     * {@link TextDecoration.State#NOT_SET} is only overridden to
     * {@link TextDecoration.State#FALSE}; an explicit italic set by the caller
     * (e.g. {@code <i>} in MiniMessage) is preserved.
     * </p>
     *
     * @param component the component to normalize
     * @return the component with italic disabled where it was previously unset
     */
    private static Component noItalic(Component component) {
        if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            return component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }
}
