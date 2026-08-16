package com.niuqu.chatbubble;

import net.minecraft.client.MinecraftClient;

/**
 * 跨版本安全的面板模糊入口。
 *
 * <p>早期版本使用 raw GL30 framebuffer blit 实现真实模糊（make/ensure/destroy/blit，
 * 直接操作 GL_FRAMEBUFFER_BINDING / viewport / scissor）。在现代 Minecraft 的
 * RenderSystem 渲染管线中，GUI 渲染常被导向 RenderSystem 管理的中间缓冲而非窗口
 * framebuffer，导致一次意外的 blit 或泄漏的 GL 状态让退出服务器后整个画面黑屏。
 *
 * <p>因此该真实模糊路径已被禁用，统一改用纯 GUI 半透明遮罩（overlayFallback），
 * 仅通过 DrawContext.fill() 绘制，不修改任何 GL 状态，与老仓库的空实现行为一致，
 * 不会造成黑屏回归。
 */
public class BlurRenderer {

    /**
     * Volatile 标志：断连一开始即（从网络线程）置位，用于在 {@link #blurPanel}
     * 顶部提前拦截任何渲染操作，避免主线程在 mc.world 尚未置空时仍执行绘制。
     * 进入新世界后由 ChatBubbleClientSetup 的 tick 处理器清除。
     */
    static volatile boolean disconnecting = false;

    /** Called on disconnect to mark the blur renderer as disconnecting. */
    public static void cleanup() {
        disconnecting = true;
    }

    /** Whether a server disconnect is in progress (network thread may set it). */
    public static boolean isDisconnecting() {
        return disconnecting;
    }

    /**
     * Draw a blurred panel background over the given region.
     *
     * @param g DrawContext (1.20+) or MatrixStack (1.16.5-1.19.2) — used only
     *          for the fallback overlay path
     * @param x left edge (inclusive, GUI-space)
     * @param y top edge (inclusive, GUI-space)
     * @param w width
     * @param h height
     */
    public static void blurPanel(Object g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (disconnecting) return;
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        // The overlayFallback (60% black fill) was removed on all versions —
        // it created an unwanted black mask over the world behind the panel.
        // The panel's own PANEL_BG texture + panelOpacity provides sufficient
        // visual separation without darkening the world.
    }
}