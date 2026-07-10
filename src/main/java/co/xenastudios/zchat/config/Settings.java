package co.xenastudios.zchat.config;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Immutable snapshot of all plugin configuration. Loaded once by {@link ConfigLoader}
 * and swapped atomically on {@code /zchat reload}.
 *
 * <p>All player-facing text is stored as {@link Msg} (MiniMessage pre-parsed at load)
 * and every filter pattern is pre-compiled, so the async chat pipeline never reads
 * {@code getConfig()}, re-parses a static message, or recompiles a regex. Structural
 * fields (a command's {@code enabled} and {@code aliases}) are consumed once at
 * registration and are therefore restart-only; message/behaviour fields apply live.
 *
 * <p>Mutable runtime state — the global mute flag, the per-player "chat hidden" set and
 * cooldown timestamps — lives outside this snapshot in {@code chat.ChatState} so a
 * reload never clears it.
 */
public record Settings(
        Errors errors,
        Formatting formatting,
        Filter filter,
        Cooldown cooldown,
        ClearChat clearChat,
        MuteChat muteChat,
        ToggleChat toggleChat
) {

    /** Common structural fields shared by every command. */
    public record CommandSpec(
            boolean enabled,
            List<String> aliases,
            String permission
    ) {}

    /** Shared error messages. */
    public record Errors(
            Msg playerOnly,
            Msg noPermission
    ) {}

    /**
     * Group-based chat formatting. {@code groups} is sorted highest-{@code weight}
     * first so the listener can return the first permission match. {@code defaultFormat}
     * is the fallback when no group matches; {@code colorPermission} (blank = disabled)
     * gates whether a sender may use MiniMessage tags in their own message.
     */
    public record Formatting(
            boolean enabled,
            String colorPermission,
            String defaultFormat,
            List<Group> groups
    ) {}

    /** A single chat-format group: name, tie-break weight, gate permission, template. */
    public record Group(
            String name,
            int weight,
            String permission,
            String format
    ) {
        /** True when this group has no permission gate (applies to everyone). */
        public boolean isOpen() {
            return permission == null || permission.isBlank();
        }
    }

    /** Word/pattern filter. Patterns are pre-compiled, case-insensitive. */
    public record Filter(
            boolean enabled,
            Mode mode,
            String censorChar,
            List<Pattern> patterns,
            Msg messageBlocked
    ) {
        public enum Mode {
            /** Cancel the message and warn the sender. */
            BLOCK,
            /** Replace each match with the censor char and let it through. */
            CENSOR
        }
    }

    /** Per-player minimum delay between messages. */
    public record Cooldown(
            boolean enabled,
            long millis,
            Msg message
    ) {}

    public record ClearChat(
            CommandSpec spec,
            int lines,
            Msg messageCleared
    ) {}

    public record MuteChat(
            CommandSpec spec,
            Msg messageMuted,
            Msg messageUnmuted,
            Msg messageBlocked
    ) {}

    public record ToggleChat(
            CommandSpec spec,
            Msg messageOn,
            Msg messageOff
    ) {}
}
