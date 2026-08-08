package com.niuqu.chatbubble;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.slf4j.Logger;

/**
 * Bridge to kitUIN's ChatImage mod. ChatImage's @ModifyVariable rewrites the
 * vanilla addMessage argument (so the vanilla chat shows [Image] with hover),
 * but our mixin runs first and stores the pre-conversion CICode text — the
 * bubble then shows raw "[[CICode,...]]" with no hover, and ChatImage's
 * render-layer mixin never gets a HoverEvent to draw.
 *
 * This class converts stored chat content back into ChatImage's styled
 * component (green "[Image]" text carrying the show_chatimage HoverEvent) by
 * reflection, so the bubble matches the vanilla chat. Nothing here compiles
 * against ChatImage classes — when the mod is absent every lookup fails and
 * the input passes through unchanged.
 */
public final class ChatImageCompat {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean resolved = false;
    private static boolean available = false;
    private static Method sliceMsg;
    private static Method checkImageUri;
    private static Method messageFromCode;
    private static Constructor<?> booleanCtor;

    private ChatImageCompat() {}

    private static void resolve() {
        if (resolved) return;
        resolved = true;
        try {
            Class<?> tool = Class.forName("io.github.kituin.ChatImageCode.ChatImageCodeTool");
            Class<?> bool = Class.forName("io.github.kituin.ChatImageCode.ChatImageBoolean");
            Class<?> style = Class.forName("io.github.kituin.chatimage.tool.ChatImageStyle");
            sliceMsg = tool.getMethod("sliceMsg", String.class, boolean.class, bool, java.util.function.Consumer.class);
            checkImageUri = tool.getMethod("checkImageUri", List.class, boolean.class, bool);
            messageFromCode = style.getMethod("messageFromCode", Class.forName("io.github.kituin.ChatImageCode.ChatImageCode"));
            booleanCtor = bool.getConstructor(boolean.class);
            available = true;
        } catch (Throwable t) {
            // ChatImage (or its ChatImageCode dependency) is not installed — degrade
            LOGGER.debug("[e33chat] ChatImage not present, image codes shown as plain text: {}", t.toString());
        }
    }

    /**
     * Rebuilds {@code input} so embedded [[CICode,...]] / CQ / http image links
     * become ChatImage's hover-styled components. Returns the input unchanged
     * when ChatImage is absent or conversion fails.
     */
    public static Text convert(Text input) {
        resolve();
        if (!available) return input;
        try {
            Object allString = booleanCtor.newInstance(false);
            @SuppressWarnings("unchecked")
            List<Object> parts = (List<Object>) sliceMsg.invoke(
                null, input.getString(), false, allString,
                (java.util.function.Consumer<Exception>) e ->
                    LOGGER.debug("[e33chat] ChatImage code parse failed: {}", e.toString()));
            checkImageUri.invoke(null, parts, false, allString);
            if (allString.getClass().getMethod("isValue").invoke(allString).equals(Boolean.TRUE)) {
                return input; // plain text, nothing to do
            }
            MutableText out = Text.empty();
            for (Object part : parts) {
                if (part instanceof String s) {
                    out.append(Text.literal(s));
                } else {
                    out.append((MutableText) messageFromCode.invoke(null, part));
                }
            }
            return out;
        } catch (Throwable t) {
            // Any hiccup (mix of code and unparseable text) falls back to plain
            LOGGER.debug("[e33chat] ChatImage convert failed, showing plain text: {}", t.toString());
            return input;
        }
    }
}
