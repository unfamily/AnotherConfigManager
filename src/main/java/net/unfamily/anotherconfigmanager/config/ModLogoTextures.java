package net.unfamily.anotherconfigmanager.config;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import net.neoforged.neoforgespi.language.IModInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and caches mod logo textures (same source as NeoForge Mod List).
 */
public final class ModLogoTextures {
    private static final Map<String, Optional<Logo>> CACHE = new ConcurrentHashMap<>();

    private ModLogoTextures() {}

    public record Logo(Identifier location, int width, int height) {}

    public static Optional<Logo> get(String modId) {
        if (modId == null || modId.isBlank()) {
            return Optional.empty();
        }
        return CACHE.computeIfAbsent(modId, ModLogoTextures::load);
    }

    private static Optional<Logo> load(String modId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return Optional.empty();
        }
        Optional<? extends net.neoforged.fml.ModContainer> container = ModList.get().getModContainerById(modId);
        if (container.isEmpty()) {
            return Optional.empty();
        }
        IModInfo info = container.get().getModInfo();
        Optional<String> logoFile = info.getLogoFile();
        if (logoFile.isEmpty()) {
            return Optional.empty();
        }
        TextureManager tm = minecraft.getTextureManager();
        Optional<Pack.ResourcesSupplier> resourcePack = ResourcePackLoader.getPackFor(modId);
        if (resourcePack.isEmpty()) {
            resourcePack = ResourcePackLoader.getPackFor("neoforge");
        }
        if (resourcePack.isEmpty()) {
            return Optional.empty();
        }
        try (PackResources packResources = resourcePack.get().openPrimary(
                new PackLocationInfo("mod/" + modId, Component.empty(), PackSource.BUILT_IN, Optional.empty()))) {
            IoSupplier<InputStream> logoResource = packResources.getRootResource(logoFile.get().split("[/\\\\]"));
            if (logoResource == null) {
                return Optional.empty();
            }
            NativeImage image = NativeImage.read(logoResource.get());
            if (image == null) {
                return Optional.empty();
            }
            int w = image.getWidth();
            int h = image.getHeight();
            String safeId = modId.replace(':', '_').replaceAll("[^a-z0-9_./-]", "_");
            Identifier location = Identifier.fromNamespaceAndPath("iska_lib", "modlogo/" + safeId);
            tm.register(location, new DynamicTexture(() -> "iska_lib mod logo " + modId, image));
            return Optional.of(new Logo(location, w, h));
        } catch (Exception error) {
            return Optional.empty();
        }
    }
}
