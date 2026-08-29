package com.niuqu.chatbubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout contract for the chat panel width (2.4.4 physical pixels, 2.4.5 cap).
 *
 * <p>{@code panel_width} is a PHYSICAL-pixel size. The logical (GUI) width is
 * physicalW / guiScale using the exact — possibly fractional — GUI scale;
 * rounding the scale to an int was the 2.4.3 bug that made the panel's real
 * width drift with the window and misalign on resize.
 *
 * <p>2.4.5 adds a windowed-mode cap: the panel never exceeds 40% of the window
 * width (a fixed 1000px physical panel otherwise covered 66% of a 2184px
 * window at GUI scale 5). Fullscreen mode opts out of both the cap and
 * {@code panel_width}.
 *
 * <p>Only the pure static computation is unit-tested here; rendering and
 * resize behavior is verified in-game.
 */
class ChatBubbleScreenLayoutTest {

    // ---- physical pixel semantics: exact conversion at integer scale ----

    @Test
    void integerScale_exactPhysicalWidth() {
        // 800 physical px at scale 2.0 → 400 logical px → 800 physical px back.
        // Window 1200 logical (2400 physical): 800/2400 = 33% < 40% cap → untouched.
        assertEquals(400, ChatBubbleScreen.computePanelWidth(800, 2.0, 1200, 0, false));
    }

    // ---- the 2.4.3 bug: fractional scale must NOT be rounded to int ----

    @Test
    void fractionalScale_keepsPhysicalWidth() {
        // scale 1.5: old code rounded to 2 → 400 logical → 600 physical (wrong).
        // New code: 800 / 1.5 = 533.33 → 533 logical → ~800 physical (correct).
        // Window 1400 logical (2100 physical): 800/2100 = 38% < 40% cap.
        assertEquals(533, ChatBubbleScreen.computePanelWidth(800, 1.5, 1400, 0, false));
    }

    @Test
    void fractionalScale25_exact() {
        // cap = 1024 * 0.4 = 409 > 400 → cap not binding
        assertEquals(400, ChatBubbleScreen.computePanelWidth(1000, 2.5, 1024, 0, false));
    }

    @Test
    void fractionalScale2_8_rounds() {
        // 800 / 2.8 = 285.71 → 286
        assertEquals(286, ChatBubbleScreen.computePanelWidth(800, 2.8, 1200, 0, false));
    }

    // ---- 2.4.5: windowed-mode panel capped at 40% of window width ----

    @Test
    void windowedMode_cappedAtFortyPercent() {
        // The 2.4.4 test-report case: 2184px window at scale 5 → width 437.
        // 1000 physical → 200 logical, but cap = 437 * 0.4 = 174.
        assertEquals(174, ChatBubbleScreen.computePanelWidth(1000, 5.0, 437, 0, false));
    }

    @Test
    void windowCap_isScaleInvariant() {
        // Both calls describe a 2000-physical-px window and a 1600-physical-px
        // panel: the result is 40% of the window regardless of GUI scale.
        assertEquals(400, ChatBubbleScreen.computePanelWidth(1600, 2.0, 1000, 0, false));
        assertEquals(200, ChatBubbleScreen.computePanelWidth(1600, 4.0, 500, 0, false));
    }

    @Test
    void windowCap_leaves2560FullscreenPanelIntact() {
        // 2560 fullscreen at scale 4 → width 640; cap 256 > 250 → 1000px panel intact
        assertEquals(250, ChatBubbleScreen.computePanelWidth(1000, 4.0, 640, 0, false));
    }

    @Test
    void fullscreen_ignoresWindowFraction() {
        // Fullscreen mode fills the remaining width even past the 40% cap
        assertEquals(500, ChatBubbleScreen.computePanelWidth(1000, 2.0, 500, 0, true));
    }

    // ---- small window: hard clamp to the remaining width (never exceed it) ----

    @Test
    void smallWindow_fractionCapBindsBeforeWindowWidth() {
        // window logical width 640 (< 800 physical at scale 1.0): the 40% cap
        // (256) binds before the fill-the-window clamp (640)
        assertEquals(256, ChatBubbleScreen.computePanelWidth(800, 1.0, 640, 0, false));
    }

    @Test
    void smallWindow_withSidebar_clampsToRemaining() {
        // panelX = 90 (sidebar) leaves 550 of a 640-wide window; the 40% cap
        // (256) still binds first
        assertEquals(256, ChatBubbleScreen.computePanelWidth(800, 1.0, 640, 90, false));
    }

    @Test
    void sidebarOffset_doesNotShrinkWideEnoughPanel() {
        // 400 logical fits in width - panelX = 1110, and 800/2400 = 33% is
        // under the cap → stays 400
        assertEquals(400, ChatBubbleScreen.computePanelWidth(800, 2.0, 1200, 90, false));
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
    void floor_winsWhenCapBelow100() {
        // 200-logical window: 40% cap = 80 < floor → floor 100
        assertEquals(100, ChatBubbleScreen.computePanelWidth(800, 1.0, 200, 0, false));
    }

    @Test
    void degenerateGuiScale_treatedAsOne() {
        // guiScale <= 0 is a broken window state; fall back to scale 1.0.
        // Window 2000 logical: cap 800 = panel width → cap not binding.
        assertEquals(800, ChatBubbleScreen.computePanelWidth(800, 0.0, 2000, 0, false));
    }
}
