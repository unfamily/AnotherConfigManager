package net.unfamily.anotherconfigmanager.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Flattens a {@link ModConfigSpec} tree into navigable nodes for the opener UI.
 */
public final class ConfigSpecNodes {
    private ConfigSpecNodes() {}

    public sealed interface Node permits SectionNode, ValueNode {}

    public record SectionNode(List<String> path, String displayName, String rawKey, List<Node> children) implements Node {}

    public record ValueNode(
            List<String> path,
            String displayName,
            String rawKey,
            ModConfigSpec.ConfigValue<?> value,
            ValueKind kind,
            boolean editable
    ) implements Node {}

    public enum ValueKind {
        BOOLEAN,
        BYTE,
        SHORT,
        INT,
        LONG,
        DOUBLE,
        STRING,
        LIST,
        ENUM,
        READ_ONLY
    }

    public static List<Node> rootChildren(ModConfigSpec spec, String modId) {
        return childrenAt(spec, List.of(), modId);
    }

    public static List<Node> childrenAt(ModConfigSpec spec, List<String> path, String modId) {
        UnmodifiableConfig values = nestedValues(spec.getValues(), path);
        if (values == null) {
            return List.of();
        }
        List<SectionNode> sections = new ArrayList<>();
        List<ValueNode> valueNodes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.valueMap().entrySet()) {
            String key = entry.getKey();
            Object raw = entry.getValue();
            List<String> childPath = append(path, key);
            if (raw instanceof ModConfigSpec.ConfigValue<?> configValue) {
                valueNodes.add(valueNode(modId, childPath, key, configValue));
            } else if (raw instanceof UnmodifiableConfig) {
                String display = ConfigDisplayNames.sectionDisplayName(modId, childPath, key);
                sections.add(new SectionNode(childPath, display, key, childrenAt(spec, childPath, modId)));
            }
        }
        sections.sort(Comparator.comparing(s -> s.displayName().toLowerCase(Locale.ROOT)));
        valueNodes.sort(Comparator.comparing(v -> v.displayName().toLowerCase(Locale.ROOT)));
        List<Node> nodes = new ArrayList<>(sections.size() + valueNodes.size());
        nodes.addAll(sections);
        nodes.addAll(valueNodes);
        return nodes;
    }

    /** @deprecated Prefer {@link #childrenAt(ModConfigSpec, List, String)} */
    @Deprecated
    public static List<Node> childrenAt(ModConfigSpec spec, List<String> path) {
        return childrenAt(spec, path, "unknown");
    }

    private static ValueNode valueNode(String modId, List<String> path, String key, ModConfigSpec.ConfigValue<?> value) {
        ValueKind kind = classify(value);
        boolean editable = kind != ValueKind.READ_ONLY;
        String display = ConfigDisplayNames.displayName(modId, path, key);
        return new ValueNode(path, display, key, value, kind, editable);
    }

    public static ValueKind classify(ModConfigSpec.ConfigValue<?> value) {
        if (value instanceof ModConfigSpec.BooleanValue) {
            return ValueKind.BOOLEAN;
        }
        if (value instanceof ModConfigSpec.IntValue) {
            return ValueKind.INT;
        }
        if (value instanceof ModConfigSpec.LongValue) {
            return ValueKind.LONG;
        }
        if (value instanceof ModConfigSpec.DoubleValue) {
            return ValueKind.DOUBLE;
        }
        if (value instanceof ModConfigSpec.EnumValue<?>) {
            return ValueKind.ENUM;
        }
        Object def = value.getDefault();
        Class<?> clazz = null;
        try {
            clazz = value.getSpec().getClazz();
        } catch (RuntimeException ignored) {
            // ignore
        }
        if (def instanceof List<?> || (clazz != null && List.class.isAssignableFrom(clazz))) {
            return ValueKind.LIST;
        }
        if (def instanceof Enum<?> || (clazz != null && clazz.isEnum())) {
            return ValueKind.ENUM;
        }
        if (def instanceof String || clazz == String.class) {
            return ValueKind.STRING;
        }
        if (def instanceof Boolean) {
            return ValueKind.BOOLEAN;
        }
        if (def instanceof Byte || clazz == Byte.class || clazz == byte.class) {
            return ValueKind.BYTE;
        }
        if (def instanceof Short || clazz == Short.class || clazz == short.class) {
            return ValueKind.SHORT;
        }
        if (def instanceof Integer) {
            return ValueKind.INT;
        }
        if (def instanceof Long) {
            return ValueKind.LONG;
        }
        if (def instanceof Double || def instanceof Float) {
            return ValueKind.DOUBLE;
        }
        return ValueKind.READ_ONLY;
    }

    /** Numeric kinds usable as ARGB channel paths. */
    public static boolean isColorChannelKind(ValueKind kind) {
        return kind == ValueKind.BYTE
                || kind == ValueKind.SHORT
                || kind == ValueKind.INT
                || kind == ValueKind.LONG
                || kind == ValueKind.DOUBLE;
    }

    /**
     * True when the value is a numeric color-channel kind with an explicit ModConfigSpec range of exactly 0–255.
     */
    public static boolean isColorChannel0to255(ModConfigSpec.ConfigValue<?> value) {
        if (value == null || !isColorChannelKind(classify(value))) {
            return false;
        }
        ModConfigSpec.Range<?> range = null;
        try {
            range = value.getSpec().getRange();
        } catch (RuntimeException ignored) {
            // fall through
        }
        if (range == null) {
            return false;
        }
        try {
            Object min = range.getMin();
            Object max = range.getMax();
            if (min instanceof Number && max instanceof Number) {
                if (!isExactBound(min, 0.0) || !isExactBound(max, 255.0)) {
                    return false;
                }
            }
            // Also require the predicate to accept the full channel span and reject outside.
            return range.test(0) && range.test(255) && !range.test(-1) && !range.test(256);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    /** Whether this config value can be declared as a Color binding from the single-value editor. */
    public static boolean canDeclareAsColor(ModConfigSpec.ConfigValue<?> value) {
        if (value == null) {
            return false;
        }
        ValueKind kind = classify(value);
        if (kind == ValueKind.STRING) {
            return true;
        }
        return isColorChannel0to255(value);
    }

    private static boolean isExactBound(@Nullable Object bound, double expected) {
        return bound instanceof Number number && Math.abs(number.doubleValue() - expected) < 1e-9;
    }

    /** Coerce a 0–255 channel into the storage type of {@code value}. */
    public static Object coerceChannelValue(ModConfigSpec.ConfigValue<?> value, int channel0to255) {
        int v = Math.max(0, Math.min(255, channel0to255));
        return switch (classify(value)) {
            case BYTE -> (byte) v;
            case SHORT -> (short) v;
            case LONG -> (long) v;
            case DOUBLE -> (double) v;
            default -> v;
        };
    }

    public static String formatCurrent(ModConfigSpec.ConfigValue<?> value) {
        return formatValue(safeGet(value));
    }

    public static String formatValue(@Nullable Object current) {
        if (current == null) {
            return "null";
        }
        if (current instanceof List<?> list) {
            if (list.isEmpty()) {
                return "[]";
            }
            if (list.size() <= 3) {
                return list.toString();
            }
            return "[" + list.get(0) + ", … +" + (list.size() - 1) + "]";
        }
        return String.valueOf(current);
    }

    /** Compact label for list rows (entry count / short scalar). */
    public static String formatValueShort(@Nullable Object current) {
        if (current == null) {
            return "null";
        }
        if (current instanceof List<?> list) {
            return Component.translatable("screen.another_config_manager.config.entries", list.size()).getString();
        }
        if (current instanceof Boolean bool) {
            return Component.translatable(bool
                    ? "screen.another_config_manager.config.on"
                    : "screen.another_config_manager.config.off").getString();
        }
        String text = String.valueOf(current);
        if (text.length() > 28) {
            return text.substring(0, 25) + "…";
        }
        return text;
    }

    public static String formatDefault(ModConfigSpec.ConfigValue<?> value) {
        return formatValue(value.getDefault());
    }

    public static boolean differsFromDefault(ConfigEditSession session, ModConfigSpec.ConfigValue<?> value) {
        Object current = session.getEffective(value);
        Object def = value.getDefault();
        return !java.util.Objects.equals(current, def);
    }

    /**
     * Parses text and buffers the change in {@code session} (no disk write).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean trySetBuffered(ConfigEditSession session, ModConfigSpec.ConfigValue<?> value, String text) {
        ValueKind kind = classify(value);
        try {
            Object parsed = switch (kind) {
                case BOOLEAN -> Boolean.parseBoolean(text.trim());
                case BYTE -> {
                    int v = Integer.parseInt(text.trim());
                    if (v < Byte.MIN_VALUE || v > Byte.MAX_VALUE) {
                        throw new NumberFormatException("byte out of range");
                    }
                    yield (byte) v;
                }
                case SHORT -> {
                    int v = Integer.parseInt(text.trim());
                    if (v < Short.MIN_VALUE || v > Short.MAX_VALUE) {
                        throw new NumberFormatException("short out of range");
                    }
                    yield (short) v;
                }
                case INT -> Integer.parseInt(text.trim());
                case LONG -> Long.parseLong(text.trim());
                case DOUBLE -> Double.parseDouble(text.trim());
                case STRING -> text;
                case ENUM -> parseEnum(value, text.trim());
                case LIST, READ_ONLY -> null;
            };
            if (parsed == null) {
                return false;
            }
            session.setPending(value.getPath(), parsed);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    public static boolean trySetListBuffered(ConfigEditSession session, ModConfigSpec.ConfigValue<?> value, List<?> list) {
        if (classify(value) != ValueKind.LIST) {
            return false;
        }
        session.setPending(value.getPath(), new ArrayList<>(list));
        return true;
    }

    public static void toggleBoolean(ConfigEditSession session, ModConfigSpec.BooleanValue value) {
        Object current = session.getEffective(value);
        boolean now = current instanceof Boolean bool ? bool : value.get();
        session.setPending(value.getPath(), !now);
    }

    /** @deprecated Immediate save removed; use {@link #toggleBoolean(ConfigEditSession, ModConfigSpec.BooleanValue)}. */
    @Deprecated
    public static void toggleBoolean(ModConfigSpec.BooleanValue value) {
        value.set(!value.get());
        value.save();
    }

    /** @deprecated Immediate save removed; use {@link #trySetBuffered}. */
    @Deprecated
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean trySet(ModConfigSpec.ConfigValue<?> value, String text) {
        ValueKind kind = classify(value);
        try {
            switch (kind) {
                case BOOLEAN -> ((ModConfigSpec.ConfigValue) value).set(Boolean.parseBoolean(text.trim()));
                case BYTE -> ((ModConfigSpec.ConfigValue) value).set((byte) Integer.parseInt(text.trim()));
                case SHORT -> ((ModConfigSpec.ConfigValue) value).set((short) Integer.parseInt(text.trim()));
                case INT -> ((ModConfigSpec.ConfigValue) value).set(Integer.parseInt(text.trim()));
                case LONG -> ((ModConfigSpec.ConfigValue) value).set(Long.parseLong(text.trim()));
                case DOUBLE -> ((ModConfigSpec.ConfigValue) value).set(Double.parseDouble(text.trim()));
                case STRING -> ((ModConfigSpec.ConfigValue) value).set(text);
                case ENUM -> ((ModConfigSpec.ConfigValue) value).set(parseEnum(value, text.trim()));
                default -> {
                    return false;
                }
            }
            value.save();
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Nullable
    public static String commentOf(ModConfigSpec.ConfigValue<?> value) {
        String raw = null;
        try {
            ModConfigSpec.ValueSpec spec = value.getSpec();
            String comment = spec.getComment();
            if (comment != null && !comment.isBlank()) {
                raw = comment;
            }
        } catch (RuntimeException ignored) {
            // fall through to reflection
        }
        if (raw == null) {
            raw = commentViaReflection(value);
        }
        return ConfigComments.normalize(raw);
    }

    @Nullable
    private static String commentViaReflection(ModConfigSpec.ConfigValue<?> value) {
        try {
            Object valueSpec = value.getSpec();
            Method getComment = valueSpec.getClass().getMethod("getComment");
            Object result = getComment.invoke(valueSpec);
            if (result instanceof String text && !text.isBlank()) {
                return text;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // unavailable
        }
        return null;
    }

    public static boolean isStringList(ModConfigSpec.ConfigValue<?> value) {
        List<?> list = null;
        Object def = value.getDefault();
        if (def instanceof List<?> defList) {
            list = defList;
        } else {
            Object current = safeGet(value);
            if (current instanceof List<?> cur) {
                list = cur;
            }
        }
        if (list == null) {
            return false;
        }
        if (list.isEmpty()) {
            Class<?> clazz = null;
            try {
                clazz = value.getSpec().getClazz();
            } catch (RuntimeException ignored) {
                // ignore
            }
            // Empty list: assume string list when clazz is absent or List
            return clazz == null || List.class.isAssignableFrom(clazz) || clazz == String.class;
        }
        return list.get(0) instanceof String;
    }

    public static Class<?> listElementType(ModConfigSpec.ConfigValue<?> value) {
        Object sample = null;
        Object current = safeGet(value);
        if (current instanceof List<?> list && !list.isEmpty()) {
            sample = list.get(0);
        } else {
            Object def = value.getDefault();
            if (def instanceof List<?> list && !list.isEmpty()) {
                sample = list.get(0);
            }
        }
        if (sample instanceof String) {
            return String.class;
        }
        if (sample instanceof Integer) {
            return Integer.class;
        }
        if (sample instanceof Long) {
            return Long.class;
        }
        if (sample instanceof Double || sample instanceof Float) {
            return Double.class;
        }
        if (value.getSpec() instanceof ModConfigSpec.ListValueSpec listSpec) {
            Object neu = listSpec.getNewElementSupplier() == null ? null : listSpec.getNewElementSupplier().get();
            if (neu instanceof String) {
                return String.class;
            }
            if (neu instanceof Integer) {
                return Integer.class;
            }
            if (neu instanceof Long) {
                return Long.class;
            }
            if (neu instanceof Double || neu instanceof Float) {
                return Double.class;
            }
        }
        return String.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseEnum(ModConfigSpec.ConfigValue<?> value, String text) {
        Class<?> clazz = value.getSpec().getClazz();
        if (clazz == null || !clazz.isEnum()) {
            Object def = value.getDefault();
            if (def instanceof Enum<?> e) {
                clazz = e.getDeclaringClass();
            } else {
                throw new IllegalArgumentException("Not an enum");
            }
        }
        for (Object constant : clazz.getEnumConstants()) {
            if (constant.toString().equalsIgnoreCase(text) || ((Enum) constant).name().equalsIgnoreCase(text)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown enum: " + text);
    }

    @Nullable
    public static Object[] enumConstants(ModConfigSpec.ConfigValue<?> value) {
        Class<?> clazz = null;
        try {
            clazz = value.getSpec().getClazz();
        } catch (RuntimeException ignored) {
            // ignore
        }
        if (clazz == null || !clazz.isEnum()) {
            Object def = value.getDefault();
            if (def instanceof Enum<?> e) {
                clazz = e.getDeclaringClass();
            }
        }
        return clazz == null || !clazz.isEnum() ? null : clazz.getEnumConstants();
    }

    private static Object safeGet(ModConfigSpec.ConfigValue<?> value) {
        try {
            return value.get();
        } catch (RuntimeException error) {
            return value.getDefault();
        }
    }

    @Nullable
    private static UnmodifiableConfig nestedValues(UnmodifiableConfig root, List<String> path) {
        UnmodifiableConfig current = root;
        for (String segment : path) {
            Object next = current.get(segment);
            if (!(next instanceof UnmodifiableConfig nested)) {
                return null;
            }
            current = nested;
        }
        return current;
    }

    private static List<String> append(List<String> path, String key) {
        List<String> next = new ArrayList<>(path.size() + 1);
        next.addAll(path);
        next.add(key);
        return List.copyOf(next);
    }

    public static String joinPath(List<String> path) {
        return String.join(".", path);
    }

    public static String typeLabel(net.neoforged.fml.config.ModConfig.Type type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
