package com.niuqu.chatbubble.render;

/**
 * Cross-screen "hide the vanilla HUD while a translucent E33Chat screen is
 * open" flag (2.4.11).
 *
 * Why this exists: the 2.4.9 implementation set {@code options.hideGui}, which
 * is the same flag F1 toggles — {@code GameRenderer} also gates first-person
 * hand rendering on it, so opening the chat panel made the held item (and the
 * hand) disappear. The correct lever is cancelling {@code RenderGuiEvent.Pre},
 * which skips only the HUD layer; this flag carries the intent from the screens
 * to that hook.
 *
 * Reference-counted because screens can nest (chat panel -> config screen ->
 * server config screen): the HUD comes back only when the last one closes.
 */
public final class HudVisibility {

    private static int hides;

    private HudVisibility() {}

    /** Called from a screen's init() (idempotent per screen). */
    public static void push() {
        hides++;
    }

    /** Called from a screen's removed(). */
    public static void pop() {
        if (hides > 0) hides--;
    }

    /** True while at least one E33Chat translucent screen wants the HUD hidden. */
    public static boolean shouldHideHud() {
        return hides > 0;
    }

    /** Disconnect/reset safety: never leave the HUD suppressed across worlds. */
    public static void reset() {
        hides = 0;
    }
}
