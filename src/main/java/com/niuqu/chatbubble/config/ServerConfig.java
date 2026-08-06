package com.niuqu.chatbubble.config;

import java.util.ArrayList;
import java.util.List;

public class ServerConfig {
    public boolean use_tpa;
    public boolean history_enabled;
    public boolean template_debug;
    public List<String> chat_templates = new ArrayList<>();
    public List<String> whisper_templates = new ArrayList<>();
    public static ServerConfig defaults() {
        ServerConfig c = new ServerConfig();
        c.use_tpa = false;
        c.history_enabled = false;
        c.template_debug = false;
        c.chat_templates = new ArrayList<>();
        c.whisper_templates = new ArrayList<>();
        return c;
    }
}