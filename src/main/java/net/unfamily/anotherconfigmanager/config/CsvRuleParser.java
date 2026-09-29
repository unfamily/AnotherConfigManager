package net.unfamily.anotherconfigmanager.config;

import net.unfamily.anotherconfigmanager.config.CsvRule.ColumnSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses CSV rule-strings: {@code {sep}[path]{sep}{colDef}{sep}...}.
 * Single-character separators use the first character as {@code sep}.
 * Multi-character separators require {@link #parse(String, String)}.
 */
public final class CsvRuleParser {
    private static final Pattern COL_CLOSED = Pattern.compile("^(\\d+)-(\\d+)\\$$");
    private static final Pattern COL_RANGE_LANG = Pattern.compile("^(\\d+)-(\\d+)=(.+)$");
    private static final Pattern COL_RANGE = Pattern.compile("^(\\d+)-(\\d+)$");
    private static final Pattern COL_OPEN_LANG = Pattern.compile("^(\\d+)\\+=(.+)$");
    private static final Pattern COL_OPEN = Pattern.compile("^(\\d+)\\+$");
    private static final Pattern COL_SINGLE_LANG = Pattern.compile("^(\\d+)=(.+)$");
    private static final Pattern COL_SINGLE = Pattern.compile("^(\\d+)$");

    private CsvRuleParser() {}

    public static CsvRule parse(String rule) {
        if (rule == null || rule.isEmpty()) {
            throw new IllegalArgumentException("CSV rule must not be empty");
        }
        return parse(String.valueOf(rule.charAt(0)), rule);
    }

    public static CsvRule parse(String separator, String rule) {
        if (separator == null || separator.isEmpty()) {
            throw new IllegalArgumentException("CSV separator must not be empty");
        }
        if (rule == null || rule.isEmpty()) {
            throw new IllegalArgumentException("CSV rule must not be empty");
        }
        if (!rule.startsWith(separator)) {
            throw new IllegalArgumentException("CSV rule must start with separator");
        }
        String rest = rule.substring(separator.length());
        List<String> blocks = splitKeepingEmpty(rest, separator);

        String path = "";
        int colStart = 0;
        if (!blocks.isEmpty()) {
            String first = blocks.getFirst();
            if (first.isEmpty() || !looksLikeColDef(first)) {
                path = first;
                colStart = 1;
            }
        }

        List<ColumnSpec> columns = new ArrayList<>();
        boolean closed = false;
        for (int i = colStart; i < blocks.size(); i++) {
            String block = blocks.get(i);
            if (block == null || block.isEmpty()) {
                continue;
            }
            // type:{...};{colDef} embeds a ';' which equals the rule separator — rejoin split pieces.
            while (block.startsWith("type:") && !looksLikeColDef(block) && i + 1 < blocks.size()) {
                i++;
                block = block + separator + blocks.get(i);
            }
            ParsedCol parsed = parseColDef(block);
            columns.add(parsed.spec());
            closed |= parsed.closed();
        }
        Optional<String> pathOpt = path == null || path.isEmpty() ? Optional.empty() : Optional.of(path);
        return new CsvRule(separator, pathOpt, columns, closed);
    }

    public static boolean looksLikeColDef(String block) {
        if (block == null || block.isEmpty()) {
            return false;
        }
        String stripped = stripTypePrefix(block).def();
        return COL_CLOSED.matcher(stripped).matches()
                || COL_RANGE_LANG.matcher(stripped).matches()
                || COL_RANGE.matcher(stripped).matches()
                || COL_OPEN_LANG.matcher(stripped).matches()
                || COL_OPEN.matcher(stripped).matches()
                || COL_SINGLE_LANG.matcher(stripped).matches()
                || COL_SINGLE.matcher(stripped).matches();
    }

    private static ParsedCol parseColDef(String block) {
        TypePrefix typePrefix = stripTypePrefix(block);
        String def = typePrefix.def();
        Optional<String> cellType = typePrefix.cellType();
        Map<String, String> attrs = typePrefix.attrs();
        Matcher m = COL_CLOSED.matcher(def);
        if (m.matches()) {
            int from = Integer.parseInt(m.group(1));
            int to = Integer.parseInt(m.group(2));
            return new ParsedCol(new ColumnSpec(from, to, Optional.empty(), true, cellType, attrs), true);
        }
        m = COL_RANGE_LANG.matcher(def);
        if (m.matches()) {
            int from = Integer.parseInt(m.group(1));
            int to = Integer.parseInt(m.group(2));
            return new ParsedCol(new ColumnSpec(from, to, Optional.of(m.group(3)), false, cellType, attrs), false);
        }
        m = COL_RANGE.matcher(def);
        if (m.matches()) {
            int from = Integer.parseInt(m.group(1));
            int to = Integer.parseInt(m.group(2));
            return new ParsedCol(new ColumnSpec(from, to, Optional.empty(), true, cellType, attrs), false);
        }
        m = COL_OPEN_LANG.matcher(def);
        if (m.matches()) {
            int from = Integer.parseInt(m.group(1));
            return new ParsedCol(new ColumnSpec(from, -1, Optional.of(m.group(2)), false, cellType, attrs), false);
        }
        m = COL_OPEN.matcher(def);
        if (m.matches()) {
            int from = Integer.parseInt(m.group(1));
            return new ParsedCol(new ColumnSpec(from, -1, Optional.empty(), true, cellType, attrs), false);
        }
        m = COL_SINGLE_LANG.matcher(def);
        if (m.matches()) {
            int index = Integer.parseInt(m.group(1));
            return new ParsedCol(new ColumnSpec(index, index, Optional.of(m.group(2)), false, cellType, attrs), false);
        }
        m = COL_SINGLE.matcher(def);
        if (m.matches()) {
            int index = Integer.parseInt(m.group(1));
            return new ParsedCol(new ColumnSpec(index, index, Optional.empty(), true, cellType, attrs), false);
        }
        throw new IllegalArgumentException("Unknown CSV column definition: " + block);
    }

    /**
     * Strips optional {@code type:{cellType}[:key=value...];} prefix from a colDef block.
     */
    private static TypePrefix stripTypePrefix(String block) {
        if (block == null || !block.startsWith("type:")) {
            return new TypePrefix(Optional.empty(), Map.of(), block);
        }
        int semi = block.indexOf(';');
        if (semi <= 5 || semi >= block.length() - 1) {
            return new TypePrefix(Optional.empty(), Map.of(), block);
        }
        String typePart = block.substring("type:".length(), semi);
        String def = block.substring(semi + 1);
        if (typePart.isBlank()) {
            return new TypePrefix(Optional.empty(), Map.of(), def);
        }
        String[] segs = typePart.split(":");
        String cellType = segs[0].trim();
        if (cellType.isBlank()) {
            return new TypePrefix(Optional.empty(), Map.of(), def);
        }
        Map<String, String> attrs = new LinkedHashMap<>();
        for (int i = 1; i < segs.length; i++) {
            String seg = segs[i].trim();
            int eq = seg.indexOf('=');
            if (eq > 0) {
                attrs.put(seg.substring(0, eq).trim().toLowerCase(), seg.substring(eq + 1).trim());
            }
        }
        return new TypePrefix(Optional.of(cellType), attrs, def);
    }

    private record TypePrefix(Optional<String> cellType, Map<String, String> attrs, String def) {}

    private static List<String> splitKeepingEmpty(String input, String sep) {
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

    private record ParsedCol(ColumnSpec spec, boolean closed) {}
}
