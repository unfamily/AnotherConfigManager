package net.unfamily.anotherconfigmanager.config;

import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Codec for Library {@code csv.csv_bindings} rows:
 * {@code {len}{SEP}{rulePath};{modId}:{type}:{configPath};{colDef0};{colDef1};...}.
 * <p>
 * {@code len} is decimal digits = {@code SEP.length()}; the next {@code len} characters are the
 * CSV separator of the target rule. The remainder is {@code ;}-separated fields.
 * {@code rulePath} is the optional path block of a {@link CsvRule} (not a column definition).
 * Following fields after target are column definitions only ({@code 0+}, {@code 0=key}, …).
 */
public final class CsvBindingLine {
    public static final String BODY_FIELD_SEP = ";";
    public static final String DEFAULT_SEPARATOR = ";";
    public static final int MAX_SEPARATOR_LENGTH = 8;

    private CsvBindingLine() {}

    public record Parsed(
            String separator,
            String rulePath,
            String modId,
            ModConfig.Type type,
            String configPath,
            String ruleString,
            List<String> columnDefs
    ) {
        public String target() {
            return formatTarget(modId, type, configPath);
        }

        public String columnsBody() {
            return String.join(BODY_FIELD_SEP, columnDefs);
        }
    }

    public static String formatTarget(String modId, ModConfig.Type type, String configPath) {
        return modId + ":" + type.name().toLowerCase(Locale.ROOT) + ":" + configPath;
    }

    public static String format(
            String separator,
            String rulePath,
            String modId,
            ModConfig.Type type,
            String configPath,
            String ruleString
    ) {
        return format(separator, rulePath, modId, type, configPath, columnDefsFromRule(separator, rulePath, ruleString));
    }

    public static String format(
            String separator,
            String rulePath,
            String modId,
            ModConfig.Type type,
            String configPath,
            List<String> columnDefs
    ) {
        if (separator == null || separator.isEmpty() || separator.length() > MAX_SEPARATOR_LENGTH) {
            throw new IllegalArgumentException("Invalid CSV separator length");
        }
        String path = rulePath == null ? "" : rulePath;
        StringBuilder sb = new StringBuilder();
        sb.append(separator.length());
        sb.append(separator);
        sb.append(path);
        sb.append(BODY_FIELD_SEP);
        sb.append(formatTarget(modId, type, configPath));
        if (columnDefs != null) {
            for (String col : columnDefs) {
                sb.append(BODY_FIELD_SEP);
                sb.append(col == null ? "" : col);
            }
        }
        return sb.toString();
    }

    public static String formatFromCells(String separator, String rulePath, String target, String columnsBody) {
        TargetParts parts = parseTarget(target);
        if (parts == null) {
            throw new IllegalArgumentException("Invalid binding target: " + target);
        }
        List<String> cols = splitBodyFields(columnsBody == null ? "" : columnsBody);
        return format(separator, rulePath, parts.modId(), parts.type(), parts.configPath(), cols);
    }

