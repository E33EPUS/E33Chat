package com.niuqu.chatbubble.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The record's {@code with*} methods are hand-written copies of the full
 * 63-component constructor, so a wrong tail argument silently drops unrelated
 * settings. This asserts the general contract instead of trusting review:
 * calling a with-method changes its own component and nothing else.
 */
class ChatBubbleConfigWithMethodsTest {

    /** A base config with distinctive values on the fields most at risk (the
     *  panel-background trio) so a clobbered tail is visible. */
    private static ChatBubbleConfig base() {
        return ChatBubbleConfig.defaults()
            .withPanelBgCrop("0.5,0.5,2.0")
            .withTheme("dark")
            .withQuickChatPhrases(List.of("hi"));
    }

    @Test
    void withThemePreservesPanelBackground() {
        ChatBubbleConfig before = base();
        ChatBubbleConfig after = before.withTheme("light");

        assertEquals("light", after.theme(), "target component changes");
        assertEquals(before.panelBgCrop(), after.panelBgCrop(), "crop must survive a theme switch");
        assertEquals(before.panelBgOpacity(), after.panelBgOpacity(), "opacity must survive a theme switch");
        assertEquals(before.panelBgImage(), after.panelBgImage(), "background image must survive a theme switch");
    }

    @Test
    void everyWithMethodOnlyChangesItsOwnComponent() throws Exception {
        ChatBubbleConfig before = base();
        int checked = 0;
        for (Method m : ChatBubbleConfig.class.getDeclaredMethods()) {
            if (!m.getName().startsWith("with") || m.getParameterCount() != 1) continue;
            m.setAccessible(true);

            String expected = Character.toLowerCase(m.getName().charAt(4)) + m.getName().substring(5);
            Object arg = sampleValue(m.getParameterTypes()[0]);
            if (arg == null) continue;
            checked++;

            ChatBubbleConfig after = (ChatBubbleConfig) m.invoke(before, arg);
            assertNotNull(after, m.getName() + " returned null");

            boolean sawTarget = false;
            for (RecordComponent rc : ChatBubbleConfig.class.getRecordComponents()) {
                Object b = rc.getAccessor().invoke(before);
                Object a = rc.getAccessor().invoke(after);
                if (rc.getName().equals(expected)) {
                    sawTarget = true;
                    continue; // the one component this method is allowed to change
                }
                assertEquals(b, a, m.getName() + " must not change " + rc.getName());
            }
            assertTrue(sawTarget, "no record component matches method " + m.getName());
        }
        assertTrue(checked >= 5, "expected to exercise every with-method, saw " + checked);
    }

    /** A value of the parameter type that differs from the base config's value. */
    private static Object sampleValue(Class<?> type) {
        if (type == String.class) return "zzz-sentinel";
        if (type == int.class) return Integer.MIN_VALUE;
        if (type == long.class) return Long.MIN_VALUE;
        if (type == boolean.class) return true;
        if (type == double.class) return Double.MIN_VALUE;
        if (type == float.class) return Float.MIN_VALUE;
        if (List.class.isAssignableFrom(type)) return List.of("zzz-sentinel");
        return null;
    }
}
