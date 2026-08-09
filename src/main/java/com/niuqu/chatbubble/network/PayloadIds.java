package com.niuqu.chatbubble.network;

import net.minecraft.util.Identifier;

/**
 * Server-safe Identifier factory for network payloads.
 * MUST NOT import any client-only classes (MinecraftClient, Screen, etc.).
 * Extracted from GuiCompat to avoid pulling client classes into server-side
 * payload static initializers, which causes NoClassDefFoundError on dedicated servers.
 */
public final class PayloadIds {
    private PayloadIds() {}

    public static Identifier of(String path) {
        //#if MC >= 12000
        return Identifier.of("e33chat", path);
        //#else
        //$$ return new Identifier("e33chat", path);
        //#endif
    }
}
