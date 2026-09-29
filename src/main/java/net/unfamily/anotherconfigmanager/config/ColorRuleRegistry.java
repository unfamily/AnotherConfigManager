package net.unfamily.anotherconfigmanager.config;

import net.neoforged.fml.config.ModConfig;
import net.unfamily.anotherconfigmanager.AcmConfig;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default (hidden) and override color bindings. Overrides from {@link AcmConfig#COLOR_MANAGER} win.
 */
public final class ColorRuleRegistry {
    private static final Map<String, ColorBindingLine.Parsed> DEFAULTS = new ConcurrentHashMap<>();
    private static final Map<String, ColorBindingLine.Parsed> OVERRIDES = new ConcurrentHashMap<>();

    private ColorRuleRegistry() {}

    public static void register(String modId, String path, String formatSpec) {
        register(modId, ModConfig.Type.COMMON, path, formatSpec);
    }

    /**
     * Registers a default binding. {@code formatSpec} is {@code storage;order[;prefix=…][;case=…]}.
     */
    public static void register(String modId, ModConfig.Type type, String path, String formatSpec) {
        if (modId == null || path == null || formatSpec == null || formatSpec.isBlank()) {
            return;
        }
        ColorCodec.Spec spec = ColorCodec.Spec.parseFormatSpec(formatSpec.trim());
        ColorBindingLine.Parsed parsed = new ColorBindingLine.Parsed(
                spec.storage(),
                spec.order(),
                modId.trim(),
                type,
                path.trim(),
                List.of(),
                null,
                spec.prefix(),
                spec.letterCase(),
                spec.scale()
        );
        DEFAULTS.put(key(modId, type, path), parsed);
    }

    public static void register(String modId, ModConfig.Type type, List<String> path, String formatSpec) {
        register(modId, type, ConfigSpecNodes.joinPath(path), formatSpec);
    }

    @Nullable
    public static ColorBindingLine.Parsed resolveBinding(String modId, ModConfig.Type type, List<String> path) {
        return resolveBinding(modId, type, ConfigSpecNodes.joinPath(path));
    }

    @Nullable
    public static String resolve(String modId, ModConfig.Type type, List<String> path) {
        return resolve(modId, type, ConfigSpecNodes.joinPath(path));
    }

    public static boolean hasRule(String modId, ModConfig.Type type, List<String> path) {
        return hasRule(modId, type, ConfigSpecNodes.joinPath(path));
    }

    public static void registerBuiltinRules() {
        // No built-in color defaults for ACM itself yet.
    }

    public static void reloadFromAcmConfig() {
        OVERRIDES.clear();
        List<? extends String> lines = AcmConfig.COLOR_MANAGER.get();
        if (lines == null) {
            return;
        }
        for (Object raw : lines) {
            if (!(raw instanceof String line) || line.isBlank()) {
                continue;
            }
            ColorBindingLine.Parsed parsed = ColorBindingLine.parse(line.trim());
            if (parsed == null) {
                AnotherConfigManager.LOGGER.warn("Skipping invalid color_manager line: {}", line);
                continue;
            }
            OVERRIDES.put(key(parsed.modId(), parsed.type(), parsed.primaryPath()), parsed);
            // Also index extra paths so sibling channels resolve to the same binding.
            for (String extra : parsed.pathExtras()) {
                OVERRIDES.put(key(parsed.modId(), parsed.type(), extra), parsed);
            }
        }
    }

    @Nullable
    public static ColorBindingLine.Parsed resolveBinding(String modId, ModConfig.Type type, String path) {
        String k = key(modId, type, path);
        ColorBindingLine.Parsed override = OVERRIDES.get(k);
        if (override != null) {
            return override;
        }
        return DEFAULTS.get(k);
    }

    /** Format spec for {@link ColorCodec}, or null if unbound. */
    @Nullable
    public static String resolve(String modId, ModConfig.Type type, String path) {
        ColorBindingLine.Parsed binding = resolveBinding(modId, type, path);
        return binding == null ? null : binding.formatSpec();
    }

    public static boolean hasRule(String modId, ModConfig.Type type, String path) {
        return resolveBinding(modId, type, path) != null;
    }

    /**
     * Declares a single-value color binding (hex or packed int) into {@code color_manager}.
     */
    public static ColorBindingLine.Parsed declareSingle(
            String modId,
            ModConfig.Type type,
            String path,
            String storage,
            String order,
            String prefix
    ) {
        String line = ColorBindingLine.format(storage, order, modId, type, path, List.of(), prefix, "upper");
        appendOverride(line);
        return ColorBindingLine.parse(line);
    }

    /**
     * Declares a multi-path RGB/ARGB binding (3 or 4 sibling int channels).
     *
     * @param paths ordered channel paths (rgb or argb length)
     */
    public static ColorBindingLine.Parsed declarePaths(
            String modId,
            ModConfig.Type type,
            List<String> paths,
            String order
    ) {
        if (paths == null || paths.size() < 3) {
            throw new IllegalArgumentException("paths color binding needs at least 3 channels");
        }
        String primary = paths.getFirst();
        List<String> extras = paths.subList(1, paths.size());
        String line = ColorBindingLine.format("paths", order, modId, type, primary, extras, "none", "upper");
        appendOverride(line);
        return ColorBindingLine.parse(line);
    }

    private static void appendOverride(String line) {
        List<String> bindings = new ArrayList<>(AcmConfig.COLOR_MANAGER.get());
        ColorBindingLine.Parsed incoming = ColorBindingLine.parse(line);
        if (incoming != null) {
            bindings.removeIf(existing -> {
                ColorBindingLine.Parsed p = ColorBindingLine.parse(existing);
                return p != null
                        && p.modId().equalsIgnoreCase(incoming.modId())
                        && p.type() == incoming.type()
                        && p.primaryPath().equals(incoming.primaryPath());
            });
        }
        bindings.add(line);
        AcmConfig.COLOR_MANAGER.set(bindings);
        AcmConfig.COLOR_MANAGER.save();
        reloadFromAcmConfig();
    }

    private static String key(String modId, ModConfig.Type type, String path) {
        return modId.trim().toLowerCase(Locale.ROOT)
                + "|"
                + type.name().toLowerCase(Locale.ROOT)
                + "|"
                + path.trim();
    }
}
