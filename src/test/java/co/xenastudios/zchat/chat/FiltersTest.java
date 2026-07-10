package co.xenastudios.zchat.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FiltersTest {

    private static List<Pattern> patterns(String... raw) {
        return java.util.Arrays.stream(raw)
                .map(p -> Pattern.compile(p, Pattern.CASE_INSENSITIVE))
                .toList();
    }

    @Test
    void matchesAnyIsCaseInsensitive() {
        assertTrue(Filters.matchesAny("what a BadWord here", patterns("badword")));
        assertFalse(Filters.matchesAny("perfectly fine", patterns("badword")));
    }

    @Test
    void censorReplacesMatchWithSameLength() {
        assertEquals("this is ****", Filters.censor("this is spam", patterns("spam"), "*"));
    }

    @Test
    void censorLeavesCleanTextUntouched() {
        String clean = "hello world";
        assertEquals(clean, Filters.censor(clean, patterns("spam"), "*"));
    }

    @Test
    void censorHandlesMultiplePatternsAndOccurrences() {
        assertEquals("### and ###", Filters.censor("foo and bar", patterns("foo", "bar"), "#"));
    }

    @Test
    void censorFallsBackToStarWhenCharBlank() {
        assertEquals("****", Filters.censor("spam", patterns("spam"), ""));
    }
}
