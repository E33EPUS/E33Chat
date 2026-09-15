package com.niuqu.chatbubble.image;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import java.io.File;
import java.util.ArrayDeque;
import net.minecraft.client.MinecraftClient;

/**
 * Serial upload pipeline for images: queue + worker thread + server-first /
 * third-party fallback. UI side effects (toasts, input box, sending) are
 * reported through {@link Callbacks} on the main thread.
 *
 * Extracted from ChatBubbleScreen during the 2.3.14 restructure; the queueing
 * and retry semantics are unchanged.
 */
public final class UploadQueue {
    /** One queued upload; when pendingText is set the send is completed
     * automatically after the upload (draft semantics: the user hits enter
     * once, the message goes out when the file is up). */
    public record UploadJob(File file, byte[] bytes, String fileName,
                            boolean emote, String pendingText) {}

    public interface Callbacks {
        /** Upload started: show the busy indicator. */
        void onBusyStart();
        /** Upload finished without error: clear the busy indicator. */
        void onIdle();
        /** Upload failed (or nothing to upload): toast + restore draft. */
        void onFailure();
        /**
         * Rejected before upload: the animated source exceeds what the receiver
         * can render. Carries which limit was broken so the toast can name it.
         */
        void onRejected(AnimatedImageLoader.OverBudget reason);
        /** Emote upload done: send the emote message immediately. */
        void onEmoteSent(String url);
        /** Draft send done: send the text with the real URL substituted. */
        void onSendText(String text);
        /** Plain upload done: insert/replace the CICode in the input box. */
        void onInputImage(String code);
        /** Restore the draft text after a failed send. */
        void onRestoreInput(String text);
    }

    private static final int MAX_JOBS = 8;
    private final ArrayDeque<UploadJob> jobs = new ArrayDeque<>();
    private boolean running;
    private final Callbacks cb;

    public UploadQueue(Callbacks cb) {
        this.cb = cb;
    }

    public boolean enqueue(UploadJob job) {
        if (jobs.size() >= MAX_JOBS) return false;
        jobs.addLast(job);
        drain();
        return true;
    }

    public int pending() { return jobs.size(); }

    /** Runs queued uploads one at a time; the completion callback in
     * finish() calls this again for the next job. */
    private void drain() {
        if (running) return;
        UploadJob job = jobs.pollFirst();
        if (job == null) return;
        running = true;
        cb.onBusyStart();
        ImageLoader.executor().execute(() -> {
            try {
                com.mojang.logging.LogUtils.getLogger().info(
                    "[e33chat] upload start | file={} | emote={} | serverEnabled={}",
                    job.file() != null ? job.file().getName() : job.fileName(), job.emote(),
                    MediaClient.serverEnabled());
                LocalImageSource.PreparedImage prep;
                if (job.file() != null) {
                    LocalImageSource.Prep rep = LocalImageSource.prepare(job.file());
                    if (rep instanceof LocalImageSource.Prep.Rejected rejected) {
                        // Refuse rather than downgrade: a silently re-encoded GIF
                        // arrives as a still frame and reads as "the animation broke".
                        com.mojang.logging.LogUtils.getLogger().info(
                            "[e33chat] upload rejected (animated over budget: {}) | file={}",
                            rejected.reason(), job.file().getName());
                        AnimatedImageLoader.OverBudget reason = rejected.reason();
                        MinecraftClient.getInstance().execute(() -> {
                            running = false;
                            cb.onRejected(reason);
                            if (job.pendingText() != null) cb.onRestoreInput(job.pendingText());
                            drain();
                        });
                        return;
                    }
                    prep = ((LocalImageSource.Prep.Ok) rep).image();
                } else {
                    prep = new LocalImageSource.PreparedImage(job.bytes(), job.fileName());
                }
                if (prep == null) {
                    MinecraftClient.getInstance().execute(() -> {
                        running = false;
                        cb.onFailure();
                        if (job.pendingText() != null) cb.onRestoreInput(job.pendingText());
                        drain();
                    });
                    return;
                }
                finish(job, prep);
            } catch (Throwable t) {
                // Never let a worker crash leak into the queue: reset the latch so
                // queued jobs keep draining and the failure is visible.
                com.mojang.logging.LogUtils.getLogger().error("[e33chat] upload worker crashed", t);
                MinecraftClient.getInstance().execute(() -> {
                    running = false;
                    cb.onFailure();
                    if (job.pendingText() != null) cb.onRestoreInput(job.pendingText());
                    drain();
                });
            }
        });
    }

    private void finish(UploadJob job, LocalImageSource.PreparedImage prep) {
        // The real content type: an untouched GIF must not be announced as PNG,
        // or a server-side store may re-wrap it and lose the animation again.
        var cfg = ChatBubbleClientSetup.config();
        String serverUrl = MediaClient.serverEnabled()
            ? MediaClient.upload(prep.bytes(), prep.contentType())
            : null;
        // Server hosting unavailable (not installed / disabled / failed) — fall back to third-party
        final String url = serverUrl != null
            ? serverUrl
            : ImageUploader.upload(prep.bytes(), prep.fileName(),
                cfg.uploadUrl(), cfg.uploadField(), cfg.uploadExtra(), cfg.uploadResponse());
        com.mojang.logging.LogUtils.getLogger().info("[e33chat] upload {} -> {}", prep.fileName(), url == null ? "FAILED" : url);
        MinecraftClient.getInstance().execute(() -> {
            running = false;
            if (url == null) {
                cb.onFailure();
                // Failed send: restore the draft so the user sees what did not
                // go out.
                if (job.pendingText() != null) cb.onRestoreInput(job.pendingText());
                drain();
                return;
            }
            cb.onIdle();
            // Carry the original file name: image hosts hand back extension-less
            // URLs, and the download side can only recognise an animation by
            // extension or content probe. "name=" is part of the CICode format,
            // so ChatImage and our own parser both read it.
            String nameAttr = "name=" + prep.fileName();
            if (job.emote()) {
                // Emote click = send immediately as a bubble-less emote message.
                cb.onEmoteSent("[[E33Emote,url=" + url + "," + nameAttr + "]]");
            } else if (job.pendingText() != null) {
                // Enter was pressed on a file:// CICode: finish the send now —
                // one enter, no second press, no lost message.
                String finalText = job.pendingText().replaceFirst(
                    "\\[\\[CICode,url=file://[^]]*]]", "[[CICode,url=" + url + "," + nameAttr + "]]");
                cb.onSendText(finalText);
            } else {
                String code = "[[CICode,url=" + url + "," + nameAttr + "]]";
                cb.onInputImage(code);
            }
            drain();
        });
    }
}
