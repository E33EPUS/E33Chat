package com.niuqu.chatbubble.ui;

import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.PanelBackground;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Framing editor for the custom chat-panel background (2.4.12, client-only).
 *
 * The panel is a tall narrow column, so a normal picture loses its sides to the
 * cover crop and there was no way to say which part should survive. This screen
 * shows the whole picture with a selection box locked to the panel's aspect
 * ratio: drag inside the box to move it, scroll to tighten, Confirm writes
 * {@code panel_bg_crop} (normalized center + zoom, so it survives the panel
 * changing shape) and Cancel leaves the config untouched.
 */
public class PanelCropScreen extends Screen {

    private static final int PAD = 16;
    private static final int BTN_H = 20;
    private static final int BTN_W = 90;
    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 8f;

    private final Screen parent;

    private float centerX, centerY, zoom;

    // Layout
    private int imgX, imgY, dispW, dispH;
    private int btnCancelX, btnConfirmX, btnY;
    private boolean dragging;

    public PanelCropScreen(Screen parent) {
        super(Text.translatable("e33chat.crop.title"));
        this.parent = parent;
        PanelBackground.Crop c = PanelBackground.parseCrop(ChatBubbleClientSetup.config().panelBgCrop());
        this.centerX = c.centerX();
        this.centerY = c.centerY();
        this.zoom = c.zoom();
    }

    @Override
    protected void init() {
        // The decode+upload is asynchronous and only the chat panel used to kick
        // it off, so opening this screen straight from the settings screen left
        // it showing nothing at all. Start it here, and if it is still in flight
        // the render pass keeps re-running init (cheap) until the size is known.
        PanelBackground.ensureLoaded();
        lastKnownSize = PanelBackground.imageWidth() * 10000 + PanelBackground.imageHeight();
        layout();
    }

    private int lastKnownSize = -1;

    @Override
    public void tick() {
        // Wait for the picture: without this the screen stays blank forever when
        // it was opened before the first frame ever drew the chat panel.
        if (PanelBackground.available()) {
            int now = PanelBackground.imageWidth() * 10000 + PanelBackground.imageHeight();
            if (now != lastKnownSize) {
                lastKnownSize = now;
                // yarn Screen has no rebuildWidgets(); re-running init() is the
                // equivalent, and this screen builds no widgets of its own.
                clearChildren();
                init();
            }
        }
        super.tick();
    }

    private void layout() {
        int texW = PanelBackground.imageWidth();
        int texH = PanelBackground.imageHeight();
        int availH = height - PAD * 2 - BTN_H - 24;
        int availW = width - PAD * 2;
        if (texW <= 0 || texH <= 0) {
            dispW = dispH = 0;
            imgX = imgY = 0;
        } else {
            float scale = Math.min((float) availW / texW, (float) availH / texH);
            // Never blow a small picture up past its own size; it only looks worse.
            scale = Math.min(scale, 1f);
            dispW = Math.max(1, Math.round(texW * scale));
            dispH = Math.max(1, Math.round(texH * scale));
            imgX = (width - dispW) / 2;
            imgY = PAD + (availH - dispH) / 2;
        }
        btnY = height - PAD - BTN_H;
        btnConfirmX = width - PAD - BTN_W;
        btnCancelX = btnConfirmX - BTN_W - 8;
    }

