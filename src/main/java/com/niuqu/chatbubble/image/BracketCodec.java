package com.niuqu.chatbubble.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

/**
 * Parses image bracket codes out of chat content.
 *
 * Two wire tags are accepted so the mod interoperates with both ecosystems:
 *   [[CICode,url=...,name=...]]        (ChatImage)
 *   [[ChatUpgrade,url=...,name=...,type=...]]  (chat-upgrade; type=image assumed)
 *
 * {@link #strip(Text)} removes the bracket blocks from the styled component
 * tree (keeping the surrounding styles) and returns the extracted image refs.
 * {@link #extractFromHover(Text)} recovers image URLs from legacy history
 * lines that were stored as ChatImage-converted components (green "[Image]"
 * text carrying a show_chatimage hover event) before 2.3.10.
 */
public final class BracketCodec {
    /** [tag,attrs] — attribute values may be URL-encoded, commas are not quoted. */
    private static final Pattern BRACKET = Pattern.compile(
        "\\[\\[(ChatUpgrade|CICode),([^\\]]+)\\]\\]", Pattern.CASE_INSENSITIVE);

    public record ImageRef(String url, String name) {}

    public record ParseResult(Text textWithoutImages, List<ImageRef> images) {}

    private BracketCodec() {}

    /**
     * Splits {@code text} into plain segments and image refs. Each image ref
     * remembers the segment index it appeared in; segments render in order,
     * images render after the text as card rows.
     */
    public static ParseResult parse(Text text) {
        if (text == null) return new ParseResult(text, List.of());
        String plain = text.getString();
        Matcher m = BRACKET.matcher(plain);
        if (!m.find()) return new ParseResult(text, List.of());

        List<ImageRef> images = new ArrayList<>();
        MutableText out = Text.empty();
        boolean[] hasText = {false};
        int[] segIndex = {0};
        // Walk the styled tree once, copying every character range that is not
        // part of a bracket block (bracket blocks are stripped, styles kept).
        text.visit((style, part) -> {
            int partStart = 0;
            Matcher local = BRACKET.matcher(part);
            while (local.find()) {
                if (local.start() > partStart) {
                    out.append(Text.literal(part.substring(partStart, local.start())).fillStyle(style));
                    hasText[0] = true;
                    segIndex[0]++;
                }
                ImageRef ref = parseAttrs(local.group(2), local.group(1));
                if (ref != null) images.add(ref);
                partStart = local.end();
            }
            if (partStart < part.length()) {
                out.append(Text.literal(part.substring(partStart)).fillStyle(style));
                hasText[0] = true;
                segIndex[0]++;
            }
            return Optional.empty();
        }, Style.EMPTY);

        // No bracket survived the visit pass (e.g. styles split the code) —
        // fall back to a plain-text strip so the code never shows raw in the bubble.
        if (images.isEmpty() && !hasText[0]) {
            String stripped = m.replaceAll("");
            return new ParseResult(Text.literal(stripped).setStyle(text.getStyle()), List.of());
        }
        return new ParseResult(out, images);
    }

    /**
     * Bubble-side entry: strips bracket codes (new format) or ChatImage hover
     * components (legacy history) and returns the image refs to render.
     */
    public static ParseResult parseOrExtract(Text text) {
        ParseResult r = parse(text);
        if (!r.images().isEmpty() || text == null) return r;
        List<ImageRef> refs = extractFromHover(text);
        if (refs.isEmpty()) return r;
        MutableText out = Text.empty();
        text.visit((style, part) -> {
            net.minecraft.text.HoverEvent hover = style.getHoverEvent();
            if (hover != null && isChatImageHover(hover)) {
                return Optional.empty(); // drop the [Image] placeholder text
            }
            out.append(Text.literal(part).fillStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return new ParseResult(out, refs);
    }

    private static ImageRef parseAttrs(String attrs, String tag) {
        String url = null;
        String name = null;
        String type = null;
        for (String kv : attrs.split(",")) {
            int eq = kv.indexOf('=');
            if (eq <= 0) continue;
            String key = kv.substring(0, eq).trim().toLowerCase();
            String val = kv.substring(eq + 1).trim();
            if (val.isEmpty()) continue;
            switch (key) {
                case "url" -> url = val;
                case "name" -> name = val;
                case "type" -> type = val;
                default -> { }
            }
        }
        if (url == null || url.isBlank()) return null;
        // Only images are rendered as cards; audio/video refs stay stripped
        // (their text is dropped so the raw bracket never shows).
        if (type != null && !type.equalsIgnoreCase("image")) return null;
        return new ImageRef(url, name);
    }

    /**
     * Recovers image URLs from ChatImage-converted components: green
     * "[Image]" text whose style carries a show_chatimage hover event whose
     * custom value is a JSON object like {"url":...,"name":...}.
     */
    public static List<ImageRef> extractFromHover(Text text) {
        if (text == null) return List.of();
        List<ImageRef> out = new ArrayList<>();
        text.visit((style, part) -> {
            net.minecraft.text.HoverEvent hover = style.getHoverEvent();
            if (hover != null && isChatImageHover(hover)) {
                String url = readUrlFromHover(hover);
                if (url != null && !url.isBlank()) {
                    out.add(new ImageRef(url, null));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static boolean isChatImageHover(net.minecraft.text.HoverEvent hover) {
        try {
            String actionId = String.valueOf(hover.getAction());
            return actionId.toLowerCase().contains("chatimage");
        } catch (Throwable t) {
            return false;
        }
    }

    private static String readUrlFromHover(net.minecraft.text.HoverEvent hover) {
        try {
            Object value = hover.getValue(hover.getAction());
            // Custom actions carry whatever their codec decoded — ChatImage's
            // show_chatimage payload is a JSON object {"url":...,"name":...}.
            if (value instanceof com.google.gson.JsonElement je) {
                if (je.isJsonObject() && je.getAsJsonObject().has("url")
                        && je.getAsJsonObject().get("url").isJsonPrimitive()) {
                    return je.getAsJsonObject().get("url").getAsString();
                }
                if (je.isJsonPrimitive() && je.getAsJsonPrimitive().isString()) {
                    return je.getAsString();
                }
            } else if (value instanceof String s) {
                return s;
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }
}
