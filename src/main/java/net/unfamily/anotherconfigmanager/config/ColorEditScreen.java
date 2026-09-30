package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.unfamily.anotherconfigmanager.client.gui.AcmButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.unfamily.anotherconfigmanager.client.gui.color.HsvColorPickerPanel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * HSV color editor backed by Library {@link HsvColorPickerPanel} (MC 26).
 * Shows hex plus numeric R/G/B/(A) fields for direct channel editing.
 */
public class ColorEditScreen extends Screen {
    private static final int HEADER_H = 48;
    private static final int FOOTER_MARGIN = 14;
    private static final int TOP_STACK_GAP = 10;
    private static final int BUTTON_H = 20;
    private static final int CONTENT_MARGIN = 20;

    private final Screen parent;
    private final @Nullable ConfigEditSession session;
    private final ColorCodec.Spec spec;
    private final List<List<String>> configPaths;
    private final @Nullable Consumer<String> csvCellConsumer;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_H, FOOTER_MARGIN);

    private HsvColorPickerPanel picker;
    private EditBox hexEdit;
    private EditBox rEdit;
    private EditBox gEdit;
    private EditBox bEdit;
    private @Nullable EditBox aEdit;
    private @Nullable StringWidget status;
    private @Nullable LinearLayout buttonRow;
    private int initialArgb;
    private boolean syncingUi;

    public ColorEditScreen(
            Screen parent,
            ConfigEditSession session,
            ColorBindingLine.Parsed binding,
            Component title
    ) {
        super(title);
        this.parent = parent;
        this.session = session;
        this.spec = ColorCodec.Spec.fromBinding(binding);
        this.configPaths = new ArrayList<>();
        for (String p : binding.allPaths()) {
            this.configPaths.add(List.of(p.split("\\.")));
        }
        this.csvCellConsumer = null;
        this.initialArgb = readInitialArgb();
    }

    public ColorEditScreen(
            Screen parent,
            ColorCodec.Spec spec,
            int initialArgb,
            Consumer<String> onApply,
            Component title
    ) {
        super(title);
        this.parent = parent;
        this.session = null;
        this.spec = spec;
        this.configPaths = List.of();
        this.csvCellConsumer = onApply;
        this.initialArgb = initialArgb;
    }

    private int readInitialArgb() {
        if (session == null || configPaths.isEmpty()) {
            return 0xFFFFFFFF;
        }
        if ("paths".equalsIgnoreCase(spec.storage())) {
            List<Object> channels = new ArrayList<>();
            for (List<String> path : configPaths) {
                channels.add(session.getEffective(path));
            }
            Integer decoded = ColorCodec.decodeChannels(channels, spec);
            return decoded == null ? 0xFFFFFFFF : decoded;
        }
        Object raw = session.getEffective(configPaths.getFirst());
        Integer decoded = ColorCodec.decodeSingleValue(raw, spec);
        if (decoded != null) {
            return decoded;
        }
        if (raw instanceof String s) {
            Integer rgb = net.unfamily.anotherconfigmanager.client.gui.color.ColorMath.parseHexRgb(s);
            if (rgb != null) {
                return 0xFF000000 | rgb;
            }
        }
        return 0xFFFFFFFF;
    }

    @Override
    protected void init() {
        layout.setHeaderHeight(HEADER_H);
        layout.setFooterHeight(FOOTER_MARGIN);

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(0));
        header.defaultCellSetting().alignHorizontallyCenter();
        Component headerTitle = parent instanceof ConfigOpenerScreen.SpecScreen specScreen
                ? specScreen.stickyHeaderTitle()
                : title;
        header.addChild(new StringWidget(headerTitle, font));

        int pickerW = HsvColorPickerPanel.SV_SIZE + 8 + HsvColorPickerPanel.HUE_BAR_W + 8 + HsvColorPickerPanel.SWATCH_SIZE;
        int px = (width - pickerW) / 2;
        int py = layout.getHeaderHeight() + TOP_STACK_GAP + 52;
        picker = new HsvColorPickerPanel(px, py, initialArgb & 0xFFFFFF);

        hexEdit = HsvColorPickerPanel.createHexEditBox(font, 0, 0, 100, picker.getRgb());
        hexEdit.setResponder(text -> {
            if (syncingUi) {
                return;
            }
            picker.applyHexFromEditBox(hexEdit);
            syncChannelBoxesFromArgb(currentArgbFromPickerAndAlpha(), false);
        });
        addRenderableWidget(hexEdit);

        rEdit = channelBox("R", (initialArgb >>> 16) & 0xFF);
        gEdit = channelBox("G", (initialArgb >>> 8) & 0xFF);
        bEdit = channelBox("B", initialArgb & 0xFF);
        addRenderableWidget(rEdit);
        addRenderableWidget(gEdit);
        addRenderableWidget(bEdit);

        if (needsAlphaEditor()) {
            aEdit = channelBox("A", (initialArgb >>> 24) & 0xFF);
            addRenderableWidget(aEdit);
            picker.setShowAlphaBar(true);
            picker.setAlpha((initialArgb >>> 24) & 0xFF);
            picker.setOnAlphaChanged(a -> {
                if (syncingUi) {
                    return;
                }
                syncingUi = true;
                try {
                    writeChannelBox(aEdit, a);
                } finally {
                    syncingUi = false;
                }
                refreshStatus();
            });
        }

        picker.setOnChanged(rgb -> {
            if (syncingUi) {
                return;
            }
            syncingUi = true;
            try {
                picker.syncHexToEditBox(hexEdit);
                int argb = ((readChannel(aEdit, 0xFF) & 0xFF) << 24) | (rgb & 0xFFFFFF);
                writeChannelBox(rEdit, (argb >>> 16) & 0xFF);
                writeChannelBox(gEdit, (argb >>> 8) & 0xFF);
                writeChannelBox(bEdit, argb & 0xFF);
            } finally {
                syncingUi = false;
            }
            refreshStatus();
        });

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
        refreshStatus();
    }

    private EditBox channelBox(String label, int initial) {
        EditBox box = new EditBox(font, 0, 0, 40, 20, Component.literal(label));
        box.setMaxLength(3);
        box.setFilter(s -> s == null || s.isEmpty() || s.chars().allMatch(Character::isDigit));
        box.setValue(String.valueOf(initial));
        box.setHint(Component.literal(label));
        box.setResponder(text -> onChannelTextChanged());
        return box;
    }

    private void onChannelTextChanged() {
        if (syncingUi || picker == null) {
            return;
        }
        int argb = currentArgbFromChannelBoxes();
        syncingUi = true;
        try {
            picker.setRgb(argb & 0xFFFFFF);
            if (picker.isShowAlphaBar()) {
                picker.setAlpha((argb >>> 24) & 0xFF);
            }
            picker.syncHexToEditBox(hexEdit);
        } finally {
            syncingUi = false;
        }
        refreshStatus();
    }

    private void syncChannelBoxesFromArgb(int argb, boolean updatePicker) {
        syncingUi = true;
        try {
            writeChannelBox(rEdit, (argb >>> 16) & 0xFF);
            writeChannelBox(gEdit, (argb >>> 8) & 0xFF);
            writeChannelBox(bEdit, argb & 0xFF);
            if (aEdit != null) {
                writeChannelBox(aEdit, (argb >>> 24) & 0xFF);
            }
            if (updatePicker && picker != null) {
                picker.setRgb(argb & 0xFFFFFF);
                if (picker.isShowAlphaBar()) {
                    picker.setAlpha((argb >>> 24) & 0xFF);
                }
                picker.syncHexToEditBox(hexEdit);
            }
        } finally {
            syncingUi = false;
        }
        refreshStatus();
    }

    private static void writeChannelBox(@Nullable EditBox box, int value) {
        if (box == null) {
            return;
        }
        String next = String.valueOf(value);
        if (!next.equals(box.getValue())) {
            box.setValue(next);
        }
    }

    private int readChannel(@Nullable EditBox box, int fallback) {
        if (box == null) {
            return fallback;
        }
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(box.getValue().trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int currentArgbFromChannelBoxes() {
        int a = needsAlphaEditor() ? readChannel(aEdit, (initialArgb >>> 24) & 0xFF) : 0xFF;
        int r = readChannel(rEdit, (initialArgb >>> 16) & 0xFF);
        int g = readChannel(gEdit, (initialArgb >>> 8) & 0xFF);
        int b = readChannel(bEdit, initialArgb & 0xFF);
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private int currentArgbFromPickerAndAlpha() {
        int rgb = picker == null ? (initialArgb & 0xFFFFFF) : picker.getRgb();
        int a;
        if (needsAlphaEditor()) {
            a = picker != null && picker.isShowAlphaBar()
                    ? picker.getAlpha()
                    : readChannel(aEdit, (initialArgb >>> 24) & 0xFF);
        } else {
            a = 0xFF;
        }
        return ((a & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    private void refreshStatus() {
        if (status == null) {
            return;
        }
        int argb = currentArgbFromChannelBoxes();
        if (needsAlphaEditor()) {
            status.setMessage(Component.literal(String.format(
                    "A=%d  R=%d  G=%d  B=%d",
                    (argb >>> 24) & 0xFF,
                    (argb >>> 16) & 0xFF,
                    (argb >>> 8) & 0xFF,
                    argb & 0xFF
            )));
        } else {
            status.setMessage(Component.literal(String.format(
                    "R=%d  G=%d  B=%d",
                    (argb >>> 16) & 0xFF,
                    (argb >>> 8) & 0xFF,
                    argb & 0xFF
            )));
        }
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        int innerW = Math.max(80, width - CONTENT_MARGIN * 2);
        int innerX = CONTENT_MARGIN;
        int buttonsY = height - layout.getFooterHeight() - TOP_STACK_GAP - BUTTON_H;
        if (buttonRow != null) {
            buttonRow.arrangeElements();
            int rowW = Math.min(buttonRow.getWidth(), innerW);
            buttonRow.setPosition(innerX + (innerW - rowW) / 2, buttonsY);
            buttonRow.arrangeElements();
        }
        int statusY = buttonsY - 14;
        if (status != null) {
            int sw = Math.min(innerW, Math.max(40, font.width(status.getMessage())));
            status.setWidth(sw);
            status.setPosition(innerX + (innerW - sw) / 2, statusY);
        }
        int pickerW = picker == null ? 140 : picker.preferredWidth();
        int px = (width - pickerW) / 2;
        int py = layout.getHeaderHeight() + TOP_STACK_GAP + 52;
        if (picker != null) {
            picker.setPosition(px, py);
        }
        int topY = py - 48;
        if (hexEdit != null) {
            hexEdit.setWidth(100);
            hexEdit.setPosition(px, topY);
        }
        int chY = topY + 24;
        int x = px;
        x = placeChannel(rEdit, x, chY);
        x = placeChannel(gEdit, x + 6, chY);
        x = placeChannel(bEdit, x + 6, chY);
        if (aEdit != null) {
            placeChannel(aEdit, x + 6, chY);
        }
    }

    private static int placeChannel(@Nullable EditBox box, int x, int y) {
        if (box == null) {
            return x;
        }
        box.setWidth(40);
        box.setPosition(x, y);
        return x + 40;
    }

    private boolean needsAlphaEditor() {
        String order = ColorCodec.normalizeChannelOrder(spec.order());
        return order.indexOf('a') >= 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        if (picker != null) {
            picker.render(graphics);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (picker != null && picker.mouseClicked(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (picker != null && picker.mouseDragged(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (picker != null) {
            picker.mouseReleased(event.x(), event.y(), event.button());
        }
        return super.mouseReleased(event);
    }

    private void apply() {
        if (picker == null) {
            return;
        }
        int argb = currentArgbFromChannelBoxes();
        syncingUi = true;
        try {
            picker.setRgb(argb & 0xFFFFFF);
            picker.syncHexToEditBox(hexEdit);
        } finally {
            syncingUi = false;
        }

        if (csvCellConsumer != null) {
            csvCellConsumer.accept(ColorCodec.encodeHex(argb, spec));
            minecraft.setScreen(parent);
            return;
        }
        if (session == null || configPaths.isEmpty()) {
            onClose();
            return;
        }
        if ("paths".equalsIgnoreCase(spec.storage())) {
            List<Integer> encoded = ColorCodec.encodeChannels(argb, spec);
            for (int i = 0; i < configPaths.size() && i < encoded.size(); i++) {
                List<String> path = configPaths.get(i);
                ModConfigSpec.ConfigValue<?> value = session.valueAt(path);
                Object typed = value == null
                        ? encoded.get(i)
                        : ConfigSpecNodes.coerceChannelValue(value, encoded.get(i));
                session.setPending(path, typed);
            }
        } else {
            Object encoded = ColorCodec.encodeForStorage(argb, spec);
            session.setPending(configPaths.getFirst(), encoded);
        }
        if (parent instanceof ConfigOpenerScreen.SpecScreen specScreen) {
            specScreen.refreshAfterChildEdit();
        }
        minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
