package co.xenastudios.zchat.chat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStateTest {

    @Test
    void muteTogglesBothWays() {
        ChatState st = new ChatState();
        assertFalse(st.isMuted());
        assertTrue(st.toggleMuted());
        assertTrue(st.isMuted());
        assertFalse(st.toggleMuted());
        assertFalse(st.isMuted());
    }

    @Test
    void hiddenTogglesPerPlayer() {
        ChatState st = new ChatState();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(st.toggleHidden(a));   // now hidden
        assertTrue(st.isHidden(a));
        assertFalse(st.isHidden(b));       // unaffected
        assertFalse(st.toggleHidden(a));  // now shown again
        assertFalse(st.isHidden(a));
    }

    @Test
    void cooldownBlocksWithinWindowThenClears() {
        ChatState st = new ChatState();
        UUID id = UUID.randomUUID();
        long window = 3000;
        assertEquals(0, st.remainingCooldown(id, window, 1_000)); // first message always allowed
        long remaining = st.remainingCooldown(id, window, 2_000); // 1s later, still cooling down
        assertEquals(2000, remaining);
        assertEquals(0, st.remainingCooldown(id, window, 5_000)); // window elapsed
    }

    @Test
    void zeroWindowNeverBlocks() {
        ChatState st = new ChatState();
        UUID id = UUID.randomUUID();
        assertEquals(0, st.remainingCooldown(id, 0, 1_000));
        assertEquals(0, st.remainingCooldown(id, 0, 1_001));
    }

    @Test
    void forgetDropsPlayerState() {
        ChatState st = new ChatState();
        UUID id = UUID.randomUUID();
        st.toggleHidden(id);
        st.remainingCooldown(id, 3000, 1_000);
        st.forget(id);
        assertFalse(st.isHidden(id));
        assertEquals(0, st.remainingCooldown(id, 3000, 1_500)); // cooldown record was cleared
    }
}
