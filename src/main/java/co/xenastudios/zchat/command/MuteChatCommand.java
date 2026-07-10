package co.xenastudios.zchat.command;

import co.xenastudios.zchat.ZChatPlugin;
import co.xenastudios.zchat.config.Settings;
import co.xenastudios.zchat.util.Text;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /mutechat} — toggle the global chat mute. While muted, only players holding
 * {@code zchat.bypass.mute} can talk. The mute state lives in {@code ChatState} (memory
 * only), so it survives {@code /zchat reload} but resets on restart.
 */
public final class MuteChatCommand {

    private MuteChatCommand() {
    }

    public static void register(Commands registrar, ZChatPlugin plugin) {
        Settings.MuteChat cfg = plugin.settings().muteChat();
        if (!cfg.spec().enabled()) {
            return;
        }
        registrar.register(
                Commands.literal("mutechat")
                        .executes(ctx -> {
                            run(plugin, Cmd.sender(ctx));
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                "Toggle the global chat mute.",
                cfg.spec().aliases());
    }

    private static void run(ZChatPlugin plugin, CommandSender sender) {
        Settings s = plugin.settings();
        Settings.MuteChat cfg = s.muteChat();
        if (!Cmd.allowed(sender, cfg.spec().permission(), s)) {
            return;
        }
        String actor = sender instanceof Player p ? p.getName() : "Console";
        boolean nowMuted = plugin.chatState().toggleMuted();
        plugin.getServer().broadcast(
                (nowMuted ? cfg.messageMuted() : cfg.messageUnmuted()).resolve(Text.player(actor)));
    }
}
