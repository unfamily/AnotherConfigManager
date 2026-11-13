package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.unfamily.anotherconfigmanager.client.gui.AcmButton;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Warns that dirty NeoForge game/world restart values need a pack restart after Save.
 * Lists top-level category names only.
 */
public class RestartRequiredScreen extends Screen {
    private final Screen parent;
    @Nullable
    private final String modId;
    private final List<String> categories;
    private final Runnable onContinue;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public RestartRequiredScreen(
            Screen parent,
            @Nullable String modId,
            List<String> categories,
            Runnable onContinue) {
        super(Component.translatable("screen.another_config_manager.config.restart.title"));
        this.parent = parent;
        this.modId = modId;
        this.categories = List.copyOf(categories);
        this.onContinue = onContinue;
    }

    /**
     * Commit immediately, or open this dialog when dirty restart categories exist.
     */
    public static void saveWithRestartWarning(
            Screen parent,
            ConfigEditSession session,
            @Nullable String modId,
            Runnable afterCommit) {
        List<String> cats = session.dirtyRestartCategories();
        if (cats.isEmpty()) {
            session.commit();
            afterCommit.run();
            return;
        }
        Minecraft.getInstance().setScreen(new RestartRequiredScreen(parent, modId, cats, () -> {
            session.commit();
            afterCommit.run();
        }));
    }

    @Override
    protected void init() {
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(6));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        header.addChild(new StringWidget(
                Component.translatable("screen.another_config_manager.config.restart.message"), font));

        CategoryList categoryList = layout.addToContents(new CategoryList(minecraft));
        for (String key : categories) {
            String label = modId != null
                    ? ConfigDisplayNames.sectionDisplayName(modId, List.of(key), key)
                    : ConfigDisplayNames.formatRawKey(key);
            categoryList.addCategory(label);
        }

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(AcmButton.text(
                100,
                Component.translatable("screen.another_config_manager.config.restart.continue"),
                onContinue::run));
        footer.addChild(AcmButton.text(
                100,
                Component.translatable("screen.another_config_manager.config.restart.cancel"),
                () -> minecraft.setScreen(parent)));

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private final class CategoryList extends ObjectSelectionList<CategoryList.Entry> {
        CategoryList(Minecraft minecraft) {
            super(minecraft, RestartRequiredScreen.this.width, 140, 0, 20);
        }

        void addCategory(String label) {
            addEntry(new Entry(label));
        }

        @Override
        public int getRowWidth() {
            return Math.min(320, width - 40);
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String label;

            Entry(String label) {
                this.label = label;
            }

            @Override
            public void extractContent(
                    GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovering, float a) {
                graphics.text(font, label, getContentX() + 4, getContentY() + 6, 0xFFFFFFFF);
            }

            @Override
            public Component getNarration() {
                return Component.literal(label);
            }
        }
    }
}
