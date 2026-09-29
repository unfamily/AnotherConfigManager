package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Client screen that navigates ModConfigSpec trees and edits values via a dirty session.
 */
public class ConfigOpenerScreen extends Screen {
    private final @Nullable Screen lastScreen;
    private final String modId;
    private final List<ModConfig> configs;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private @Nullable TypeList typeList;

    public ConfigOpenerScreen(@Nullable Screen lastScreen, String modId, List<ModConfig> configs) {
        super(Component.translatable("screen.another_config_manager.config.title", ConfigDisplayNames.modTitle(modId)));
        this.lastScreen = lastScreen;
        this.modId = modId;
        this.configs = List.copyOf(configs);
    }

    @Override
    protected void init() {
        layout.setHeaderHeight(48);
        layout.setFooterHeight(36);
        layout.addTitleHeader(title, font);

        typeList = layout.addToContents(new TypeList(minecraft));
        for (ModConfig config : configs) {
            typeList.addConfig(config);
        }

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(
                Component.translatable("screen.another_config_manager.config.mods_browser"),
                button -> minecraft.setScreen(new ConfigModBrowserScreen(this))
        ).width(100).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).width(100).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (typeList != null) {
            typeList.updateSize(width, layout);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(lastScreen);
    }

    private void openConfig(ModConfig config) {
        if (!ConfigAccessPolicy.canOpenType(config.getType())) {
            return;
        }
        if (!(config.getSpec() instanceof ModConfigSpec spec) || spec.isEmpty()) {
            return;
        }
        boolean editable = ConfigAccessPolicy.canEdit(config.getType());
        ConfigEditSession session = new ConfigEditSession(spec);
        minecraft.setScreen(new SpecScreen(this, this, modId, config, session, List.of(), editable));
    }

    private final class TypeList extends ObjectSelectionList<TypeList.Entry> {
        TypeList(Minecraft minecraft) {
            super(minecraft, ConfigOpenerScreen.this.width, ConfigOpenerScreen.this.height, 0, 28);
        }

        void addConfig(ModConfig config) {
            addEntry(new Entry(config));
        }

        @Override
        public int getRowWidth() {
            return Math.min(400, width - 40);
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final ModConfig config;

            Entry(ModConfig config) {
                this.config = config;
            }

            @Override
            public Component getNarration() {
                return Component.literal(ConfigSpecNodes.typeLabel(config.getType()));
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovering, float a) {
                boolean allowed = ConfigAccessPolicy.canOpenType(config.getType());
                if (hovering && allowed) {
                    graphics.fill(getContentX() - 2, getContentY(), getContentX() + getRowWidth(), getContentY() + 26, 0x22FFFFFF);
                }
                String type = config.getType().name();
                String file = config.getFileName();
                graphics.text(font, type, getContentX(), getContentY() + 4, allowed ? 0xFFFFFFFF : 0xFF888888);
                graphics.text(font, file, getContentX(), getContentY() + 15, allowed ? 0xFFAAAAAA : 0xFF666666);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (ConfigAccessPolicy.canOpenType(config.getType())) {
                    openConfig(config);
                }
                return true;
            }
        }
    }

    /**
     * Spec section / value browser nested under {@link ConfigOpenerScreen}.
     */
    public static class SpecScreen extends Screen {
        private static final int ROW_HEIGHT = 36;
        private static final int TOGGLE_WIDTH = 40;
        private static final int TOGGLE_HEIGHT = 16;
        private static final int HEADER_H = 48;
        private static final int FOOTER_MARGIN = 14;
        private static final int TOP_STACK_GAP = 10;
        private static final int LIST_GAP = 12;
        private static final int BUTTON_H = 20;

        private final @Nullable Screen lastScreen;
        private final ConfigOpenerScreen typesScreen;
        private final String modId;
        private final ModConfig config;
        private final ConfigEditSession session;
        private final List<String> sectionPath;
        private final boolean editable;
        private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_H, FOOTER_MARGIN);
        private @Nullable NodeList nodeList;
        private @Nullable EditBox searchBox;
        private @Nullable StringWidget modTitleWidget;
        private @Nullable StringWidget pathWidget;
        private @Nullable LinearLayout buttonRow;
        private @Nullable Button saveButton;
        private @Nullable Button undoButton;
        private @Nullable Button channelSelectButton;
        private String searchQuery = "";
        /** Shared across SpecScreen navigations while picking channels. */
        private ChannelPickState channelPick = new ChannelPickState();

        private static final class ChannelPickState {
            boolean active;
            @Nullable ConfigSpecNodes.ValueKind kind;
            final java.util.LinkedHashSet<List<String>> paths = new java.util.LinkedHashSet<>();
        }

