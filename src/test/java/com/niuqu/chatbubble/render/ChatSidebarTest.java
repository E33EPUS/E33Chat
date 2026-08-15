package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.render.Animation;
import com.niuqu.chatbubble.render.ChatBubbleScreen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatSidebar's rendering and hit-testing need a live Minecraft instance,
 * so only its layout contract is unit-tested here. Sidebar animation and
 * rendering behavior is verified in-game.
 *
 * History note (2.2.0 audit): this file used to hold easing/screenX cases
 * that were tautologies — they asserted hand-copied formulas of animation
 * code that no longer lives in ChatSidebar (state moved back to
 * ChatBubbleScreen in v2.2.0). Fake coverage is worse than none; deleted.
 * Real easing coverage now lives in AnimationTest against the actual
 * Animation class.
 */
class ChatSidebarTest {

    @Test
    void width_constant() {
        assertEquals(90, ChatSidebar.WIDTH);
    }
}
