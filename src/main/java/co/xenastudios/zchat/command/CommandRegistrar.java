package co.xenastudios.zchat.command;

import co.xenastudios.zchat.ZChatPlugin;
import io.papermc.paper.command.brigadier.Commands;

import java.util.logging.Level;

/**
 * Wires every command into the Brigadier registrar. Each command builder self-checks
 * its {@code enabled} flag and only registers when on, so only enabled commands ever
 * exist. Each registration is guarded so one broken command is logged and skipped
 * rather than aborting the rest — stability first.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {
    }

    public static void registerAll(Commands registrar, ZChatPlugin plugin) {
        guard(plugin, "clearchat", () -> ClearChatCommand.register(registrar, plugin));
        guard(plugin, "mutechat", () -> MuteChatCommand.register(registrar, plugin));
        guard(plugin, "togglechat", () -> ToggleChatCommand.register(registrar, plugin));
        guard(plugin, "zchat", () -> ZChatCommand.register(registrar, plugin));
    }

    private static void guard(ZChatPlugin plugin, String label, Runnable registration) {
        try {
            registration.run();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to register '" + label + "' command(s) (skipped).", t);
        }
    }
}
