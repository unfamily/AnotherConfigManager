package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Confirmation dialog shown when leaving a dirty config session.
 */
public class UnsavedChangesScreen extends Screen {
    private final Screen parent;
    private final ConfigEditSession session;
    private final Runnable onSavedOrDiscarded;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    public UnsavedChangesScreen(Screen parent, ConfigEditSession session, Runnable onSavedOrDiscarded) {
        super(Component.translatable("screen.another_config_manager.config.unsaved.title"));
        this.parent = parent;
        this.session = session;
        this.onSavedOrDiscarded = onSavedOrDiscarded;
    }

    @Override
    protected void init() {
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(6));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(title, font));
        header.addChild(new StringWidget(Component.translatable("screen.another_config_manager.config.unsaved.message"), font));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.unsaved.save"), button -> {
            session.commit();
            onSavedOrDiscarded.run();
        }).width(100).build());
        footer.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.unsaved.discard"), button -> {
            session.discard();
            onSavedOrDiscarded.run();
        }).width(100).build());
        footer.addChild(Button.builder(Component.translatable("screen.another_config_manager.config.unsaved.cancel"), button ->
                minecraft.setScreen(parent)).width(100).build());

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
}
