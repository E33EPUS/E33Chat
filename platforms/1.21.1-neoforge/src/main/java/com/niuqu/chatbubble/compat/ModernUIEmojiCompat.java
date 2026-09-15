package com.niuqu.chatbubble.compat;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.components.EditBox;
import org.slf4j.Logger;

/**
 * Bridge to BloCamLimb's ModernUI emoji shortcodes.
 *
 * ModernUI normally transforms shortcodes like {@code :pig2:} in the vanilla
 * {@code ChatScreen.onEdited} responder. E33Chat replaces the chat field's
 * change listener with its own onInputEdited, so that injection never fires.
 * This class mirrors the same hook by reflection: when ModernUI is installed
 * and its emoji shortcode option is enabled, E33Chat asks ModernUI's font
 * manager for the replacement text. Nothing here compiles against ModernUI
 * classes — when the mod is absent the lookup fails and this becomes a no-op.
 */
public final class ModernUIEmojiCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Same shape as ModernUI's shortcode pattern: {@code :word-or-+-:}.
     */
    private static final Pattern SHORTCODE_PATTERN = Pattern.compile(":[A-Za-z0-9_+\\-]+:");

    private static boolean resolved;
    private static boolean available;
    private static boolean enabledFlag = true;
    private static Object manager;
    private static Method lookupMethod;

    private ModernUIEmojiCompat() {}

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> clientClass = Class.forName("icyllis.modernui.mc.ModernUIClient");
            try {
                Field enabledField = clientClass.getField("sEmojiShortcodes");
                enabledFlag = enabledField.getBoolean(null);
            } catch (NoSuchFieldException e) {
                // Field renamed/removed in a future ModernUI version: assume enabled.
                enabledFlag = true;
            }
            Class<?> managerClass = Class.forName("icyllis.modernui.mc.FontResourceManager");
            Method getInstance = managerClass.getMethod("getInstance");
            manager = getInstance.invoke(null);
            lookupMethod = managerClass.getMethod("lookupEmojiShortcode", String.class);
            available = true;
        } catch (Throwable t) {
            LOGGER.debug("[e33chat] ModernUI emoji shortcodes not available: {}", t.toString());
        }
    }

    /**
     * True when ModernUI is installed and its emoji shortcode option is on.
     */
    public static boolean isEnabled() {
        resolve();
        return available && enabledFlag;
    }

    /**
     * Looks up a full shortcode (with colons, e.g. {@code :pig2:}) through
     * ModernUI's font manager. Returns null when ModernUI is absent or the
     * shortcode is unknown.
     */
    public static String lookup(String shortcode) {
        resolve();
        if (!available || shortcode == null) return null;
        try {
            return (String) lookupMethod.invoke(manager, shortcode);
        } catch (Throwable t) {
            LOGGER.debug("[e33chat] ModernUI emoji lookup failed: {}", t.toString());
            return null;
        }
    }

    /**
     * Replaces every known ModernUI shortcode in the given EditBox, preserving
     * the cursor as if the player had typed the emoji in place. Commands are
     * intentionally left untouched, matching ModernUI's behaviour.
     *
     * @return true if at least one shortcode was replaced
     */
    public static boolean replaceIn(EditBox input) {
        if (!isEnabled() || input == null) return false;
        String text = input.getValue();
        if (text.indexOf(':') < 0 || text.startsWith("/")) return false;
        boolean any = false;
        while (true) {
            Matcher matcher = SHORTCODE_PATTERN.matcher(input.getValue());
            boolean replaced = false;
            while (matcher.find()) {
                String shortcode = matcher.group();
                String replacement = lookup(shortcode);
                if (replacement != null) {
                    input.setHighlightPos(matcher.start());
                    input.setCursorPosition(matcher.end());
                    input.insertText(replacement);
                    any = true;
                    replaced = true;
                    break;
                }
            }
            if (!replaced) break;
        }
        return any;
    }

    /**
     * Pure string replacement helper, used by unit tests to pin down the
     * matching/order rules without needing a live ModernUI install.
     */
    static String replaceAll(String text, Function<String, String> lookup) {
        Matcher matcher = SHORTCODE_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length());
        int last = 0;
        while (matcher.find()) {
            String shortcode = matcher.group();
            String replacement = lookup.apply(shortcode);
            if (replacement == null) continue;
            sb.append(text, last, matcher.start()).append(replacement);
            last = matcher.end();
        }
        sb.append(text, last, text.length());
        return sb.toString();
    }
}
