package com.lumiczi.lurpg.core.message;

import com.lumiczi.lurpg.LuRPGPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;

/**
 * Manages all plugin messages loaded from messages.yml.
 * <p>
 * Uses MiniMessage for rich text formatting. Messages are loaded once
 * and cached in memory. Placeholder tokens in the form {@code {key}}
 * can be replaced at runtime via the variadic {@code Pair} parameters.
 */
public class MessageManager {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private FileConfiguration messages;
    private Component prefix;

    public MessageManager(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) messages.yml from the plugin data folder.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            try {
                plugin.saveResource("messages.yml", false);
            } catch (IllegalArgumentException ignored) {
                // Resource not bundled - create empty config
            }
        }
        messages = YamlConfiguration.loadConfiguration(file);

        // Parse the prefix (defaults to empty string if not set)
        String prefixStr = messages.getString("prefix", "");
        prefix = prefixStr.isBlank() ? Component.empty() : miniMessage.deserialize(prefixStr);
    }

    /**
     * Returns the parsed message Component for the given path.
     * Handles both single-string and string-list paths.
     *
     * @param path the dot-separated path in messages.yml
     * @return the parsed Component, or an error Component if the path is missing
     */
    public Component get(String path) {
        String msg = messages.getString(path);
        if (msg != null) {
            return miniMessage.deserialize(msg);
        }
        // Try list
        List<String> list = messages.getStringList(path);
        if (!list.isEmpty()) {
            Component result = Component.empty();
            for (int i = 0; i < list.size(); i++) {
                result = result.append(miniMessage.deserialize(list.get(i)));
                if (i < list.size() - 1) {
                    result = result.append(Component.newline());
                }
            }
            return result;
        }
        return Component.text("missing message: " + path);
    }

    /**
     * Returns the parsed message Component with placeholder replacements.
     * Placeholders in the message are in the form {@code {key}} and are
     * replaced with the corresponding value before MiniMessage parsing.
     *
     * @param path         the dot-separated path in messages.yml
     * @param replacements key-value pairs for placeholder substitution
     * @return the parsed Component with replacements applied
     */
    @SafeVarargs
    public final Component get(String path, Pair<String, String>... replacements) {
        String msg = messages.getString(path);
        if (msg != null) {
            return miniMessage.deserialize(applyReplacements(msg, replacements));
        }
        // Try list
        List<String> list = messages.getStringList(path);
        if (!list.isEmpty()) {
            Component result = Component.empty();
            for (int i = 0; i < list.size(); i++) {
                String line = applyReplacements(list.get(i), replacements);
                result = result.append(miniMessage.deserialize(line));
                if (i < list.size() - 1) {
                    result = result.append(Component.newline());
                }
            }
            return result;
        }
        return Component.text("missing message: " + path);
    }

    /**
     * Sends a message to a player with the plugin prefix prepended.
     *
     * @param player the target player
     * @param path   the message path in messages.yml
     */
    public void send(Player player, String path) {
        sendMessage(player, prefix.append(get(path)));
    }

    /**
     * Sends a message with placeholder replacements and the plugin prefix.
     *
     * @param player       the target player
     * @param path         the message path in messages.yml
     * @param replacements key-value pairs for placeholder substitution
     */
    @SafeVarargs
    public final void send(Player player, String path, Pair<String, String>... replacements) {
        sendMessage(player, prefix.append(get(path, replacements)));
    }

    /**
     * Sends a raw Component to a player (no prefix).
     *
     * @param player    the target player
     * @param component the Component to send
     */
    public void sendMessage(Player player, Component component) {
        player.sendMessage(component);
    }

    /**
     * Returns a list of parsed Components for a string-list path.
     *
     * @param path the message path in messages.yml
     * @return list of Components (empty if path not found)
     */
    public List<Component> getList(String path) {
        List<String> list = messages.getStringList(path);
        return list.stream().map(miniMessage::deserialize).toList();
    }

    /**
     * Returns a list of parsed Components with placeholder replacements.
     */
    @SafeVarargs
    public final List<Component> getList(String path, Pair<String, String>... replacements) {
        List<String> list = messages.getStringList(path);
        return list.stream()
                .map(msg -> miniMessage.deserialize(applyReplacements(msg, replacements)))
                .toList();
    }

    /**
     * Sends each line of a string-list message to the player (without prefix).
     */
    public void sendList(Player player, String path) {
        for (Component line : getList(path)) {
            player.sendMessage(line);
        }
    }

    /**
     * Sends each line of a string-list message with placeholder replacements (without prefix).
     */
    @SafeVarargs
    public final void sendList(Player player, String path, Pair<String, String>... replacements) {
        for (Component line : getList(path, replacements)) {
            player.sendMessage(line);
        }
    }

    /**
     * Returns the prefix Component.
     *
     * @return the parsed prefix
     */
    public Component getPrefix() {
        return prefix;
    }

    /**
     * Returns the MiniMessage instance used by this manager.
     *
     * @return the MiniMessage instance
     */
    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    // ---- Private helpers ----

    @SafeVarargs
    private String applyReplacements(String msg, Pair<String, String>... replacements) {
        for (Pair<String, String> replacement : replacements) {
            msg = msg.replace("{" + replacement.key() + "}", replacement.value());
        }
        return msg;
    }
}
