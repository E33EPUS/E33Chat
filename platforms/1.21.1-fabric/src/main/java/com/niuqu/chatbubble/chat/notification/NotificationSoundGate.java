package com.niuqu.chatbubble.chat.notification;

/**
 * Global notification-sound dedupe: when many banners arrive in a short burst,
 * only the first one actually plays a sound. The 2s window is intentionally
 * hardcoded for now (grill decision).
 */
public final class NotificationSoundGate {
    private static final long COOLDOWN_MS = 2000;
    private static long lastSoundMs = Long.MIN_VALUE;

    private NotificationSoundGate() {}

    /** Runs the sound only if the global cooldown has elapsed; returns true if played. */
    public static boolean tryPlay(Runnable sound) {
        long now = System.currentTimeMillis();
        if (now - lastSoundMs < COOLDOWN_MS) return false;
        lastSoundMs = now;
        sound.run();
        return true;
    }
}
