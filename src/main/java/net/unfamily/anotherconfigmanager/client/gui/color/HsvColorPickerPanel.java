package net.unfamily.anotherconfigmanager.client.gui.color;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;

import java.util.function.IntConsumer;

/**
 * Embeddable HSV color picker: SV square, hue bar, optional alpha bar, swatch, optional vanilla palette.
 * Not tied to a container menu.
 */
public final class HsvColorPickerPanel {
    public static final int SV_SIZE = 96;
    public static final int HUE_BAR_W = 12;
    public static final int ALPHA_BAR_W = 12;
    public static final int SWATCH_SIZE = 20;
    public static final int PALETTE_SIZE = 10;
    public static final int PALETTE_GAP = 2;
    public static final int PALETTE_COLS = 8;

    private int x;
    private int y;
    private float hue;
    private float sat;
    private float val;
    private int rgb;
    private int alpha = 255;
    private boolean draggingSv;
    private boolean draggingHue;
    private boolean draggingAlpha;
    private IntConsumer onChanged = rgb -> {};
    private IntConsumer onAlphaChanged = a -> {};
    private boolean showVanillaPalette = true;
    private boolean showAlphaBar = false;

    public HsvColorPickerPanel(int x, int y, int initialRgb) {
        this.x = x;
        this.y = y;
        setRgb(initialRgb);
    }

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setOnChanged(IntConsumer onChanged) {
        this.onChanged = onChanged == null ? r -> {} : onChanged;
    }

    public void setOnAlphaChanged(IntConsumer onAlphaChanged) {
        this.onAlphaChanged = onAlphaChanged == null ? a -> {} : onAlphaChanged;
    }

    public void setShowVanillaPalette(boolean show) {
        this.showVanillaPalette = show;
    }

    public void setShowAlphaBar(boolean show) {
        this.showAlphaBar = show;
    }

    public boolean isShowAlphaBar() {
        return showAlphaBar;
    }

    public int getRgb() {
        return rgb & 0xFFFFFF;
    }

