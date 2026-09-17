package com.lumiczi.lurpg.core.message;

/**
 * A simple immutable key-value pair utility class.
 * <p>
 * Primarily used by {@link MessageManager} for placeholder replacements
 * in message strings (e.g. {@code Pair.of("class", "Warrior")} replaces
 * {@code {class}} in the message).
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public record Pair<K, V>(K key, V value) {

    /**
     * Convenience factory method for creating pairs.
     *
     * @param key   the key
     * @param value the value
     * @param <K>   the key type
     * @param <V>   the value type
     * @return a new Pair
     */
    public static <K, V> Pair<K, V> of(K key, V value) {
        return new Pair<>(key, value);
    }
}
