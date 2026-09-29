package net.unfamily.anotherconfigmanager.config;

import net.neoforged.fml.config.ModConfig;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;
import net.unfamily.anotherconfigmanager.AcmConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory CSV editing rules keyed by {@code modId + type + config path}.
 * <p>
 * Mods register <strong>defaults</strong> via {@link #register}. Library toml
 * {@code csv.csv_bindings} supplies <strong>overrides</strong> only.
 */
public final class CsvRuleRegistry {
    private static final Map<String, CsvRule> DEFAULTS = new ConcurrentHashMap<>();
    private static final Map<String, String> DEFAULT_STRINGS = new ConcurrentHashMap<>();
    private static final Map<String, CsvRule> OVERRIDES = new ConcurrentHashMap<>();
    private static final Map<String, String> OVERRIDE_STRINGS = new ConcurrentHashMap<>();

    private CsvRuleRegistry() {}

    /**
     * Registers a mod-provided default schema for {@link ModConfig.Type#COMMON} (does not write toml).
     * Overrides from {@code csv_bindings} still win for the same key.
     */
    public static void register(String modId, String configPathDots, String ruleString) {
        register(modId, ModConfig.Type.COMMON, configPathDots, ruleString);
    }

    public static void register(String modId, ModConfig.Type type, String configPathDots, String ruleString) {
        CsvRule rule = CsvRuleParser.parse(ruleString);
        String key = storageKey(modId, type, configPathDots);
        DEFAULTS.put(key, rule);
        DEFAULT_STRINGS.put(key, ruleString);
    }

    public static void register(String modId, String configPathDots, CsvRule rule) {
        register(modId, ModConfig.Type.COMMON, configPathDots, rule);
    }

    public static void register(String modId, ModConfig.Type type, String configPathDots, CsvRule rule) {
        String key = storageKey(modId, type, configPathDots);
        DEFAULTS.put(key, rule);
        DEFAULT_STRINGS.put(key, toMinimalString(rule));
    }

    public static void register(String modId, List<String> configPath, String ruleString) {
        register(modId, ModConfig.Type.COMMON, ConfigSpecNodes.joinPath(configPath), ruleString);
    }

    public static void register(String modId, ModConfig.Type type, List<String> configPath, String ruleString) {
        register(modId, type, ConfigSpecNodes.joinPath(configPath), ruleString);
    }

    @Nullable
    public static CsvRule get(String modId, ModConfig.Type type, String configPathDots) {
        String key = storageKey(modId, type, configPathDots);
        CsvRule override = OVERRIDES.get(key);
        if (override != null) {
            return override;
        }
        return DEFAULTS.get(key);
    }

    @Nullable
    public static CsvRule get(String modId, ModConfig.Type type, List<String> configPath) {
        return get(modId, type, ConfigSpecNodes.joinPath(configPath));
    }

    /**
     * Replaces all toml overrides from {@code csv_bindings} lines
     * ({@code {len}{SEP}modId:type:path;ruleFrags...}).
     * Does not clear Java defaults.
     */
    public static void applyConfigBindings(List<? extends String> lines) {
        OVERRIDES.clear();
        OVERRIDE_STRINGS.clear();
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            CsvBindingLine.Parsed parsed = CsvBindingLine.parse(line.trim());
            if (parsed == null) {
                AnotherConfigManager.LOGGER.warn("Skipping invalid csv_bindings line: {}", line);
                continue;
            }
            try {
                putOverride(parsed.modId(), parsed.type(), parsed.configPath(), parsed.separator(), parsed.ruleString());
            } catch (RuntimeException error) {
                AnotherConfigManager.LOGGER.warn("Skipping invalid csv_bindings rule {}: {}", line, error.toString());
            }
        }
    }

    /**
     * Reloads overrides from {@link AcmConfig#CSV_MANAGER}, then re-applies builtins as defaults.
     */
    public static void reloadFromAcmConfig() {
        applyConfigBindings(AcmConfig.CSV_MANAGER.get());
        registerBuiltinRules();
    }

    /**
     * Hardcoded CSV schemas that must not depend on {@code csv_bindings} (avoids recursion).
     * The Library {@code csv.csv_bindings} list is itself edited as a CSV table via {@link CsvBindingLine}.
     */
    public static void registerBuiltinRules() {
        register(
                AnotherConfigManager.MOD_ID,
                ModConfig.Type.COMMON,
                "csv_manager",
                ";another_config_manager:csv_manager"
                        + ";0=another_config_manager.config.csv.binding.separator"
                        + ";1=another_config_manager.config.csv.binding.path"
                        + ";2=another_config_manager.config.csv.binding.target"
                        + ";3=another_config_manager.config.csv.binding.rule"
        );
        // color_manager rows use ColorBindingLine cell codec (storage;order;target;extras).
        register(
                AnotherConfigManager.MOD_ID,
                ModConfig.Type.COMMON,
                "color_manager",
                ";another_config_manager:color_manager"
                        + ";0=another_config_manager.config.color.binding.storage"
                        + ";1=another_config_manager.config.color.binding.order"
                        + ";2=another_config_manager.config.color.binding.target"
                        + ";3=another_config_manager.config.color.binding.extras"
        );
    }

    /**
     * Registry lookup, then optional {@code iska_csv:} rule embedded in the value comment.
     */
    @Nullable
    public static CsvRule resolve(
            String modId,
            ModConfig.Type type,
            List<String> configPath,
            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> value
    ) {
        CsvRule existing = get(modId, type, configPath);
        if (existing != null) {
            return existing;
        }
        String comment = ConfigSpecNodes.commentOf(value);
        if (comment == null || comment.isBlank()) {
            return null;
        }
        for (String line : comment.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.length() < 10) {
                continue;
            }
            if (trimmed.regionMatches(true, 0, "iska_csv:", 0, 9)) {
                String ruleString = trimmed.substring(9).trim();
                if (ruleString.isEmpty()) {
                    continue;
                }
                try {
                    register(modId, type, configPath, ruleString);
                    return get(modId, type, configPath);
                } catch (RuntimeException error) {
                    AnotherConfigManager.LOGGER.warn(
                            "Invalid iska_csv rule for {};{};{}: {}",
                            modId,
                            type.name(),
                            ConfigSpecNodes.joinPath(configPath),
                            error.toString());
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Creates a minimal numbered-columns rule, persists into Library {@code csv_bindings} as an override.
     */
    public static CsvRule generateMinimalRule(
            String modId,
            ModConfig.Type type,
            List<String> path,
            String separator
    ) {
        if (separator == null || separator.isEmpty() || separator.length() > CsvBindingLine.MAX_SEPARATOR_LENGTH) {
            throw new IllegalArgumentException("CSV separator length must be 1.." + CsvBindingLine.MAX_SEPARATOR_LENGTH);
        }
        String configPath = ConfigSpecNodes.joinPath(path);
        String autoPath = "another_config_manager:generated/" + modId + "/" + configPath.replace('.', '/');
        String ruleString = separator + autoPath + separator + "0+";
        CsvRule rule = CsvRuleParser.parse(separator, ruleString);
        putOverride(modId, type, configPath, separator, ruleString);

        String bindingLine = CsvBindingLine.format(separator, autoPath, modId, type, configPath, List.of("0+"));
        List<String> bindings = new ArrayList<>(AcmConfig.CSV_MANAGER.get());
        boolean exists = false;
        for (int i = 0; i < bindings.size(); i++) {
            String line = bindings.get(i);
            if (line == null) {
                continue;
            }
            CsvBindingLine.Parsed existing = CsvBindingLine.parse(line);
            if (existing != null
                    && existing.modId().equals(modId)
                    && existing.type() == type
                    && existing.configPath().equals(configPath)) {
                bindings.set(i, bindingLine);
                exists = true;
                break;
            }
        }
        if (!exists) {
            bindings.add(bindingLine);
        }
        AcmConfig.CSV_MANAGER.set(bindings);
        AcmConfig.CSV_MANAGER.save();
        return rule;
    }

    /** Canonical on-disk form for one csv_bindings row. */
    public static String toBindingLine(
            String separator,
            String rulePath,
            String modId,
            ModConfig.Type type,
            String configPath,
            List<String> columnDefs
    ) {
        return CsvBindingLine.format(separator, rulePath, modId, type, configPath, columnDefs);
    }

    private static void putOverride(
            String modId,
            ModConfig.Type type,
            String configPathDots,
            String separator,
            String ruleString
    ) {
        CsvRule rule = CsvRuleParser.parse(separator, ruleString);
        String key = storageKey(modId, type, configPathDots);
        OVERRIDES.put(key, rule);
        OVERRIDE_STRINGS.put(key, ruleString);
    }

    public static boolean hasRule(String modId, ModConfig.Type type, List<String> configPath) {
        return get(modId, type, configPath) != null;
    }

    private static String storageKey(String modId, ModConfig.Type type, String configPathDots) {
        return modId + ";" + type.name() + ";" + configPathDots;
    }

    private static String toMinimalString(CsvRule rule) {
        String path = rule.path().orElse("");
        return rule.separator() + path + rule.separator();
    }
}
