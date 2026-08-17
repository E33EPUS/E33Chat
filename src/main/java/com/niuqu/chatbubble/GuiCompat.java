package com.niuqu.chatbubble;

import com.niuqu.chatbubble.mixin.ScreenAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GUI/API compatibility helpers for pre-1.20 client classes.
 */
public final class GuiCompat {
    private GuiCompat() {}

    public static ButtonWidget button(Text message, ButtonWidget.PressAction action, int x, int y, int w, int h) {
        //#if MC >= 11903
        return ButtonWidget.builder(message, action).dimensions(x, y, w, h).build();
        //#else
        //$$ return new ButtonWidget(x, y, w, h, message, action);
        //#endif
    }

    public static Identifier id(String namespace, String path) {
        //#if MC >= 12000
        return Identifier.of(namespace, path);
        //#else
        //$$ return new Identifier(namespace, path);
        //#endif
    }

    public static <T extends ClickableWidget> T addDrawableChild(Screen screen, T widget) {
        if (screen == null || widget == null) return widget;
        //#if MC >= 11900
        try {
            return ((ScreenAccessor) screen).e33chat$addDrawableChild(widget);
        } catch (Throwable ignored) {
        }
        //#endif
        if (invokeScreenMethod(screen, "addDrawableChild", widget)) return widget;
        if (invokeScreenMethod(screen, "addSelectableChild", widget)) return widget;
        if (invokeScreenMethod(screen, "addButton", widget)) return widget;
        invokeScreenMethod(screen, "addChild", widget);
        return widget;
    }

    public static void clearChildren(Screen screen) {
        if (screen == null) return;
        //#if MC >= 11900
        try {
            ((ScreenAccessor) screen).e33chat$clearChildren();
            return;
        } catch (Throwable ignored) {
        }
        //#endif
        if (invokeNoArg(screen, "clearChildren")) return;
        clearListField(screen, "children");
        clearListField(screen, "buttons");
        clearListField(screen, "selectables");
        clearListField(screen, "drawables");
    }

    public static void setScreen(MinecraftClient client, Screen screen) {
        if (client == null) return;
        //#if MC >= 11700
        client.setScreen(screen);
        //#else
        //$$ client.openScreen(screen);
        //#endif
    }

    public static Text chatKeyText(MinecraftClient client) {
        //#if MC >= 11700
        return client.options.chatKey.getBoundKeyLocalizedText();
        //#else
        //$$ return client.options.keyChat.getBoundKeyLocalizedText();
        //#endif
    }

    public static boolean matchesChatKey(MinecraftClient client, int keyCode, int scanCode) {
        //#if MC >= 12109
        return client.options.chatKey.matchesKey(new net.minecraft.client.input.KeyInput(keyCode, scanCode, 0));
        //#else
        //#if MC >= 11700
        //$$ return client.options.chatKey.matchesKey(keyCode, scanCode);
        //#else
        //$$ return client.options.keyChat.matchesKey(keyCode, scanCode);
        //#endif
        //#endif
    }

    public static Text doneText() {
        //#if MC >= 11900
        return net.minecraft.screen.ScreenTexts.DONE;
        //#else
        //$$ return com.niuqu.chatbubble.Txt.translatable("gui.done");
        //#endif
    }

    public static Text onText() {
        //#if MC >= 11900
        return net.minecraft.screen.ScreenTexts.ON;
        //#else
        //$$ return com.niuqu.chatbubble.Txt.translatable("options.on");
        //#endif
    }

    public static Text offText() {
        //#if MC >= 11900
        return net.minecraft.screen.ScreenTexts.OFF;
        //#else
        //$$ return com.niuqu.chatbubble.Txt.translatable("options.off");
        //#endif
    }

    public static void setWidgetY(ClickableWidget widget, int y) {
        //#if MC >= 12000
        widget.setY(y);
        //#else
        //$$ setIntField(widget, "y", y);
        //#endif
    }

    public static int getWidgetX(ClickableWidget widget) {
        //#if MC >= 12000
        return widget.getX();
        //#else
        //$$ return getIntField(widget, "x");
        //#endif
    }

    public static int getWidgetY(ClickableWidget widget) {
        //#if MC >= 12000
        return widget.getY();
        //#else
        //$$ return getIntField(widget, "y");
        //#endif
    }

    public static void setWidgetFocused(ClickableWidget widget, boolean focused) {
        //#if MC >= 11904
        widget.setFocused(focused);
        //#else
        //#if MC == 11903
        //$$ try {
        //$$     java.lang.reflect.Method m = ClickableWidget.class.getDeclaredMethod("setFocused", boolean.class);
        //$$     m.setAccessible(true);
        //$$     m.invoke(widget, focused);
        //$$ } catch (Exception ignored) {
        //$$ }
        //#else
        //$$ ((net.minecraft.client.gui.widget.TextFieldWidget) widget).setTextFieldFocused(focused);
        //#endif
        //#endif
    }

    public static void sendChat(ClientPlayNetworkHandler handler, String text) {
        if (handler == null || text == null || text.isEmpty()) return;
        //#if MC >= 11903
        if (text.startsWith("/")) handler.sendChatCommand(text.substring(1));
        else handler.sendChatMessage(text);
        //#else
        //#if MC >= 11900
        //$$ if (MinecraftClient.getInstance().player != null) {
        //$$     MinecraftClient.getInstance().player.sendChatMessage(text, com.niuqu.chatbubble.Txt.empty());
        //$$ }
        //#else
        //$$ if (MinecraftClient.getInstance().player != null) {
        //$$     MinecraftClient.getInstance().player.sendChatMessage(text);
        //$$ }
        //#endif
        //#endif
    }

