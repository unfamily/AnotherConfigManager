package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Spreadsheet-style editor for string-list config values guided by a {@link CsvRule}.
 * Vertical scroll via list; horizontal scroll via scrollbar drag only (mouse wheel stays vertical).
 */
public class CsvTableScreen extends Screen {
    private static final int HEADER_H = 48;
    private static final int FOOTER_MARGIN = 14;
    private static final int TOP_STACK_GAP = 10;
    private static final int LIST_GAP = 12;
    private static final int BUTTON_H = 20;
    private static final int CONTENT_MARGIN = 20;
    private static final int COL_HEADER_H = 14;
    private static final int H_SCROLLBAR_H = 6;
    private static final int COL_PAD = 8;
    private static final int COL_MIN = 48;
    private static final int COL_MAX = 140;
    private static final int INDEX_W = 28;

    private final ConfigOpenerScreen.SpecScreen parent;
    private final ConfigEditSession session;
    private final ConfigSpecNodes.ValueNode node;
    private final CsvRule rule;
    private final boolean bindingsEditor;
    private final boolean colorManagerEditor;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_H, FOOTER_MARGIN);
    private final List<List<String>> table = new ArrayList<>();
    private List<ColumnHeader> headers = List.of();
    private int[] columnWidths = new int[0];
    private @Nullable TableList tableList;
    private @Nullable ColumnHeaderBar headerBar;
    private @Nullable EditBox searchBox;
    private @Nullable EditBox cellBox;
    private @Nullable StringWidget status;
    private @Nullable LinearLayout buttonRow;
    private @Nullable LinearLayout editRow;
    private @Nullable LinearLayout structureRow;
    private String searchQuery = "";
    private int selectedRow = -1;
    private int selectedCol = -1;

    public CsvTableScreen(
            ConfigOpenerScreen.SpecScreen parent,
            ConfigEditSession session,
            ConfigSpecNodes.ValueNode node,
            CsvRule rule
    ) {
        super(Component.literal(node.displayName()));
        this.parent = parent;
        this.session = session;
        this.node = node;
        this.rule = rule;
        this.bindingsEditor = CsvBindingLine.isBindingsPath(node.path());
        this.colorManagerEditor = ColorBindingLine.isColorManagerPath(node.path());
        Object effective = session.getEffective(node.value());
        if (effective instanceof List<?> list) {
            for (Object row : list) {
                if (bindingsEditor) {
                    table.add(new ArrayList<>(CsvBindingLine.toTableCells(String.valueOf(row))));
                } else if (colorManagerEditor) {
                    table.add(new ArrayList<>(ColorBindingLine.toTableCells(String.valueOf(row))));
                } else {
                    table.add(splitRow(String.valueOf(row), rule));
                }
            }
        }
        refreshHeaders(false);
    }

    @Override
    protected void init() {
        layout.setHeaderHeight(HEADER_H);
        layout.setFooterHeight(FOOTER_MARGIN);

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(0));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));

        searchBox = new EditBox(font, 0, 0, 200, 20, Component.translatable("screen.another_config_manager.config.search"));
        searchBox.setHint(Component.translatable("screen.another_config_manager.config.search"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value == null ? "" : value;
            rebuildList();
        });
        addRenderableWidget(searchBox);

        refreshHeaders(true);
        headerBar = new ColumnHeaderBar();
        addRenderableWidget(headerBar);

        tableList = new TableList(minecraft);
        addRenderableWidget(tableList);
        rebuildList();

        editRow = LinearLayout.horizontal().spacing(4);
        cellBox = editRow.addChild(new EditBox(font, 0, 0, 160, 20, Component.translatable("screen.another_config_manager.config.csv.cell")));
        cellBox.setMaxLength(2048);
        editRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.csv.set_cell"), button -> setCell())
                .width(72)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.csv.set_cell.tooltip")))
                .build());
        editRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.list.add"), button -> addRow())
                .width(48)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.list.add.tooltip")))
                .build());
        editRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.list.remove"), button -> removeRow())
                .width(64)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.list.remove.tooltip")))
                .build());
        editRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.csv.pick_color_short"), button -> pickColorForCell())
                .width(56)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.csv.pick_color.tooltip")))
                .build());
        editRow.visitWidgets(this::addRenderableWidget);

        structureRow = LinearLayout.horizontal().spacing(4);
        structureRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.csv.insert_column"), button -> insertColumnAtSelection())
                .width(100)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.csv.insert_column.tooltip")))
                .build());
        structureRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.csv.move_column"), button -> moveColumnRight())
                .width(100)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.csv.move_column.tooltip")))
                .build());
        structureRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.csv.delete_column"), button -> deleteColumnAtSelection())
                .width(108)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.csv.delete_column.tooltip")))
                .build());
        structureRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.csv.list_view"), button -> openRawListView())
                .width(80)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.csv.list_view.tooltip")))
                .build());
        structureRow.visitWidgets(this::addRenderableWidget);

        status = new StringWidget(Component.empty(), font);
        addRenderableWidget(status);

        buttonRow = LinearLayout.horizontal().spacing(8);
        buttonRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.apply"), button -> apply())
                .width(100)
                .tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.apply.tooltip")))
                .build());
        buttonRow.addChild(Button.builder(CommonComponents.GUI_BACK, button -> onClose()).width(100).build());
        buttonRow.visitWidgets(this::addRenderableWidget);

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        selectFirstCellIfNeeded();
    }

    /** Select first row/col when opening so a single-entry table is editable without a prior click. */
    private void selectFirstCellIfNeeded() {
        if (selectedRow >= 0 || table.isEmpty() || headers.isEmpty()) {
            return;
        }
        selectedRow = 0;
        selectedCol = headers.get(0).index();
        if (cellBox != null) {
            List<String> row = table.get(0);
            cellBox.setValue(selectedCol < row.size() && row.get(selectedCol) != null ? row.get(selectedCol) : "");
        }
    }

    private void refreshHeaders(boolean withFontMetrics) {
        int maxSeen = 0;
        for (List<String> row : table) {
            maxSeen = Math.max(maxSeen, row.size());
        }
        headers = resolveHeaders(rule, maxSeen);
        int cols = headers.size();
        for (List<String> row : table) {
            while (row.size() < cols) {
                row.add("");
            }
            if (rule.closed() && row.size() > cols) {
                while (row.size() > cols) {
                    row.removeLast();
                }
            }
        }
        if (withFontMetrics && font != null) {
            recomputeColumnWidths();
        } else {
            columnWidths = new int[headers.size()];
            for (int i = 0; i < columnWidths.length; i++) {
                columnWidths[i] = COL_MIN;
            }
        }
    }

    private void recomputeColumnWidths() {
        columnWidths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            int w = font.width(headers.get(i).label()) + COL_PAD;
            int col = headers.get(i).index();
            for (List<String> row : table) {
                String cell = col < row.size() ? row.get(col) : "";
                w = Math.max(w, font.width(cell) + COL_PAD);
            }
            columnWidths[i] = Math.min(COL_MAX, Math.max(COL_MIN, w));
        }
    }

    private int contentWidth() {
        int total = INDEX_W;
        for (int w : columnWidths) {
            total += w + 4;
        }
        return Math.max(total, 80);
    }

    private static List<ColumnHeader> resolveHeaders(CsvRule rule, int maxSeen) {
        List<ColumnHeader> out = new ArrayList<>();
        if (rule.columns().isEmpty()) {
            int count = Math.max(1, maxSeen + 1);
            for (int i = 0; i < count; i++) {
                out.add(new ColumnHeader(i, numberedLabel(i)));
            }
            return out;
        }

        int maxFinite = -1;
        int openFrom = Integer.MAX_VALUE;
        boolean hasOpen = false;
        boolean namedOpen = false;
        for (CsvRule.ColumnSpec spec : rule.columns()) {
            if (spec.isOpenEnded()) {
                hasOpen = true;
                openFrom = Math.min(openFrom, spec.from());
                if (!spec.numberedOnly()) {
                    namedOpen = true;
                }
            } else {
                maxFinite = Math.max(maxFinite, spec.toInclusiveOrNeg1());
            }
        }

        int last;
        if (rule.closed()) {
            last = maxFinite;
        } else if (hasOpen && namedOpen) {
            last = Math.max(maxFinite, openFrom);
        } else if (hasOpen) {
            last = Math.max(maxFinite, Math.max(maxSeen, openFrom)) + 1;
        } else {
            last = maxFinite;
        }

        for (int i = 0; i <= last; i++) {
            CsvRule.ColumnSpec match = findSpec(rule, i);
            if (match == null) {
                continue;
            }
            if (match.isOpenEnded() && !match.numberedOnly() && i != match.from()) {
                continue;
            }
            out.add(new ColumnHeader(i, headerLabel(match, i)));
        }
        return out;
    }

    /**
     * Index of a trailing "remainder" column that must keep separator chars inside one cell.
     * Named open-ended ({@code N+=label}) or the last finite column when there is no numbered {@code N+}.
     * Returns {@code -1} for numbered-only open schemas ({@code 0+}) that expand columns.
     */
    static int trailingRemainderColumn(CsvRule rule) {
        if (rule.columns().isEmpty()) {
            return -1;
        }
        boolean hasNumberedOpen = false;
        int namedOpenFrom = -1;
        int maxFinite = -1;
        for (CsvRule.ColumnSpec spec : rule.columns()) {
            if (spec.isOpenEnded()) {
                if (spec.numberedOnly()) {
                    hasNumberedOpen = true;
                } else if (namedOpenFrom < 0 || spec.from() < namedOpenFrom) {
                    namedOpenFrom = spec.from();
                }
            } else {
                maxFinite = Math.max(maxFinite, spec.toInclusiveOrNeg1());
            }
        }
        if (namedOpenFrom >= 0) {
            return namedOpenFrom;
        }
        if (hasNumberedOpen) {
            return -1;
        }
        return maxFinite;
    }

    private static List<String> splitRow(String row, CsvRule rule) {
        List<String> parts = splitRaw(row, rule.separator());
        int rem = trailingRemainderColumn(rule);
        if (rem < 0 || parts.size() <= rem + 1) {
            return parts;
        }
        List<String> out = new ArrayList<>(rem + 1);
        for (int i = 0; i < rem; i++) {
            out.add(i < parts.size() ? parts.get(i) : "");
        }
        StringBuilder tail = new StringBuilder();
        for (int i = rem; i < parts.size(); i++) {
            if (i > rem) {
                tail.append(rule.separator());
            }
            tail.append(parts.get(i));
        }
        out.add(tail.toString());
        return out;
    }

    private static List<String> splitRaw(String row, String sep) {
        List<String> parts = new ArrayList<>();
        if (sep == null || sep.isEmpty()) {
            parts.add(row);
            return parts;
        }
        int start = 0;
        int idx;
        while ((idx = row.indexOf(sep, start)) >= 0) {
            parts.add(row.substring(start, idx));
            start = idx + sep.length();
        }
        parts.add(row.substring(start));
        return parts;
    }

    private static String joinRow(List<String> cells, String sep) {
        StringBuilder sb = new StringBuilder();
        int lastNonEmpty = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) != null && !cells.get(i).isEmpty()) {
                lastNonEmpty = i;
            }
        }
        // Never emit a trailing separator: pad headers / empty trailing cells must not append SEP.
        int limit = lastNonEmpty + 1;
        if (limit <= 0) {
            return "";
        }
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(i < cells.size() && cells.get(i) != null ? cells.get(i) : "");
        }
        return sb.toString();
    }

    @Nullable
    private static CsvRule.ColumnSpec findSpec(CsvRule rule, int index) {
        for (CsvRule.ColumnSpec spec : rule.columns()) {
            if (spec.covers(index)) {
                return spec;
            }
        }
        return null;
    }

    private static String numberedLabel(int index) {
        return Component.translatable("screen.another_config_manager.config.csv.columns.numbered", index).getString();
    }

    private static String headerLabel(@Nullable CsvRule.ColumnSpec spec, int index) {
        if (spec == null || spec.numberedOnly() || spec.translationKey().isEmpty()) {
            return numberedLabel(index);
        }
        String key = spec.translationKey().get();
        if (Language.getInstance().has(key)) {
            return Component.translatable(key).getString();
        }
        return key;
    }

    private void rebuildList() {
        double scrollY = tableList != null ? tableList.scrollAmount() : 0;
        refreshHeaders(font != null);
        if (tableList != null) {
            tableList.replaceRows(filteredRows());
            tableList.setScrollAmount(scrollY);
            tableList.clampHorizontalScroll();
        }
        if (selectedRow >= table.size()) {
            selectedRow = table.isEmpty() ? -1 : table.size() - 1;
        }
        if (selectedRow >= 0 && selectedCol >= 0 && cellBox != null) {
            List<String> row = table.get(selectedRow);
            cellBox.setValue(selectedCol < row.size() && row.get(selectedCol) != null ? row.get(selectedCol) : "");
        }
    }

    private List<IndexedTableRow> filteredRows() {
        List<IndexedTableRow> out = new ArrayList<>();
        String needle = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < table.size(); i++) {
            List<String> row = table.get(i);
            if (!needle.isEmpty()) {
                boolean match = false;
                for (String cell : row) {
                    if (cell != null && cell.toLowerCase(Locale.ROOT).contains(needle)) {
                        match = true;
                        break;
                    }
                }
                if (!match) {
                    continue;
                }
            }
            out.add(new IndexedTableRow(i, row));
        }
        return out;
    }

    private void setCell() {
        if (cellBox == null || selectedRow < 0 || selectedCol < 0 || selectedRow >= table.size()) {
            return;
        }
        List<String> row = table.get(selectedRow);
        while (row.size() <= selectedCol) {
            row.add("");
        }
        row.set(selectedCol, cellBox.getValue());
        rebuildList();
    }

    private void pickColorForCell() {
        if (selectedRow < 0 || selectedCol < 0 || selectedRow >= table.size()) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.color.need_cell"));
            return;
        }
        CsvRule.ColumnSpec spec = findSpec(rule, selectedCol);
        ColorCodec.Spec colorSpec;
        if (spec != null && spec.isColorCell()) {
            colorSpec = ColorCodec.Spec.parseFormatSpec(spec.colorFormatSpec());
        } else {
            colorSpec = ColorCodec.Spec.parseFormatSpec("hex;rgb;prefix=none");
        }
        List<String> row = table.get(selectedRow);
        while (row.size() <= selectedCol) {
            row.add("");
        }
        String current = row.get(selectedCol);
        Integer packed = ColorCodec.decodeSingleValue(current, colorSpec);
        int argb = packed != null ? packed : 0xFFFFFFFF;
        if (packed == null) {
            Integer rgb = net.unfamily.anotherconfigmanager.client.gui.color.ColorMath.parseHexRgb(current);
            if (rgb != null) {
                argb = 0xFF000000 | rgb;
            }
        }
        final int rowIdx = selectedRow;
        final int colIdx = selectedCol;
        minecraft.setScreen(new ColorEditScreen(
                this,
                colorSpec,
                argb,
                encoded -> {
                    List<String> r = table.get(rowIdx);
                    while (r.size() <= colIdx) {
                        r.add("");
                    }
                    r.set(colIdx, encoded);
                    if (cellBox != null) {
                        cellBox.setValue(encoded);
                    }
                    rebuildList();
                },
                Component.translatable("screen.another_config_manager.config.color.badge")
        ));
    }

    private boolean isSelectedColorCell() {
        if (selectedCol < 0) {
            return false;
        }
        CsvRule.ColumnSpec spec = findSpec(rule, selectedCol);
        return spec != null && spec.isColorCell();
    }

    private void moveColumnRight() {
        if (!canMutateColumns() || selectedCol < 0) {
            return;
        }
        int from = selectedCol;
        int to = from + 1;
        int maxCols = 0;
        for (List<String> row : table) {
            maxCols = Math.max(maxCols, row.size());
        }
        if (to >= maxCols) {
            return;
        }
        for (List<String> row : table) {
            while (row.size() <= to) {
                row.add("");
            }
            String tmp = row.get(from);
            row.set(from, row.get(to));
            row.set(to, tmp);
        }
        selectedCol = to;
        refreshHeaders(true);
        rebuildList();
    }

    private void deleteColumnAtSelection() {
        if (!canMutateColumns() || selectedCol < 0) {
            return;
        }
        int at = selectedCol;
        for (List<String> row : table) {
            if (at < row.size()) {
                row.remove(at);
            }
        }
        selectedCol = Math.min(at, Math.max(0, headers.size() - 2));
        refreshHeaders(true);
        rebuildList();
    }

    private void addRow() {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < Math.max(1, headers.size()); i++) {
            row.add("");
        }
        table.add(row);
        selectedRow = table.size() - 1;
        selectedCol = headers.isEmpty() ? 0 : headers.get(0).index();
        rebuildList();
    }

    private void removeRow() {
        if (selectedRow < 0 || selectedRow >= table.size()) {
            return;
        }
        table.remove(selectedRow);
        selectedRow = -1;
        selectedCol = -1;
        rebuildList();
    }

    private void insertColumnAtSelection() {
        if (!canMutateColumns()) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
            return;
        }
        int at = selectedCol >= 0 ? selectedCol : headers.size();
        for (List<String> row : table) {
            while (row.size() < at) {
                row.add("");
            }
            row.add(at, "");
        }
        if (table.isEmpty()) {
            List<String> row = new ArrayList<>();
            for (int i = 0; i <= at; i++) {
                row.add("");
            }
            table.add(row);
        }
        selectedCol = at;
        refreshHeaders(true);
        rebuildList();
    }

    private void moveCellSameRow(int fromCol, int toCol) {
        if (selectedRow < 0 || selectedRow >= table.size() || fromCol == toCol || fromCol < 0 || toCol < 0) {
            return;
        }
        List<String> row = table.get(selectedRow);
        while (row.size() <= Math.max(fromCol, toCol)) {
            row.add("");
        }
        String tmp = row.get(fromCol);
        row.set(fromCol, row.get(toCol));
        row.set(toCol, tmp);
        selectedCol = toCol;
        rebuildList();
    }

    private boolean canMutateColumns() {
        if (bindingsEditor) {
            return true;
        }
        if (rule.closed()) {
            return false;
        }
        if (rule.columns().isEmpty()) {
            return true;
        }
        for (CsvRule.ColumnSpec spec : rule.columns()) {
            if (spec.isOpenEnded()) {
                return true;
            }
        }
        return false;
    }

    /** Flush current table into the session and open the plain string-list editor (CSV rule kept). */
    private void openRawListView() {
        List<String> joined = encodeTableRows();
        if (joined == null) {
            return;
        }
        if (!ConfigSpecNodes.trySetListBuffered(session, node.value(), joined)) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
            return;
        }
        parent.refreshAfterChildEdit();
        minecraft.setScreen(new ListEditScreen(parent, session, node, true));
    }

    private @Nullable List<String> encodeTableRows() {
        List<String> joined = new ArrayList<>();
        for (List<String> row : table) {
            if (bindingsEditor) {
                try {
                    joined.add(CsvBindingLine.fromTableCells(row));
                } catch (RuntimeException error) {
                    setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
                    return null;
                }
            } else if (colorManagerEditor) {
                try {
                    joined.add(ColorBindingLine.fromTableCells(row));
                } catch (RuntimeException error) {
                    setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
                    return null;
                }
            } else {
                joined.add(joinRow(row, rule.separator()));
            }
        }
        return joined;
    }

    private void setStatusMessage(Component message) {
        if (status == null) {
            return;
        }
        status.setMessage(message);
        int innerW = Math.max(120, width - CONTENT_MARGIN * 2);
        int innerX = CONTENT_MARGIN;
        int buttonsY = height - layout.getFooterHeight() - TOP_STACK_GAP - BUTTON_H;
        int statusY = buttonsY - 12 - LIST_GAP;
        int textW = font.width(message);
        int sw = Math.min(innerW, Math.max(8, textW));
        status.setWidth(sw);
        status.setHeight(9);
        status.setPosition(innerX + (innerW - sw) / 2, statusY);
    }

    private void apply() {
        List<String> joined = encodeTableRows();
        if (joined == null) {
            return;
        }
        if (ConfigSpecNodes.trySetListBuffered(session, node.value(), joined)) {
            parent.refreshAfterChildEdit();
            minecraft.setScreen(parent);
        } else {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
        }
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();

        int contentTop = layout.getHeaderHeight() + TOP_STACK_GAP;
        int contentBottom = height - layout.getFooterHeight() - TOP_STACK_GAP;
        int innerW = Math.max(120, width - CONTENT_MARGIN * 2);
        int innerX = CONTENT_MARGIN;

        int y = contentTop;
        if (searchBox != null) {
            int searchW = Math.min(360, innerW);
            searchBox.setWidth(searchW);
            searchBox.setPosition(innerX + (innerW - searchW) / 2, y);
            y += BUTTON_H + TOP_STACK_GAP;
        }

        if (headerBar != null) {
            headerBar.setPosition(innerX, y);
            headerBar.setSize(innerW, COL_HEADER_H);
            y += COL_HEADER_H + 4;
        }

        int buttonsY = contentBottom - BUTTON_H;
        if (buttonRow != null) {
            buttonRow.arrangeElements();
            int rowW = Math.min(buttonRow.getWidth(), innerW);
            buttonRow.setPosition(innerX + (innerW - rowW) / 2, buttonsY);
            buttonRow.arrangeElements();
        }

        int statusY = buttonsY - 12 - LIST_GAP;
        if (status != null) {
            int sw = Math.min(innerW, Math.max(40, font.width(status.getMessage())));
            status.setWidth(sw);
            status.setHeight(9);
            status.setPosition(innerX + (innerW - sw) / 2, statusY);
        }

        int editY = statusY - BUTTON_H - LIST_GAP;
        if (structureRow != null) {
            structureRow.arrangeElements();
            structureRow.setPosition(innerX, editY);
            structureRow.arrangeElements();
            editY -= BUTTON_H + 4;
        }
        if (editRow != null) {
            editRow.arrangeElements();
            editRow.setPosition(innerX, editY);
            editRow.arrangeElements();
        }

        int listTop = y;
        int listBottom = editY - LIST_GAP;
        int listH = Math.max(44, listBottom - listTop);
        if (tableList != null) {
            // Pass X so entry hitboxes reposition with the list (setX alone does not).
            tableList.updateSizeAndPosition(innerW, listH, innerX, listTop);
            tableList.clampHorizontalScroll();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private record ColumnHeader(int index, String label) {}

    private record IndexedTableRow(int sourceIndex, List<String> cells) {}

    private final class ColumnHeaderBar extends AbstractWidget {
        ColumnHeaderBar() {
            super(0, 0, 100, COL_HEADER_H, Component.empty());
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            if (tableList == null) {
                return;
            }
            int clipLeft = getX();
            int clipRight = getX() + getWidth();
            graphics.enableScissor(clipLeft, getY(), clipRight, getY() + getHeight());
            try {
                int x = tableList.contentOriginX() + INDEX_W;
                for (int i = 0; i < headers.size(); i++) {
                    int colW = i < columnWidths.length ? columnWidths[i] : COL_MIN;
                    String label = headers.get(i).label();
                    String shown = label;
                    if (font.width(shown) > colW) {
                        shown = font.plainSubstrByWidth(shown, Math.max(8, colW - font.width("…"))) + "…";
                    }
                    if (x + colW > clipLeft && x < clipRight) {
                        graphics.text(font, shown, x, getY() + 2, 0xFFFFD080);
                    }
                    x += colW + 4;
                }
            } finally {
                graphics.disableScissor();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    private final class TableList extends ObjectSelectionList<TableList.Entry> {
        private double horizontalScroll;
        private boolean draggingHScroll;

        TableList(Minecraft minecraft) {
            super(minecraft, CsvTableScreen.this.width, CsvTableScreen.this.height, 0, 22);
        }

        void replaceRows(List<IndexedTableRow> rows) {
            clearEntries();
            for (IndexedTableRow row : rows) {
                addEntry(new Entry(row.sourceIndex(), row.cells()));
            }
        }

        int contentOriginX() {
            return getX() + 2 - (int) horizontalScroll;
        }

        void clampHorizontalScroll() {
            horizontalScroll = Math.max(0, Math.min(horizontalScroll, maxHorizontalScroll()));
        }

        private int maxHorizontalScroll() {
            return Math.max(0, contentWidth() - Math.max(1, getWidth() - 8));
        }

        private boolean hasHorizontalScroll() {
            return maxHorizontalScroll() > 0;
        }

        private int hScrollBarY() {
            return getBottom() - H_SCROLLBAR_H - 1;
        }

        private int hScrollThumbX() {
            int trackW = getWidth() - 4;
            int max = maxHorizontalScroll();
            if (max <= 0) {
                return getX() + 2;
            }
            int thumbW = Math.max(16, (int) ((long) trackW * getWidth() / (getWidth() + max)));
            int travel = trackW - thumbW;
            return getX() + 2 + (int) (horizontalScroll * travel / max);
        }

        private int hScrollThumbW() {
            int trackW = getWidth() - 4;
            int max = maxHorizontalScroll();
            if (max <= 0) {
                return trackW;
            }
            return Math.max(16, (int) ((long) trackW * getWidth() / (getWidth() + max)));
        }

        @Override
        public int getRowWidth() {
            return Math.max(1, getWidth() - 8);
        }

        @Override
        public int getRowLeft() {
            return getX() + 2;
        }

        @Override
        protected int scrollBarX() {
            return getX() + getWidth() - 6;
        }

        @Override
        protected boolean entriesCanBeSelected() {
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return super.mouseScrolled(mouseX, mouseY, 0.0, scrollY);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (hasHorizontalScroll() && event.button() == 0) {
                double mouseX = event.x();
                double mouseY = event.y();
                int y = hScrollBarY();
                if (mouseY >= y && mouseY <= y + H_SCROLLBAR_H
                        && mouseX >= getX() && mouseX <= getX() + getWidth()) {
                    draggingHScroll = true;
                    int thumbW = hScrollThumbW();
                    int trackW = getWidth() - 4 - thumbW;
                    if (trackW > 0) {
                        double rel = (mouseX - getX() - 2 - thumbW / 2.0) / trackW;
                        horizontalScroll = Math.max(0, Math.min(maxHorizontalScroll(), rel * maxHorizontalScroll()));
                    }
                    return true;
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
            if (draggingHScroll && hasHorizontalScroll()) {
                double mouseX = event.x();
                int thumbW = hScrollThumbW();
                int trackW = getWidth() - 4 - thumbW;
                if (trackW > 0) {
                    double rel = (mouseX - getX() - 2 - thumbW / 2.0) / trackW;
                    horizontalScroll = Math.max(0, Math.min(maxHorizontalScroll(), rel * maxHorizontalScroll()));
                }
                return true;
            }
            return super.mouseDragged(event, dx, dy);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            draggingHScroll = false;
            return super.mouseReleased(event);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            if (!hasHorizontalScroll()) {
                return;
            }
            int y = hScrollBarY();
            graphics.fill(getX() + 2, y, getX() + getWidth() - 2, y + H_SCROLLBAR_H, 0x66000000);
            int tx = hScrollThumbX();
            int tw = hScrollThumbW();
            graphics.fill(tx, y, tx + tw, y + H_SCROLLBAR_H, 0xFFAAAAAA);
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final int rowIndex;
            private final List<String> cells;

            Entry(int rowIndex, List<String> cells) {
                this.rowIndex = rowIndex;
                this.cells = cells;
            }

            @Override
            public Component getNarration() {
                return Component.literal("#" + rowIndex);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovering, float a) {
                int clipLeft = TableList.this.getX() + 2;
                int clipRight = TableList.this.getX() + TableList.this.getWidth() - 2;
                int x = contentOriginX();
                int top = getContentY();
                int height = getContentHeight();
                int y = top + (height - 8) / 2;
                int rowTop = top + 1;
                int rowBottom = top + height - 1;
                String indexLabel = "#" + rowIndex;
                int indexColor = rowIndex == selectedRow ? 0xFFFFFF80 : 0xFFAAAAAA;
                if (x + INDEX_W > clipLeft && x < clipRight) {
                    graphics.text(font, indexLabel, x, y, indexColor);
                }
                x += INDEX_W;
                for (int i = 0; i < headers.size(); i++) {
                    int col = headers.get(i).index();
                    int colW = i < columnWidths.length ? columnWidths[i] : COL_MIN;
                    String cell = col < cells.size() ? cells.get(col) : "";
                    boolean selected = rowIndex == selectedRow && col == selectedCol;
                    if (selected && x + colW > clipLeft && x < clipRight) {
                        int fillL = Math.max(x - 1, clipLeft);
                        int fillR = Math.min(x + colW, clipRight);
                        if (fillR > fillL) {
                            graphics.fill(fillL, rowTop, fillR, rowBottom, 0x55FFFFFF);
                        }
                    }
                    String shown = cell;
                    if (font.width(shown) > colW) {
                        shown = font.plainSubstrByWidth(shown, Math.max(8, colW - font.width("…"))) + "…";
                    }
                    if (x + colW > clipLeft && x < clipRight) {
                        graphics.text(font, shown, x, y, selected ? 0xFFFFFFA0 : 0xFFFFFFFF);
                    }
                    x += colW + 4;
                }
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                selectedRow = rowIndex;
                int relative = (int) event.x() - contentOriginX() - INDEX_W;
                selectedCol = headers.isEmpty() ? 0 : headers.get(0).index();
                int x = 0;
                for (int i = 0; i < headers.size(); i++) {
                    int colW = i < columnWidths.length ? columnWidths[i] : COL_MIN;
                    if (relative >= x && relative < x + colW + 4) {
                        selectedCol = headers.get(i).index();
                        break;
                    }
                    x += colW + 4;
                    selectedCol = headers.get(i).index();
                }
                if (cellBox != null) {
                    String cell = selectedCol < cells.size() ? cells.get(selectedCol) : "";
                    cellBox.setValue(cell);
                }
                if (isSelectedColorCell()) {
                    minecraft.execute(CsvTableScreen.this::pickColorForCell);
                } else {
                    setStatusMessage(Component.empty());
                }
                return true;
            }
        }
    }
}
