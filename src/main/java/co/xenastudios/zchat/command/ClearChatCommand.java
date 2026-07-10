package co.xenastudios.zchat.command;

import co.xenastudios.zchat.ZChatPlugin;
import co.xenastudios.zchat.config.Settings;
import co.xenastudios.zchat.util.Schedulers;
import co.xenastudios.zchat.util.Text;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /clearchat} — flood the chat with blank lines to clear it for everyone, then
 * broadcast who did it. Players holding {@code zchat.bypass.clearchat} keep their view.
 */
public final class ClearChatCommand {

    private ClearChatCommand() {
    }

    public static void register(Commands registrar, ZChatPlugin plugin) {
        Settings.ClearChat cfg = plugin.settings().clearChat();
        if (!cfg.spec().enabled()) {
            return;
        }
        registrar.register(
                Commands.literal("clearchat")
                        .executes(ctx -> {
                            run(plugin, Cmd.sender(ctx));
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                "Clear the chat for everyone.",
                cfg.spec().aliases());
    }

    private static void run(ZChatPlugin plugin, CommandSender sender) {
        Settings s = plugin.settings();
        Settings.ClearChat cfg = s.clearChat();
        if (!Cmd.allowed(sender, cfg.spec().permission(), s)) {
            return;
        }

        String actor = sender instanceof Player p ? p.getName() : "Console";
        Component blank = Component.empty();

        // Iterate players on the correct thread; sending components is itself thread-safe.
        Schedulers.global(plugin, () -> {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.hasPermission("zchat.bypass.clearchat")) {
                    continue;
                }
                for (int i = 0; i < cfg.lines(); i++) {
                    online.sendMessage(blank);
                }
            }
            plugin.getServer().broadcast(cfg.messageCleared().resolve(Text.player(actor)));
        });
    }
}
