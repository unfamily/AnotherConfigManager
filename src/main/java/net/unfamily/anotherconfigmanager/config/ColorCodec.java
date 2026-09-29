package net.unfamily.anotherconfigmanager.config;

import net.unfamily.iskalib.client.gui.color.ColorMath;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translates declared color storage/order/prefix to/from an internal ARGB int.
 */
public final class ColorCodec {
    private ColorCodec() {}

    public record Spec(String storage, String order, String prefix, String letterCase, double scale) {
        public static Spec parseFormatSpec(String formatSpec) {
            String storage = "hex";
            String order = "argb";
            String prefix = "#";
            String letterCase = "upper";
            double scale = 255d;
            if (formatSpec == null || formatSpec.isBlank()) {
                return new Spec(storage, order, prefix, letterCase, scale);
            }
            String body = formatSpec.trim();
            int i = 0;
            while (i < body.length() && Character.isDigit(body.charAt(i))) {
                i++;
            }
            if (i > 0) {
                try {
                    int sepLen = Integer.parseInt(body.substring(0, i));
                    if (sepLen >= 1 && sepLen <= 8 && i + sepLen < body.length()) {
                        body = body.substring(i + sepLen);
                    }
                } catch (NumberFormatException ignored) {
                    // keep body
                }
            }
            String[] parts = body.split(";", -1);
            if (parts.length > 0 && !parts[0].isBlank()) {
                storage = parts[0].trim().toLowerCase(Locale.ROOT);
            }
            if (parts.length > 1 && !parts[1].isBlank()) {
                order = parts[1].trim().toLowerCase(Locale.ROOT);
            }
            for (int p = 2; p < parts.length; p++) {
                String part = parts[p].trim();
                if (part.startsWith("prefix=")) {
                    prefix = part.substring("prefix=".length());
                } else if (part.startsWith("case=")) {
                    letterCase = part.substring("case=".length());
                } else if (part.startsWith("scale=")) {
                    try {
                        scale = Double.parseDouble(part.substring("scale=".length()));
                    } catch (NumberFormatException ignored) {
                        // keep default
                    }
                }
            }
            return new Spec(storage, order, prefix, letterCase, scale);
        }

        public static Spec fromBinding(ColorBindingLine.Parsed binding) {
            return new Spec(
                    binding.storage(),
                    binding.order(),
                    binding.prefix(),
                    binding.letterCase(),
                    binding.scale()
            );
        }
    }

    @Nullable
    public static Integer decodeSingleValue(@Nullable Object raw, Spec spec) {
        if (raw == null || spec == null) {
            return null;
        }
        return switch (spec.storage()) {
            case "hex" -> {
                if (raw instanceof String s) {
                    Integer packed = ColorMath.parseHexPacked(s, spec.order());
                    yield packed == null ? null : ColorMath.rearrangePacked(packed, spec.order(), "argb");
                }
                yield null;
            }
            case "int" -> {
                if (raw instanceof Number n) {
                    yield ColorMath.rearrangePacked(n.intValue(), spec.order(), "argb");
                }
                if (raw instanceof String s) {
                    Integer packed = ColorMath.parseHexPacked(s, spec.order());
                    if (packed != null) {
                        yield ColorMath.rearrangePacked(packed, spec.order(), "argb");
                    }
                    try {
                        yield ColorMath.rearrangePacked(Integer.decode(s.trim()), spec.order(), "argb");
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
                yield null;
            }
            default -> null;
        };
    }

    /**
     * Decodes channel list (0–255 ints or scaled floats) into ARGB using {@code order}
     * (any permutation of {@code rgb} or {@code argb}, e.g. {@code rbga}).
     */
    @Nullable
    public static Integer decodeChannels(List<Object> channelValues, Spec spec) {
        if (channelValues == null || spec == null) {
            return null;
        }
        String order = normalizeChannelOrder(spec.order());
        int expected = order.length();
        if (expected < 3 || channelValues.size() < expected) {
            return null;
        }
        int a = 255;
        int r = 0;
        int g = 0;
        int b = 0;
        for (int i = 0; i < expected; i++) {
            Integer v = channelToByte(channelValues.get(i), spec.scale());
            if (v == null) {
                return null;
            }
            switch (order.charAt(i)) {
                case 'a' -> a = v;
                case 'r' -> r = v;
                case 'g' -> g = v;
                case 'b' -> b = v;
                default -> {
                    return null;
                }
            }
        }
        return ColorMath.packArgb(a, r, g, b);
    }

    public static List<Integer> encodeChannels(int argb, Spec spec) {
        String order = normalizeChannelOrder(spec.order());
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        List<Integer> out = new ArrayList<>(order.length());
        for (int i = 0; i < order.length(); i++) {
            out.add(switch (order.charAt(i)) {
                case 'a' -> a;
                case 'r' -> r;
                case 'g' -> g;
                case 'b' -> b;
                default -> 0;
            });
        }
        return out;
    }

    /** Keeps only a/r/g/b letters; defaults to {@code rgb} or {@code argb} by length hint. */
    public static String normalizeChannelOrder(@Nullable String order) {
        if (order == null || order.isBlank()) {
            return "argb";
        }
        String cleaned = order.toLowerCase(Locale.ROOT).replaceAll("[^argb]", "");
        if (cleaned.length() >= 3) {
            return cleaned;
        }
        return cleaned.contains("a") ? "argb" : "rgb";
    }

    public static String encodeHex(int argb, Spec spec) {
        boolean upper = !"lower".equalsIgnoreCase(spec.letterCase());
        String prefix = spec.prefix();
        if ("none".equalsIgnoreCase(prefix)) {
            prefix = "";
        }
        return ColorMath.formatHex(argb, spec.order(), prefix, upper);
    }

    public static int encodePackedInt(int argb, Spec spec) {
        return ColorMath.rearrangePacked(argb, "argb", spec.order());
    }

    public static Object encodeForStorage(int argb, Spec spec) {
        return switch (spec.storage()) {
            case "int" -> encodePackedInt(argb, spec);
            case "hex" -> encodeHex(argb, spec);
            default -> encodeHex(argb, spec);
        };
    }

    @Nullable
    private static Integer channelToByte(@Nullable Object raw, double scale) {
        if (raw instanceof Number n) {
            double v = n.doubleValue();
            if (scale > 1.5d) {
                return clampByte((int) Math.round(v));
            }
            return clampByte((int) Math.round(v * 255d / (scale <= 0 ? 1d : scale)));
        }
        if (raw instanceof String s) {
            try {
                if (s.contains(".")) {
                    double v = Double.parseDouble(s.trim());
                    return clampByte((int) Math.round(v * 255d));
                }
                return clampByte(Integer.decode(s.trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static int clampByte(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
