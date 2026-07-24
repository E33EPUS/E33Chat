package com.niuqu.chatbubble.config;

public class ServerConfig {
    public boolean use_tpa;
    public boolean history_enabled;

    public static ServerConfig defaults() {
        ServerConfig c = new ServerConfig();
        c.use_tpa = false;
        c.history_enabled = false;
        return c;
    }
}
