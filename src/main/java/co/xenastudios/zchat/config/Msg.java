package co.xenastudios.zchat.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

/**
 * A configurable, MiniMessage-backed message.
 *
 * <p>Every message is <b>pre-parsed once</b> at config load into {@link #parsed}, so a
 * message with no per-use placeholder is never re-parsed on a hot path. Messages that
 * carry a per-invocation placeholder (e.g. {@code <player>}, {@code <seconds>}) keep
 * their {@link #raw} template and are deserialized with the supplied resolvers only on
 * their (rare) use.
 *
 * <p>Parsing is fully defensive: a malformed template falls back to plain text so a
 * config typo can never throw out of the chat pipeline.
 */
public record Msg(String raw, Component parsed) {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    /** Pre-parse a raw MiniMessage string, falling back to plain text on error. */
    public static Msg of(String raw) {
        String safe = raw == null ? "" : raw;
        Component component;
        try {
            component = MM.deserialize(safe);
        } catch (Exception e) {
            component = Component.text(safe);
        }
        return new Msg(safe, component);
    }

    /**
     * Resolve this message for sending. With no resolvers the pre-parsed component is
     * returned directly; with resolvers the raw template is deserialized so
     * placeholders bind, falling back to the pre-parsed component on error.
     */
    public Component resolve(TagResolver... resolvers) {
        if (resolvers == null || resolvers.length == 0) {
            return parsed;
        }
        try {
            return MM.deserialize(raw, resolvers);
        } catch (Exception e) {
            return parsed;
        }
    }

    /** True when this message is blank (treated as "send nothing"). */
    public boolean isEmpty() {
        return raw == null || raw.isBlank();
    }
}
