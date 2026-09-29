package net.unfamily.anotherconfigmanager.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.unfamily.anotherconfigmanager.AcmConfig;
import net.unfamily.anotherconfigmanager.config.ConfigModBrowserScreen;
import net.neoforged.neoforge.client.gui.ModListScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When {@link AcmConfig#REPLACE_MODS_BUTTON} is enabled, the title/pause Mods button
 * opens the Library mod browser instead of NeoForge {@link ModListScreen}.
 */
@Mixin(value = ModListScreen.class, remap = false)
public class ModListScreenMixin {

    @Shadow
    private Screen parentScreen;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true, remap = false)
    private void acm$redirectModsButtonToBrowser(CallbackInfo ci) {
        if (!AcmConfig.REPLACE_MODS_BUTTON.get()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new ConfigModBrowserScreen(parentScreen));
        ci.cancel();
    }
}
