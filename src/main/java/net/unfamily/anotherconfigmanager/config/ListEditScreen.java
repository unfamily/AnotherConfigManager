package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.unfamily.anotherconfigmanager.client.gui.AcmButton;
import net.unfamily.anotherconfigmanager.client.gui.AcmUi;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Editor for {@link ConfigSpecNodes.ValueKind#LIST} config values.
 * String lists without a CSV rule can declare CSV editing (persisted to Library toml).
 * With an existing CSV rule, opens the table by default; {@code preferRawList} keeps this list view
 * and offers a button to return to CSV.
 */
public class ListEditScreen extends Screen {
    private static final int HEADER_H = 48;
    private static final int FOOTER_MARGIN = 14;
    private static final int TOP_STACK_GAP = 10;
    private static final int LIST_GAP = 12;
    private static final int BUTTON_H = 20;
    private static final int CONTENT_MARGIN = 20;
    private static final int INDEX_W = 44;
    private static final int ROW_ITEM_H = 36;
    private static final int ROW_MAIN_H = 20;
    private static final int HDR_ICON = 12;

    private final ConfigOpenerScreen.SpecScreen parent;
    private final ConfigEditSession session;
    private final ConfigSpecNodes.ValueNode node;
    private final Class<?> elementType;
    private final boolean stringList;
    /** When true, do not auto-open CSV even if a rule exists (temporary raw string list). */
    private final boolean preferRawList;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_H, FOOTER_MARGIN);
    private final List<Object> rows = new ArrayList<>();
    private @Nullable RowList rowList;
    private @Nullable EditBox searchBox;
    private @Nullable EditBox separatorBox;
    private @Nullable EditBox valueBox;
    private @Nullable StringWidget status;
    private @Nullable StringWidget paramSubtitle;
    private @Nullable StringWidget sepLabel;
    private @Nullable AcmButton enableCsvButton;
    private @Nullable LinearLayout buttonRow;
    private @Nullable LinearLayout editRow;
    private String searchQuery = "";
    private boolean showEnableCsv;
    private boolean showCsvView;

    public ListEditScreen(ConfigOpenerScreen.SpecScreen parent, ConfigEditSession session, ConfigSpecNodes.ValueNode node) {
        this(parent, session, node, false);
    }

    public ListEditScreen(
            ConfigOpenerScreen.SpecScreen parent,
            ConfigEditSession session,
            ConfigSpecNodes.ValueNode node,
            boolean preferRawList
    ) {
        super(parent.stickyHeaderTitle());
        this.parent = parent;
        this.session = session;
        this.node = node;
        this.preferRawList = preferRawList;
        this.elementType = ConfigSpecNodes.listElementType(node.value());
        this.stringList = ConfigSpecNodes.isStringList(node.value());
        Object effective = session.getEffective(node.value());
        if (effective instanceof List<?> list) {
            rows.addAll(list);
        }
    }

    @Override
    protected void init() {
        CsvRule existing = CsvRuleRegistry.resolve(parent.modId(), parent.configType(), node.path(), node.value());
        if (stringList && existing != null && !preferRawList) {
            // Defer navigation: setScreen during init is unsafe.
            minecraft.execute(() -> minecraft.setScreen(new CsvTableScreen(parent, session, node, existing)));
            return;
        }
        showCsvView = stringList && existing != null;
        showEnableCsv = stringList && existing == null;

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
            rebuildRows();
        });
        addRenderableWidget(searchBox);

        if (showEnableCsv) {
            sepLabel = new StringWidget(Component.translatable("screen.another_config_manager.config.csv.separator"), font);
            addRenderableWidget(sepLabel);
            separatorBox = new EditBox(font, 0, 0, 48, 20, Component.translatable("screen.another_config_manager.config.csv.separator"));
            separatorBox.setMaxLength(CsvBindingLine.MAX_SEPARATOR_LENGTH);
            separatorBox.setValue(CsvBindingLine.DEFAULT_SEPARATOR);
            addRenderableWidget(separatorBox);
            enableCsvButton = AcmButton.text(110,
                    Component.translatable("screen.another_config_manager.config.csv.enable"),
                    this::confirmSeparator);
            addRenderableWidget(enableCsvButton);
        }

        rowList = new RowList(minecraft);
        addRenderableWidget(rowList);
        rebuildRows();

        editRow = LinearLayout.horizontal().spacing(6);
        valueBox = editRow.addChild(new EditBox(font, 0, 0, 220, 20, Component.translatable("screen.another_config_manager.config.list.value")));
        valueBox.setMaxLength(1024);
        editRow.addChild(AcmButton.text(56,
                Component.translatable("screen.another_config_manager.config.list.set"),
                this::setSelectedFromBox,
                Component.translatable("screen.another_config_manager.config.list.set.tooltip")));
        if (showCsvView) {
            editRow.addChild(AcmButton.text(100,
                    Component.translatable("screen.another_config_manager.config.csv.csv_view"),
                    this::openCsvView,
                    Component.translatable("screen.another_config_manager.config.csv.csv_view.tooltip")));
        }
        editRow.visitWidgets(this::addRenderableWidget);

        status = new StringWidget(Component.empty(), font);
        addRenderableWidget(status);

        buttonRow = LinearLayout.horizontal().spacing(8);
        buttonRow.addChild(AcmButton.text(100,
                Component.translatable("screen.another_config_manager.config.apply"),
                this::apply));
        buttonRow.addChild(AcmButton.text(100,
                Component.translatable("screen.another_config_manager.config.back"), this::onClose));
        buttonRow.visitWidgets(this::addRenderableWidget);

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    private void confirmSeparator() {
        if (separatorBox == null) {
            return;
        }
        String text = separatorBox.getValue();
        if (text == null || text.isEmpty() || text.length() > CsvBindingLine.MAX_SEPARATOR_LENGTH) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.csv.need_sep"));
            return;
        }
        if (!ConfigSpecNodes.trySetListBuffered(session, node.value(), rows)) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
            return;
        }
        CsvRule rule = CsvRuleRegistry.generateMinimalRule(parent.modId(), parent.configType(), node.path(), text);
        parent.refreshAfterChildEdit();
        minecraft.setScreen(new CsvTableScreen(parent, session, node, rule));
    }

    private void openCsvView() {
        CsvRule rule = CsvRuleRegistry.resolve(parent.modId(), parent.configType(), node.path(), node.value());
        if (rule == null) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
            return;
        }
        if (!ConfigSpecNodes.trySetListBuffered(session, node.value(), rows)) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
            return;
        }
        parent.refreshAfterChildEdit();
        minecraft.setScreen(new CsvTableScreen(parent, session, node, rule));
    }

    private void rebuildRows() {
        if (rowList == null) {
            return;
        }
        rowList.replaceRows(filteredRows());
        rowList.setScrollAmount(0);
    }

    private List<IndexedRow> filteredRows() {
        List<IndexedRow> out = new ArrayList<>();
        String needle = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < rows.size(); i++) {
            Object value = rows.get(i);
            if (!needle.isEmpty() && !String.valueOf(value).toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            out.add(new IndexedRow(i, value));
        }
        return out;
    }

    private void setSelectedFromBox() {
        if (rowList == null || valueBox == null) {
            return;
        }
        RowList.Entry selected = rowList.getSelected();
        if (selected == null) {
            return;
        }
        try {
            rows.set(selected.sourceIndex, parseElement(valueBox.getValue()));
            rebuildRows();
            setStatusMessage(Component.empty());
        } catch (RuntimeException error) {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
        }
    }

    private void removeRowAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= rows.size()) {
            return;
        }
        rows.remove(rowIndex);
        rebuildRows();
    }

    private void insertEmptyRowAfter(int rowIndex) {
        Object empty;
        try {
            empty = stringList ? "" : parseElement("0");
        } catch (RuntimeException error) {
            empty = "";
        }
        if (rows.isEmpty()) {
            rows.add(empty);
        } else {
            rows.add(Math.min(rowIndex + 1, rows.size()), empty);
        }
        rebuildRows();
    }

    private Object parseElement(String text) {
        if (elementType == Integer.class) {
            return Integer.parseInt(text.trim());
        }
        if (elementType == Long.class) {
            return Long.parseLong(text.trim());
        }
        if (elementType == Double.class) {
            return Double.parseDouble(text.trim());
        }
        return text;
    }

    private void apply() {
        if (ConfigSpecNodes.trySetListBuffered(session, node.value(), rows)) {
            parent.refreshAfterChildEdit();
            minecraft.setScreen(parent);
        } else {
            setStatusMessage(Component.translatable("screen.another_config_manager.config.invalid"));
        }
    }

    private void setStatusMessage(Component message) {
        if (status == null) {
            return;
        }
        status.setMessage(message);
        layoutStatus();
    }

    private void layoutStatus() {
        if (status == null) {
            return;
        }
        int innerW = Math.max(120, width - CONTENT_MARGIN * 2);
        int innerX = CONTENT_MARGIN;
        int contentBottom = height - layout.getFooterHeight() - TOP_STACK_GAP;
        int buttonsY = contentBottom - BUTTON_H;
        int statusY = buttonsY - 12 - LIST_GAP;
        int textW = font.width(status.getMessage());
        int sw = Math.min(innerW, Math.max(8, textW));
        status.setWidth(sw);
        status.setHeight(9);
        status.setPosition(innerX + (innerW - sw) / 2, statusY);
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

        if (showEnableCsv && separatorBox != null && sepLabel != null && enableCsvButton != null) {
            int labelW = font.width(sepLabel.getMessage());
            int sepW = 48;
            sepLabel.setWidth(labelW);
            sepLabel.setHeight(9);
            int rowW = Math.min(innerW, labelW + 6 + sepW + 6 + 110);
            int rowX = innerX + (innerW - rowW) / 2;
            sepLabel.setPosition(rowX, y + 5);
            separatorBox.setWidth(sepW);
            separatorBox.setPosition(rowX + labelW + 6, y);
            enableCsvButton.setPosition(rowX + labelW + 6 + sepW + 6, y);
            y += BUTTON_H + TOP_STACK_GAP;
        }

        int buttonsY = contentBottom - BUTTON_H;
        if (buttonRow != null) {
            buttonRow.arrangeElements();
            int rowW = Math.min(buttonRow.getWidth(), innerW);
            buttonRow.setPosition(innerX + (innerW - rowW) / 2, buttonsY);
            buttonRow.arrangeElements();
        }

        layoutStatus();
        int statusY = buttonsY - 12 - LIST_GAP;

        int editY = statusY - BUTTON_H - LIST_GAP;
        if (editRow != null) {
            editRow.arrangeElements();
            int rowW = Math.min(editRow.getWidth(), innerW);
            editRow.setPosition(innerX + (innerW - rowW) / 2, editY);
            editRow.arrangeElements();
        }

        int listTop = y;
        int listBottom = editY - LIST_GAP;
        int listH = Math.max(44, listBottom - listTop);
        if (rowList != null) {
            rowList.updateSizeAndPosition(innerW, listH, listTop);
            rowList.setX(innerX);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private record IndexedRow(int sourceIndex, Object value) {}

    private final class RowList extends ObjectSelectionList<RowList.Entry> {
        RowList(Minecraft minecraft) {
            super(minecraft, ListEditScreen.this.width, ListEditScreen.this.height, 0, ROW_ITEM_H);
        }

        void replaceRows(List<IndexedRow> values) {
            clearEntries();
            for (IndexedRow row : values) {
                addEntry(new Entry(row.sourceIndex(), row.value()));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(400, width - 40);
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final int sourceIndex;
            private final Object value;

            Entry(int sourceIndex, Object value) {
                this.sourceIndex = sourceIndex;
                this.value = value;
            }

            @Override
            public Component getNarration() {
                return Component.literal("#" + sourceIndex);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovering, float a) {
                int top = getContentY();
                int height = getContentHeight();
                int mainY = top + (ROW_MAIN_H - 8) / 2;
                int rowTop = top + 1;
                int rowBottom = top + ROW_MAIN_H - 1;
                int x = getContentX();
                String indexLabel = "#" + sourceIndex;
                graphics.text(font, indexLabel, x + 2, mainY, 0xFFAAAAAA);
                int minusX = x + INDEX_W - HDR_ICON - 2;
                boolean minusHover = mouseX >= minusX && mouseX < minusX + HDR_ICON
                        && mouseY >= rowTop && mouseY < rowBottom;
                if (minusHover) {
                    graphics.fill(minusX, rowTop, minusX + HDR_ICON, rowBottom, 0x55FF8080);
                }
                graphics.text(font, "−", minusX + (HDR_ICON - font.width("−")) / 2, mainY, 0xFFE8E8F0);

                int addY = top + ROW_MAIN_H + 2;
                int addH = height - ROW_MAIN_H - 2;
                int plusX = x + (INDEX_W - HDR_ICON) / 2;
                if (addH > 6) {
                    boolean plusHover = mouseX >= plusX && mouseX < plusX + HDR_ICON
                            && mouseY >= addY && mouseY < addY + addH;
                    if (plusHover) {
                        graphics.fill(plusX, addY, plusX + HDR_ICON, addY + addH, 0x5544FF44);
                    }
                    graphics.text(font, "+", plusX + (HDR_ICON - font.width("+")) / 2, addY + 1, 0xFF88FF88);
                }

                String text = String.valueOf(value);
                if (font.width(text) > getRowWidth() - INDEX_W - 8) {
                    text = font.plainSubstrByWidth(text, Math.max(8, getRowWidth() - INDEX_W - font.width("…"))) + "…";
                }
                graphics.text(font, text, x + INDEX_W + 4, mainY, 0xFFFFFFFF);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() != 0) {
                    return false;
                }
                int top = getContentY();
                int height = getContentHeight();
                int x = getContentX();
                int rowTop = top + 1;
                int rowBottom = top + ROW_MAIN_H - 1;
                int minusX = x + INDEX_W - HDR_ICON - 2;
                double mx = event.x();
                double my = event.y();
                if (mx >= minusX && mx < minusX + HDR_ICON && my >= rowTop && my < rowBottom) {
                    AcmUi.playClick();
                    removeRowAt(sourceIndex);
                    return true;
                }
                int addY = top + ROW_MAIN_H + 2;
                int addH = height - ROW_MAIN_H - 2;
                int plusX = x + (INDEX_W - HDR_ICON) / 2;
                if (addH > 6 && mx >= plusX && mx < plusX + HDR_ICON && my >= addY && my < addY + addH) {
                    AcmUi.playClick();
                    insertEmptyRowAfter(sourceIndex);
                    return true;
                }
                RowList.this.setSelected(this);
                if (valueBox != null) {
                    valueBox.setValue(String.valueOf(value));
                }
                return true;
            }
        }
    }
}
