package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.chat.WhisperSignal;
import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.client.MinecraftClient;

/**
 * Outgoing whisper-echo suppression decision (the text-path branch that runs
 * on the system channel when the translation key is gone — NCR-converted
 * servers). The key-path branch stays inside ChatClassifier.classifyByKey for
 * now; both share the same store state machine.
 *
 * Extracted from ChatListenerMixin during the 2.3.14 restructure; behaviour
 * unchanged.
 */
public final class EchoSuppressor {
    private EchoSuppressor() {}

    /** @return true when sysText is our own outgoing whisper echo and was suppressed. */
    public static boolean trySuppressOutgoingEcho(String sysText) {
        boolean hasEchoFlag = ChatMessageStore.hasPendingWhisperEcho();
        boolean hasKw = WhisperSignal.containsZh(sysText)
            || WhisperSignal.EN.matcher(sysText.toLowerCase()).find();
        ChatMessageStore.debugLog(() -> "[e33chat] System(echo check) | text='" + sysText + "' | flag=" + hasEchoFlag + " | kw=" + hasKw);
        if (hasEchoFlag && hasKw) {
            var player = MinecraftClient.getInstance().player;
            boolean otherPlayerFound = false;
            if (player != null && player.networkHandler != null) {
                String myName = player.getName().getString();
                String skipTarget = ChatMessageStore.getPendingWhisperTarget();
                for (var info : player.networkHandler.getPlayerList()) {
                    for (String cand : ChatClassifier.nameCandidates(info)) {
                        if (cand.equals(myName) || cand.isEmpty()) continue;
                        if (cand.equals(skipTarget)) continue;
                        int idx = sysText.indexOf(cand);
                        if (idx >= 0 && idx < 30) {
                            otherPlayerFound = true;
                            break;
                        }
                    }
                    if (otherPlayerFound) break;
                }
            }
            if (!otherPlayerFound) {
                ChatMessageStore.consumeWhisperEcho();
                ChatMessageStore.markSuppressCapture();
                ChatMessageStore.debugLog(() -> "[e33chat] System(echo suppressed) | text='" + sysText + "'");
                return true;
            }
        }
        return false;
    }
}
