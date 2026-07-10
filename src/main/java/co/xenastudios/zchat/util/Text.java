package co.xenastudios.zchat.util;

import co.xenastudios.zchat.config.Msg;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

/**
 * Tiny sending helper. Resolves a {@link Msg} against optional placeholders and sends
 * it; a blank message is silently skipped so "leave empty to send nothing" works.
 */
public final class Text {

    private Text() {
    }

    public static void send(CommandSender to, Msg msg, TagResolver... resolvers) {
        if (to == null || msg == null || msg.isEmpty()) {
            return;
        }
        to.sendMessage(msg.resolve(resolvers));
    }

    /** Convenience {@code <player>} placeholder (unparsed — names are never markup). */
    public static TagResolver player(String name) {
        return Placeholder.unparsed("player", name == null ? "" : name);
    }
}
