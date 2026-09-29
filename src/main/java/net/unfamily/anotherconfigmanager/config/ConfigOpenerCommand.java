package net.unfamily.anotherconfigmanager.config;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;

/**
 * Client command {@code /another_config_manager config [modId]}.
 * Bare {@code config} always opens the Library mod browser.
 * With {@code modId}, opens that mod (native factory including ConfigurationScreen, else Library UI).
 */
public final class ConfigOpenerCommand {
    private static final SuggestionProvider<CommandSourceStack> MOD_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(ConfigDiscovery.modIdsWithConfigs(), builder);

    private ConfigOpenerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("another_config_manager")
                .then(Commands.literal("config")
                        .requires(source -> source.hasPermission(2))
                        .executes(ConfigOpenerCommand::openBare)
                        .then(Commands.argument("modId", StringArgumentType.string())
                                .suggests(MOD_SUGGESTIONS)
                                .executes(context -> open(context, StringArgumentType.getString(context, "modId"))))));
    }

    private static int openBare(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!ConfigOpener.openModBrowser()) {
            source.sendFailure(Component.translatable("commands.another_config_manager.config.browser_failed"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.another_config_manager.config.browser_opened"), false);
        return 1;
    }

    private static int open(CommandContext<CommandSourceStack> context, String modId) {
        CommandSourceStack source = context.getSource();
        Component failure = ConfigOpener.failureReason(modId);
        if (failure != null) {
            source.sendFailure(failure);
            return 0;
        }

        boolean hasOpenable = false;
        for (ModConfig config : ConfigDiscovery.configsFor(modId)) {
            if (ConfigAccessPolicy.canOpenType(config.getType(), source)) {
                hasOpenable = true;
                break;
            }
        }
        if (!hasOpenable) {
            source.sendFailure(Component.translatable("commands.another_config_manager.config.denied", modId));
            return 0;
        }

        if (!ConfigOpener.open(modId)) {
            source.sendFailure(Component.translatable("commands.another_config_manager.config.open_failed", modId));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.another_config_manager.config.opened", modId), false);
        return 1;
    }
}
