package net.unfamily.anotherconfigmanager.config;

import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves whether a mod is forced onto the Library {@link ConfigOpener}
 * (toml property, built-in Unfamily claim set, or runtime API claim).
 */
public final class ConfigClaim {
    /** Declared under {@code [modproperties.<modId>]} in neoforge.mods.toml. */
    public static final String PROPERTY_KEY = "another_config_manager";

    /**
     * Unfamily mods not updated in the Library 1.12 config-opener patch.
     * Safe alongside toml claims (double-claim is a no-op).
     */
    public static final Set<String> BUILTIN_CLAIMED_MOD_IDS = Set.of(
            "ae2_storage_warnings",
            "another_dynamics",
            "another_quarries",
            "another_tinkering",
            "colossal_reactors",
            "lightning_generator",
            "mobsmash_ext_species_fix",
            "pattern_crafter",
            "rep_ae2_bridge",
            "rep_ext_reg",
            "rep_up",
            "rs_storage_warnings",
            "seeds_filtering",
            "simp_cm_portal",
            "skb_res_gen_custom",
            "shuriken",
            "sg_addon_shuriken"
    );

    private static final Set<String> RUNTIME_CLAIMS =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    private ConfigClaim() {}

    /** Runtime claim (tests / edge cases). Prefer toml for new mods. */
    public static void claim(String modId) {
        if (modId != null && !modId.isBlank()) {
            RUNTIME_CLAIMS.add(modId.trim());
        }
    }

    public static boolean isClaimed(String modId) {
        if (modId == null || modId.isBlank()) {
            return false;
        }
        String id = modId.trim();
        if (RUNTIME_CLAIMS.contains(id) || BUILTIN_CLAIMED_MOD_IDS.contains(id)) {
            return true;
        }
        return hasTomlClaim(id);
    }

    public static boolean hasTomlClaim(String modId) {
        Optional<? extends ModContainer> container = ModList.get().getModContainerById(modId);
        if (container.isEmpty()) {
            return false;
        }
        return hasTomlClaim(container.get().getModInfo());
    }

    public static boolean hasTomlClaim(IModInfo modInfo) {
        if (modInfo == null) {
            return false;
        }
        Map<String, Object> properties = modInfo.getModProperties();
        if (properties == null || properties.isEmpty()) {
            return false;
        }
        Object value = properties.get(PROPERTY_KEY);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text.trim()) || "1".equals(text.trim());
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return false;
    }

    /** All claimed mod ids currently loaded (builtin + toml + runtime). */
    public static Set<String> loadedClaimedModIds() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        ModList.get().forEachModContainer((id, container) -> {
            if (isClaimed(id)) {
                out.add(id);
            }
        });
        return out;
    }
}
