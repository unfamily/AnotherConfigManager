package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.unfamily.anotherconfigmanager.client.gui.AcmButton;
import net.unfamily.anotherconfigmanager.client.gui.AcmUi;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
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
    private static final int COL_HEADER_H = 20;
    private static final int H_SCROLLBAR_H = 6;
    private static final int COL_PAD = 8;
    private static final int COL_MIN = 48;
    private static final int COL_MAX = 140;
    private static final int INDEX_W = 44;
    private static final int ROW_ITEM_H = 36;
    private static final int ROW_MAIN_H = 20;
    private static final int HDR_ICON = 12;
    private static final int HDR_ICON_STEP = HDR_ICON + 1;

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
    private @Nullable StringWidget paramSubtitle;
    private String searchQuery = "";
    private int selectedRow = -1;
    private int selectedCol = -1;

    public CsvTableScreen(
            ConfigOpenerScreen.SpecScreen parent,
            ConfigEditSession session,
            ConfigSpecNodes.ValueNode node,
            CsvRule rule
    ) {
        super(parent.stickyHeaderTitle());
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
        header.addChild(new StringWidget(parent.stickyHeaderTitle(), font));

        paramSubtitle = new StringWidget(Component.literal(node.displayName()), font);
        addRenderableWidget(paramSubtitle);

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
        editRow.addChild(AcmButton.text(72,
                Component.translatable("screen.another_config_manager.config.csv.set_cell"),
                this::setCell,
                Component.translatable("screen.another_config_manager.config.csv.set_cell.tooltip")));
        editRow.addChild(AcmButton.text(80,
                Component.translatable("screen.another_config_manager.config.csv.list_view"),
                this::openRawListView,
                Component.translatable("screen.another_config_manager.config.csv.list_view.tooltip")));
        editRow.addChild(AcmButton.text(56,
                Component.translatable("screen.another_config_manager.config.csv.pick_color_short"),
                this::pickColorForCell,
                Component.translatable("screen.another_config_manager.config.csv.pick_color.tooltip"))
                .activeWhen(this::isSelectedColorCell));
        editRow.visitWidgets(this::addRenderableWidget);

        status = new StringWidget(Component.empty(), font);
        addRenderableWidget(status);

        buttonRow = LinearLayout.horizontal().spacing(8);
        buttonRow.addChild(AcmButton.text(100,
                Component.translatable("screen.another_config_manager.config.apply"),
                this::apply,
                Component.translatable("screen.another_config_manager.config.apply.tooltip")));
        buttonRow.addChild(AcmButton.text(100,
                Component.translatable("screen.another_config_manager.config.back"), this::onClose));
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
        double scrollY = tableList != null ? tableList.getScrollAmount() : 0;
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

    private void swapColumns(int from, int to) {
        if (from == to || from < 0 || to < 0) {
            return;
        }
        for (List<String> row : table) {
            while (row.size() <= Math.max(from, to)) {
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

    private void moveColumnRight(int colIndex) {
        if (!canMutateColumns() || colIndex < 0) {
            return;
        }
        int to = colIndex + 1;
        int maxCols = 0;
        for (List<String> row : table) {
            maxCols = Math.max(maxCols, row.size());
        }
        if (to >= maxCols) {
            return;
        }
        swapColumns(colIndex, to);
    }

    private void moveColumnLeft(int colIndex) {
        if (!canMutateColumns() || colIndex <= 0) {
            return;
        }
        swapColumns(colIndex, colIndex - 1);
    }

    private void deleteColumnAt(int at) {
        if (!canMutateColumns() || at < 0) {
            return;
        }
        for (List<String> row : table) {
            if (at < row.size()) {
                row.remove(at);
            }
        }
        if (selectedCol == at) {
            selectedCol = Math.min(at, Math.max(0, headers.size() - 2));
        } else if (selectedCol > at) {
            selectedCol--;
        }
        refreshHeaders(true);
        rebuildList();
    }

    private void insertColumnAt(int at) {
        if (!canMutateColumns()) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
            return;
        }
        if (at < 0) {
            at = headers.isEmpty() ? 0 : headers.getLast().index() + 1;
        }
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

    private void insertColumnAfter(int colIndex) {
        insertColumnAt(colIndex + 1);
    }

    private void removeRowAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= table.size()) {
            return;
        }
        table.remove(rowIndex);
        if (selectedRow == rowIndex) {
            selectedRow = -1;
            selectedCol = -1;
        } else if (selectedRow > rowIndex) {
            selectedRow--;
        }
        rebuildList();
    }

    private void insertEmptyRowAfter(int rowIndex) {
        List<String> row = new ArrayList<>();
        for (int i = 0; i < Math.max(1, headers.size()); i++) {
            row.add("");
        }
        if (table.isEmpty()) {
            table.add(row);
            selectedRow = 0;
            selectedCol = headers.isEmpty() ? 0 : headers.get(0).index();
        } else {
            int insertAt = Math.min(rowIndex + 1, table.size());
            table.add(insertAt, row);
            if (selectedRow >= insertAt) {
                selectedRow++;
            }
        }
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
        if (paramSubtitle != null) {
            int pw = font.width(paramSubtitle.getMessage());
            paramSubtitle.setWidth(Math.min(pw, innerW));
            paramSubtitle.setHeight(9);
            paramSubtitle.setPosition(innerX + (innerW - paramSubtitle.getWidth()) / 2, y);
            y += 12 + TOP_STACK_GAP;
        }
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
        if (editRow != null) {
            editRow.arrangeElements();
            editRow.setPosition(innerX, editY);
            editRow.arrangeElements();
        }

        int listTop = y;
        int listBottom = editY - LIST_GAP;
        int listH = Math.max(44, listBottom - listTop);
        if (tableList != null) {
            tableList.setX(innerX);
            tableList.updateSizeAndPosition(innerW, listH, listTop);
            tableList.setX(innerX);
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

        private int leftClusterWidth() {
            return 3 * HDR_ICON_STEP - 1;
        }

        private int rightClusterWidth() {
            return leftClusterWidth();
        }

        private void drawIcon(GuiGraphics graphics, int x, int y, String glyph, int mouseX, int mouseY, boolean enabled) {
            if (!enabled) {
                return;
            }
            boolean hover = mouseX >= x && mouseX < x + HDR_ICON && mouseY >= y && mouseY < y + HDR_ICON;
            if (hover) {
                graphics.fill(x, y, x + HDR_ICON, y + HDR_ICON, 0x55FFFFFF);
            }
            int color = enabled ? 0xFFE8E8F0 : 0xFF777788;
            graphics.drawString(font, glyph, x + (HDR_ICON - font.width(glyph)) / 2, y + 2, color);
        }

        private void renderColumn(
                GuiGraphics graphics,
                int colVisual,
                int colX,
                int colW,
                int mouseX,
                int mouseY,
                int clipLeft,
                int clipRight
        ) {
            if (colX + colW <= clipLeft || colX >= clipRight) {
                return;
            }
            boolean mutate = canMutateColumns();
            boolean first = colVisual == 0;
            boolean last = colVisual == headers.size() - 1;
            int iconY = getY() + (COL_HEADER_H - HDR_ICON) / 2;
            int labelX = colX;
            int labelMax = colW;
            if (mutate) {
                int lc = leftClusterWidth();
                int rc = rightClusterWidth();
                drawIcon(graphics, colX, iconY, "+", mouseX, mouseY, true);
                drawIcon(graphics, colX + HDR_ICON_STEP, iconY, "←", mouseX, mouseY, !first);
                drawIcon(graphics, colX + 2 * HDR_ICON_STEP, iconY, "−", mouseX, mouseY, true);
                int rightX = colX + colW - rc;
                drawIcon(graphics, rightX, iconY, "−", mouseX, mouseY, true);
                drawIcon(graphics, rightX + HDR_ICON_STEP, iconY, "→", mouseX, mouseY, !last);
                drawIcon(graphics, rightX + 2 * HDR_ICON_STEP, iconY, "+", mouseX, mouseY, true);
                labelX = colX + lc + 2;
                labelMax = Math.max(8, colW - lc - rc - 4);
            }
            String label = headers.get(colVisual).label();
            String shown = label;
            if (font.width(shown) > labelMax) {
                shown = font.plainSubstrByWidth(shown, Math.max(8, labelMax - font.width("…"))) + "…";
            }
            graphics.drawString(font, shown, labelX, getY() + 6, 0xFFFFD080);
        }

        private @Nullable HeaderHit hitAt(double mouseX, double mouseY) {
            if (tableList == null || !canMutateColumns()) {
                return null;
            }
            int iconY = getY() + (COL_HEADER_H - HDR_ICON) / 2;
            if (mouseY < iconY || mouseY >= iconY + HDR_ICON) {
                return null;
            }
            int x = tableList.contentOriginX() + INDEX_W;
            for (int i = 0; i < headers.size(); i++) {
                int colW = i < columnWidths.length ? columnWidths[i] : COL_MIN;
                int colIndex = headers.get(i).index();
                boolean first = i == 0;
                boolean last = i == headers.size() - 1;
                int colX = x;
                int lc = leftClusterWidth();
                int rc = rightClusterWidth();
                if (mouseX >= colX && mouseX < colX + lc) {
                    int rel = (int) mouseX - colX;
                    int slot = rel / HDR_ICON_STEP;
                    return switch (slot) {
                        case 0 -> new HeaderHit(HeaderAction.INSERT_BEFORE, colIndex);
                        case 1 -> first ? null : new HeaderHit(HeaderAction.MOVE_LEFT, colIndex);
                        case 2 -> new HeaderHit(HeaderAction.DELETE, colIndex);
                        default -> null;
                    };
                }
                int rightX = colX + colW - rc;
                if (mouseX >= rightX && mouseX < colX + colW) {
                    int rel = (int) mouseX - rightX;
                    int slot = rel / HDR_ICON_STEP;
                    return switch (slot) {
                        case 0 -> new HeaderHit(HeaderAction.DELETE, colIndex);
                        case 1 -> last ? null : new HeaderHit(HeaderAction.MOVE_RIGHT, colIndex);
                        case 2 -> new HeaderHit(HeaderAction.INSERT_AFTER, colIndex);
                        default -> null;
                    };
                }
                x += colW + 4;
            }
            return null;
        }

        private void applyHit(HeaderHit hit) {
            AcmUi.playClick();
            selectedCol = hit.colIndex();
            switch (hit.action()) {
                case INSERT_BEFORE -> insertColumnAt(hit.colIndex());
                case INSERT_AFTER -> insertColumnAfter(hit.colIndex());
                case MOVE_LEFT -> moveColumnLeft(hit.colIndex());
                case MOVE_RIGHT -> moveColumnRight(hit.colIndex());
                case DELETE -> deleteColumnAt(hit.colIndex());
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }
            HeaderHit hit = hitAt(mouseX, mouseY);
            if (hit == null) {
                return false;
            }
            applyHit(hit);
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
                    renderColumn(graphics, i, x, colW, mouseX, mouseY, clipLeft, clipRight);
                    x += colW + 4;
                }
            } finally {
                graphics.disableScissor();
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }

    private enum HeaderAction {
        INSERT_BEFORE,
        INSERT_AFTER,
        MOVE_LEFT,
        MOVE_RIGHT,
        DELETE
    }

    private record HeaderHit(HeaderAction action, int colIndex) {}

    private final class TableList extends ObjectSelectionList<TableList.Entry> {
        private double horizontalScroll;
        private boolean draggingHScroll;

        TableList(Minecraft minecraft) {
            super(minecraft, CsvTableScreen.this.width, CsvTableScreen.this.height, 0, ROW_ITEM_H);
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
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            return super.mouseScrolled(mouseX, mouseY, 0.0, scrollY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (hasHorizontalScroll() && button == 0) {
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
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingHScroll && hasHorizontalScroll()) {
                int thumbW = hScrollThumbW();
                int trackW = getWidth() - 4 - thumbW;
                if (trackW > 0) {
                    double rel = (mouseX - getX() - 2 - thumbW / 2.0) / trackW;
                    horizontalScroll = Math.max(0, Math.min(maxHorizontalScroll(), rel * maxHorizontalScroll()));
                }
                return true;
            }
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            draggingHScroll = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
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
            private int layoutTop;
            private int layoutHeight;

            Entry(int rowIndex, List<String> cells) {
                this.rowIndex = rowIndex;
                this.cells = cells;
            }

            @Override
            public Component getNarration() {
                return Component.literal("#" + rowIndex);
            }

            @Override
            public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
                layoutTop = top;
                layoutHeight = height;
                int clipLeft = TableList.this.getX() + 2;
                int clipRight = TableList.this.getX() + TableList.this.getWidth() - 2;
                int x = contentOriginX();
                int rowHeight = layoutHeight;
                int mainY = top + (ROW_MAIN_H - 8) / 2;
                int rowTop = top + 1;
                int rowBottom = top + ROW_MAIN_H - 1;
                String indexLabel = "#" + rowIndex;
                int indexColor = rowIndex == selectedRow ? 0xFFFFFF80 : 0xFFAAAAAA;
                if (x + INDEX_W > clipLeft && x < clipRight) {
                    graphics.drawString(font, indexLabel, x + 2, mainY, indexColor);
                    int minusX = x + INDEX_W - HDR_ICON - 2;
                    boolean minusHover = mouseX >= minusX && mouseX < minusX + HDR_ICON
                            && mouseY >= rowTop && mouseY < rowBottom;
                    if (minusHover) {
                        graphics.fill(minusX, rowTop, minusX + HDR_ICON, rowBottom, 0x55FF8080);
                    }
                    graphics.drawString(font, "−", minusX + (HDR_ICON - font.width("−")) / 2, mainY, 0xFFE8E8F0);
                }
                int addY = top + ROW_MAIN_H + 2;
                int addH = rowHeight - ROW_MAIN_H - 2;
                if (addH > 6 && x + INDEX_W > clipLeft && x < clipRight) {
                    int plusX = x + (INDEX_W - HDR_ICON) / 2;
                    boolean plusHover = mouseX >= plusX && mouseX < plusX + HDR_ICON
                            && mouseY >= addY && mouseY < addY + addH;
                    if (plusHover) {
                        graphics.fill(plusX, addY, plusX + HDR_ICON, addY + addH, 0x5544FF44);
                    }
                    graphics.drawString(font, "+", plusX + (HDR_ICON - font.width("+")) / 2, addY + 1, 0xFF88FF88);
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
                        graphics.drawString(font, shown, x, mainY, selected ? 0xFFFFFFA0 : 0xFFFFFFFF);
                    }
                    x += colW + 4;
                }
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button != 0) {
                    return false;
                }
                int top = layoutTop;
                int rowHeight = layoutHeight > 0 ? layoutHeight : ROW_ITEM_H;
                int originX = contentOriginX();
                int rowTop = top + 1;
                int rowBottom = top + ROW_MAIN_H - 1;
                int minusX = originX + INDEX_W - HDR_ICON - 2;
                double mx = mouseX;
                double my = mouseY;
                if (mx >= minusX && mx < minusX + HDR_ICON && my >= rowTop && my < rowBottom) {
                    AcmUi.playClick();
                    removeRowAt(rowIndex);
                    return true;
                }
                int addY = top + ROW_MAIN_H + 2;
                int addH = rowHeight - ROW_MAIN_H - 2;
                int plusX = originX + (INDEX_W - HDR_ICON) / 2;
                if (addH > 6 && mx >= plusX && mx < plusX + HDR_ICON && my >= addY && my < addY + addH) {
                    AcmUi.playClick();
                    insertEmptyRowAfter(rowIndex);
                    return true;
                }
                selectedRow = rowIndex;
                int relative = (int) mouseX - contentOriginX() - INDEX_W;
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
