package com.niuqu.chatbubble.mixin;
import com.niuqu.chatbubble.BedScreen;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.GuiCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
//#if MC >= 26200
//$$ import net.minecraft.client.gui.Gui;
//#endif
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.lang.reflect.Field;
//#if MC >= 26200
//$$ @Mixin(value = Gui.class, remap = false)
//#else
@Mixin(MinecraftClient.class)
//#endif
public class MinecraftClientMixin {
    // 26.2: setScreen moved from Minecraft to Gui class; target Gui with remap=false
    //#if MC >= 26200
    //$$ @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true, remap = false)
    //#else
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    //#endif
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        var cfg = ChatBubbleClientSetup.config();
        if (cfg == null || !cfg.enabled()) return;
        if (screen instanceof SleepingChatScreen) {
            ci.cancel();
            BedScreen.setScreenBeforeSleep(MinecraftClient.getInstance().currentScreen);
            GuiCompat.setScreen(MinecraftClient.getInstance(), new BedScreen());
        } else if (screen instanceof ChatScreen chatScreen) {
            ci.cancel();
            String initial = getChatInitialText(chatScreen);
            GuiCompat.setScreen(MinecraftClient.getInstance(), new ChatBubbleScreen(initial));
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
