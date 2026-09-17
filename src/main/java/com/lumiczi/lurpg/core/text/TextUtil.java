package com.lumiczi.lurpg.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Central text utility for building item display names and lore.
 * <p>
 * Minecraft renders custom item names and lore in <b>italics by default</b>.
 * Adventure components do not turn this off automatically, so every component
 * that ends up on an {@code ItemStack} (name or lore) must have the italic
 * decoration explicitly disabled. Routing all item text through this class
 * guarantees a single, consistent rule instead of repeating the fix at every
 * call site.
 * </p>
 * <p>
 * All methods are static and stateless; the class cannot be instantiated.
 * </p>
 */
public final class TextUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextUtil() {
        // Utility class - no instantiation
    }

    /**
     * Parses a MiniMessage string into a component with the default italic
     * decoration disabled.
     * <p>
     * Intended for item display names and single lore lines. An italic style
     * the caller set explicitly (e.g. {@code <i>} in the MiniMessage input) is
     * preserved; only the unset state is forced to non-italic.
     * </p>
     *
     * @param input the MiniMessage string (null/blank yields an empty component)
     * @return the parsed component, guaranteed not italic unless explicitly set
     */
    public static Component item(String input) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }
        return noItalic(MINI_MESSAGE.deserialize(input));
    }

    /**
     * Parses a list of MiniMessage strings into lore components with the default
     * italic decoration disabled.
     *
     * @param lines the MiniMessage strings (null yields an empty list)
     * @return the parsed lore components
     */
    public static List<Component> itemLore(List<String> lines) {
        List<Component> result = new ArrayList<>();
        if (lines == null) {
            return result;
        }
        for (String line : lines) {
            result.add(item(line));
        }
        return result;
    }

    /**
     * Disables the default italic decoration on a component unless the caller
     * has explicitly set it.
     *
     * @param component the component to normalize
     * @return the component with italic disabled where it was previously unset
     */
    public static Component noItalic(Component component) {
        if (component.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            return component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }
}
