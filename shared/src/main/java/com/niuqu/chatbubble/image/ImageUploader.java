package com.niuqu.chatbubble.image;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;

/**
 * Uploads image bytes to a file host and returns the resulting URL.
 *
 * Default host: uguu.se — measured reachable from the user's network on BOTH
 * legs (upload https://uguu.se/upload 200, download https://d.uguu.se/ 200).
 * Files expire after 3 hours (fine for live chat; history images will show
 * "failed to load" after expiry).
 *
 * Litterbox (litterbox.catbox.moe) was the previous default: its upload API
 * is reachable but the download CDN (litter.catbox.moe) is blocked in the
 * user's network (HTTP 000) — uploads "succeed" but the image can never load.
 *
 * Custom host config: POST url with multipart/form-data (file field), plus
 * optional extra key=value fields (comma-separated) and a response mode:
 * "text" (response body IS the URL) or "json:<path>" where <path> is a
 * dotted path with array indices, e.g. "json:files[0].url" for
 * {"files":[{"url":"https://..."}]} or plain "json:url" for a top-level field.
 */
public final class ImageUploader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DEFAULT_URL = "https://uguu.se/upload";
    public static final String DEFAULT_FIELD = "files[]";
    // uguu needs no extra fields; kept empty so nothing is injected.
    public static final String DEFAULT_EXTRA = "";
    public static final String DEFAULT_RESPONSE = "json:files[0].url";

    private static final int MAX_UPLOAD_BYTES = 16 * 1024 * 1024;
    private static final long UPLOAD_TIMEOUT_SECONDS = 30;

    private ImageUploader() {}

    /** Synchronous upload (call on a worker thread). Returns the URL or null on failure. */
    public static String upload(byte[] bytes, String fileName,
                                String url, String field, String extra, String responseMode) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_UPLOAD_BYTES) {
            LOGGER.info("[e33chat] upload skipped: {} bytes", bytes == null ? 0 : bytes.length);
            return null;
        }
        String endpoint = (url == null || url.isBlank()) ? DEFAULT_URL : url.trim();
        String fld = (field == null || field.isBlank()) ? DEFAULT_FIELD : field.trim();
        String extraFields = (extra == null || extra.isBlank()) ? DEFAULT_EXTRA : extra;
        // Litterbox requires reqtype=fileupload (412 otherwise). Old configs
        // saved without it; inject only for the Litterbox endpoint — other
        // hosts (uguu) must not receive the field.
        if (endpoint.contains("litterbox.catbox.moe") && !extraFields.contains("reqtype")) {
            extraFields = "reqtype=fileupload," + extraFields;
        }
        String mode = (responseMode == null || responseMode.isBlank()) ? DEFAULT_RESPONSE : responseMode.trim();

        try {
            String boundary = "----e33chat" + UUID.randomUUID().toString().replace("-", "");
            byte[] body = buildMultipart(bytes, fileName, fld, extraFields, boundary);
            HttpRequest req = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(UPLOAD_TIMEOUT_SECONDS))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
            HttpResponse<String> resp = ImageLoader.client().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                LOGGER.info("[e33chat] upload {} -> HTTP {}: {} ({} bytes, boundary={}, UA={})",
                    endpoint, resp.statusCode(), resp.body(), body.length,
                    boundary, req.headers().firstValue("User-Agent").orElse("?"));
                return null;
            }
            String out = extractUrl(resp.body(), mode);
            if (out == null) {
                LOGGER.info("[e33chat] upload {} -> unparsable response: {}", endpoint, resp.body());
            }
            return out;
        } catch (Throwable t) {
            LOGGER.info("[e33chat] upload {} -> exception: {}", endpoint, t.toString());
            return null;
        }
    }

    /** multipart/form-data body: extra key=value parts first, then the file part. */
    static byte[] buildMultipart(byte[] fileBytes, String fileName, String field,
                                 String extra, String boundary) {
        StringBuilder head = new StringBuilder();
        if (extra != null && !extra.isBlank()) {
            for (String kv : extra.split(",")) {
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String k = kv.substring(0, eq).trim();
                String v = kv.substring(eq + 1).trim();
                if (k.isEmpty() || v.isEmpty()) continue;
                head.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(k).append("\"\r\n\r\n")
                    .append(v).append("\r\n");
            }
        }
        head.append("--").append(boundary).append("\r\n")
            .append("Content-Disposition: form-data; name=\"").append(field)
            .append("\"; filename=\"").append(sanitizeFileName(fileName)).append("\"\r\n")
            .append("Content-Type: application/octet-stream\r\n\r\n");
        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[headBytes.length + fileBytes.length + tailBytes.length];
        System.arraycopy(headBytes, 0, out, 0, headBytes.length);
        System.arraycopy(fileBytes, 0, out, headBytes.length, fileBytes.length);
        System.arraycopy(tailBytes, 0, out, headBytes.length + fileBytes.length, tailBytes.length);
        return out;
    }

    /** Response → URL. text: the body is the URL. json:<path>: walk a dotted
     * path with array indices ("files[0].url"); plain "json:field" reads a
     * top-level field (legacy form). */
    public static String extractUrl(String responseBody, String responseMode) {
        if (responseBody == null) return null;
        String body = responseBody.trim();
        if (body.isEmpty()) return null;
        // null mode means text (legacy callers); upload() resolves the
        // configured default before delegating here.
        String mode = (responseMode == null || responseMode.isBlank()) ? "text" : responseMode.trim();
        if (mode.startsWith("json:")) {
            String path = mode.substring(5).trim();
            try {
                var el = JsonParser.parseString(body);
                Object v = walkJson(el, path);
                return (v instanceof String s) ? (isHttpUrl(s) ? s : null) : null;
            } catch (Throwable t) {
                return null;
            }
        }
        // text mode: the bare URL
        return isHttpUrl(body) ? body : null;
    }

    /** Walks "a.b[0].c" through a JsonElement. Null when any step is missing. */
    private static Object walkJson(com.google.gson.JsonElement el, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] segments = path.split("\\.");
        com.google.gson.JsonElement cur = el;
        for (String seg : segments) {
            if (seg.isEmpty()) return null;
            // "name[0]" → name + index
            int bi = seg.indexOf('[');
            String name = bi >= 0 ? seg.substring(0, bi) : seg;
            Integer idx = null;
            if (bi >= 0) {
                if (!seg.endsWith("]")) return null;
                try {
                    idx = Integer.parseInt(seg.substring(bi + 1, seg.length() - 1));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (!name.isEmpty()) {
                if (!cur.isJsonObject() || !cur.getAsJsonObject().has(name)) return null;
                cur = cur.getAsJsonObject().get(name);
            }
            if (idx != null) {
                if (!cur.isJsonArray() || idx < 0 || idx >= cur.getAsJsonArray().size()) return null;
                cur = cur.getAsJsonArray().get(idx);
            }
        }
        return cur.isJsonPrimitive() ? cur.getAsJsonPrimitive().getAsString() : null;
    }

    private static boolean isHttpUrl(String s) {
        String lower = s.toLowerCase();
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return false;
        if (s.length() >= 2048) return false;
        try {
            return new URI(s).getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) return "image.png";
        String n = name.replaceAll("[^A-Za-z0-9._-]", "_");
        return n.length() > 64 ? n.substring(n.length() - 64) : n;
    }
}
