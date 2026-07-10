package co.xenastudios.zchat.chat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure, server-free filter helpers used by {@link ChatListener}. Kept separate so the
 * matching + censoring logic is unit-testable without a running server.
 */
public final class Filters {

    private Filters() {
    }

    /** True if any pattern matches somewhere in {@code text}. */
    public static boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replace every match of every pattern with {@code censorChar} repeated to the
     * match's length. Returns the original string unchanged when nothing matches.
     */
    public static String censor(String text, List<Pattern> patterns, String censorChar) {
        char c = censorChar == null || censorChar.isEmpty() ? '*' : censorChar.charAt(0);
        String result = text;
        for (Pattern p : patterns) {
            Matcher m = p.matcher(result);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(String.valueOf(c).repeat(m.group().length())));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }
}
