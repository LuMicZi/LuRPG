package com.lumiczi.lurpg.item;

import com.lumiczi.lurpg.LuRPGPlugin;
import com.lumiczi.lurpg.class_.GameClass;
import com.lumiczi.lurpg.core.text.TextUtil;
import com.lumiczi.lurpg.item.enhance.EnhanceManager;
import com.lumiczi.lurpg.item.gem.GemManager;
import com.lumiczi.lurpg.item.tier.TierManager;
import com.lumiczi.lurpg.skill.api.Skill;
import com.lumiczi.lurpg.stat.StatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds dynamic item lore (the hover-text list) for RPG items based on
 * lore template configuration files.
 * <p>
 * Each lore template resides in {@code lore_templates/{template}.yml} inside
 * the plugin data folder and controls which sections of the lore are visible
 * (stats, elements, skills, requirements, set info) as well as the formatting
 * strings used for each line.
 * <p>
 * The generated lore follows this top-to-bottom structure:
 * <ol>
 *   <li>Rarity name + item type (e.g. "◆ 传说 ◆          武器")</li>
 *   <li>Separator line</li>
 *   <li>Description / lore text (if show-description=true and item has description)</li>
 *   <li>Separator line</li>
 *   <li>Stat list (if {@code show-stats=true})</li>
 *   <li>Elemental affinities (if {@code show-element=true})</li>
 *   <li>Separator line</li>
 *   <li>Bound skills with descriptions (if {@code show-skills=true})</li>
 *   <li>Separator line</li>
 *   <li>Level / class requirements (if {@code show-requirements=true}), split into lines</li>
 *   <li>Armor set info (if {@code show-set-info=true} and item belongs to a set)</li>
 * </ol>
 */
public class ItemLoreBuilder {

    private final LuRPGPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** Cached lore template configurations, keyed by template name. */
    private final Map<String, FileConfiguration> templateCache = new LinkedHashMap<>();

    /** Display names for elemental affinities, keyed by lowercase element key. */
    private static final Map<String, String> ELEMENT_NAMES = Map.of(
            "fire", "火焰",
            "ice", "冰霜",
            "thunder", "雷电",
            "dark", "暗影",
            "light", "光明"
    );

