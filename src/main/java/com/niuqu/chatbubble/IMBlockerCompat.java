package com.niuqu.chatbubble;

//#if MC >= 11800
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
//#else
//$$ // 1.16.5 lacks com.mojang.logging/org.slf4j; use System.out.println
//#endif
import java.lang.reflect.Method;

/**
 * Bridge to reserveword's IMBlocker mod. IMBlocker normally listens to vanilla
 * ChatScreen.onChatFieldUpdate to flip the IME to English while typing a
 * command, but e33chat replaces the chat field's change listener with its own
 * onInputEdited, so that injection never fires.
 *
 * This class mirrors the same hook by reflection: it calls
 * MinecraftTextFieldWidget#setPreferredEnglishState(boolean) on the chat field
 * whenever the text changes. Nothing here compiles against IMBlocker classes —
 * when the mod is absent the lookup fails and this becomes a no-op.
 */
public final class IMBlockerCompat {
    //#if MC >= 11800
    private static final Logger LOGGER = LogUtils.getLogger();
    //#endif

    private static boolean resolved = false;
    private static boolean available = false;
    private static Method setPreferredEnglishState;

    private IMBlockerCompat() {}

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> iface = Class.forName("io.github.reserveword.imblocker.common.gui.MinecraftTextFieldWidget");
            setPreferredEnglishState = iface.getMethod("setPreferredEnglishState", boolean.class);
            available = true;
        } catch (Throwable t) {
            // IMBlocker is not installed — degrade to no-op
            //#if MC >= 11800
            LOGGER.debug("[e33chat] IMBlocker not present, IME state sync disabled: {}", t.toString());
            //#else
            //$$ System.out.println("[e33chat] IMBlocker not present, IME state sync disabled: " + t);
            //#endif
        }
    }

    /**
     * Asks IMBlocker (if installed) to set the IME conversion state of the
     * chat field: English while typing a command, native otherwise. No-op when
     * IMBlocker is absent or the call fails.
     */
    public static void setCommandMode(Object textField, boolean command) {
        resolve();
        if (!available || textField == null) return;
        try {
            setPreferredEnglishState.invoke(textField, command);
        } catch (Throwable t) {
            //#if MC >= 11800
            LOGGER.debug("[e33chat] IMBlocker sync failed: {}", t.toString());
            //#else
            //$$ System.out.println("[e33chat] IMBlocker sync failed: " + t);
            //#endif
        }
    }
}
