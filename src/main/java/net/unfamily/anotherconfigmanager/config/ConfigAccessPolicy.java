package net.unfamily.anotherconfigmanager.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;
import net.unfamily.anotherconfigmanager.command.AcmCommandBootstrap;

/**
 * Access rules for the library config opener (client).
 * CLIENT/COMMON (and STARTUP) are open at permission level 0.
 * SERVER configs are editable only on an integrated host with level 2.
 */
@EventBusSubscriber(modid = AnotherConfigManager.MOD_ID, value = Dist.CLIENT)
public final class ConfigAccessPolicy {
    private ConfigAccessPolicy() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        AcmCommandBootstrap.registerClientCommands(event.getDispatcher());
    }

    public static boolean canOpenType(ModConfig.Type type, CommandSourceStack source) {
        if (type == ModConfig.Type.SERVER) {
            return isIntegratedHost() && hasLevel(source, 2);
        }
        return hasLevel(source, 0);
    }

    public static boolean canEdit(ModConfig.Type type, CommandSourceStack source) {
        return canOpenType(type, source);
    }

    public static boolean canOpenType(ModConfig.Type type) {
        if (type == ModConfig.Type.SERVER) {
            return isIntegratedHost() && localPlayerHasLevel(2);
        }
        return true;
    }

    public static boolean canEdit(ModConfig.Type type) {
        return canOpenType(type);
    }

    public static boolean isIntegratedHost() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.hasSingleplayerServer();
    }

    private static boolean hasLevel(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }

    private static boolean localPlayerHasLevel(int level) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return false;
        }
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return level <= 0;
        }
        return player.getPermissionLevel() >= level;
    }
}
