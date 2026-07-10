package co.xenastudios.zchat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerColorsTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void colourTagsAreHonoured() {
        Component c = PlayerColors.parse("<red>hello");
        assertEquals("hello", PLAIN.serialize(c));
        assertEquals(NamedTextColor.RED, c.color());
    }

    @Test
    void clickTagIsNotInterpreted() {
        // A dangerous tag must be left as literal text (never turned into a real event),
        // so the raw "<click...>" survives into the output rather than being consumed.
        Component c = PlayerColors.parse("<click:run_command:/op me>gift");
        assertNull(c.clickEvent(), "click tag must not become a real ClickEvent");
        assertTrue(PLAIN.serialize(c).contains("click"), "dangerous tag should remain literal");
        assertTrue(PLAIN.serialize(c).contains("gift"));
    }

    @Test
    void hoverAndInsertTagsAreNotInterpreted() {
        Component c = PlayerColors.parse("<hover:show_text:'x'><insert:/op>hi");
        assertNull(c.hoverEvent());
        assertTrue(PLAIN.serialize(c).contains("hover") || PLAIN.serialize(c).contains("insert"));
        assertTrue(PLAIN.serialize(c).contains("hi"));
    }
}
