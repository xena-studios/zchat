package co.xenastudios.zchat.chat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;

/**
 * Parses a player's own message when they hold the colour permission — but with a
 * <b>restricted</b> MiniMessage that only understands colour/decoration tags. Dangerous
 * tags ({@code <click>}, {@code <hover>}, {@code <insert>}, {@code <selector>}, …) are
 * NOT resolved, so a player can never craft a clickable/hoverable component that runs a
 * command as whoever reads it. Unrecognised tags are left as literal text.
 */
public final class PlayerColors {

    /** Colour + decoration tags only — deliberately no interaction/entity tags. */
    private static final MiniMessage MM = MiniMessage.builder()
            .tags(TagResolver.resolver(
                    StandardTags.color(),
                    StandardTags.decorations(),
                    StandardTags.gradient(),
                    StandardTags.rainbow(),
                    StandardTags.reset()))
            .build();

    private PlayerColors() {
    }

    /** Parse {@code plain} with the restricted tag set, falling back to literal text. */
    public static Component parse(String plain) {
        try {
            return MM.deserialize(plain);
        } catch (Exception e) {
            return Component.text(plain);
        }
    }
}
