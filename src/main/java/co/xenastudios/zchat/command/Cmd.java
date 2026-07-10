package co.xenastudios.zchat.command;

import co.xenastudios.zchat.config.Settings;
import co.xenastudios.zchat.util.Text;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Shared command-path helpers: sender resolution, permission gating (with the
 * configured no-permission message), and player-only enforcement. Keeps every command
 * class small and consistent.
 */
public final class Cmd {

    private Cmd() {
    }

    /** The command's sender (the acting {@link CommandSender}). */
    public static CommandSender sender(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender();
    }

    /**
     * True if {@code permission} is blank (no restriction) or the sender holds it.
     * Otherwise sends the configured no-permission message and returns false.
     */
    public static boolean allowed(CommandSender sender, String permission, Settings settings) {
        if (permission == null || permission.isBlank() || sender.hasPermission(permission)) {
            return true;
        }
        Text.send(sender, settings.errors().noPermission());
        return false;
    }

    /**
     * Return the sender as a {@link Player}, or null (after sending the configured
     * player-only message) if the sender is not a player.
     */
    public static Player self(CommandSender sender, Settings settings) {
        if (sender instanceof Player player) {
            return player;
        }
        Text.send(sender, settings.errors().playerOnly());
        return null;
    }
}