    @Nullable
    public static Parsed parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String trimmed = line.trim();
        int i = 0;
        while (i < trimmed.length() && Character.isDigit(trimmed.charAt(i))) {
            i++;
        }
        if (i == 0) {
            return null;
        }
        int sepLen;
        try {
            sepLen = Integer.parseInt(trimmed.substring(0, i));
        } catch (NumberFormatException error) {
            return null;
        }
        if (sepLen < 1 || sepLen > MAX_SEPARATOR_LENGTH || i + sepLen > trimmed.length()) {
            return null;
        }
        String separator = trimmed.substring(i, i + sepLen);
        String body = trimmed.substring(i + sepLen);
        List<String> fields = splitBodyFields(body);
        // path ; target [; colDef...]
        if (fields.size() < 2) {
            return null;
        }
        String rulePath = fields.get(0) == null ? "" : fields.get(0);
        TargetParts target = parseTarget(fields.get(1));
        if (target == null) {
            return null;
        }
        List<String> columnDefs = fields.size() == 2 ? List.of() : List.copyOf(fields.subList(2, fields.size()));
        String ruleString = toRuleString(separator, rulePath, columnDefs);
        if (ruleString.isEmpty()) {
            return null;
        }
        return new Parsed(
                separator,
                rulePath,
                target.modId(),
                target.type(),
                target.configPath(),
                ruleString,
                columnDefs
        );
    }

    /** Table cells: separator, rule path, target, column defs body. */
    public static List<String> toTableCells(String line) {
        Parsed parsed = parse(line);
        if (parsed == null) {
            return List.of("", "", "", line == null ? "" : line);
        }
        return List.of(parsed.separator(), parsed.rulePath(), parsed.target(), parsed.columnsBody());
    }

    public static String fromTableCells(List<String> cells) {
        String sep = cells.size() > 0 && cells.get(0) != null ? cells.get(0) : "";
        String rulePath = cells.size() > 1 && cells.get(1) != null ? cells.get(1) : "";
        String target = cells.size() > 2 && cells.get(2) != null ? cells.get(2) : "";
        String columnsBody = cells.size() > 3 && cells.get(3) != null ? cells.get(3) : "";
        return formatFromCells(sep, rulePath, target, columnsBody);
    }

    public static boolean isBindingsPath(List<String> path) {
        return path != null
                && path.size() == 1
                && "csv_manager".equals(path.get(0));
    }

    /**
     * Rebuilds a classic rule string: {@code {SEP}{path}{SEP}{colDef}{SEP}...}.
     */
    public static String toRuleString(String separator, String rulePath, List<String> columnDefs) {
        if (separator == null || separator.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(separator);
        sb.append(rulePath == null ? "" : rulePath);
        if (columnDefs != null) {
            for (String col : columnDefs) {
                sb.append(separator);
                sb.append(col == null ? "" : col);
            }
        }
        return sb.toString();
    }

    /**
     * Extracts column-definition blocks from a classic rule string, given its separator and path.
     */
    public static List<String> columnDefsFromRule(String separator, String rulePath, String ruleString) {
        if (ruleString == null || ruleString.isEmpty() || separator == null || separator.isEmpty()) {
            return List.of();
        }
        String rest = ruleString.startsWith(separator)
                ? ruleString.substring(separator.length())
                : ruleString;
        String path = rulePath == null ? "" : rulePath;
        if (!path.isEmpty()) {
            if (rest.equals(path)) {
                return List.of();
            }
            String prefix = path + separator;
            if (rest.startsWith(prefix)) {
                rest = rest.substring(prefix.length());
            } else if (rest.startsWith(path) && rest.length() == path.length()) {
                return List.of();
            }
        }
        if (rest.isEmpty()) {
            return List.of();
        }
        // If path was empty, rest is pathOrCols — first block may be path-like; callers should pass rulePath.
        return splitOnSeparator(rest, separator);
    }

    @Nullable
    public static TargetParts parseTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        int first = target.indexOf(':');
        if (first <= 0) {
            return null;
        }
        int second = target.indexOf(':', first + 1);
        if (second <= first + 1 || second >= target.length() - 1) {
            return null;
        }
        String modId = target.substring(0, first).trim();
        String typeRaw = target.substring(first + 1, second).trim();
        String configPath = target.substring(second + 1).trim();
        if (modId.isEmpty() || typeRaw.isEmpty() || configPath.isEmpty()) {
            return null;
        }
        ModConfig.Type type;
        try {
            type = ModConfig.Type.valueOf(typeRaw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return null;
        }
        return new TargetParts(modId, type, configPath);
    }

    public record TargetParts(String modId, ModConfig.Type type, String configPath) {}

    private static List<String> splitBodyFields(String body) {
        List<String> parts = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return parts;
        }
        int start = 0;
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == ';') {
                parts.add(body.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(body.substring(start));
        return parts;
    }

    private static List<String> splitOnSeparator(String input, String sep) {
        List<String> parts = new ArrayList<>();
        if (sep.isEmpty()) {
            parts.add(input);
            return parts;
        }
        int start = 0;
        int idx;
        while ((idx = input.indexOf(sep, start)) >= 0) {
            parts.add(input.substring(start, idx));
            start = idx + sep.length();
        }
        parts.add(input.substring(start));
        return parts;
    }
}
