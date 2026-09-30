package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ModListScreen;
import net.unfamily.anotherconfigmanager.client.gui.AcmButton;
import net.unfamily.anotherconfigmanager.client.gui.AcmUi;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Searchable list of mods that have NeoForge configs.
 * Row click uses {@link ConfigOpener#open(String)} (native factory including ConfigurationScreen, else Library UI).
 */
public class ConfigModBrowserScreen extends Screen {
    private static final int HEADER_H = 33;
    private static final int FOOTER_H = 36;
    private static final int TOP_STACK_GAP = 6;
    private static final int LIST_GAP = 6;
    private static final int SEARCH_H = 20;
    private static final int ROW_H = 36;
    private static final int ICON = 28;

    private final @Nullable Screen lastScreen;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_H, FOOTER_H);
    private final List<String> modIds;
    private @Nullable ModListWidget listWidget;
    private @Nullable EditBox searchBox;
    private @Nullable StringWidget status;
    private String searchQuery = "";

    public ConfigModBrowserScreen(@Nullable Screen lastScreen) {
        super(Component.translatable("screen.another_config_manager.config.browser.title"));
        this.lastScreen = lastScreen;
        this.modIds = List.copyOf(ConfigDiscovery.modIdsWithConfigs());
    }

    @Override
    protected void init() {
        layout.setHeaderHeight(HEADER_H);
        layout.setFooterHeight(FOOTER_H);

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(0));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));

        searchBox = new EditBox(font, 0, 0, 200, SEARCH_H, Component.translatable("screen.another_config_manager.config.search"));
        searchBox.setHint(Component.translatable("screen.another_config_manager.config.search"));
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value == null ? "" : value;
            rebuildList();
        });
        addRenderableWidget(searchBox);

        listWidget = new ModListWidget(minecraft);
        addRenderableWidget(listWidget);
        rebuildList();

        status = new StringWidget(Component.empty(), font);
        addRenderableWidget(status);

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(AcmButton.text(
                120,
                Component.translatable("screen.another_config_manager.config.neoforge_mod_list"),
                () -> minecraft.setScreen(new ModListScreen(this))
        ));
        footer.addChild(AcmButton.text(
                120,
                Component.translatable("screen.another_config_manager.config.return_to_list"),
                this::onClose
        ));

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        updateStatus();
    }

    private void rebuildList() {
        if (listWidget == null) {
            return;
        }
        listWidget.replaceEntries(filteredMods());
        listWidget.setScrollAmount(0);
        updateStatus();
    }

    private List<String> filteredMods() {
        String needle = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String modId : modIds) {
            if (!needle.isEmpty()) {
                String title = ConfigDisplayNames.modTitle(modId).getString().toLowerCase(Locale.ROOT);
                if (!modId.toLowerCase(Locale.ROOT).contains(needle) && !title.contains(needle)) {
                    continue;
                }
            }
            out.add(modId);
        }
        return out;
    }

    private void updateStatus() {
        if (status == null) {
            return;
        }
        if (modIds.isEmpty()) {
            status.setMessage(Component.translatable("screen.another_config_manager.config.browser.empty"));
        } else {
            status.setMessage(Component.empty());
        }
    }

    private void openMod(String modId) {
        Component failure = ConfigOpener.failureReason(modId);
        if (failure != null) {
            if (status != null) {
                status.setMessage(failure);
                repositionElements();
            }
            return;
        }
        if (!ConfigOpener.open(modId) && status != null) {
            status.setMessage(Component.translatable("commands.another_config_manager.config.open_failed", modId));
            repositionElements();
        }
    }

    private static String versionOf(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(c -> String.valueOf(c.getModInfo().getVersion()))
                .orElse("");
    }

    private boolean hasStatusMessage() {
        return status != null && !status.getMessage().getString().isEmpty();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();

        int contentTop = layout.getHeaderHeight() + TOP_STACK_GAP;
        int contentBottom = height - layout.getFooterHeight() - TOP_STACK_GAP;
        int innerW = Math.max(120, width - 40);
        int innerX = (width - innerW) / 2;

        int y = contentTop;
        if (searchBox != null) {
            int searchW = Math.min(360, innerW);
            searchBox.setWidth(searchW);
            searchBox.setPosition(innerX + (innerW - searchW) / 2, y);
            y += SEARCH_H + LIST_GAP;
        }

        int listBottom = contentBottom;
        if (hasStatusMessage()) {
            int statusH = 12;
            int statusY = contentBottom - statusH;
            int sw = Math.min(innerW, Math.max(40, font.width(status.getMessage())));
            status.setWidth(sw);
            status.setHeight(9);
            status.setPosition(innerX + (innerW - sw) / 2, statusY);
            status.visible = true;
            listBottom = statusY - LIST_GAP;
        } else if (status != null) {
            status.visible = false;
            status.setPosition(0, -100);
        }

        int listH = Math.max(44, listBottom - y);
        if (listWidget != null) {
            listWidget.updateSizeAndPosition(innerW, listH, innerX, y);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(lastScreen);
    }

    private final class ModListWidget extends ObjectSelectionList<ModListWidget.Entry> {
        ModListWidget(Minecraft minecraft) {
            super(minecraft, ConfigModBrowserScreen.this.width, ConfigModBrowserScreen.this.height, 0, ROW_H);
        }

        void replaceEntries(List<String> ids) {
            clearEntries();
            for (String id : ids) {
                addEntry(new Entry(id));
            }
        }

        @Override
        public int getRowWidth() {
            return Math.min(420, width - 40);
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String modId;

            Entry(String modId) {
                this.modId = modId;
            }

            @Override
            public Component getNarration() {
                return ConfigDisplayNames.modTitle(modId);
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovering, float a) {
                int x = getContentX();
                int y = getContentY();
                Optional<ModLogoTextures.Logo> logo = ModLogoTextures.get(modId);
                int textX = x + 2;
                if (logo.isPresent()) {
                    ModLogoTextures.Logo l = logo.get();
                    int draw = ICON;
                    // Pass full texture as src so the logo scales into ICON×ICON instead of cropping.
                    graphics.blit(
                            RenderPipelines.GUI_TEXTURED,
                            l.location(),
                            x + 2,
                            y + (ROW_H - draw) / 2 - 2,
                            0f,
                            0f,
                            draw,
                            draw,
                            l.width(),
                            l.height(),
                            l.width(),
                            l.height());
                    textX = x + 2 + draw + 8;
                } else {
                    graphics.fill(x + 2, y + 4, x + 2 + ICON, y + 4 + ICON, 0x33FFFFFF);
                    textX = x + 2 + ICON + 8;
                }
                Component title = ConfigDisplayNames.modTitle(modId);
                graphics.text(font, title, textX, y + 4, 0xFFFFFFFF);
                String meta = modId + "  ·  " + versionOf(modId);
                graphics.text(font, meta, textX, y + 18, 0xFFAAAAAA);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                ModListWidget.this.setSelected(this);
                AcmUi.playClick();
                openMod(modId);
                return true;
            }
        }
    }
}
