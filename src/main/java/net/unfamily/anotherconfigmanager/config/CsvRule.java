package net.unfamily.anotherconfigmanager.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Column schema for editing a string-list config value as a CSV table.
 *
 * @param separator   column separator (1+ characters)
 * @param path        optional rule id / ResourceLocation string
 * @param columns     column definitions (empty = unlimited numbered columns)
 * @param closed      when true (from {@code $}), only declared columns are shown
 */
public record CsvRule(
        String separator,
        Optional<String> path,
        List<ColumnSpec> columns,
        boolean closed
) {
    public CsvRule {
        if (separator == null || separator.isEmpty()) {
            throw new IllegalArgumentException("CSV separator must not be empty");
        }
        path = path == null ? Optional.empty() : path;
        columns = columns == null ? List.of() : List.copyOf(columns);
    }

    /**
     * One column range or open-ended group inside a {@link CsvRule}.
     *
     * @param from                first column index (inclusive)
     * @param toInclusiveOrNeg1   last index inclusive, or {@code -1} for open-ended ({@code +})
     * @param translationKey      optional lang key for the column header
     * @param numberedOnly        when true, header is the column index (no translation)
     * @param cellType            optional typed cell (e.g. {@code color_hex})
     * @param typeAttrs           attrs from {@code type:cellType:key=value:...} (order, prefix, …)
     */
    public record ColumnSpec(
            int from,
            int toInclusiveOrNeg1,
            Optional<String> translationKey,
            boolean numberedOnly,
            Optional<String> cellType,
            Map<String, String> typeAttrs
    ) {
        public ColumnSpec {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            cellType = cellType == null ? Optional.empty() : cellType;
            typeAttrs = typeAttrs == null || typeAttrs.isEmpty() ? Map.of() : Map.copyOf(typeAttrs);
        }

        public ColumnSpec(int from, int toInclusiveOrNeg1, Optional<String> translationKey, boolean numberedOnly) {
            this(from, toInclusiveOrNeg1, translationKey, numberedOnly, Optional.empty(), Map.of());
        }

        public ColumnSpec(
                int from,
                int toInclusiveOrNeg1,
                Optional<String> translationKey,
                boolean numberedOnly,
                Optional<String> cellType
        ) {
            this(from, toInclusiveOrNeg1, translationKey, numberedOnly, cellType, Map.of());
        }

        public boolean isOpenEnded() {
            return toInclusiveOrNeg1 < 0;
        }

        public boolean covers(int index) {
            if (index < from) {
                return false;
            }
            return isOpenEnded() || index <= toInclusiveOrNeg1;
        }

        public boolean isColorCell() {
            return cellType.filter(t -> t.startsWith("color_")).isPresent();
        }

        /** Format spec for {@link ColorCodec} from cell type attrs (defaults to hex/rgb). */
        public String colorFormatSpec() {
            String order = typeAttrs.getOrDefault("order", "rgb");
            String prefix = typeAttrs.getOrDefault("prefix", "none");
            String letterCase = typeAttrs.getOrDefault("case", "upper");
            String storage = "hex";
            String type = cellType.orElse("color_hex");
            if (type.contains("int_packed") || type.equals("color_int_packed")) {
                storage = "int";
            }
            return storage + ";" + order + ";prefix=" + prefix + ";case=" + letterCase;
        }
    }
}
