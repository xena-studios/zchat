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
    void remainingIsPureUntilMarked() {
        ChatState st = new ChatState();
        UUID id = UUID.randomUUID();
        long window = 3000;
        // remaining() alone never starts the cooldown — repeated checks stay 0.
        assertEquals(0, st.remaining(id, window, 1_000));
        assertEquals(0, st.remaining(id, window, 1_500));

        st.markSent(id, 2_000);                         // message actually sent
        assertEquals(2000, st.remaining(id, window, 3_000)); // 1s later, still cooling down
        assertEquals(0, st.remaining(id, window, 6_000));    // window elapsed
    }

    @Test
    void zeroWindowNeverBlocks() {
        ChatState st = new ChatState();
        UUID id = UUID.randomUUID();
        st.markSent(id, 1_000);
        assertEquals(0, st.remaining(id, 0, 1_000));
        assertEquals(0, st.remaining(id, 0, 1_001));
    }

    @Test
    void forgetDropsPlayerState() {
        ChatState st = new ChatState();
        UUID id = UUID.randomUUID();
        st.toggleHidden(id);
        st.markSent(id, 1_000);
        st.forget(id);
        assertFalse(st.isHidden(id));
        assertEquals(0, st.remaining(id, 3000, 1_500)); // cooldown record was cleared
    }
}
