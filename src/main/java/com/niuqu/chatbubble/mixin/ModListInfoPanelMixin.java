package com.niuqu.chatbubble.mixin;

import java.util.List;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.util.Size2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Forge's mod-list description is a static mods.toml string with no i18n hook;
// swap the English fallback for the localized one when the info panel builds its
// lines. Must stay in sync with mod_description in gradle.properties.
@Mixin(targets = "net.minecraftforge.client.gui.ModListScreen$InfoPanel")
public class ModListInfoPanelMixin {
    private static final String FALLBACK_DESC = "Rebuilds the vanilla chat HUD in a chat-app style";

    @Inject(method = "setInfo", at = @At("HEAD"), remap = false)
    private void localizeModDescription(List<String> lines, ResourceLocation logo, Size2i dims,
                                        CallbackInfo ci) {
        if (lines == null) return;
        if (!I18n.exists("e33chat.mod.description")) return;
        String localized = I18n.get("e33chat.mod.description");
        if (localized.equals(FALLBACK_DESC)) return;
        for (int i = 0; i < lines.size(); i++) {
            if (FALLBACK_DESC.equals(lines.get(i))) {
                lines.set(i, localized);
                return;
            }
        }
    }
}