    public ItemLoreBuilder(LuRPGPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds the lore component list for the given RPG item.
     *
     * @param item the RPG item
     * @return a list of Adventure Components forming the lore lines
     */
    public List<Component> buildLore(RPGItem item) {
        List<Component> lore = new ArrayList<>();
        if (item == null) {
            return lore;
        }

        FileConfiguration template = getTemplate(item.getLoreTemplate());

        // Resolve tier info
        TierManager tierManager = plugin.getItemManager().getTierManager();
        TierManager.Tier tier = null;
        double tierMultiplier = 1.0;
        if (item.getTier() != null && !item.getTier().isBlank()) {
            tier = tierManager.getTier(item.getTier());
            if (tier != null) {
                tierMultiplier = tier.multiplier();
            }
        }

        // Resolve enhance info
        EnhanceManager enhanceManager = plugin.getItemManager().getEnhanceManager();
        int enhanceLevel = item.getEnhanceLevel();
        double enhanceMultiplier = enhanceManager.getStatMultiplier(enhanceLevel);

        // 1. Rarity + Item type header
        Component headerLine = buildHeaderLine(item, template, tier);
        lore.add(headerLine);

        // 2. Separator
        String separator = template.getString("separator", "<dark_gray>━━━━━━━━━━━━━━━━━━");
        lore.add(miniMessage.deserialize(separator));

        // 3. Description section
        if (template.getBoolean("show-description", true) && item.hasDescription()) {
            List<Component> descLines = buildDescriptionSection(item, template);
            lore.addAll(descLines);
            lore.add(miniMessage.deserialize(separator));
        }

        // 4. Stats
        boolean hasStatsSection = false;
        if (template.getBoolean("show-stats", true) && !item.getStats().isEmpty()) {
            String statFormat = template.getString("stat-format", "  {icon} {color}{display}: <white>{value}{suffix}");
            for (var entry : item.getStats().entrySet()) {
                StatType statType = entry.getKey();
                double baseValue = entry.getValue();
                // Apply tier and enhance multipliers for display
                double displayValue = baseValue * tierMultiplier * enhanceMultiplier;
                String line = statFormat
                        .replace("{icon}", statType.getIcon())
                        .replace("{color}", statType.getColorPrefix())
                        .replace("{display}", statType.getDisplayName())
                        .replace("{value}", formatValue(displayValue))
                        .replace("{suffix}", statType.getSuffix());
                // Add bonus hints
                StringBuilder bonusHint = new StringBuilder();
                if (tierMultiplier != 1.0) {
                    double pct = (tierMultiplier - 1.0) * 100;
                    String pctStr = (pct > 0 ? "+" : "") + (int) pct + "%";
                    String pctColor = pct > 0 ? "<green>" : "<red>";
                    bonusHint.append(" ").append(pctColor).append("[").append(pctStr).append("品级]");
                }
                if (enhanceMultiplier != 1.0) {
                    double pct = (enhanceMultiplier - 1.0) * 100;
                    String pctStr = "+" + (int) pct + "%";
                    bonusHint.append(" <yellow>[+").append(enhanceLevel).append("强化]");
                }
                if (!bonusHint.isEmpty()) {
                    line += bonusHint.toString();
                }
                lore.add(miniMessage.deserialize(line));
            }
            hasStatsSection = true;
        }

        // 5. Elements
        if (template.getBoolean("show-element", false) && item.hasElements()) {
            String elementFormat = template.getString("element-format", "  <gray>{element}: <white>{value}");
            for (var entry : item.getElements().entrySet()) {
                String elementDisplay = ELEMENT_NAMES.getOrDefault(entry.getKey().toLowerCase(), entry.getKey());
                double value = entry.getValue() * tierMultiplier * enhanceMultiplier;
                String line = elementFormat
                        .replace("{element}", elementDisplay)
                        .replace("{value}", formatValue(value));
                // Add bonus hints
                StringBuilder bonusHint = new StringBuilder();
                if (tierMultiplier != 1.0) {
                    double pct = (tierMultiplier - 1.0) * 100;
                    String pctStr = (pct > 0 ? "+" : "") + (int) pct + "%";
                    String pctColor = pct > 0 ? "<green>" : "<red>";
                    bonusHint.append(" ").append(pctColor).append("[").append(pctStr).append("品级]");
                }
                if (enhanceMultiplier != 1.0) {
                    bonusHint.append(" <yellow>[+").append(enhanceLevel).append("强化]");
                }
                if (!bonusHint.isEmpty()) {
                    line += bonusHint.toString();
                }
                lore.add(miniMessage.deserialize(line));
            }
            hasStatsSection = true;
        }

        // 5b. Gem Sockets
        boolean hasSockets = item.getSockets() != null && !item.getSockets().isEmpty();
        if (hasSockets) {
            GemManager gemManager = plugin.getItemManager().getGemManager();
            List<String> sockets = item.getSockets();
            int filledCount = item.getFilledSocketCount();

            lore.add(miniMessage.deserialize(separator));
            lore.add(miniMessage.deserialize("<aqua>【镶嵌宝石】 <gray>(" + filledCount + "/8)"));

            for (int i = 0; i < sockets.size(); i++) {
                String gemId = sockets.get(i);
                if (gemId != null && !gemId.isBlank()) {
                    GemManager.Gem gem = gemManager.getGem(gemId);
                    if (gem != null) {
                        // Gem with stats
                        lore.add(miniMessage.deserialize("  <dark_aqua>◆ " + gem.name()));
                        for (var statEntry : gem.stats().entrySet()) {
                            StatType statType = statEntry.getKey();
                            double value = statEntry.getValue();
                            lore.add(miniMessage.deserialize("    " + statType.getColorPrefix()
                                    + "+" + formatValue(value) + " "
                                    + statType.getDisplayName() + statType.getSuffix()));
                        }
                    } else {
                        lore.add(miniMessage.deserialize("  <dark_aqua>◆ <gray>" + gemId));
                    }
                } else {
                    lore.add(miniMessage.deserialize("  <gray>◇ 空槽位"));
                }
            }
            hasStatsSection = true;
        }

        // 6. Separator (only if we had stats/elements and there is more content below)
        boolean hasSpecialEffects = template.getBoolean("show-special-effects", true) && item.hasSpecialEffects();
        boolean hasSkills = template.getBoolean("show-skills", false) && item.hasSkills();
        boolean hasRequirements = template.getBoolean("show-requirements", true)
                && (item.getLevelRequirement() > 0 || !item.getClassRequirement().isEmpty());
        boolean hasSetInfo = template.getBoolean("show-set-info", false) && item.hasArmorSet();

        if (hasStatsSection && (hasSpecialEffects || hasSkills || hasRequirements || hasSetInfo)) {
            lore.add(miniMessage.deserialize(separator));
        }

        // 6b. Special effects section
        if (hasSpecialEffects) {
            List<Component> effectLines = buildSpecialEffectsSection(item, template);
            lore.addAll(effectLines);
            if (hasSkills || hasRequirements || hasSetInfo) {
                lore.add(miniMessage.deserialize(separator));
            }
        }

        // 7. Skills section with descriptions
        if (hasSkills) {
            List<Component> skillLines = buildSkillSection(item, template);
            lore.addAll(skillLines);
            if (hasRequirements || hasSetInfo) {
                lore.add(miniMessage.deserialize(separator));
            }
        }

        // 8. Requirements section (split into level and class lines)
        if (hasRequirements) {
            List<Component> reqLines = buildRequirementSection(item, template);
            lore.addAll(reqLines);
            if (hasSetInfo) {
                lore.add(miniMessage.deserialize(separator));
            }
        }

        // 9. Set info
        if (hasSetInfo) {
            List<Component> setLines = buildSetInfo(item.getArmorSetId());
            lore.addAll(setLines);
        }

        // 10. Seal status section
        List<Component> sealLines = buildSealStatusSection(item);
        lore.addAll(sealLines);

        // Disable Minecraft's default italic rendering on every lore line.
        lore.replaceAll(TextUtil::noItalic);
        return lore;
    }

    /**
     * Clears the template cache so that subsequent calls reload templates from disk.
     */
    public void clearCache() {
        templateCache.clear();
    }

    // ---- Section builders ----

    /**
     * Builds the header line showing rarity on the left, item type on the right,
     * and tier info next to the type.
     *
     * @param item     the RPG item
     * @param template the lore template config
     * @param tier     the item tier (may be null)
     * @return the header Component
     */
    private Component buildHeaderLine(RPGItem item, FileConfiguration template, TierManager.Tier tier) {
        String rarityName = template.getString("rarity-name", "<gray>未知");
        String typeFormat = template.getString("type-format", "<gray>{type}");

        if (item.getType() == null) {
            return miniMessage.deserialize(rarityName);
        }

        String typeStr = typeFormat.replace("{type}", item.getType().getDisplayName());

        // Add tier info on the right side
        String tierStr = "";
        if (tier != null) {
            tierStr = tier.color() + "[" + tier.name() + "品级]";
        }

        // Use a simple space-based alignment; Minecraft lore is left-aligned
        String header = rarityName + "                    " + typeStr;
        if (!tierStr.isEmpty()) {
            header += "  " + tierStr;
        }
        return miniMessage.deserialize(header);
    }

    /**
     * Builds the description / lore section for the item.
     * Reads description lines from RPGItem and applies the description format.
     *
     * @param item     the RPG item
     * @param template the lore template config
     * @return a list of lore Components for the description section
     */
    private List<Component> buildDescriptionSection(RPGItem item, FileConfiguration template) {
        List<Component> lines = new ArrayList<>();
        String descFormat = template.getString("description-format", "<gray>  {text}");

        for (String descLine : item.getDescription()) {
            String formatted = descFormat.replace("{text}", descLine);
            lines.add(miniMessage.deserialize(formatted));
        }

        return lines;
    }

    /**
     * Builds the special effects section for the item.
     * Each effect entry can be in "name|description" format.
     * If no pipe separator is present, the entire string is treated
     * as a description-only line.
     *
     * @param item     the RPG item
     * @param template the lore template config
     * @return a list of lore Components for the special effects section
     */
    private List<Component> buildSpecialEffectsSection(RPGItem item, FileConfiguration template) {
        List<Component> lines = new ArrayList<>();
        String title = template.getString("special-effects-title", "<gold>【特殊效果】</gold>");
        String effectFormat = template.getString("special-effect-format",
                "  <yellow>▸ {name}\n  <gray>{desc}");

        // Title line
        lines.add(miniMessage.deserialize(title));

        // Split the format into name-line and desc-line
        String[] formatParts = effectFormat.split("\\n", 2);
        String nameFormat = formatParts.length > 0 ? formatParts[0] : "";
        String descFormat = formatParts.length > 1 ? formatParts[1] : "";

        for (String effect : item.getSpecialEffects()) {
            if (effect == null || effect.isBlank()) continue;

            int pipeIdx = effect.indexOf('|');
            if (pipeIdx >= 0) {
                // Has both name and description
                String name = effect.substring(0, pipeIdx).trim();
                String desc = effect.substring(pipeIdx + 1).trim();

                if (!name.isBlank()) {
                    String nameLine = nameFormat.replace("{name}", name);
                    lines.add(miniMessage.deserialize(nameLine));
                }
                if (!desc.isBlank()) {
                    String descLine = descFormat.replace("{desc}", desc);
                    lines.add(miniMessage.deserialize(descLine));
                }
            } else {
                // Description only
                String descLine = descFormat.replace("{desc}", effect.trim());
                lines.add(miniMessage.deserialize(descLine));
            }
        }

        return lines;
    }

    /**
     * Builds the skills section with skill names and their short descriptions.
     * Looks up each skill ID via the SkillManager to get its display name and description.
     *
     * @param item     the RPG item
     * @param template the lore template config
     * @return a list of lore Components for the skill section
     */
    private List<Component> buildSkillSection(RPGItem item, FileConfiguration template) {
        List<Component> lines = new ArrayList<>();
        String skillNameFormat = template.getString("skill-name-format", "<aqua>▸ {skill_name}");
        String skillDescFormat = template.getString("skill-desc-format", "<gray>  {desc}");

        for (String skillId : item.getSkills()) {
            Skill skill = plugin.getSkillManager().getSkill(skillId);
            String skillName = skill != null ? skill.getDisplayName() : prettifySkillName(skillId);

            String nameLine = skillNameFormat.replace("{skill_name}", skillName);
            lines.add(miniMessage.deserialize(nameLine));

            // Add first line of skill description as short description
            if (skill != null && skill.getDescription() != null && !skill.getDescription().isEmpty()) {
                String firstDescLine = skill.getDescription().get(0);
                // Strip MiniMessage color tags for the short desc if the format already provides color
                String descLine = skillDescFormat.replace("{desc}", firstDescLine);
                lines.add(miniMessage.deserialize(descLine));
            }
        }

        return lines;
    }

    /**
     * Builds the requirements section split into separate lines for level and class.
     *
     * @param item     the RPG item
     * @param template the lore template config
     * @return a list of lore Components for the requirement section
     */
    private List<Component> buildRequirementSection(RPGItem item, FileConfiguration template) {
        List<Component> lines = new ArrayList<>();
        boolean hasLevel = item.getLevelRequirement() > 0;
        boolean hasClasses = !item.getClassRequirement().isEmpty();

        if (hasLevel) {
            String reqLevelFormat = template.getString("req-level-format", "<red>需求等级: <white>{level}");
            String levelLine = reqLevelFormat.replace("{level}", String.valueOf(item.getLevelRequirement()));
            lines.add(miniMessage.deserialize(levelLine));
        }

        if (hasClasses) {
            String reqClassFormat = template.getString("req-class-format", "<red>职业: <white>{class}");
            String classStr = item.getClassRequirement().stream()
                    .map(GameClass::getDisplayName)
                    .collect(Collectors.joining(" / "));
            String classLine = reqClassFormat.replace("{class}", classStr);
            lines.add(miniMessage.deserialize(classLine));
        }

        return lines;
    }

    // ---- Internal helpers ----

    /**
     * Loads (and caches) the lore template configuration for the given name.
     * Falls back to the "common" template if the requested one is missing.
     *
     * @param name the template name (e.g. "legendary")
     * @return the template FileConfiguration
     */
    private FileConfiguration getTemplate(String name) {
        if (name == null || name.isBlank()) {
            name = "common";
        }
        return templateCache.computeIfAbsent(name, this::loadTemplate);
    }

    private FileConfiguration loadTemplate(String name) {
        File file = new File(plugin.getDataFolder(), "lore_templates/" + name + ".yml");
        if (!file.exists()) {
            // Fall back to common template
            File common = new File(plugin.getDataFolder(), "lore_templates/common.yml");
            if (!common.exists()) {
                // Return an empty config so callers get default values
                return new YamlConfiguration();
            }
            return YamlConfiguration.loadConfiguration(common);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Builds armor set info lines from the armor_sets.yml configuration.
     *
     * @param setId the armor set identifier
     * @return a list of lore Components for the set info section
     */
    private List<Component> buildSetInfo(String setId) {
        List<Component> lines = new ArrayList<>();
        FileConfiguration armorConfig = plugin.getConfigManager().getConfig("armor_sets");
        if (armorConfig == null) {
            return lines;
        }
        ConfigurationSection setSection = armorConfig.getConfigurationSection("sets." + setId);
        if (setSection == null) {
            return lines;
        }

        // Set display name
        String displayName = setSection.getString("display-name", setId);
        lines.add(miniMessage.deserialize(displayName));

        // Set description
        for (String desc : setSection.getStringList("description")) {
            lines.add(miniMessage.deserialize(desc));
        }

        // Bonus descriptions
        ConfigurationSection bonuses = setSection.getConfigurationSection("bonuses");
        if (bonuses != null) {
            for (String key : bonuses.getKeys(false)) {
                ConfigurationSection bonus = bonuses.getConfigurationSection(key);
                if (bonus == null) continue;
                String desc = bonus.getString("description");
                if (desc != null && !desc.isBlank()) {
                    lines.add(miniMessage.deserialize(desc));
                }
            }
        }

        return lines;
    }

    /**
     * Builds the seal status section showing whether the item is sealed
     * (tradeable) or unsealed (bound).
     * <ul>
     *   <li>Sealed: "<aqua>[封印中]</aqua> <gray>装备后将绑定</gray>"</li>
     *   <li>Unsealed: "<red>[已绑定]</red> <gray>无法交易，使用封券可重新封印</gray>"</li>
     * </ul>
     *
     * @param item the RPG item
     * @return a list containing a single lore Component for the seal status
     */
    private List<Component> buildSealStatusSection(RPGItem item) {
        List<Component> lines = new ArrayList<>();
        if (item.isSealed()) {
            lines.add(miniMessage.deserialize(
                    "<aqua>[封印中]</aqua> <gray>装备后将绑定</gray>"));
        } else {
            lines.add(miniMessage.deserialize(
                    "<red>[已绑定]</red> <gray>无法交易，使用封券可重新封印</gray>"));
        }
        return lines;
    }

    /**
     * Formats a double value for display: whole numbers are shown as integers.
     *
     * @param value the value to format
     * @return the formatted string
     */
    private String formatValue(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /**
     * Converts a skill ID into a human-readable display name by replacing
     * underscores with spaces and capitalising each word.
     *
     * @param skillId the raw skill ID
     * @return the prettified name
     */
    private String prettifySkillName(String skillId) {
        if (skillId == null || skillId.isBlank()) {
            return "";
        }
        String[] words = skillId.replace("_", " ").split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