    /**
     * The selection box in screen coordinates: source rect in picture space,
     * scaled by the picture's own on-screen scale.
     */
    private int[] selectionScreenRect() {
        int texW = PanelBackground.imageWidth();
        int texH = PanelBackground.imageHeight();
        if (texW <= 0 || texH <= 0 || dispW <= 0) return null;
        float aspect = PanelBackground.lastTargetAspect();
        // Integer stand-ins for the panel's shape keep sourceRect's maths intact.
        int targetW = Math.max(1, Math.round(aspect * 10000f));
        int targetH = 10000;
        int[] src = PanelBackground.sourceRect(texW, texH, targetW, targetH,
            new PanelBackground.Crop(centerX, centerY, zoom));
        float scale = (float) dispW / texW;
        int sx = imgX + Math.round(src[0] * scale);
        int sy = imgY + Math.round(src[1] * scale);
        int sw = Math.max(1, Math.round(src[2] * scale));
        int sh = Math.max(1, Math.round(src[3] * scale));
        return new int[]{sx, sy, sw, sh};
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        ChatBubbleTheme.Colors c = Appearance.snapshot();

        Identifier tex = PanelBackground.textureId();
        if (tex != null && dispW > 0) {
            // The whole picture, unscaled by any crop: the point of the editor is
            // to see what is being left out.
            ColoredTextureRenderer.drawWithAlpha(g, tex, imgX, imgY, dispW, dispH,
                0f, 0f, PanelBackground.imageWidth(), PanelBackground.imageHeight(),
                PanelBackground.imageWidth(), PanelBackground.imageHeight(), 1f);
        } else {
            // Never leave the user staring at an empty screen: say why it is empty.
            String reason = PanelBackground.failed() ? "e33chat.crop.failed"
                : PanelBackground.loading() ? "e33chat.crop.loading" : "e33chat.crop.no_image";
            String msg = net.minecraft.text.Text.translatable(reason).getString();
            g.drawText(textRenderer, msg, (width - textRenderer.getWidth(msg)) / 2, height / 2,
                PanelBackground.failed() ? 0xFFFF6666 : c.textSecondary(), false);
        }

        int[] sel = selectionScreenRect();
        if (sel != null) {
            // Dim everything the panel will NOT show.
            int dim = 0x99000000;
            g.fill(imgX, imgY, imgX + dispW, sel[1], dim);
            g.fill(imgX, sel[1] + sel[3], imgX + dispW, imgY + dispH, dim);
            g.fill(imgX, sel[1], sel[0], sel[1] + sel[3], dim);
            g.fill(sel[0] + sel[2], sel[1], imgX + dispW, sel[1] + sel[3], dim);
            // Selection border: 1px, white, plus corner ticks so the shape reads.
            int border = 0xFFFFFFFF;
            g.fill(sel[0], sel[1], sel[0] + sel[2], sel[1] + 1, border);
            g.fill(sel[0], sel[1] + sel[3] - 1, sel[0] + sel[2], sel[1] + sel[3], border);
            g.fill(sel[0], sel[1], sel[0] + 1, sel[1] + sel[3], border);
            g.fill(sel[0] + sel[2] - 1, sel[1], sel[0] + sel[2], sel[1] + sel[3], border);
        }

        // Hint + buttons
        String hint = Text.translatable("e33chat.crop.hint").getString();
        g.drawText(textRenderer, hint, PAD, btnY + 6, c.textSecondary(), false);

        boolean hoverCancel = over(mouseX, mouseY, btnCancelX, btnY, BTN_W, BTN_H);
        boolean hoverConfirm = over(mouseX, mouseY, btnConfirmX, btnY, BTN_W, BTN_H);
        RoundRectRenderer.fill(g, btnCancelX, btnY, btnCancelX + BTN_W, btnY + BTN_H, 4,
            hoverCancel ? 0xFF4A4A52 : 0xFF36363E);
        RoundRectRenderer.fill(g, btnConfirmX, btnY, btnConfirmX + BTN_W, btnY + BTN_H, 4,
            hoverConfirm ? 0xFF3A5FCD : 0xFF2C4A9E);
        String cancelLabel = Text.translatable("gui.cancel").getString();
        String confirmLabel = Text.translatable("gui.done").getString();
        g.drawText(textRenderer, cancelLabel,
            btnCancelX + (BTN_W - textRenderer.getWidth(cancelLabel)) / 2, btnY + 6, 0xFFFFFFFF, false);
        g.drawText(textRenderer, confirmLabel,
            btnConfirmX + (BTN_W - textRenderer.getWidth(confirmLabel)) / 2, btnY + 6, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (over(mouseX, mouseY, btnCancelX, btnY, BTN_W, BTN_H)) {
            close();
            return true;
        }
        if (over(mouseX, mouseY, btnConfirmX, btnY, BTN_W, BTN_H)) {
            ChatBubbleClientSetup.saveConfig(ChatBubbleClientSetup.config().withPanelBgCrop(
                PanelBackground.formatCrop(new PanelBackground.Crop(centerX, centerY, zoom))));
            MinecraftClient.getInstance().setScreen(parent);
            return true;
        }
        int[] sel = selectionScreenRect();
        if (sel != null && over(mouseX, mouseY, sel[0], sel[1], sel[2], sel[3])) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && dispW > 0 && dispH > 0) {
            // Screen delta -> normalized picture delta. Clamped so a stray drag can
            // never push the window off the picture (sourceRect clamps again too).
            centerX = MathHelper.clamp(centerX + (float) dragX / dispW, 0f, 1f);
            centerY = MathHelper.clamp(centerY + (float) dragY / dispH, 0f, 1f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            zoom = MathHelper.clamp(zoom * (1f + 0.12f * (float) scrollY), MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
