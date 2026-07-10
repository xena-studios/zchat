package co.xenastudios.zchat.util;

import org.bukkit.entity.Player;

/**
 * Optional PlaceholderAPI bridge. PlaceholderAPI is a soft dependency: present at compile
 * time only, declared {@code required: false} in {@code paper-plugin.yml}. Presence is a
 * one-time class check, and every call is guarded so the class is never touched (and can
 * never {@code NoClassDefFoundError}) when PAPI isn't installed.
 *
 * <p>Placeholders are expanded in the group <em>format template</em> only — never in the
 * player's own message — so a player can't smuggle a {@code %placeholder%} into chat.
 */
public final class Placeholders {

    private static final boolean AVAILABLE = detect();

    private Placeholders() {
    }

    private static boolean detect() {
        try {
            Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** True when PlaceholderAPI is installed. */
    public static boolean available() {
        return AVAILABLE;
    }

    /**
     * Expand {@code %placeholder%} tokens in {@code text} for {@code player}. Returns
     * {@code text} unchanged when PAPI is absent or expansion fails — never throws.
     */
    public static String apply(Player player, String text) {
        if (!AVAILABLE || text == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Throwable ignored) {
            return text;
        }
    }
}
