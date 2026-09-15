package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.chat.TemplateMatcher;
import com.niuqu.chatbubble.store.ChatMessageStore;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Template layer: server-declared message formats parse exactly (strongest
 * evidence). Unconfigured or unmatched lines fall through to the heuristic
 * guards. Miss diagnostics are rate-limited (60s window / 5 lines).
 *
 * Extracted from ChatListenerMixin during the 2.3.14 restructure; behaviour
 * unchanged.
 */
public final class TemplateLayer {
    private TemplateLayer() {}

    private static long templateMissWindowStart;
    private static int templateMissBurst;

    public static void logTemplateMiss(String text) {
        if (!ChatMessageStore.serverTemplateDebug()) return;
        long now = System.currentTimeMillis();
        if (now - templateMissWindowStart >= 60_000) {
            templateMissWindowStart = now;
            templateMissBurst = 0;
        }
        if (++templateMissBurst > 5) return;
        String s = text.length() <= 100 ? text : text.substring(0, 100) + "…";
        // G4: 诊断信息含已配置模板列表（原始串），方便核对模板是否写错/漏配
        StringBuilder tpl = new StringBuilder();
        for (var t : ChatMessageStore.serverChatTemplates()) tpl.append("\n  chat: ").append(t.raw());
        for (var t : ChatMessageStore.serverWhisperTemplates()) tpl.append("\n  whisper: ").append(t.raw());
        ChatMessageStore.debugLog(() -> "[e33chat] System(template miss) | text='" + s + "' | templates=" + tpl);
    }

    public static boolean isTemplateNameKnown(String name) {
        if (name == null || name.isEmpty()) return false;
        var player = MinecraftClient.getInstance().player;
        if (player != null) {
            String myName = player.getName().getString();
            if (!myName.isEmpty() && (name.equals(myName) || name.contains(myName))) return true;
        }
        return ChatClassifier.resolveOnlinePlayer(name) != null || ChatMessageStore.findSeenUuid(name) != null;
    }

    // Server template parse: exact field split with style-preserving offsets.
    // Returns null on no match (fall back to the guards) or when the line is our
    // own echo (already bubbled via the authoritative player channel / suppressed).
    public static ChatMessageStore.SenderMeta matchByTemplate(Text message, String text) {
        var r = TemplateMatcher.match(text, ChatMessageStore.serverChatTemplates(),
            ChatMessageStore.serverWhisperTemplates(), TemplateLayer::isTemplateNameKnown);
        if (r.isEmpty()) {
            logTemplateMiss(text);
            return null;
        }
        var tpl = r.orElseThrow();
        String verified = tpl.verifiedName();
        var info = ChatClassifier.resolveOnlinePlayer(verified);
        UUID uid = info != null ? info.getProfile().getId() : ChatMessageStore.findSeenUuid(verified);
        String rawName = info != null ? info.getProfile().getName() : verified;
        boolean isSelf = uid != null && MinecraftClient.getInstance().player != null
            && uid.equals(MinecraftClient.getInstance().player.getUuid());
        if (isSelf) {
            if (tpl.whisper()) {
                // outgoing whisper echo — never bubble a second copy; the suppress
                // flag absorbs it when the pipeline reaches addMessage
                ChatMessageStore.markSuppressCapture();
                ChatMessageStore.debugLog(() -> "[e33chat] System(template outgoing whisper) | text='" + text + "'");
                return null;
            }
            // own public echo: the authoritative player channel already bubbled it;
            // keep the decorated name for repost/echo rendering
            ChatMessageStore.cacheOwnDecoratedName(
                templateSlice(message, text, tpl.nameStart(), tpl.nameEnd()));
            ChatMessageStore.debugLog(() -> "[e33chat] System(template own line) | text='" + text + "'");
            return null;
        }
        Text nameComp = templateSlice(message, text, tpl.nameStart(), tpl.nameEnd());
        Text contentComp = templateSlice(message, text, tpl.contentStart(), tpl.contentEnd());
        boolean whisper = tpl.whisper();
        String partner = whisper ? tpl.sender() : null;
        ChatMessageStore.debugLog(() -> "[e33chat] System(template) | text='" + text + "' | name='" + nameComp.getString() + "' | whisper=" + whisper + " | partner=" + partner + " | content='" + contentComp.getString() + "'");
        return new ChatMessageStore.SenderMeta(uid != null ? uid : new UUID(0, 0), nameComp, contentComp,
            false, rawName, whisper, partner);
    }

    // Template-path field slicing: if the captured region contains literal §-codes
    // (some plugins embed raw "§6" text instead of real styles), rebuild it with
    // parseStyledText to render actual colors; otherwise keep the original
    // component slice (preserves real per-run styles like the guards do).
    public static Text templateSlice(Text message, String text, int from, int to) {
        String sub = text.substring(from, to);
        if (sub.indexOf('§') >= 0) return ChatMessageStore.parseStyledText(sub);
        return ChatMessageStore.sliceStyled(message, from, to);
    }
}
