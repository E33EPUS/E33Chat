package com.niuqu.chatbubble.compat;

import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModernUIEmojiCompatTest {

    private static final Function<String, String> LOOKUP = s -> switch (s) {
        case ":pig2:" -> "🐖";
        case ":pig:" -> "🐖";
        case ":+1:" -> "👍";
        case ":slightly_smiling_face:" -> "🙂";
        default -> null;
    };

    @Test
    void knownShortcodeIsReplaced() {
        assertEquals("hi 🐖", ModernUIEmojiCompat.replaceAll("hi :pig2:", LOOKUP));
    }

    @Test
    void unknownShortcodeIsLeftAlone() {
        assertEquals(":not_a_real_code:", ModernUIEmojiCompat.replaceAll(":not_a_real_code:", LOOKUP));
    }

    @Test
    void multipleShortcodesAreAllReplaced() {
        assertEquals("🐖 and 👍",
            ModernUIEmojiCompat.replaceAll(":pig2: and :+1:", LOOKUP));
    }

    @Test
    void patternAllowsUnderscorePlusAndMinus() {
        assertEquals("🙂", ModernUIEmojiCompat.replaceAll(":slightly_smiling_face:", LOOKUP));
        assertEquals("👍", ModernUIEmojiCompat.replaceAll(":+1:", LOOKUP));
        assertEquals(":-1:", ModernUIEmojiCompat.replaceAll(":-1:", LOOKUP)); // unknown in fake map
    }

    @Test
    void plainColonTextIsNotTouched() {
        assertEquals("12:30 and http://example.com",
            ModernUIEmojiCompat.replaceAll("12:30 and http://example.com", LOOKUP));
    }
}
