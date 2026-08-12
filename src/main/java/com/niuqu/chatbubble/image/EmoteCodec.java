package com.niuqu.chatbubble.image;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * Inline emote codes: `[:token]` → a full-width space placeholder whose style
 * carries the token (as a SHOW_TEXT hover marker "e33emote:<token>"). The
 * renderer replaces the placeholder with the emote image at draw time.
 *
 * Unknown tokens are left as literal text. Known custom tokens load through
 * ImageLoader (same anti-flood/cache guards as chat images).
 */
public final class EmoteCodec {
    private static final Pattern TOKEN = Pattern.compile("\\[:([^\\]\\r\\n]+)]");
    private static final String PREFIX = "e33emote:";
    /** Full-width space: its width equals the font height in the vanilla font,
     *  so wrapping/measuring stay correct while the image is drawn over it. */
    private static final char PLACEHOLDER = '\u3000';

    private EmoteCodec() {}

    /**
     * Replaces known `[:token]` codes with placeholder characters carrying the
     * token in their hover marker. Returns the input unchanged if nothing matched.
     */
    public static Text process(Text text) {
        if (text == null) return null;
        Matcher m = TOKEN.matcher(text.getString());
        if (!m.find()) return text;
        MutableText out = Text.empty();
        text.visit((style, part) -> {
            int partStart = 0;
            Matcher local = TOKEN.matcher(part);
            while (local.find()) {
                String token = local.group(1).trim();
                if (local.start() > partStart) {
                    out.append(Text.literal(part.substring(partStart, local.start())).fillStyle(style));
                }
                if (!token.isEmpty() && EmoteCatalog.contains(token)) {
                    // Marker rides in Style.insertion (a plain field): HoverEvent's
                    // static init blows up in headless test environments.
                    out.append(Text.literal(String.valueOf(PLACEHOLDER))
                        .fillStyle(style.withInsertion(PREFIX + token)));
                } else {
                    // unknown token: keep the literal text untouched
                    out.append(Text.literal(local.group()).fillStyle(style));
                }
                partStart = local.end();
            }
            if (partStart < part.length()) {
                out.append(Text.literal(part.substring(partStart)).fillStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** Extracts the emote token from a placeholder style, or null. */
    public static String tokenOf(Style style) {
        if (style == null) return null;
        try {
            String s = style.getInsertion();
            if (s != null && s.startsWith(PREFIX)) return s.substring(PREFIX.length());
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    public static boolean isPlaceholder(char c) {
        return c == PLACEHOLDER;
    }
}
