package com.niuqu.chatbubble;

/**
 * Cross-version logging facade backed by Log4j2, which is shipped with
 * Minecraft on every supported version (1.16.5 ~ 26.x).  This avoids the
 * com.mojang.logging.LogUtils / org.slf4j dependency that only exists from
 * MC 1.18.2 onwards, so callers no longer need //#if preprocessor guards.
 */
public final class E33Log {
    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger("e33chat");

    private E33Log() {}

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void warn(String message, Throwable t) {
        LOGGER.warn(message, t);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(message, t);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    public static void debug(String message, Object... args) {
        LOGGER.debug(message, args);
    }
}
