package net.unfamily.anotherconfigmanager.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.unfamily.anotherconfigmanager.AnotherConfigManager;

public final class AcmCommandBootstrap {
    private AcmCommandBootstrap() {}

    public static void registerClientCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            Class.forName("net.unfamily.anotherconfigmanager.config.ConfigOpenerCommand")
                    .getMethod("register", CommandDispatcher.class)
                    .invoke(null, dispatcher);
        } catch (Throwable error) {
            AnotherConfigManager.LOGGER.error("Failed to register /another_config_manager config", error);
        }
    }
}
