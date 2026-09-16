package com.niuqu.chatbubble.chat;

import java.util.regex.Pattern;

/**
 * Wildcard matching shared by every platform's sidebar hide list. Kept in one
 * place because the three copies had drifted into two different bugs: Forge
 * and NeoForge quoted the whole pattern before replacing '*', which left it a
 * literal (wildcards never matched), while Fabric spliced the raw user text
 * into a regex and threw PatternSyntaxException on '(' or '['.
 *
 * <p>Syntax: '*' matches any run (including empty), '?' matches exactly one
 * character, everything else is literal. Matching is case-insensitive and
 * anchored to the whole name.
 */
public final class WildcardPatterns {

    private WildcardPatterns() {}

    public static boolean matches(String value, String pattern) {
        if (value == null || pattern == null) return false;
        String trimmed = pattern.trim();
        if (trimmed.isEmpty()) return false;
        return value.toLowerCase(java.util.Locale.ROOT)
            .matches(toRegex(trimmed.toLowerCase(java.util.Locale.ROOT)));
    }

    /** Literal runs are quoted; wildcards become regex operators between them. */
    static String toRegex(String pattern) {
        StringBuilder regex = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '*' || ch == '?') {
                if (literal.length() > 0) {
                    regex.append(Pattern.quote(literal.toString()));
                    literal.setLength(0);
                }
                regex.append(ch == '*' ? ".*" : ".");
            } else {
                literal.append(ch);
            }
        }
        if (literal.length() > 0) regex.append(Pattern.quote(literal.toString()));
        // A pattern made only of wildcards can leave the builder empty for '*'.
        if (regex.length() == 0) regex.append(".*");
        return regex.toString();
    }
}