    public static void sendCommand(ClientPlayNetworkHandler handler, String command) {
        if (command == null || command.isEmpty()) return;
        sendChat(handler, command.startsWith("/") ? command : "/" + command);
    }

    public static SoundEvent soundValue(Object event) {
        if (event instanceof SoundEvent) return (SoundEvent) event;
        //#if MC >= 11903
        if (event instanceof net.minecraft.registry.entry.RegistryEntry<?>) {
            Object value = ((net.minecraft.registry.entry.RegistryEntry<?>) event).value();
            if (value instanceof SoundEvent) return (SoundEvent) value;
        }
        //#endif
        return null;
    }

    @SuppressWarnings("unchecked")
    public static PositionedSoundInstance uiSound(Object event, float volume, float pitch) {
        //#if MC >= 26000
        return SimpleSoundInstance.forUI(soundValue(event), pitch, volume);
        //#else
        //#if MC >= 12111
        return PositionedSoundInstance.ui(soundValue(event), pitch, volume);
        //#else
        //#if MC >= 11903
        //$$ return PositionedSoundInstance.master(soundValue(event), pitch, volume);
        //#else
        //$$ return PositionedSoundInstance.master((SoundEvent) event, pitch, volume);
        //#endif
        //#endif
        //#endif
    }

    public static void renderTooltip(Object ctx, Screen screen, Text text, int x, int y) {
        if (screen == null || text == null) return;
        //#if MC >= 12000
        var tr = MinecraftClient.getInstance().textRenderer;
        ((net.minecraft.client.gui.DrawContext) ctx).drawTooltip(tr, text, x, y);
        //#else
        //$$ screen.renderTooltip((net.minecraft.client.util.math.MatrixStack) ctx, text, x, y);
        //#endif
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void renderTooltipWrapped(Object ctx, Screen screen, java.util.List<? extends net.minecraft.text.OrderedText> lines, int x, int y) {
        if (screen == null || lines == null || lines.isEmpty()) return;
        try {
            //#if MC >= 12106
            var tr = MinecraftClient.getInstance().textRenderer;
            ((net.minecraft.client.gui.DrawContext) ctx).drawTooltip(tr, (java.util.List) lines,
                net.minecraft.client.gui.tooltip.HoveredTooltipPositioner.INSTANCE, x, y, false);
            //#else
            //#if MC >= 12100
            //$$ var tr = MinecraftClient.getInstance().textRenderer;
            //$$ ((net.minecraft.client.gui.DrawContext) ctx).drawTooltip(tr, (java.util.List) lines,
            //$$     net.minecraft.client.gui.tooltip.HoveredTooltipPositioner.INSTANCE, x, y);
            //#else
            //#if MC >= 12000
            //$$ var tr = MinecraftClient.getInstance().textRenderer;
            //$$ ((net.minecraft.client.gui.DrawContext) ctx).drawTooltip(tr, (java.util.List) lines, x, y);
            //#else
            //$$ screen.renderOrderedTooltip((net.minecraft.client.util.math.MatrixStack) ctx, lines, x, y);
            //#endif
            //#endif
            //#endif
        } catch (RuntimeException e) {
            // ModernUI wraps OrderedText in FormattedTextWrapper which breaks
            // DrawContext.drawTooltip's internal Lists.transform() cast to Text.
            // Fall back to single-Text overload with extracted plain text.
            try {
                var tr = MinecraftClient.getInstance().textRenderer;
                StringBuilder sb = new StringBuilder();
                for (var ot : lines) {
                    ot.accept((index, style, codePoint) -> {
                        sb.appendCodePoint(codePoint);
                        return true;
                    });
                    sb.append('\n');
                }
                Text fallback = Txt.literal(sb.toString().trim());
                //#if MC >= 12000
                ((net.minecraft.client.gui.DrawContext) ctx).drawTooltip(tr, fallback, x, y);
                //#else
                //$$ screen.renderTooltip((net.minecraft.client.util.math.MatrixStack) ctx, fallback, x, y);
                //#endif
            } catch (Exception ignored) {
            }
        }
    }

    // ---- reflection helpers (MC < 1.19) ----

    private static boolean invokeNoArg(Object target, String methodName) {
        try {
            Method m = findNoArgMethod(target.getClass(), methodName);
            if (m == null) return false;
            m.setAccessible(true);
            m.invoke(target);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean invokeScreenMethod(Screen screen, String methodName, Object arg) {
        try {
            Method m = findOneArgMethod(screen.getClass(), methodName, arg.getClass());
            if (m == null) return false;
            m.setAccessible(true);
            m.invoke(screen, arg);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Method findNoArgMethod(Class<?> type, String methodName) {
        Class<?> cur = type;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Method findOneArgMethod(Class<?> type, String methodName, Class<?> argType) {
        Class<?> cur = type;
        while (cur != null) {
            for (Method m : cur.getDeclaredMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (m.getName().equals(methodName) && params.length == 1 && params[0].isAssignableFrom(argType)) {
                    return m;
                }
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static void clearListField(Object target, String fieldName) {
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return;
            f.setAccessible(true);
            Object value = f.get(target);
            if (value instanceof java.util.List) {
                ((java.util.List<?>) value).clear();
            }
        } catch (Exception ignored) {
        }
    }

    private static void setIntField(Object target, String fieldName, int value) {
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return;
            f.setAccessible(true);
            f.setInt(target, value);
        } catch (Exception ignored) {
        }
    }

    private static int getIntField(Object target, String fieldName) {
        try {
            Field f = findField(target.getClass(), fieldName);
            if (f == null) return 0;
            f.setAccessible(true);
            return f.getInt(target);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static Field findField(Class<?> type, String fieldName) {
        Class<?> cur = type;
        while (cur != null) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