        public SpecScreen(
                ConfigOpenerScreen typesScreen,
                @Nullable Screen lastScreen,
                String modId,
                ModConfig config,
                ConfigEditSession session,
                List<String> sectionPath,
                boolean editable
        ) {
            super(ConfigDisplayNames.modTitle(modId));
            this.typesScreen = typesScreen;
            this.lastScreen = lastScreen;
            this.modId = modId;
            this.config = config;
            this.session = session;
            this.sectionPath = List.copyOf(sectionPath);
            this.editable = editable;
        }

        public String modId() {
            return modId;
        }

        public ModConfig.Type configType() {
            return config.getType();
        }

        public ConfigEditSession session() {
            return session;
        }

        private Component modHeaderTitle() {
            Component name = ConfigDisplayNames.modTitle(modId);
            if (session.isDirty()) {
                return name.copy().append(Component.literal(" *"));
            }
            return name;
        }

        private Component pathBreadcrumb() {
            StringBuilder sb = new StringBuilder(ConfigSpecNodes.typeLabel(config.getType()));
            if (!sectionPath.isEmpty()) {
                for (String part : sectionPath) {
                    sb.append(" › ").append(ConfigDisplayNames.formatRawKey(part));
                }
            }
            return Component.literal(sb.toString());
        }

        @Override
        protected void init() {
            layout.setHeaderHeight(HEADER_H);
            layout.setFooterHeight(FOOTER_MARGIN);

            LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(0));
            header.defaultCellSetting().alignHorizontallyCenter();
            modTitleWidget = new StringWidget(modHeaderTitle(), font);
            header.addChild(modTitleWidget);

            // Internal block widgets (path / search / buttons) — positioned in repositionElements
            pathWidget = new StringWidget(pathBreadcrumb(), font);
            addRenderableWidget(pathWidget);

            int searchWidth = Math.min(360, Math.max(200, width - 48));
            searchBox = new EditBox(font, 0, 0, searchWidth, 20, Component.translatable("screen.another_config_manager.config.search"));
            searchBox.setHint(Component.translatable("screen.another_config_manager.config.search"));
            searchBox.setValue(searchQuery);
            searchBox.setResponder(value -> {
                searchQuery = value == null ? "" : value;
                rebuildNodeList();
            });
            addRenderableWidget(searchBox);

            nodeList = new NodeList(minecraft);
            addRenderableWidget(nodeList);
            rebuildNodeList();

