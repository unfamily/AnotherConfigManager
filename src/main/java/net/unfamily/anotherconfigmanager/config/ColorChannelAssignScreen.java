package net.unfamily.anotherconfigmanager.config;

import net.unfamily.anotherconfigmanager.client.gui.AcmButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * After multi-selecting numeric config paths, asks which path is R/G/B/(A)
 * so channel order is explicit (e.g. RBGA vs ARGB).
 */
public class ColorChannelAssignScreen extends Screen {
    private static final int HEADER_H = 48;
    private static final int FOOTER_MARGIN = 14;
    private static final int TOP_STACK_GAP = 8;
    private static final int BUTTON_H = 20;
    private static final int CONTENT_MARGIN = 20;
    private static final char[] ROLES_RGB = {'R', 'G', 'B'};
    private static final char[] ROLES_ARGB = {'A', 'R', 'G', 'B'};

    public record Candidate(List<String> path, String displayName) {}

    private final ConfigOpenerScreen.SpecScreen parent;
    private final ConfigEditSession session;
    private final String modId;
    private final ModConfig.Type configType;
    private final List<Candidate> candidates;
    private final boolean withAlpha;
    private final char[] roles;
    /** Parallel to {@link #candidates}: assigned role letter, or {@code '?'} if unset. */
    private final char[] assigned;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, HEADER_H, FOOTER_MARGIN);

    private @Nullable StringWidget status;
    private @Nullable LinearLayout buttonRow;
    private final List<AcmButton> roleButtons = new ArrayList<>();

    public ColorChannelAssignScreen(
            ConfigOpenerScreen.SpecScreen parent,
            ConfigEditSession session,
            String modId,
            ModConfig.Type configType,
            List<Candidate> candidates,
            boolean withAlpha
    ) {
        super(Component.translatable("screen.another_config_manager.config.color.assign.title"));
        this.parent = parent;
        this.session = session;
        this.modId = modId;
        this.configType = configType;
        this.candidates = List.copyOf(candidates);
        this.withAlpha = withAlpha;
        this.roles = withAlpha ? ROLES_ARGB : ROLES_RGB;
        this.assigned = new char[this.candidates.size()];
        for (int i = 0; i < assigned.length; i++) {
            assigned[i] = '?';
        }
    }

    @Override
    protected void init() {
        layout.setHeaderHeight(HEADER_H);
        layout.setFooterHeight(FOOTER_MARGIN);

        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(2));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(parent.stickyHeaderTitle(), font));
        header.addChild(new StringWidget(
                Component.translatable("screen.another_config_manager.config.color.assign.hint"),
                font
        ));

        roleButtons.clear();
        int innerW = Math.max(120, width - CONTENT_MARGIN * 2);
        int rowY = layout.getHeaderHeight() + TOP_STACK_GAP + 8;
        for (int i = 0; i < candidates.size(); i++) {
            final int index = i;
            Candidate c = candidates.get(i);
            String label = c.displayName();
            if (label.length() > 28) {
                label = label.substring(0, 27) + "…";
            }
            StringWidget name = new StringWidget(Component.literal(label), font);
            name.setWidth(Math.min(220, innerW - 90));
            name.setPosition(CONTENT_MARGIN, rowY + 4);
            addRenderableWidget(name);

            AcmButton roleBtn = AcmButton.text(72, roleLabel(index), () -> cycleRole(index),
                    Component.translatable(
                            "screen.another_config_manager.config.color.assign.role.tooltip",
                            withAlpha ? ", A" : ""
                    ));
            roleBtn.setPosition(CONTENT_MARGIN + Math.min(230, innerW - 80), rowY);
            roleBtn.setWidth(72);
            roleBtn.setHeight(BUTTON_H);
            roleButtons.add(roleBtn);
            addRenderableWidget(roleBtn);
            rowY += BUTTON_H + 6;
        }

        status = new StringWidget(Component.empty(), font);
        addRenderableWidget(status);

        buttonRow = LinearLayout.horizontal().spacing(8);
        buttonRow.addChild(AcmButton.text(120,
                Component.translatable("screen.another_config_manager.config.color.assign.confirm"),
                this::confirm,
                Component.translatable("screen.another_config_manager.config.color.assign.confirm.tooltip")));
        buttonRow.addChild(AcmButton.text(100,
                Component.translatable("screen.another_config_manager.config.back"), this::onClose));
        buttonRow.visitWidgets(this::addRenderableWidget);

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        refreshStatus();
    }

    private Component roleLabel(int index) {
        char role = assigned[index];
        if (role == '?') {
            return Component.translatable("screen.another_config_manager.config.color.assign.unset");
        }
        return Component.literal(String.valueOf(role));
    }

    private void cycleRole(int index) {
        char current = assigned[index];
        int at = -1;
        for (int i = 0; i < roles.length; i++) {
            if (roles[i] == current) {
                at = i;
                break;
            }
        }
        assigned[index] = roles[(at + 1) % roles.length];
        if (index < roleButtons.size()) {
            roleButtons.get(index).setMessage(roleLabel(index));
        }
        refreshStatus();
    }

    private void refreshStatus() {
        if (status == null) {
            return;
        }
        Set<Character> seen = new HashSet<>();
        boolean complete = true;
        boolean duplicate = false;
        for (char c : assigned) {
            if (c == '?') {
                complete = false;
                continue;
            }
            if (!seen.add(c)) {
                duplicate = true;
            }
        }
        if (duplicate) {
            status.setMessage(Component.translatable("screen.another_config_manager.config.color.assign.duplicate"));
        } else if (!complete || seen.size() != roles.length) {
            status.setMessage(Component.translatable(
                    "screen.another_config_manager.config.color.assign.need_all",
                    withAlpha ? "A,R,G,B" : "R,G,B"
            ));
        } else {
            StringBuilder order = new StringBuilder();
            for (char c : assigned) {
                order.append(Character.toLowerCase(c));
            }
            status.setMessage(Component.translatable(
                    "screen.another_config_manager.config.color.assign.order",
                    order.toString().toUpperCase(Locale.ROOT)
            ));
        }
        int innerW = Math.max(120, width - CONTENT_MARGIN * 2);
        int innerX = CONTENT_MARGIN;
        int buttonsY = height - layout.getFooterHeight() - TOP_STACK_GAP - BUTTON_H;
        int textW = font.width(status.getMessage());
        int sw = Math.min(innerW, Math.max(8, textW));
        status.setWidth(sw);
        status.setPosition(innerX + (innerW - sw) / 2, buttonsY - 16);
    }

    private boolean isAssignmentValid() {
        Set<Character> seen = new HashSet<>();
        for (char c : assigned) {
            if (c == '?' || !seen.add(c)) {
                return false;
            }
        }
        return seen.size() == roles.length;
    }

    private void confirm() {
        if (!isAssignmentValid()) {
            refreshStatus();
            return;
        }
        List<String> dotted = new ArrayList<>(candidates.size());
        StringBuilder order = new StringBuilder(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            dotted.add(ConfigSpecNodes.joinPath(candidates.get(i).path()));
            order.append(Character.toLowerCase(assigned[i]));
        }
        ColorBindingLine.Parsed binding = ColorRuleRegistry.declarePaths(
                modId, configType, dotted, order.toString());
        parent.refreshAfterChildEdit();
        minecraft.setScreen(new ColorEditScreen(
                parent,
                session,
                binding,
                Component.translatable("screen.another_config_manager.config.color.badge")
        ));
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        int innerW = Math.max(120, width - CONTENT_MARGIN * 2);
        int innerX = CONTENT_MARGIN;
        int buttonsY = height - layout.getFooterHeight() - TOP_STACK_GAP - BUTTON_H;
        if (buttonRow != null) {
            buttonRow.arrangeElements();
            int rowW = Math.min(buttonRow.getWidth(), innerW);
            buttonRow.setPosition(innerX + (innerW - rowW) / 2, buttonsY);
            buttonRow.arrangeElements();
        }
        if (status != null) {
            int sw = Math.min(innerW, Math.max(40, font.width(status.getMessage())));
            status.setWidth(sw);
            status.setPosition(innerX + (innerW - sw) / 2, buttonsY - 16);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
