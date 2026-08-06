package com.niuqu.chatbubble.mixin;
import com.niuqu.chatbubble.BedScreen;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null || !cfg.enabled()) return;
        if (screen instanceof SleepingChatScreen) {
            ci.cancel();
            BedScreen.setScreenBeforeSleep(MinecraftClient.getInstance().currentScreen);
            MinecraftClient.getInstance().setScreen(new BedScreen());
        } else if (screen instanceof ChatScreen chatScreen) {
            ci.cancel();
            String initial = getChatInitialText(chatScreen);
            MinecraftClient.getInstance().setScreen(new ChatBubbleScreen(initial));
        }
    }
    private static String getChatInitialText(ChatScreen chatScreen) {
        try {
            Field f = ChatScreen.class.getDeclaredField("initial");
            f.setAccessible(true);
            String val = (String) f.get(chatScreen);
            return val != null ? val : "";
        } catch (Exception ignored) {}
        for (Field f : ChatScreen.class.getDeclaredFields()) {
            if (f.getType() == String.class) {
                f.setAccessible(true);
                try {
                    String val = (String) f.get(chatScreen);
                    if (val != null && !val.isEmpty()) return val;
                } catch (Exception ignored) {}
            }
        }
        return "";
    }
}