            buttonRow = LinearLayout.horizontal().spacing(6);
            saveButton = buttonRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.save"), button -> {
                session.commit();
                refreshInPlace();
            }).width(68).tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.save.tooltip"))).build());
            undoButton = buttonRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.undo"), button -> {
                session.undo();
                refreshInPlace();
            }).width(68).tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.undo.tooltip"))).build());
            buttonRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.reset_all"), button -> {
                if (editable) {
                    session.resetAll();
                    refreshInPlace();
                }
            }).width(80).tooltip(Tooltip.create(Component.translatable("screen.another_config_manager.config.reset_all.tooltip"))).build());
            channelSelectButton = buttonRow.addChild(Button.builder(
                    Component.translatable("screen.another_config_manager.config.color.select_channels_short"),
                    button -> toggleChannelSelectMode()
            ).width(100).tooltip(Tooltip.create(Component.translatable(
                    "screen.another_config_manager.config.color.select_channels.tooltip"
            ))).build());
            buttonRow.addChild(Button.builder(CommonComponents.GUI_BACK, button -> requestClose()).width(72).build());
            buttonRow.addChild(Button.builder(CommonComponents.GUI_DONE, button -> requestDone()).width(72).build());
            buttonRow.visitWidgets(this::addRenderableWidget);

            layout.visitWidgets(this::addRenderableWidget);
            repositionElements();
            updateActionButtons();
            updateChannelSelectButton();
        }

        private void rebuildNodeList() {
            if (nodeList == null) {
                return;
            }
            nodeList.clearAll();
            List<ConfigSpecNodes.Node> children = ConfigSpecNodes.childrenAt(session.spec(), sectionPath, modId);
            for (ConfigSpecNodes.Node node : searchNodes(children, searchQuery)) {
                if (channelPick.active && !isVisibleInChannelSelect(node)) {
                    continue;
                }
                nodeList.addNode(node);
            }
            nodeList.setScrollAmount(0);
        }

        /** Values/sections shown while picking RGB/A channels. */
        private boolean isVisibleInChannelSelect(ConfigSpecNodes.Node node) {
            if (node instanceof ConfigSpecNodes.SectionNode section) {
                return sectionHasChannelCandidate(section);
            }
            if (node instanceof ConfigSpecNodes.ValueNode value) {
                return isChannelSelectCandidate(value);
            }
            return false;
        }

        private boolean sectionHasChannelCandidate(ConfigSpecNodes.SectionNode section) {
            for (ConfigSpecNodes.Node child : section.children()) {
                if (child instanceof ConfigSpecNodes.ValueNode value && isChannelSelectCandidate(value)) {
                    return true;
                }
                if (child instanceof ConfigSpecNodes.SectionNode nested && sectionHasChannelCandidate(nested)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isChannelSelectCandidate(ConfigSpecNodes.ValueNode value) {
            if (!value.editable()) {
                return false;
            }
            if (value.kind() == ConfigSpecNodes.ValueKind.LIST) {
                return false;
            }
            if (ColorRuleRegistry.hasRule(modId, config.getType(), value.path())) {
                return false;
            }
            if (!ConfigSpecNodes.isColorChannel0to255(value.value())) {
                return false;
            }
            return channelPick.kind == null || channelPick.kind == value.kind();
        }

        /** Public refresh after returning from a child editor. */
        public void refreshAfterChildEdit() {
            refreshInPlace();
        }

        /** Updates list + chrome without recreating the whole screen (avoids stacked GUIs). */
        private void refreshInPlace() {
            rebuildNodeList();
            updateActionButtons();
            if (modTitleWidget != null) {
                modTitleWidget.setMessage(modHeaderTitle());
            }
            if (pathWidget != null) {
                pathWidget.setMessage(pathBreadcrumb());
            }
        }

        private void updateActionButtons() {
            if (saveButton != null) {
                saveButton.active = editable && session.isDirty();
            }
            if (undoButton != null) {
                undoButton.active = editable && session.canUndo();
            }
        }

        @Override
        protected void repositionElements() {
            layout.arrangeElements();

            int contentTop = layout.getHeaderHeight() + TOP_STACK_GAP;
            int contentBottom = height - layout.getFooterHeight() - TOP_STACK_GAP;

            int pathY = contentTop;
            if (pathWidget != null) {
                int pathW = font.width(pathWidget.getMessage());
                pathWidget.setWidth(Math.max(pathW, 20));
                pathWidget.setHeight(9);
                pathWidget.setPosition((width - pathWidget.getWidth()) / 2, pathY);
            }

            int searchY = pathY + 9 + TOP_STACK_GAP;
            if (searchBox != null) {
                int searchW = Math.min(360, Math.max(200, width - 48));
                searchBox.setWidth(searchW);
                searchBox.setPosition((width - searchW) / 2, searchY);
            }

            int buttonsY = contentBottom - BUTTON_H;
            if (buttonRow != null) {
                buttonRow.arrangeElements();
                int rowW = buttonRow.getWidth();
                buttonRow.setPosition((width - rowW) / 2, buttonsY);
                buttonRow.arrangeElements();
            }

            int listTop = searchY + 20 + LIST_GAP;
            int listBottom = buttonsY - LIST_GAP;
            int listH = Math.max(ROW_HEIGHT * 2, listBottom - listTop);
            if (nodeList != null) {
                nodeList.updateSizeAndPosition(width, listH, listTop);
            }
        }

        @Override
        public void onClose() {
            requestClose();
        }

        private void requestClose() {
            if (sectionPath.isEmpty()) {
                leaveSession(lastScreen);
            } else {
                minecraft.setScreen(lastScreen);
            }
        }

        private void requestDone() {
            leaveSession(typesScreen);
        }

        private void leaveSession(@Nullable Screen target) {
            if (!session.isDirty()) {
                minecraft.setScreen(target);
                return;
            }
            minecraft.setScreen(new UnsavedChangesScreen(this, session, () -> minecraft.setScreen(target)));
        }

        private void toggleChannelSelectMode() {
            if (!channelPick.active) {
                channelPick.active = true;
                channelPick.paths.clear();
                channelPick.kind = null;
                updateChannelSelectButton();
                refreshInPlace();
                return;
            }
            int n = channelPick.paths.size();
            // 3 = RGB, 4 = RGB+A — count decides alpha, no Shift mode.
            if (n == 3 || n == 4) {
                List<ColorChannelAssignScreen.Candidate> candidates = new ArrayList<>();
                for (List<String> p : channelPick.paths) {
                    String display = p.isEmpty()
                            ? "?"
                            : ConfigDisplayNames.formatRawKey(p.get(p.size() - 1));
                    candidates.add(new ColorChannelAssignScreen.Candidate(List.copyOf(p), display));
                }
                boolean withAlpha = n == 4;
                channelPick.active = false;
                channelPick.paths.clear();
                channelPick.kind = null;
                updateChannelSelectButton();
                minecraft.setScreen(new ColorChannelAssignScreen(
                        this, session, modId, config.getType(), candidates, withAlpha));
                return;
            }
            channelPick.active = false;
            channelPick.paths.clear();
            channelPick.kind = null;
            updateChannelSelectButton();
            refreshInPlace();
        }

        private int channelIndexOf(List<String> path) {
            int i = 0;
            for (List<String> p : channelPick.paths) {
                if (p.equals(path)) {
                    return i;
                }
                i++;
            }
            return -1;
        }

        private void updateChannelSelectButton() {
            if (channelSelectButton == null) {
                return;
            }
            if (!channelPick.active) {
                channelSelectButton.setMessage(Component.literal("Color ch. [3|4]"));
            } else {
                String ready = (channelPick.paths.size() == 3 || channelPick.paths.size() == 4) ? " →OK" : "";
                channelSelectButton.setMessage(Component.literal(
                        "Pick " + channelPick.paths.size() + "/4" + ready));
            }
        }

        private void openNode(ConfigSpecNodes.Node node) {
            if (node instanceof ConfigSpecNodes.SectionNode section) {
                SpecScreen child = new SpecScreen(typesScreen, this, modId, config, session, section.path(), editable);
                child.searchQuery = "";
                if (channelPick.active) {
                    child.channelPick = channelPick;
                }
                minecraft.setScreen(child);
                return;
            }
            if (!(node instanceof ConfigSpecNodes.ValueNode value) || !value.editable() || !editable) {
                return;
            }
            if (channelPick.active) {
                if (!isChannelSelectCandidate(value) && !channelPick.paths.contains(value.path())) {
                    return;
                }
                if (!channelPick.paths.remove(value.path())) {
                    if (channelPick.paths.size() < 4) {
                        if (channelPick.paths.isEmpty()) {
                            channelPick.kind = value.kind();
                        }
                        channelPick.paths.add(value.path());
                    }
                } else if (channelPick.paths.isEmpty()) {
                    channelPick.kind = null;
                }
                updateChannelSelectButton();
                refreshInPlace();
                return;
            }
            if (value.kind() == ConfigSpecNodes.ValueKind.BOOLEAN
                    && value.value() instanceof ModConfigSpec.BooleanValue booleanValue) {
                ConfigSpecNodes.toggleBoolean(session, booleanValue);
                refreshInPlace();
                return;
            }
            if (value.kind() == ConfigSpecNodes.ValueKind.ENUM) {
                cycleEnum(value);
                refreshInPlace();
                return;
            }
            if (value.kind() == ConfigSpecNodes.ValueKind.LIST) {
                CsvRule rule = CsvRuleRegistry.resolve(modId, config.getType(), value.path(), value.value());
                if (rule != null && ConfigSpecNodes.isStringList(value.value())) {
                    minecraft.setScreen(new CsvTableScreen(this, session, value, rule));
                } else {
                    minecraft.setScreen(new ListEditScreen(this, session, value));
                }
                return;
            }
            ColorBindingLine.Parsed colorBinding = ColorRuleRegistry.resolveBinding(modId, config.getType(), value.path());
            if (colorBinding != null) {
                minecraft.setScreen(new ColorEditScreen(
                        this,
                        session,
                        colorBinding,
                        Component.literal(value.displayName())
                ));
                return;
            }
            minecraft.setScreen(new ValueEditScreen(this, session, value));
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private void cycleEnum(ConfigSpecNodes.ValueNode value) {
            Object[] constants = ConfigSpecNodes.enumConstants(value.value());
            if (constants == null || constants.length == 0) {
                return;
            }
            Object current = session.getEffective(value.value());
            int index = 0;
            for (int i = 0; i < constants.length; i++) {
                if (Objects.equals(constants[i], current)) {
                    index = i;
                    break;
                }
            }
            Object next = constants[(index + 1) % constants.length];
            session.setPending(value.path(), next);
        }

        /**
         * Empty query: direct children only.
         * Non-empty: local matches first, then matching descendants from submenus (breadcrumb label).
         */
        private List<ConfigSpecNodes.Node> searchNodes(List<ConfigSpecNodes.Node> children, String query) {
            if (query == null || query.isBlank()) {
                return children;
            }
            String needle = query.trim().toLowerCase(Locale.ROOT);
            List<ConfigSpecNodes.Node> local = new ArrayList<>();
            List<ConfigSpecNodes.Node> deep = new ArrayList<>();
            for (ConfigSpecNodes.Node node : children) {
                if (node instanceof ConfigSpecNodes.SectionNode section) {
                    boolean selfMatch = matches(section.displayName(), section.rawKey(), needle);
                    List<ConfigSpecNodes.Node> filteredChildren = filterNodes(section.children(), query);
                    if (selfMatch || !filteredChildren.isEmpty()) {
                        local.add(new ConfigSpecNodes.SectionNode(
                                section.path(), section.displayName(), section.rawKey(),
                                selfMatch ? section.children() : filteredChildren));
                    }
                    collectDeepMatches(section.children(), needle, deep);
                } else if (node instanceof ConfigSpecNodes.ValueNode value) {
                    if (matches(value.displayName(), value.rawKey(), needle)) {
                        local.add(value);
                    }
                }
            }
            List<ConfigSpecNodes.Node> out = new ArrayList<>(local.size() + deep.size());
            out.addAll(local);
            out.addAll(deep);
            return out;
        }

        /** Deep value/section matches under submenu roots (not direct children of current screen). */
        private void collectDeepMatches(List<ConfigSpecNodes.Node> nodes, String needle, List<ConfigSpecNodes.Node> out) {
            for (ConfigSpecNodes.Node node : nodes) {
                if (node instanceof ConfigSpecNodes.SectionNode section) {
                    if (matches(section.displayName(), section.rawKey(), needle)) {
                        out.add(withRelativeBreadcrumb(section));
                    }
                    collectDeepMatches(section.children(), needle, out);
                } else if (node instanceof ConfigSpecNodes.ValueNode value) {
                    if (matches(value.displayName(), value.rawKey(), needle)) {
                        out.add(withRelativeBreadcrumb(value));
                    }
                }
            }
        }

        private ConfigSpecNodes.SectionNode withRelativeBreadcrumb(ConfigSpecNodes.SectionNode section) {
            return new ConfigSpecNodes.SectionNode(
                    section.path(),
                    relativeBreadcrumbLabel(section.path(), section.displayName()),
                    section.rawKey(),
                    section.children());
        }

        private ConfigSpecNodes.ValueNode withRelativeBreadcrumb(ConfigSpecNodes.ValueNode value) {
            return new ConfigSpecNodes.ValueNode(
                    value.path(),
                    relativeBreadcrumbLabel(value.path(), value.displayName()),
                    value.rawKey(),
                    value.value(),
                    value.kind(),
                    value.editable());
        }

        private String relativeBreadcrumbLabel(List<String> fullPath, String leafDisplay) {
            if (fullPath.size() <= sectionPath.size() + 1) {
                return leafDisplay;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = sectionPath.size(); i < fullPath.size() - 1; i++) {
                if (!sb.isEmpty()) {
                    sb.append(" › ");
                }
                sb.append(ConfigDisplayNames.formatRawKey(fullPath.get(i)));
            }
            if (!sb.isEmpty()) {
                sb.append(" › ");
            }
            sb.append(leafDisplay);
            return sb.toString();
        }

        private static List<ConfigSpecNodes.Node> filterNodes(List<ConfigSpecNodes.Node> nodes, String query) {
            if (query == null || query.isBlank()) {
                return nodes;
            }
            String needle = query.trim().toLowerCase(Locale.ROOT);
            List<ConfigSpecNodes.Node> out = new ArrayList<>();
            for (ConfigSpecNodes.Node node : nodes) {
                if (node instanceof ConfigSpecNodes.SectionNode section) {
                    List<ConfigSpecNodes.Node> filteredChildren = filterNodes(section.children(), query);
                    boolean selfMatch = matches(section.displayName(), section.rawKey(), needle);
                    if (selfMatch || !filteredChildren.isEmpty()) {
                        out.add(new ConfigSpecNodes.SectionNode(
                                section.path(), section.displayName(), section.rawKey(),
                                selfMatch ? section.children() : filteredChildren));
                    }
                } else if (node instanceof ConfigSpecNodes.ValueNode value) {
                    if (matches(value.displayName(), value.rawKey(), needle)) {
                        out.add(value);
                    }
                }
            }
            return out;
        }

        private static boolean matches(String display, String raw, String needle) {
            return display.toLowerCase(Locale.ROOT).contains(needle)
                    || raw.toLowerCase(Locale.ROOT).contains(needle);
        }

        private final class NodeList extends ObjectSelectionList<NodeList.Entry> {
            NodeList(Minecraft minecraft) {
                super(minecraft, SpecScreen.this.width, SpecScreen.this.height, 0, ROW_HEIGHT);
            }

            void clearAll() {
                clearEntries();
            }

            void addNode(ConfigSpecNodes.Node node) {
                addEntry(new Entry(node));
            }

            @Override
            public int getRowWidth() {
                return Math.min(460, width - 40);
            }

            final class Entry extends ObjectSelectionList.Entry<Entry> {
                private final ConfigSpecNodes.Node node;

                Entry(ConfigSpecNodes.Node node) {
                    this.node = node;
                }

                @Override
                public Component getNarration() {
                    if (node instanceof ConfigSpecNodes.SectionNode section) {
                        return Component.literal(section.displayName());
                    }
                    if (node instanceof ConfigSpecNodes.ValueNode value) {
                        return Component.literal(value.displayName());
                    }
                    return Component.empty();
                }

                private int toggleLeft() {
                    return getContentX() + getRowWidth() - TOGGLE_WIDTH - 4;
                }

                private int toggleTop() {
                    return getContentY() + (ROW_HEIGHT - TOGGLE_HEIGHT) / 2;
                }

                @Override
                public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovering, float a) {
                    int left = getContentX();
                    int top = getContentY();
                    int rowW = getRowWidth();

                    if (hovering) {
                        graphics.fill(left - 2, top, left + rowW, top + ROW_HEIGHT - 2, 0x22FFFFFF);
                    }

                    if (node instanceof ConfigSpecNodes.SectionNode section) {
                        graphics.text(font, section.displayName(), left + 2, top + 10, 0xFFFFD080);
                        graphics.text(font, "›", left + rowW - 12, top + 10, 0xFFFFD080);
                        return;
                    }

                    if (!(node instanceof ConfigSpecNodes.ValueNode value)) {
                        graphics.text(font, "?", left + 2, top + 10, 0xFFFFFFFF);
                        return;
                    }

                    boolean dirty = session.isDirty(value.path());
                    boolean readOnly = value.kind() == ConfigSpecNodes.ValueKind.READ_ONLY || !editable;
                    boolean channelMarked = channelPick.active && channelPick.paths.contains(value.path());
                    Object current = session.getEffective(value.value());
                    Object def = value.value().getDefault();
                    boolean showDefault = !Objects.equals(current, def);

                    String name = dirty ? value.displayName() + " *" : value.displayName();
                    if (channelMarked) {
                        int idx = channelIndexOf(value.path());
                        name = "[#" + (idx + 1) + "] " + name;
                    }
                    int nameColor = channelMarked ? 0xFF80FFC8
                            : dirty ? 0xFFFFEE88 : (readOnly ? 0xFFAAAAAA : 0xFFFFFFFF);
                    graphics.text(font, name, left + 2, top + 6, nameColor);

                    if (showDefault) {
                        String defLabel = Component.translatable(
                                "screen.another_config_manager.config.default_hint",
                                ConfigSpecNodes.formatValueShort(def)
                        ).getString();
                        graphics.text(font, defLabel, left + 2, top + 18, 0xFF888888);
                    }

                    int valueRight = left + rowW - 6;
                    if (value.kind() == ConfigSpecNodes.ValueKind.BOOLEAN && !readOnly) {
                        boolean on = current instanceof Boolean bool && bool;
                        int bx = toggleLeft();
                        int by = toggleTop();
                        int bg = on ? 0xFF3A8F4A : 0xFF555555;
                        int border = on ? 0xFF6FCF7F : 0xFF888888;
                        graphics.fill(bx, by, bx + TOGGLE_WIDTH, by + TOGGLE_HEIGHT, bg);
                        graphics.fill(bx, by, bx + TOGGLE_WIDTH, by + 1, border);
                        graphics.fill(bx, by + TOGGLE_HEIGHT - 1, bx + TOGGLE_WIDTH, by + TOGGLE_HEIGHT, border);
                        graphics.fill(bx, by, bx + 1, by + TOGGLE_HEIGHT, border);
                        graphics.fill(bx + TOGGLE_WIDTH - 1, by, bx + TOGGLE_WIDTH, by + TOGGLE_HEIGHT, border);
                        String label = Component.translatable(on
                                ? "screen.another_config_manager.config.on"
                                : "screen.another_config_manager.config.off").getString();
                        int tw = font.width(label);
                        graphics.text(font, label, bx + (TOGGLE_WIDTH - tw) / 2, by + 4, 0xFFFFFFFF);
                    } else {
                        String right = ConfigSpecNodes.formatValueShort(current);
                        boolean csv = value.kind() == ConfigSpecNodes.ValueKind.LIST
                                && ConfigSpecNodes.isStringList(value.value())
                                && CsvRuleRegistry.hasRule(modId, config.getType(), value.path());
                        boolean colorBound = ColorRuleRegistry.hasRule(modId, config.getType(), value.path());
                        if (csv) {
                            String badge = Component.translatable("screen.another_config_manager.config.csv.badge").getString();
                            right = "[" + badge + "] " + right;
                        } else if (colorBound) {
                            String badge = Component.translatable("screen.another_config_manager.config.color.badge").getString();
                            right = "[" + badge + "] " + right;
                        }
                        int color = readOnly ? 0xFFAAAAAA : (csv ? 0xFF9FE8B0 : (colorBound ? 0xFFE8C09F : 0xFFC8E6FF));
                        int tw = font.width(right);
                        int maxValW = rowW / 2;
                        if (tw > maxValW) {
                            right = font.plainSubstrByWidth(right, Math.max(8, maxValW - font.width("…"))) + "…";
                            tw = font.width(right);
                        }
                        graphics.text(font, right, valueRight - tw, top + 12, color);
                    }

                    if (hovering) {
                        String comment = ConfigSpecNodes.commentOf(value.value());
                        List<FormattedCharSequence> tip = ConfigComments.tooltipSequences(font, comment);
                        if (!tip.isEmpty()) {
                            graphics.setTooltipForNextFrame(tip, mouseX, mouseY);
                        }
                    }
                }

                @Override
                public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                    openNode(node);
                    return true;
                }
            }
        }
    }

    /**
     * Simple editor for int/long/double/string config values (session-buffered).
     * Layout matches List/CSV screens (header title, content stack, no comment/edit overlap).
     */
    public static class ValueEditScreen extends Screen {
        private static final int HEADER_H = 48;
        private static final int FOOTER_MARGIN = 14;
        private static final int TOP_STACK_GAP = 10;
        private static final int LIST_GAP = 12;
        private static final int BUTTON_H = 20;
        private static final int CONTENT_MARGIN = 20;
        private static final int MAX_COMMENT_LINES = 8;

        private final SpecScreen parent;
        private final ConfigEditSession session;
        private final ConfigSpecNodes.ValueNode node;
        private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_H, FOOTER_MARGIN);
        private final List<StringWidget> commentWidgets = new ArrayList<>();
        private EditBox editBox;
        private @Nullable StringWidget status;
        private @Nullable LinearLayout buttonRow;

        public ValueEditScreen(SpecScreen parent, ConfigEditSession session, ConfigSpecNodes.ValueNode node) {
            super(Component.literal(node.displayName()));
            this.parent = parent;
            this.session = session;
            this.node = node;
        }

        @Override
        protected void init() {
            layout.setHeaderHeight(HEADER_H);
            layout.setFooterHeight(FOOTER_MARGIN);

            LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(0));
            header.defaultCellSetting().alignHorizontallyCenter();
            header.addChild(new StringWidget(title, font));

            commentWidgets.clear();
            String comment = ConfigSpecNodes.commentOf(node.value());
            int wrapW = Math.max(80, width - CONTENT_MARGIN * 2);
            int lineCount = 0;
            boolean truncated = false;
            for (String line : ConfigComments.lines(comment)) {
                for (FormattedCharSequence seq : font.split(Component.literal(line), wrapW)) {
                    if (lineCount >= MAX_COMMENT_LINES) {
                        truncated = true;
                        break;
                    }
                    StringWidget lineWidget = new StringWidget(Component.literal(seqToString(seq)), font);
                    commentWidgets.add(lineWidget);
                    addRenderableWidget(lineWidget);
                    lineCount++;
                }
                if (truncated) {
                    break;
                }
            }
            if (truncated) {
                StringWidget more = new StringWidget(Component.literal("…"), font);
                commentWidgets.add(more);
                addRenderableWidget(more);
            }

            editBox = new EditBox(font, 0, 0, Math.min(320, Math.max(80, width - CONTENT_MARGIN * 2)), 20,
                    Component.literal(node.displayName()));
            editBox.setMaxLength(1024);
            Object effective = session.getEffective(node.value());
            editBox.setValue(effective == null ? "" : String.valueOf(effective));
            addRenderableWidget(editBox);

            status = new StringWidget(Component.empty(), font);
            addRenderableWidget(status);

            buttonRow = LinearLayout.horizontal().spacing(8);
            buttonRow.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.apply"), button -> apply())
                    .width(100)
                    .build());
            if (!ColorRuleRegistry.hasRule(parent.modId(), parent.configType(), node.path())
                    && ConfigSpecNodes.canDeclareAsColor(node.value())) {
                buttonRow.addChild(Button.builder(
                        Component.translatable("screen.another_config_manager.config.color.declare"),
                        button -> declareColor()
                ).width(110).build());
            }
            buttonRow.addChild(Button.builder(
                    Component.translatable("screen.another_config_manager.config.reset_value"),
                    button -> {
                        session.resetValue(node.path());
                        Object def = session.getEffective(node.value());
                        editBox.setValue(def == null ? "" : String.valueOf(def));
                    }).width(100).build());
            buttonRow.addChild(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose()).width(100).build());
            buttonRow.visitWidgets(this::addRenderableWidget);

            layout.visitWidgets(this::addRenderableWidget);
            repositionElements();
            setInitialFocus(editBox);
        }

        private static String seqToString(FormattedCharSequence seq) {
            StringBuilder sb = new StringBuilder();
            seq.accept((index, style, codePoint) -> {
                sb.appendCodePoint(codePoint);
                return true;
            });
            return sb.toString();
        }

        @Override
        protected void repositionElements() {
            layout.arrangeElements();

            int innerX = CONTENT_MARGIN;
            int innerW = Math.max(40, width - CONTENT_MARGIN * 2);
            int y = layout.getHeaderHeight() + TOP_STACK_GAP;

            int buttonsY = height - layout.getFooterHeight() - TOP_STACK_GAP - BUTTON_H;
            int statusH = 12 + LIST_GAP;
            int editBottomLimit = buttonsY - statusH - LIST_GAP - BUTTON_H;

            int wrapW = innerW;
            int commentY = y;
            for (StringWidget commentWidget : commentWidgets) {
                if (commentY + 10 > editBottomLimit - LIST_GAP) {
                    break;
                }
                int textW = font.width(commentWidget.getMessage());
                commentWidget.setWidth(Math.min(textW, wrapW));
                commentWidget.setPosition(innerX + (innerW - commentWidget.getWidth()) / 2, commentY);
                commentY += 10;
            }
            // Hide overflow comment lines that would collide with the edit box.
            for (StringWidget commentWidget : commentWidgets) {
                if (commentWidget.getY() + commentWidget.getHeight() > editBottomLimit - LIST_GAP) {
                    commentWidget.visible = false;
                }
            }
            y = Math.min(commentY, editBottomLimit - LIST_GAP);
            if (!commentWidgets.isEmpty() && y > layout.getHeaderHeight() + TOP_STACK_GAP) {
                y += LIST_GAP;
            }

            if (editBox != null) {
                int editW = Math.min(320, innerW);
                editBox.setWidth(editW);
                int editY = Math.min(y, editBottomLimit);
                editBox.setPosition(innerX + (innerW - editW) / 2, editY);
            }

            if (status != null) {
                int statusW = Math.max(20, font.width(status.getMessage()));
                status.setWidth(Math.min(statusW, innerW));
                status.setPosition(innerX + (innerW - status.getWidth()) / 2, buttonsY - 12 - LIST_GAP);
            }
            if (buttonRow != null) {
                buttonRow.arrangeElements();
                int rowW = Math.min(buttonRow.getWidth(), innerW);
                buttonRow.setPosition(innerX + (innerW - rowW) / 2, buttonsY);
                buttonRow.arrangeElements();
            }
        }

        private void apply() {
            boolean ok = ConfigSpecNodes.trySetBuffered(session, node.value(), editBox.getValue());
            if (status != null) {
                status.setMessage(ok
                        ? Component.translatable("screen.another_config_manager.config.buffered")
                        : Component.translatable("screen.another_config_manager.config.invalid"));
                int innerW = Math.max(40, width - CONTENT_MARGIN * 2);
                int textW = font.width(status.getMessage());
                status.setWidth(Math.min(Math.max(8, textW), innerW));
                int buttonsY = height - layout.getFooterHeight() - TOP_STACK_GAP - BUTTON_H;
                status.setPosition((width - status.getWidth()) / 2, buttonsY - 12 - LIST_GAP);
            }
            if (ok) {
                parent.refreshAfterChildEdit();
                minecraft.setScreen(parent);
            }
        }


        private void declareColor() {
            if (!ConfigSpecNodes.canDeclareAsColor(node.value())) {
                if (status != null) {
                    status.setMessage(Component.translatable(
                            "screen.another_config_manager.config.color.channel_range_required"));
                    int innerW = Math.max(40, width - CONTENT_MARGIN * 2);
                    int textW = font.width(status.getMessage());
                    status.setWidth(Math.min(Math.max(8, textW), innerW));
                    int buttonsY = height - layout.getFooterHeight() - TOP_STACK_GAP - BUTTON_H;
                    status.setPosition((width - status.getWidth()) / 2, buttonsY - 12 - LIST_GAP);
                }
                return;
            }
            String storage = "hex";
            String order = "rgb";
            String prefix = "none";
            if (node.kind() == ConfigSpecNodes.ValueKind.BYTE
                    || node.kind() == ConfigSpecNodes.ValueKind.SHORT
                    || node.kind() == ConfigSpecNodes.ValueKind.INT
                    || node.kind() == ConfigSpecNodes.ValueKind.LONG) {
                storage = "int";
                order = "argb";
                prefix = "none";
            }
            ColorBindingLine.Parsed binding = ColorRuleRegistry.declareSingle(
                    parent.modId(),
                    parent.configType(),
                    ConfigSpecNodes.joinPath(node.path()),
                    storage,
                    order,
                    prefix
            );
            minecraft.setScreen(new ColorEditScreen(
                    parent,
                    session,
                    binding,
                    Component.literal(node.displayName())
            ));
        }

        @Override
        public void onClose() {
            minecraft.setScreen(parent);
        }
    }
}
