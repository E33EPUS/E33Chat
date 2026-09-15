package com.niuqu.chatbubble.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * Parses image bracket codes out of chat content.
 *
 * Two wire tags are accepted so the mod interoperates with both ecosystems:
 *   [[CICode,url=...,name=...]]        (ChatImage)
 *   [[ChatUpgrade,url=...,name=...,type=...]]  (third-party rich-message mod; type=image assumed)
 *
 * {@link #parse(Component)} removes the bracket blocks from the styled component
 * tree (keeping the surrounding styles) and returns the extracted image refs.
 * {@link #extractFromHover(Component)} recovers image URLs from legacy history
 * lines that were stored as ChatImage-converted components (green "[Image]"
 * text carrying a show_chatimage hover event) before 2.3.10.
 */
public final class BracketCodec {
    /** [tag,attrs] — attribute values may be URL-encoded, commas are not quoted. */
    private static final Pattern BRACKET = Pattern.compile(
        "\\[\\[(ChatUpgrade|CICode|E33Emote),([^\\]]+)\\]\\]", Pattern.CASE_INSENSITIVE);

    public record ImageRef(String url, String name, boolean emote) {
        public ImageRef(String url, String name) { this(url, name, false); }
    }

    public record ParseResult(Component textWithoutImages, List<ImageRef> images) {}

    private BracketCodec() {}

    /**
     * Splits {@code text} into plain segments and image refs. Each image ref
     * remembers the segment index it appeared in; segments render in order,
     * images render after the text as card rows.
     */
    public static ParseResult parse(Component text) {
        if (text == null) return new ParseResult(text, List.of());
        String plain = text.getString();
        Matcher m = BRACKET.matcher(plain);
        if (!m.find()) return new ParseResult(text, List.of());

        List<ImageRef> images = new ArrayList<>();
        MutableComponent out = Component.empty();
        boolean[] hasText = {false};
        // Walk the styled tree once, copying every character range that is not
        // part of a bracket block (bracket blocks are stripped, styles kept).
        text.visit((style, part) -> {
            int partStart = 0;
            Matcher local = BRACKET.matcher(part);
            while (local.find()) {
                if (local.start() > partStart) {
                    out.append(Component.literal(part.substring(partStart, local.start())).withStyle(style));
                    hasText[0] = true;
                }
                ImageRef ref = parseAttrs(local.group(2), local.group(1));
                if (ref != null) images.add(ref);
                partStart = local.end();
            }
            if (partStart < part.length()) {
                out.append(Component.literal(part.substring(partStart)).withStyle(style));
                hasText[0] = true;
            }
            return Optional.empty();
        }, Style.EMPTY);

        // No bracket survived the visit pass (e.g. styles split the code) —
        // fall back to a plain-text strip so the code never shows raw in the bubble.
        if (images.isEmpty() && !hasText[0]) {
            String stripped = m.replaceAll("");
            return new ParseResult(Component.literal(stripped).withStyle(text.getStyle()), List.of());
        }
        return new ParseResult(out, images);
    }

    /**
     * Bubble-side entry: strips bracket codes (new format) or ChatImage hover
     * components (legacy history) and returns the image refs to render.
     */
    public static ParseResult parseOrExtract(Component text) {
        ParseResult r = parse(text);
        if (!r.images().isEmpty() || text == null) return r;
        List<ImageRef> refs = extractFromHover(text);
        if (refs.isEmpty()) refs = extractFromShowTextHover(text);
        if (refs.isEmpty()) return r;
        MutableComponent out = Component.empty();
        text.visit((style, part) -> {
            HoverEvent hover = style.getHoverEvent();
            if (hover != null && (isChatImageHover(hover) || isEasyBotCICodeHover(hover))) {
                return Optional.empty(); // drop the [Image]/summary placeholder text
            }
            out.append(Component.literal(part).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return new ParseResult(out, refs);
    }

    /**
     * Replaces every bracket image code with a plain-text placeholder
     * ("[图片]"/"[Image]") while keeping the surrounding styles. Used when
     * image receiving is disabled — no download is ever triggered.
     */
    public static Component toPlaceholderText(Component text) {
        if (text == null) return null;
        Matcher m = BRACKET.matcher(text.getString());
        if (!m.find()) return text;
        MutableComponent out = Component.empty();
        Component placeholder = Component.translatable("e33chat.image.placeholder")
            .withStyle(ChatFormatting.GREEN);
        text.visit((style, part) -> {
            int partStart = 0;
            Matcher local = BRACKET.matcher(part);
            while (local.find()) {
                if (local.start() > partStart) {
                    out.append(Component.literal(part.substring(partStart, local.start())).withStyle(style));
                }
                out.append(placeholder.copy().withStyle(style));
                partStart = local.end();
            }
            if (partStart < part.length()) {
                out.append(Component.literal(part.substring(partStart)).withStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
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
        // E33Emote is e33chat's own bubble-less emote code; older e33chat
        // builds / other mods see the raw text, ChatImage ignores it.
        return new ImageRef(url, name, tag.equalsIgnoreCase("E33Emote"));
    }

    /**
     * Recovers image URLs from ChatImage-converted components: green
     * "[Image]" text whose style carries a show_chatimage hover event whose
     * custom value is a JSON object like {"url":...,"name":...}.
     */
    public static List<ImageRef> extractFromHover(Component text) {
        if (text == null) return List.of();
        List<ImageRef> out = new ArrayList<>();
        text.visit((style, part) -> {
            HoverEvent hover = style.getHoverEvent();
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

    /**
     * Recovers image URLs from EasyBot's relay format: the visible summary run
     * (e.g. "[图片]") carries a normal SHOW_TEXT hover whose tooltip text is the
     * {@code [[CICode,url=...,name=...]]} bracket. ChatImage understands this
     * format; E33Chat now does too.
     */
    public static List<ImageRef> extractFromShowTextHover(Component text) {
        if (text == null) return List.of();
        List<ImageRef> out = new ArrayList<>();
        text.visit((style, part) -> {
            HoverEvent hover = style.getHoverEvent();
            if (hover != null && isEasyBotCICodeHover(hover)) {
                String tooltip = hoverText(hover);
                if (tooltip != null) {
                    Matcher m = BRACKET.matcher(tooltip);
                    while (m.find()) {
                        ImageRef ref = parseAttrs(m.group(2), m.group(1));
                        if (ref != null) out.add(ref);
                    }
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static boolean isEasyBotCICodeHover(HoverEvent hover) {
        try {
            if (hover.getAction() != HoverEvent.Action.SHOW_TEXT) return false;
            String tooltip = hoverText(hover);
            return tooltip != null && BRACKET.matcher(tooltip).find();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String hoverText(HoverEvent hover) {
        try {
            Object value = hover.getValue(HoverEvent.Action.SHOW_TEXT);
            if (value instanceof Component c) return c.getString();
            if (value instanceof String s) return s;
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    private static boolean isChatImageHover(HoverEvent hover) {
        try {
            if (String.valueOf(hover.getAction()).toLowerCase(java.util.Locale.ROOT).contains("chatimage")) return true;
            // Some ChatImage builds ship an Action whose toString() is not the
            // action id — fall back to the payload's class name.
            Object value = hover.getValue(hover.getAction());
            return value != null
                && value.getClass().getName().toLowerCase(java.util.Locale.ROOT).contains("chatimage");
        } catch (Throwable t) {
            return false;
        }
    }

    private static String readUrlFromHover(HoverEvent hover) {
        try {
            Object value = hover.getValue(hover.getAction());
            // Custom actions carry whatever their codec decoded. ChatImage's
            // show_chatimage payload has been, across versions, a JSON object
            // {"url":...}, a plain code string, or (0.13+) a ChatImageCode
            // object whose toString() is the original "[[CICode,url=...]]".
            if (value instanceof com.google.gson.JsonElement je) {
                if (je.isJsonObject() && je.getAsJsonObject().has("url")
                        && je.getAsJsonObject().get("url").isJsonPrimitive()) {
                    return normalizeUrl(je.getAsJsonObject().get("url").getAsString());
                }
                if (je.isJsonPrimitive() && je.getAsJsonPrimitive().isString()) {
                    return normalizeUrl(je.getAsString());
                }
            } else if (value instanceof String s) {
                return normalizeUrl(s);
            } else if (value != null) {
                return normalizeUrl(String.valueOf(value));
            }
        } catch (Throwable t) {
            return null;
        }
        return null;
    }

    /** Accepts a bare http(s) URL, a "[[CICode,...]]" code, or a wrapper around either. */
    static String normalizeUrl(String s) {
        if (s == null || s.isBlank()) return null;
        String fromCode = urlFromCodeText(s);
        if (fromCode != null) return fromCode;
        String trimmed = s.trim();
        String lower = trimmed.toLowerCase(java.util.Locale.ROOT);
        return (lower.startsWith("http://") || lower.startsWith("https://")) ? trimmed : null;
    }

    /** Pulls the first image URL back out of a "[[CICode,...]]" code string. */
    static String urlFromCodeText(String s) {
        if (s == null || s.isEmpty()) return null;
        Matcher m = BRACKET.matcher(s);
        while (m.find()) {
            ImageRef ref = parseAttrs(m.group(2), m.group(1));
            if (ref != null) return ref.url();
        }
        return null;
    }
}
