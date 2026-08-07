package com.niuqu.chatbubble;

/**
 * 跨版本安全的面板模糊入口。
 * 不同 1.21.x 小版本的 framebuffer / GL 映射差异较大，直接访问底层字段容易导致构建失败。
 * 当前降级为空实现，面板背景仍由主题纹理和半透明色块绘制。
 */
public class BlurRenderer {
    public static void blurPanel(int x, int y, int w, int h) {
        // no-op
    }
}
