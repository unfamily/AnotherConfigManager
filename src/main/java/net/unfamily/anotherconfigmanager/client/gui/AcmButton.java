package net.unfamily.anotherconfigmanager.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Flat modern button (text or single-character icon). Replaces vanilla {@code Button} chrome in ACM GUIs.
 */
public final class AcmButton extends AbstractWidget {
    public static final int HEIGHT = 20;
    public static final int ICON_SIZE = 16;

    private static final int BG = 0xFF2C2C32;
    private static final int BG_HOVER = 0xFF3A3A44;
    private static final int BG_DISABLED = 0xFF1E1E22;
    private static final int BORDER = 0xFF5A5A68;
    private static final int BORDER_HOVER = 0xFF8A8A9A;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int TEXT_DISABLED = 0xFF777788;

    private final Runnable onPress;
    private final boolean iconStyle;
    private @Nullable Supplier<Boolean> activeWhen;

    private AcmButton(int width, int height, Component message, Runnable onPress, boolean iconStyle) {
        super(0, 0, width, height, message);
        this.onPress = onPress;
        this.iconStyle = iconStyle;
    }

    public static AcmButton text(int width, Component message, Runnable onPress) {
        return new AcmButton(width, HEIGHT, message, onPress, false);
    }

    public static AcmButton text(int width, Component message, Runnable onPress, @Nullable Component tooltip) {
        AcmButton button = text(width, message, onPress);
        if (tooltip != null) {
            button.setTooltip(Tooltip.create(tooltip));
        }
        return button;
    }

    /** Compact square control showing a short glyph (e.g. +, −, ←, →, ↶, ↺). */
    public static AcmButton icon(Component glyph, Component tooltip, Runnable onPress) {
        AcmButton button = new AcmButton(ICON_SIZE, ICON_SIZE, glyph, onPress, true);
        button.setTooltip(Tooltip.create(tooltip));
        return button;
    }

    public AcmButton activeWhen(Supplier<Boolean> activeWhen) {
        this.activeWhen = activeWhen;
        return this;
    }

    public void refreshActive() {
        if (activeWhen != null) {
            this.active = Boolean.TRUE.equals(activeWhen.get());
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (activeWhen != null) {
            refreshActive();
        }
        if (!this.active) {
            return;
        }
        onPress.run();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (activeWhen != null) {
            refreshActive();
        }
        handleCursor(graphics);
        int bg = !this.active ? BG_DISABLED : (this.isHoveredOrFocused() ? BG_HOVER : BG);
        int border = !this.active ? 0xFF3A3A44 : (this.isHoveredOrFocused() ? BORDER_HOVER : BORDER);
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        graphics.fill(x0, y0, x1, y1, bg);
        graphics.fill(x0, y0, x1, y0 + 1, border);
        graphics.fill(x0, y1 - 1, x1, y1, border);
        graphics.fill(x0, y0, x0 + 1, y1, border);
        graphics.fill(x1 - 1, y0, x1, y1, border);

        Font font = Minecraft.getInstance().font;
        Component message = getMessage();
        int color = this.active ? TEXT : TEXT_DISABLED;
        int tw = font.width(message);
        int tx = x0 + (getWidth() - tw) / 2;
        int ty = y0 + (getHeight() - 8) / 2;
        if (iconStyle) {
            ty = y0 + (getHeight() - 9) / 2;
        }
        graphics.text(font, message.getString(), tx, ty, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
