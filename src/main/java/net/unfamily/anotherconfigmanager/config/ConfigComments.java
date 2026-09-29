package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes ModConfigSpec comments (literal and real newlines) for wrapped tooltips / labels.
 */
public final class ConfigComments {
    private static final int TOOLTIP_MAX_WIDTH = 240;

    private ConfigComments() {}

    @Nullable
    public static String normalize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n");
        StringBuilder out = new StringBuilder(text.length());
        boolean lastWasBlank = false;
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                if (!lastWasBlank && !out.isEmpty()) {
                    out.append('\n');
                    lastWasBlank = true;
                }
                continue;
            }
            if (!out.isEmpty() && !lastWasBlank) {
                out.append('\n');
            } else if (lastWasBlank && !out.isEmpty()) {
                // keep the single blank already appended
            }
            out.append(trimmed);
            lastWasBlank = false;
        }
        String result = out.toString().strip();
        return result.isEmpty() ? null : result;
    }

    public static List<String> lines(@Nullable String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Splits a normalized comment into tooltip lines at {@link #TOOLTIP_MAX_WIDTH}.
     */
    public static List<FormattedCharSequence> tooltipSequences(Font font, @Nullable String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return List.of();
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String line : normalized.split("\n", -1)) {
            if (line.isBlank()) {
                continue;
            }
            out.addAll(font.split(Component.literal(line), TOOLTIP_MAX_WIDTH));
        }
        return out;
    }

    public static List<Component> tooltipComponents(@Nullable String raw) {
        List<Component> out = new ArrayList<>();
        for (String line : lines(raw)) {
            out.add(Component.literal(line));
        }
        return out;
    }
}