    public int getAlpha() {
        return alpha & 0xFF;
    }

    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
    }

    public int getArgb() {
        return ((alpha & 0xFF) << 24) | (rgb & 0xFFFFFF);
    }

    public void setRgb(int rgb) {
        this.rgb = rgb & 0xFFFFFF;
        ColorMath.Hsv hsv = ColorMath.rgbToHsv(this.rgb);
        this.hue = hsv.h();
        this.sat = hsv.s();
        this.val = hsv.v();
    }

    public void setArgb(int argb) {
        setAlpha((argb >>> 24) & 0xFF);
        setRgb(argb & 0xFFFFFF);
    }

    public void syncHexToEditBox(EditBox hexEdit) {
        if (hexEdit != null) {
            String next = ColorMath.toHexString(rgb);
            if (!next.equals(hexEdit.getValue())) {
                hexEdit.setValue(next);
            }
        }
    }

    public boolean applyHexFromEditBox(EditBox hexEdit) {
        if (hexEdit == null) {
            return false;
        }
        Integer parsed = ColorMath.parseHexRgb(hexEdit.getValue());
        if (parsed == null) {
            return false;
        }
        if ((parsed & 0xFFFFFF) == (rgb & 0xFFFFFF)) {
            return true;
        }
        setRgb(parsed);
        fireChanged();
        return true;
    }

    public void render(GuiGraphicsExtractor graphics) {
        int svLeft = x;
        int svTop = y;
        for (int py = 0; py < SV_SIZE; py++) {
            float v = 1f - (py / (float) (SV_SIZE - 1));
            for (int px = 0; px < SV_SIZE; px++) {
                float s = px / (float) (SV_SIZE - 1);
                int c = ColorMath.hsvToRgb(hue, s, v);
                graphics.fill(svLeft + px, svTop + py, svLeft + px + 1, svTop + py + 1, 0xFF000000 | c);
            }
        }
        drawRectBorder(graphics, svLeft, svTop, SV_SIZE, SV_SIZE);

        int cursorX = svLeft + Math.round(sat * (SV_SIZE - 1));
        int cursorY = svTop + Math.round((1f - val) * (SV_SIZE - 1));
        graphics.fill(cursorX - 2, cursorY - 2, cursorX + 3, cursorY + 3, 0xFFFFFFFF);
        graphics.fill(cursorX - 1, cursorY - 1, cursorX + 2, cursorY + 2, 0xFF000000);

        int hueLeft = x + SV_SIZE + 8;
        int hueTop = y;
        for (int py = 0; py < SV_SIZE; py++) {
            float h = (py / (float) (SV_SIZE - 1)) * 360f;
            int c = ColorMath.hsvToRgb(h, 1f, 1f);
            graphics.fill(hueLeft, hueTop + py, hueLeft + HUE_BAR_W, hueTop + py + 1, 0xFF000000 | c);
        }
        drawRectBorder(graphics, hueLeft, hueTop, HUE_BAR_W, SV_SIZE);
        int hueCursorY = hueTop + Math.round((hue / 360f) * (SV_SIZE - 1));
        graphics.fill(hueLeft - 2, hueCursorY - 1, hueLeft + HUE_BAR_W + 2, hueCursorY + 2, 0xFFFFFFFF);
        graphics.fill(hueLeft - 1, hueCursorY, hueLeft + HUE_BAR_W + 1, hueCursorY + 1, 0xFF000000);

        int afterHueX = hueLeft + HUE_BAR_W + 8;
        if (showAlphaBar) {
            renderAlphaBar(graphics, afterHueX, y);
            afterHueX += ALPHA_BAR_W + 8;
        }

        int swatchX = afterHueX;
        int swatchY = y;
        if (showAlphaBar) {
            fillCheckerboard(graphics, swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE);
            graphics.fill(swatchX, swatchY, swatchX + SWATCH_SIZE, swatchY + SWATCH_SIZE, getArgb());
        } else {
            graphics.fill(swatchX, swatchY, swatchX + SWATCH_SIZE, swatchY + SWATCH_SIZE, 0xFF000000 | rgb);
        }
        drawRectBorder(graphics, swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE);

        if (showVanillaPalette) {
            ChatFormatting[] palette = ChatFormatting.values();
            int index = 0;
            for (ChatFormatting formatting : palette) {
                Integer color = formatting.getColor();
                if (color == null) {
                    continue;
                }
                int col = index % PALETTE_COLS;
                int row = index / PALETTE_COLS;
                int px = x + col * (PALETTE_SIZE + PALETTE_GAP);
                int py = y + SV_SIZE + 8 + row * (PALETTE_SIZE + PALETTE_GAP);
                graphics.fill(px, py, px + PALETTE_SIZE, py + PALETTE_SIZE, 0xFF000000 | (color & 0xFFFFFF));
                drawRectBorder(graphics, px, py, PALETTE_SIZE, PALETTE_SIZE);
                index++;
                if (index >= PALETTE_COLS * 2) {
                    break;
                }
            }
        }
    }

    /** Top = transparent, bottom = opaque current RGB over a checkerboard. */
    private void renderAlphaBar(GuiGraphicsExtractor graphics, int left, int top) {
        fillCheckerboard(graphics, left, top, ALPHA_BAR_W, SV_SIZE);
        for (int py = 0; py < SV_SIZE; py++) {
            // top transparent (0), bottom opaque (255)
            int a = Math.round((py / (float) (SV_SIZE - 1)) * 255f);
            int blendedRgb = blendOverChecker(left, top + py, rgb, a);
            graphics.fill(left, top + py, left + ALPHA_BAR_W, top + py + 1, 0xFF000000 | blendedRgb);
        }
        drawRectBorder(graphics, left, top, ALPHA_BAR_W, SV_SIZE);
        int cursorY = top + Math.round((alpha / 255f) * (SV_SIZE - 1));
        graphics.fill(left - 2, cursorY - 1, left + ALPHA_BAR_W + 2, cursorY + 2, 0xFFFFFFFF);
        graphics.fill(left - 1, cursorY, left + ALPHA_BAR_W + 1, cursorY + 1, 0xFF000000);
    }

    private static void fillCheckerboard(GuiGraphicsExtractor graphics, int left, int top, int w, int h) {
        final int light = 0xFFFFFFFF;
        final int dark = 0xFFBFBFBF;
        final int cell = 4;
        for (int py = 0; py < h; py += cell) {
            for (int px = 0; px < w; px += cell) {
                boolean darkCell = ((px / cell) + (py / cell)) % 2 == 0;
                int x1 = left + px;
                int y1 = top + py;
                int x2 = Math.min(left + w, x1 + cell);
                int y2 = Math.min(top + h, y1 + cell);
                graphics.fill(x1, y1, x2, y2, darkCell ? dark : light);
            }
        }
    }

    /** Composite translucent RGB over a checkerboard cell color (opaque result). */
    private static int blendOverChecker(int absX, int absY, int rgb, int alpha) {
        boolean darkCell = ((absX / 4) + (absY / 4)) % 2 == 0;
        int bg = darkCell ? 0xBFBFBF : 0xFFFFFF;
        int a = Math.max(0, Math.min(255, alpha));
        int inv = 255 - a;
        int r = ((((rgb >> 16) & 0xFF) * a) + (((bg >> 16) & 0xFF) * inv)) / 255;
        int g = ((((rgb >> 8) & 0xFF) * a) + (((bg >> 8) & 0xFF) * inv)) / 255;
        int b = (((rgb & 0xFF) * a) + ((bg & 0xFF) * inv)) / 255;
        return (r << 16) | (g << 8) | b;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (inside(mouseX, mouseY, x, y, SV_SIZE, SV_SIZE)) {
            draggingSv = true;
            updateSv(mouseX, mouseY);
            return true;
        }
        int hueLeft = x + SV_SIZE + 8;
        if (inside(mouseX, mouseY, hueLeft, y, HUE_BAR_W, SV_SIZE)) {
            draggingHue = true;
            updateHue(mouseY);
            return true;
        }
        if (showAlphaBar) {
            int alphaLeft = hueLeft + HUE_BAR_W + 8;
            if (inside(mouseX, mouseY, alphaLeft, y, ALPHA_BAR_W, SV_SIZE)) {
                draggingAlpha = true;
                updateAlpha(mouseY);
                return true;
            }
        }
        if (showVanillaPalette) {
            ChatFormatting[] palette = ChatFormatting.values();
            int index = 0;
            for (ChatFormatting formatting : palette) {
                Integer color = formatting.getColor();
                if (color == null) {
                    continue;
                }
                int col = index % PALETTE_COLS;
                int row = index / PALETTE_COLS;
                int px = x + col * (PALETTE_SIZE + PALETTE_GAP);
                int py = y + SV_SIZE + 8 + row * (PALETTE_SIZE + PALETTE_GAP);
                if (inside(mouseX, mouseY, px, py, PALETTE_SIZE, PALETTE_SIZE)) {
                    setRgb(color);
                    fireChanged();
                    return true;
                }
                index++;
                if (index >= PALETTE_COLS * 2) {
                    break;
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (draggingSv) {
            updateSv(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseY);
            return true;
        }
        if (draggingAlpha) {
            updateAlpha(mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            draggingSv = false;
            draggingHue = false;
            draggingAlpha = false;
        }
        return false;
    }

    public int preferredWidth() {
        int w = SV_SIZE + 8 + HUE_BAR_W + 8 + SWATCH_SIZE;
        if (showAlphaBar) {
            w += 8 + ALPHA_BAR_W;
        }
        return w;
    }

    public int preferredHeight() {
        return SV_SIZE + (showVanillaPalette ? 8 + 2 * (PALETTE_SIZE + PALETTE_GAP) : 0);
    }

    /** Creates a hex edit box wired for this panel (caller must add it to the screen). */
    public static EditBox createHexEditBox(Font font, int x, int y, int width, int initialRgb) {
        EditBox box = new EditBox(font, x, y, width, 20, net.minecraft.network.chat.Component.literal("hex"));
        box.setMaxLength(12);
        box.setValue(ColorMath.toHexString(initialRgb));
        return box;
    }

    private void updateSv(double mouseX, double mouseY) {
        sat = clamp01((float) ((mouseX - x) / (SV_SIZE - 1)));
        val = clamp01(1f - (float) ((mouseY - y) / (SV_SIZE - 1)));
        rgb = ColorMath.hsvToRgb(hue, sat, val);
        fireChanged();
    }

    private void updateHue(double mouseY) {
        hue = clamp01((float) ((mouseY - y) / (SV_SIZE - 1))) * 360f;
        rgb = ColorMath.hsvToRgb(hue, sat, val);
        fireChanged();
    }

    private void updateAlpha(double mouseY) {
        // top = 0 (transparent), bottom = 255 (opaque)
        int next = Math.round(clamp01((float) ((mouseY - y) / (SV_SIZE - 1))) * 255f);
        if (next == alpha) {
            return;
        }
        alpha = next;
        onAlphaChanged.accept(alpha);
    }

    private void fireChanged() {
        onChanged.accept(rgb);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static void drawRectBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        graphics.fill(x - 1, y - 1, x + w + 1, y, 0xFF000000);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF000000);
        graphics.fill(x - 1, y - 1, x, y + h + 1, 0xFF000000);
        graphics.fill(x + w, y - 1, x + w + 1, y + h + 1, 0xFF000000);
    }
}
