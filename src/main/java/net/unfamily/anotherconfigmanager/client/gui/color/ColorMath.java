package net.unfamily.anotherconfigmanager.client.gui.color;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Color math helpers: HSV ↔ RGB, tolerant hex parse/format, packed ints by channel order.
 */
public final class ColorMath {
    public record Hsv(float h, float s, float v) {}

    private ColorMath() {}

    public static Hsv rgbToHsv(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float h;
        if (delta == 0f) {
            h = 0f;
        } else if (max == r) {
            h = 60f * (((g - b) / delta) % 6f);
        } else if (max == g) {
            h = 60f * (((b - r) / delta) + 2f);
        } else {
            h = 60f * (((r - g) / delta) + 4f);
        }
        if (h < 0f) {
            h += 360f;
        }
        float s = max == 0f ? 0f : delta / max;
        return new Hsv(h, s, max);
    }

    public static int hsvToRgb(float h, float s, float v) {
        h = ((h % 360f) + 360f) % 360f;
        s = clamp01(s);
        v = clamp01(v);
        float c = v * s;
        float x = c * (1f - Math.abs((h / 60f) % 2f - 1f));
        float m = v - c;
        float r1;
        float g1;
        float b1;
        if (h < 60f) {
            r1 = c;
            g1 = x;
            b1 = 0f;
        } else if (h < 120f) {
            r1 = x;
            g1 = c;
            b1 = 0f;
        } else if (h < 180f) {
            r1 = 0f;
            g1 = c;
            b1 = x;
        } else if (h < 240f) {
            r1 = 0f;
            g1 = x;
            b1 = c;
        } else if (h < 300f) {
            r1 = x;
            g1 = 0f;
            b1 = c;
        } else {
            r1 = c;
            g1 = 0f;
            b1 = x;
        }
        int r = Math.round((r1 + m) * 255f);
        int g = Math.round((g1 + m) * 255f);
        int b = Math.round((b1 + m) * 255f);
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** Packs ARGB channels (0–255) into a 32-bit int. */
    public static int packArgb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int withAlpha(int rgb, int alpha) {
        return (rgb & 0xFFFFFF) | ((alpha & 0xFF) << 24);
    }

    /**
     * Parses hex strings with optional {@code #}, {@code 0x}/{@code 0X}.
     * Accepts 3/4/6/8 digit forms. Returns RGB (no alpha) unless 4/8 digit with alpha in order.
     */
    @Nullable
    public static Integer parseHexRgb(@Nullable String text) {
        Integer packed = parseHexPacked(text, "argb");
        return packed == null ? null : packed & 0xFFFFFF;
    }

    @Nullable
    public static Integer parseHexPacked(@Nullable String text, String order) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.regionMatches(true, 0, "0x", 0, 2)) {
            s = s.substring(2);
        }
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        s = s.trim();
        if (s.isEmpty() || !s.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
            return null;
        }
        if (s.length() == 3 || s.length() == 4) {
            StringBuilder expanded = new StringBuilder(s.length() * 2);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                expanded.append(c).append(c);
            }
            s = expanded.toString();
        }
        int value;
        try {
            value = (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
        if (s.length() == 6) {
            return value & 0xFFFFFF;
        }
        if (s.length() == 8) {
            return rearrangePacked(value, order == null || order.isBlank() ? "argb" : order);
        }
        return null;
    }

    public static String formatHex(int rgbOrArgb, String order, String prefix, boolean uppercase) {
        String ord = order == null || order.isBlank() ? "rgb" : order.toLowerCase(Locale.ROOT);
        boolean withAlpha = ord.length() == 4;
        int packed = withAlpha ? rgbOrArgb : (rgbOrArgb & 0xFFFFFF);
        if (withAlpha && (rgbOrArgb & 0xFF000000) == 0 && (rgbOrArgb >>> 24) == 0) {
            packed = withAlpha(rgbOrArgb, 0xFF);
        }
        int ordered = rearrangePacked(packed, "argb", ord);
        int digits = withAlpha ? 8 : 6;
        String hex = String.format(uppercase ? "%0" + digits + "X" : "%0" + digits + "x", ordered & (digits == 8 ? 0xFFFFFFFFL : 0xFFFFFF));
        if (hex.length() > digits) {
            hex = hex.substring(hex.length() - digits);
        }
        String p = prefix == null ? "#" : prefix;
        return switch (p) {
            case "none", "" -> hex;
            case "0x", "0X" -> "0x" + hex;
            default -> "#" + hex;
        };
    }

    public static String toHexString(int rgb) {
        return formatHex(rgb, "rgb", "#", true);
    }

    /** Interprets {@code packed} as {@code fromOrder}, returns ARGB layout. */
    public static int rearrangePacked(int packed, String fromOrder) {
        return rearrangePacked(packed, fromOrder, "argb");
    }

    public static int rearrangePacked(int packed, String fromOrder, String toOrder) {
        String from = normalizeOrder(fromOrder);
        String to = normalizeOrder(toOrder);
        int[] ch = new int[4];
        // default missing alpha = 255
        ch[0] = 0xFF;
        ch[1] = 0;
        ch[2] = 0;
        ch[3] = 0;
        int width = from.length();
        for (int i = 0; i < width; i++) {
            int shift = (width - 1 - i) * 8;
            int val = (packed >> shift) & 0xFF;
            ch[indexOfChannel(from.charAt(i))] = val;
        }
        int out = 0;
        for (int i = 0; i < to.length(); i++) {
            out = (out << 8) | (ch[indexOfChannel(to.charAt(i))] & 0xFF);
        }
        return out;
    }

    private static String normalizeOrder(String order) {
        if (order == null || order.isBlank()) {
            return "argb";
        }
        return order.toLowerCase(Locale.ROOT).replaceAll("[^argb]", "");
    }

    private static int indexOfChannel(char c) {
        return switch (c) {
            case 'a' -> 0;
            case 'r' -> 1;
            case 'g' -> 2;
            case 'b' -> 3;
            default -> 1;
        };
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
