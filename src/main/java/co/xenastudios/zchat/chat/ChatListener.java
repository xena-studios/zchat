package co.xenastudios.zchat.chat;

import co.xenastudios.zchat.ZChatPlugin;
import co.xenastudios.zchat.config.Settings;
import co.xenastudios.zchat.util.Placeholders;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * The one always-on listener — the whole chat pipeline. Runs on the async chat thread
 * (Paper) / a region thread (Folia); every value it reads comes from the immutable
 * {@link Settings} snapshot or the thread-safe {@link ChatState}, so it never touches
 * {@code getConfig()}, re-parses a static message, or recompiles a regex on the hot path.
 *
 * <p>Order of operations, cheapest gate first: global mute → per-player cooldown →
 * word/pattern filter → group formatting (via a viewer-unaware renderer, rendered once)
 * → hide the message from players who toggled chat off.
 */
public final class ChatListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ZChatPlugin plugin;

    public ChatListener(ZChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Settings s = plugin.settings();
        ChatState st = plugin.chatState();

        // 1) Global mute.
        Settings.MuteChat mute = s.muteChat();
        if (mute.spec().enabled() && st.isMuted() && !player.hasPermission("zchat.bypass.mute")) {
            event.setCancelled(true);
            player.sendMessage(mute.messageBlocked().parsed());
            return;
        }

        // 2) Per-player cooldown (read-only check — only marked as sent once the message
        //    survives the filter below, so a blocked message never burns the cooldown).
        Settings.Cooldown cd = s.cooldown();
        boolean cooldownApplies = cd.enabled() && !player.hasPermission("zchat.bypass.cooldown");
        long now = System.currentTimeMillis();
        if (cooldownApplies) {
            long remaining = st.remaining(id, cd.millis(), now);
            if (remaining > 0) {
                event.setCancelled(true);
                long secs = (remaining + 999) / 1000;
                player.sendMessage(cd.message().resolve(
                        Placeholder.unparsed("seconds", Long.toString(secs))));
                return;
            }
        }

        // 3) Word / pattern filter.
        Settings.Filter filter = s.filter();
        if (filter.enabled() && !filter.patterns().isEmpty()
                && !player.hasPermission("zchat.bypass.filter")) {
            String text = PLAIN.serialize(event.message());
            if (filter.mode() == Settings.Filter.Mode.BLOCK) {
                if (Filters.matchesAny(text, filter.patterns())) {
                    event.setCancelled(true);
                    player.sendMessage(filter.messageBlocked().parsed());
                    return; // blocked — cooldown deliberately not marked
                }
            } else { // CENSOR
                String censored = Filters.censor(text, filter.patterns(), filter.censorChar());
                if (!censored.equals(text)) {
                    event.message(Component.text(censored));
                }
            }
        }

        // The message is going through: start the cooldown now.
        if (cooldownApplies) {
            st.markSent(id, now);
        }

        // 4) Group formatting.
        Settings.Formatting fmt = s.formatting();
        if (fmt.enabled()) {
            // Optionally let the sender colour their own message — with a RESTRICTED tag
            // set (colours/decorations only), so no click/hover/insert injection.
            if (!fmt.colorPermission().isBlank() && player.hasPermission(fmt.colorPermission())) {
                event.message(PlayerColors.parse(PLAIN.serialize(event.message())));
            }
            String template = resolveGroupFormat(player, fmt);
            event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) ->
                    render(template, source, displayName, message)));
        }

        // 5) Hide the message from players who toggled chat off (keep the sender).
        event.viewers().removeIf(viewer -> viewer instanceof Player p
                && !p.getUniqueId().equals(id)
                && st.isHidden(p.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.chatState().forget(event.getPlayer().getUniqueId());
    }

    /** First group (highest weight) the player matches, else the default format. */
    private static String resolveGroupFormat(Player player, Settings.Formatting fmt) {
        for (Settings.Group group : fmt.groups()) {
            if (group.isOpen() || player.hasPermission(group.permission())) {
                return group.format();
            }
        }
        return fmt.defaultFormat();
    }

    private static Component render(String template, Player source, Component displayName, Component message) {
        try {
            // Expand PlaceholderAPI tokens in the format template (never the player's
            // message); a no-op when PAPI isn't installed.
            String expanded = Placeholders.apply(source, template);
            return MM.deserialize(expanded,
                    Placeholder.unparsed("player", source.getName()),
                    Placeholder.component("displayname", displayName),
                    Placeholder.component("message", message));
        } catch (Exception e) {
            // Never drop a message over a bad template: fall back to a plain rendering.
            return Component.text().append(displayName).append(Component.text(": ")).append(message).build();
        }
    }
}
