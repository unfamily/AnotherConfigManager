package net.unfamily.anotherconfigmanager;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public final class AcmConfig {
    private AcmConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<? extends String>> CSV_MANAGER = BUILDER
            .comment(
                    "CSV binding overrides for Config Opener.",
                    "Mods register defaults via CsvRuleRegistry.register(modId, path, ruleString).",
                    "Each entry: {len}{SEP}{rulePath};{modId}:{type}:{configPath};{colDef};...")
            .defineList("csv_manager", ArrayList::new, obj -> obj instanceof String);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> COLOR_MANAGER = BUILDER
            .comment(
                    "Color binding overrides.",
                    "Mods register defaults via ColorRuleRegistry.register(...).",
                    "Each entry: {storage};{order};{modId}:{type}:{primaryPath};{extra...}")
            .defineList("color_manager", ArrayList::new, obj -> obj instanceof String);

    public static final ModConfigSpec.BooleanValue REPLACE_MODS_BUTTON = BUILDER
            .comment(
                    "When true, the Mods button on the title screen and pause menu opens the ACM mod browser",
                    "instead of NeoForge Mod List.",
                    "/another_config_manager config always opens the browser regardless of this flag.")
            .define("replace_mods_button", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
