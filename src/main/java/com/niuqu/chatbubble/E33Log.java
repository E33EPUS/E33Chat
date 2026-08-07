package com.niuqu.chatbubble;

public final class E33Log {
    private static final org.apache.logging.log4j.Logger LOGGER =
        org.apache.logging.log4j.LogManager.getLogger("e33chat");

    private E33Log() {}

    public static void info(String message) {
        LOGGER.info(message);
    }

    public static void warn(String message) {
        LOGGER.warn(message);
    }

    public static void warn(String message, Throwable t) {
        LOGGER.warn(message, t);
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(message, t);
    }
}
