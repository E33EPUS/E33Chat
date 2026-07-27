package com.niuqu.chatbubble.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatContextMenusTest {

    @Test
    void menuX_clampedToRightEdge() {
        int maxX = 0 + 300 - ChatContextMenus.CTX_W - 2;
        assertEquals(maxX, ChatContextMenus.menuX(500, 0, 300));
    }

    @Test
    void menuX_insidePanel() {
        int x = ChatContextMenus.menuX(50, 0, 300);
        assertEquals(50, x);
    }

    @Test
    void menuY_aboveWhenFits() {
        int y = ChatContextMenus.menuY(100, 40, 20, true);
        assertEquals(100 - 40, y);
    }

    @Test
    void menuY_fallsBackBelow() {
        int y = ChatContextMenus.menuY(30, 40, 20, true);
        assertEquals(30 + 4, y);
    }

    @Test
    void isOverItem_inside() {
        assertTrue(ChatContextMenus.isOverItem(10, 10, 5, 5, 20));
    }

    @Test
    void isOverItem_outsideX() {
        assertFalse(ChatContextMenus.isOverItem(200, 10, 5, 5, 20));
    }

    @Test
    void isOverItem_outsideY() {
        assertFalse(ChatContextMenus.isOverItem(10, 100, 5, 5, 20));
    }
}
