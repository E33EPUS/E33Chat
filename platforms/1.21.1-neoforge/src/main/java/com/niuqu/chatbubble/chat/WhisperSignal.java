package com.niuqu.chatbubble.chat;

import java.util.regex.Pattern;

/**
 * Whisper-signal keywords — the single constant source for the message
 * pipeline's whisper detection (was duplicated across ChatListenerMixin and
 * MessagePresentation, with drift between the copies).
 *
 * Consumers keep their own matching semantics: the system-chat echo check uses
 * plain substring/word-boundary matching on the whole line, while
 * hasWhisperKeywordBeforeColon adds bracket-stripping and a trailing-token
 * gate. Only the word DATA is shared here.
 */
public final class WhisperSignal {
    private WhisperSignal() {}

    /** Chinese keywords matched by plain contains(); "whisper" also covers "whispers". */
    public static final String[] ZH = {
        "悄悄", "whisper", "对你说", "私聊", "密语", "密聊", "私信", "密谈"
    };

    /** Short English words need word boundaries ("Msg: hi" is a name, "PM to X: hi" is a whisper). */
    public static final Pattern EN = Pattern.compile("\\b(?:pm|message|msg|tell)\\b");

    public static boolean containsZh(String text) {
        if (text == null) return false;
        for (String w : ZH) {
            if (text.contains(w)) return true;
        }
        return false;
    }
}
