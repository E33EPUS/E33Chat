package com.niuqu.chatbubble.chat;

/**
 * Shared name-character predicates for the message pipeline.
 */
public final class Names {
    private Names() {}

    public static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}