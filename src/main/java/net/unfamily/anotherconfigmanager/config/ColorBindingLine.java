package net.unfamily.anotherconfigmanager.config;

import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Codec for {@code color_manager} rows:
 * {@code {storage};{order};{modId}:{type}:{primaryPath};{extra...}}
 * Optional leading {@code {len}{SEP}} for storage=csv.
 * Extras may be sibling paths and/or {@code prefix=}/{@code case=}/{@code scale=} attrs.
 */
public final class ColorBindingLine {
    public static final String FIELD_SEP = ";";

    private ColorBindingLine() {}

    public record Parsed(
            String storage,
            String order,
            String modId,
            ModConfig.Type type,
            String primaryPath,
            List<String> pathExtras,
            @Nullable String csvSeparator,
            String prefix,
            String letterCase,
            double scale
    ) {
        /** Spec string for {@link ColorCodec.Spec#parseFormatSpec(String)}. */
        public String formatSpec() {
            StringBuilder sb = new StringBuilder();
            if (csvSeparator != null) {
                sb.append(csvSeparator.length()).append(csvSeparator);
            }
            sb.append(storage).append(FIELD_SEP).append(order);
            sb.append(FIELD_SEP).append("prefix=").append(prefix == null ? "#" : prefix);
            sb.append(FIELD_SEP).append("case=").append(letterCase == null ? "upper" : letterCase);
            sb.append(FIELD_SEP).append("scale=").append(scale);
            return sb.toString();
        }

        /** All config paths in channel order (primary first). */
        public List<String> allPaths() {
            List<String> out = new ArrayList<>();
            out.add(primaryPath);
            out.addAll(pathExtras);
            return List.copyOf(out);
        }
    }

    @Nullable
    public static Parsed parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String trimmed = line.trim();
        String csvSep = null;
        String body = trimmed;
        int i = 0;
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i > 0) {
            try {
                int sepLen = Integer.parseInt(trimmed.substring(0, i));
                if (sepLen >= 1 && sepLen <= 8 && i + sepLen <= trimmed.length()) {
                    csvSep = trimmed.substring(i, i + sepLen);
                    body = trimmed.substring(i + sepLen);
                }
            } catch (NumberFormatException ignored) {
                // treat as normal binding without len prefix
            }
        }
        String[] parts = body.split(FIELD_SEP, -1);
        if (parts.length < 3) {
            return null;
        }
        String storage = parts[0].trim().toLowerCase(Locale.ROOT);
        String order = parts[1].trim().toLowerCase(Locale.ROOT);
        String target = parts[2].trim();
        String[] t = target.split(":", 3);
        if (t.length < 3) {
            return null;
        }
        ModConfig.Type type;
        try {
            type = ModConfig.Type.valueOf(t[1].trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
        String prefix = "#";
        String letterCase = "upper";
        double scale = 255d;
        List<String> pathExtras = new ArrayList<>();
        for (int p = 3; p < parts.length; p++) {
            String extra = parts[p] == null ? "" : parts[p].trim();
            if (extra.startsWith("prefix=")) {
                prefix = extra.substring("prefix=".length());
            } else if (extra.startsWith("case=")) {
                letterCase = extra.substring("case=".length());
            } else if (extra.startsWith("scale=")) {
                try {
                    scale = Double.parseDouble(extra.substring("scale=".length()));
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            } else if (!extra.isEmpty()) {
                pathExtras.add(extra);
            }
        }
        return new Parsed(
                storage,
                order,
                t[0].trim(),
                type,
                t[2].trim(),
                List.copyOf(pathExtras),
                csvSep,
                prefix,
                letterCase,
                scale
        );
    }

    public static String format(
            String storage,
            String order,
            String modId,
            ModConfig.Type type,
            String primaryPath,
            List<String> pathExtras,
            String prefix,
            String letterCase
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(storage).append(FIELD_SEP).append(order).append(FIELD_SEP);
        sb.append(modId).append(':').append(type.name().toLowerCase(Locale.ROOT)).append(':').append(primaryPath);
        if (pathExtras != null) {
            for (String e : pathExtras) {
                if (e != null && !e.isBlank()) {
                    sb.append(FIELD_SEP).append(e.trim());
                }
            }
        }
        if (prefix != null && !prefix.isBlank()) {
            sb.append(FIELD_SEP).append("prefix=").append(prefix);
        }
        if (letterCase != null && !letterCase.isBlank()) {
            sb.append(FIELD_SEP).append("case=").append(letterCase);
        }
        return sb.toString();
    }

    /** Convenience: no path extras. */
    public static String format(String storage, String order, String modId, ModConfig.Type type, String primaryPath) {
        return format(storage, order, modId, type, primaryPath, List.of(), "#", "upper");
    }

    public static boolean isColorManagerPath(List<String> path) {
        return path != null
                && path.size() == 1
                && "color_manager".equals(path.get(0));
    }

    /**
     * Table cells: storage, order, target ({@code mod:type:path}), extras body ({@code ;}-joined).
     */
    public static List<String> toTableCells(String line) {
        Parsed parsed = parse(line);
        if (parsed == null) {
            return List.of("", "", "", line == null ? "" : line);
        }
        String target = parsed.modId()
                + ":"
                + parsed.type().name().toLowerCase(Locale.ROOT)
                + ":"
                + parsed.primaryPath();
        StringBuilder extras = new StringBuilder();
        for (String e : parsed.pathExtras()) {
            if (e == null || e.isBlank()) {
                continue;
            }
            if (extras.length() > 0) {
                extras.append(FIELD_SEP);
            }
            extras.append(e.trim());
        }
        if (parsed.prefix() != null && !parsed.prefix().isBlank()) {
            if (extras.length() > 0) {
                extras.append(FIELD_SEP);
            }
            extras.append("prefix=").append(parsed.prefix());
        }
        if (parsed.letterCase() != null && !parsed.letterCase().isBlank()) {
            if (extras.length() > 0) {
                extras.append(FIELD_SEP);
            }
            extras.append("case=").append(parsed.letterCase());
        }
        if (parsed.scale() != 255d) {
            if (extras.length() > 0) {
                extras.append(FIELD_SEP);
            }
            extras.append("scale=").append(parsed.scale());
        }
        return List.of(parsed.storage(), parsed.order(), target, extras.toString());
    }

    public static String fromTableCells(List<String> cells) {
        String storage = cellAt(cells, 0);
        String order = cellAt(cells, 1);
        String target = cellAt(cells, 2);
        String extrasBody = cellAt(cells, 3);
        if (storage.isBlank() || order.isBlank() || target.isBlank()) {
            throw new IllegalArgumentException("Invalid color binding row");
        }
        StringBuilder sb = new StringBuilder();
        sb.append(storage.trim()).append(FIELD_SEP).append(order.trim()).append(FIELD_SEP).append(target.trim());
        if (!extrasBody.isBlank()) {
            sb.append(FIELD_SEP).append(extrasBody.trim());
        }
        String line = sb.toString();
        if (parse(line) == null) {
            throw new IllegalArgumentException("Invalid color binding row: " + line);
        }
        return line;
    }

    private static String cellAt(List<String> cells, int index) {
        if (cells == null || index < 0 || index >= cells.size() || cells.get(index) == null) {
            return "";
        }
        return cells.get(index);
    }
}
