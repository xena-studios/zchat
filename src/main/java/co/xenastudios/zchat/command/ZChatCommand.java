package co.xenastudios.zchat.command;

import co.xenastudios.zchat.ZChatPlugin;
import co.xenastudios.zchat.util.BuildInfo;
import co.xenastudios.zchat.util.Schedulers;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /zchat} admin command. Subcommands:
 * <ul>
 *   <li>{@code reload} — re-read + re-validate config and re-apply live values.</li>
 *   <li>{@code info}   — build/version + runtime status.</li>
 * </ul>
 * Gated behind {@code zchat.admin}. Always registered.
 */
public final class ZChatCommand {

    private static final String PERMISSION = "zchat.admin";

    private ZChatCommand() {
    }

    public static void register(Commands registrar, ZChatPlugin plugin) {
        registrar.register(
                Commands.literal("zchat")
                        .executes(ctx -> {
                            usage(Cmd.sender(ctx));
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("reload").executes(ctx -> {
                            reload(plugin, Cmd.sender(ctx));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("info").executes(ctx -> {
                            info(plugin, Cmd.sender(ctx));
                            return Command.SINGLE_SUCCESS;
                        }))
                        .build(),
                "zChat admin command (reload/info).",
                List.of("zc"));
    }

    private static boolean denied(CommandSender sender) {
        if (sender.hasPermission(PERMISSION)) {
            return false;
        }
        sender.sendMessage(Component.text("You don't have permission to do that.", NamedTextColor.RED));
        return true;
    }

    private static void usage(CommandSender sender) {
        if (denied(sender)) {
            return;
        }
        sender.sendMessage(Component.text("Usage: /zchat <reload|info>", NamedTextColor.YELLOW));
    }

    private static void reload(ZChatPlugin plugin, CommandSender sender) {
        if (denied(sender)) {
            return;
        }
        boolean ok = plugin.reload();
        sender.sendMessage(ok
                ? Component.text("zChat config reloaded.", NamedTextColor.GREEN)
                : Component.text("Reload failed — see console. Previous config kept.", NamedTextColor.RED));
    }

    private static void info(ZChatPlugin plugin, CommandSender sender) {
        if (denied(sender)) {
            return;
        }
        BuildInfo build = BuildInfo.load(plugin);
        sender.sendMessage(Component.text("zChat", NamedTextColor.AQUA)
                .append(Component.text(" v" + build.version(), NamedTextColor.GRAY)));
        sender.sendMessage(line("Commit", build.shortCommit()));
        sender.sendMessage(line("Built", build.buildTimestamp()));
        sender.sendMessage(line("Scheduler", Schedulers.isFolia() ? "Folia (regionised)" : "Paper"));
        sender.sendMessage(line("Groups", Integer.toString(plugin.settings().formatting().groups().size())));
        sender.sendMessage(line("Chat muted", Boolean.toString(plugin.chatState().isMuted())));
        sender.sendMessage(line("Online", Integer.toString(plugin.getServer().getOnlinePlayers().size())));
    }

    private static Component line(String key, String value) {
        return Component.text(key + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE));
    }
}
