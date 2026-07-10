package co.xenastudios.zchat.chat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mutable, thread-safe runtime chat state — deliberately kept out of the immutable
 * {@link co.xenastudios.zchat.config.Settings} snapshot so {@code /zchat reload} never
 * clears it.
 *
 * <p>Holds the global mute flag, the set of players who hid chat via {@code /togglechat},
 * and per-player cooldown timestamps. Every field is concurrent because the chat event
 * fires off the main thread on Paper and off region threads on Folia.
 */
public final class ChatState {

    private final AtomicBoolean muted = new AtomicBoolean(false);
    private final Set<UUID> chatHidden = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> lastMessage = new ConcurrentHashMap<>();

    // ---- global mute -------------------------------------------------------

    public boolean isMuted() {
        return muted.get();
    }

    /** Flip the global mute flag and return the new state. */
    public boolean toggleMuted() {
        boolean next = !muted.get();
        muted.set(next);
        return next;
    }

    // ---- per-player chat visibility ---------------------------------------

    public boolean isHidden(UUID player) {
        return chatHidden.contains(player);
    }

    /** Flip a player's chat-hidden state and return true if chat is now hidden. */
    public boolean toggleHidden(UUID player) {
        if (chatHidden.remove(player)) {
            return false;
        }
        chatHidden.add(player);
        return true;
    }

    // ---- cooldown ----------------------------------------------------------

    /**
     * Milliseconds a player must still wait, or {@code 0} if they may speak now. When
     * {@code 0} is returned the player's timestamp is refreshed to {@code now}.
     */
    public long remainingCooldown(UUID player, long windowMillis, long now) {
        if (windowMillis <= 0) {
            return 0;
        }
        Long last = lastMessage.get(player);
        if (last != null) {
            long elapsed = now - last;
            if (elapsed < windowMillis) {
                return windowMillis - elapsed;
            }
        }
        lastMessage.put(player, now);
        return 0;
    }

    // ---- lifecycle ---------------------------------------------------------

    /** Drop a player's transient state on quit so nothing leaks. */
    public void forget(UUID player) {
        chatHidden.remove(player);
        lastMessage.remove(player);
    }

    public void clear() {
        chatHidden.clear();
        lastMessage.clear();
        muted.set(false);
    }
}
