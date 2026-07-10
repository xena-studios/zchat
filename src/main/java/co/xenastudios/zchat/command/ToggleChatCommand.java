package co.xenastudios.zchat.command;

import co.xenastudios.zchat.ZChatPlugin;
import co.xenastudios.zchat.config.Settings;
import co.xenastudios.zchat.util.Text;
import com.mojang.brigadier.Command;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /togglechat} — a player hides/shows chat for themselves. Toggled-off players
 * simply stop receiving other players' messages (their own messages and command output
 * still reach them). The state lives in {@code ChatState} (memory only).
 */
public final class ToggleChatCommand {

    private ToggleChatCommand() {
    }

    public static void register(Commands registrar, ZChatPlugin plugin) {
        Settings.ToggleChat cfg = plugin.settings().toggleChat();
        if (!cfg.spec().enabled()) {
            return;
        }
        registrar.register(
                Commands.literal("togglechat")
                        .executes(ctx -> {
                            run(plugin, Cmd.sender(ctx));
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                "Toggle whether you see chat.",
                cfg.spec().aliases());
    }

    private static void run(ZChatPlugin plugin, CommandSender sender) {
        Settings s = plugin.settings();
        Settings.ToggleChat cfg = s.toggleChat();
        if (!Cmd.allowed(sender, cfg.spec().permission(), s)) {
            return;
        }
        Player self = Cmd.self(sender, s);
        if (self == null) {
            return;
        }
        boolean nowHidden = plugin.chatState().toggleHidden(self.getUniqueId());
        Text.send(self, nowHidden ? cfg.messageOff() : cfg.messageOn());
    }
}
