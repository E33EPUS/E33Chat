package com.niuqu.chatbubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout contract for the chat panel width (2.4.4).
 *
 * <p>{@code panel_width} is a PHYSICAL-pixel size. The logical (GUI) width is
 * physicalW / guiScale using the exact — possibly fractional — GUI scale;
 * rounding the scale to an int was the 2.4.3 bug that made the panel's real
 * width drift with the window and misalign on resize.
 *
 * <p>Only the pure static computation is unit-tested here; rendering and
 * resize behavior is verified in-game.
 */
class ChatBubbleScreenLayoutTest {

    // ---- physical pixel semantics: exact conversion at integer scale ----

    @Test
    void integerScale_exactPhysicalWidth() {
        // 800 physical px at scale 2.0 → 400 logical px → 800 physical px back
        assertEquals(400, ChatBubbleScreen.computePanelWidth(800, 2.0, 960, 0, false));
    }

    // ---- the 2.4.3 bug: fractional scale must NOT be rounded to int ----

    @Test
    void fractionalScale_keepsPhysicalWidth() {
        // scale 1.5: old code rounded to 2 → 400 logical → 600 physical (wrong).
        // New code: 800 / 1.5 = 533.33 → 533 logical → ~800 physical (correct).
        assertEquals(533, ChatBubbleScreen.computePanelWidth(800, 1.5, 1280, 0, false));
    }

    @Test
    void fractionalScale25_exact() {
        assertEquals(400, ChatBubbleScreen.computePanelWidth(1000, 2.5, 1024, 0, false));
    }

    @Test
    void fractionalScale2_8_rounds() {
        // 800 / 2.8 = 285.71 → 286
        assertEquals(286, ChatBubbleScreen.computePanelWidth(800, 2.8, 1200, 0, false));
    }

    // ---- small window: hard clamp to the remaining width (never exceed it) ----

    @Test
    void smallWindow_clampsToWindowWidth() {
        // window logical width 640 (< 800 physical at scale 1.0) → panel fills it
        assertEquals(640, ChatBubbleScreen.computePanelWidth(800, 1.0, 640, 0, false));
    }

    @Test
    void smallWindow_withSidebar_clampsToRemaining() {
        // panelX = 90 (sidebar) leaves 550 of a 640-wide window
        assertEquals(550, ChatBubbleScreen.computePanelWidth(800, 1.0, 640, 90, false));
    }

    @Test
    void sidebarOffset_doesNotShrinkWideEnoughPanel() {
        // 400 logical fits in width - panelX = 870 → stays 400
        assertEquals(400, ChatBubbleScreen.computePanelWidth(800, 2.0, 960, 90, false));
    }

    // ---- fullscreen mode: ignore panel_width, fill remaining width ----

    @Test
    void fullscreen_fillsWholeWidth() {
        assertEquals(960, ChatBubbleScreen.computePanelWidth(1000, 2.0, 960, 0, true));
    }

    @Test
    void fullscreen_keepsSidebarSpace() {
        assertEquals(870, ChatBubbleScreen.computePanelWidth(1000, 2.0, 960, 90, true));
    }

    @Test
    void fullscreen_ignoresPanelWidth() {
        // panel_width irrelevant in fullscreen: even a tiny 400 gives full width
        assertEquals(960, ChatBubbleScreen.computePanelWidth(400, 2.0, 960, 0, true));
    }

    // ---- safety floor and degenerate input ----

    @Test
    void floor_atLeast100() {
        // 400 / 5 = 80 → floor 100
        assertEquals(100, ChatBubbleScreen.computePanelWidth(400, 5.0, 1920, 0, false));
    }

    @Test
    void degenerateGuiScale_treatedAsOne() {
        // guiScale <= 0 is a broken window state; fall back to scale 1.0
        assertEquals(800, ChatBubbleScreen.computePanelWidth(800, 0.0, 1920, 0, false));
    }
}
