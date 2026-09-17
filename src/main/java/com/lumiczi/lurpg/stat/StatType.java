package com.lumiczi.lurpg.stat;

/**
 * Represents all stat types available in the LuRPG stat system.
 * Each stat type has a display name, a color prefix (MiniMessage format),
 * an optional suffix (e.g. "%" for percentage-based stats), and an icon symbol.
 */
public enum StatType {

    MAX_HEALTH("最大生命", "<red>", "❤"),
    PHYSICAL_ATTACK("物理攻击", "<gold>", "⚔"),
    MAGICAL_ATTACK("魔法攻击", "<dark_purple>", "✦"),
    PHYSICAL_DEFENSE("物理防御", "<gray>", "🛡"),
    MAGICAL_DEFENSE("魔法防御", "<dark_gray>", "◈"),
    CRITICAL_CHANCE("暴击率", "<yellow>", "%", "⚡"),
    CRITICAL_DAMAGE("暴击伤害", "<gold>", "%", "💥"),
    LIFESTEAL("吸血", "<dark_red>", "%", "❤"),
    FIRE_DAMAGE("火焰伤害", "<#ff6600>", "🔥"),
    ICE_DAMAGE("冰霜伤害", "<aqua>", "❄"),
    THUNDER_DAMAGE("雷电伤害", "<yellow>", "⚡"),
    DARK_DAMAGE("暗影伤害", "<dark_purple>", "🌑"),
    LIGHT_DAMAGE("光明伤害", "<white>", "☀"),
    RESOURCE_MAX("资源上限", "<green>", "✧"),
    MOVEMENT_SPEED("移动速度", "<blue>", "%", "👟");

    private final String displayName;
    private final String colorPrefix;
    private final String suffix;
    private final String icon;

    StatType(String displayName, String colorPrefix, String icon) {
        this(displayName, colorPrefix, "", icon);
    }

    StatType(String displayName, String colorPrefix, String suffix, String icon) {
        this.displayName = displayName;
        this.colorPrefix = colorPrefix;
        this.suffix = suffix;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorPrefix() {
        return colorPrefix;
    }

    public String getSuffix() {
        return suffix;
    }

    /**
     * Returns the Unicode icon symbol for this stat type.
     *
     * @return the icon string
     */
    public String getIcon() {
        return icon;
    }

    /**
     * Checks whether this stat type is an element damage type.
     *
     * @return true if this is an elemental damage stat
     */
    public boolean isElemental() {
        return switch (this) {
            case FIRE_DAMAGE, ICE_DAMAGE, THUNDER_DAMAGE, DARK_DAMAGE, LIGHT_DAMAGE -> true;
            default -> false;
        };
    }

    /**
     * Checks whether this stat type uses a percentage suffix.
     *
     * @return true if this stat is percentage-based
     */
    public boolean isPercentage() {
        return !suffix.isEmpty();
    }
}
