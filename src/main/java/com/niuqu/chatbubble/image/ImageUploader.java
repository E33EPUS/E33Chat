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
 * Default host: Litterbox (litterbox.catbox.moe) — Catbox's sister domain.
 * catbox.moe itself was unreachable in the user's network (HTTP 000) while
 * litterbox.catbox.moe works, and 0x0.st has uploads disabled. Litterbox
 * files expire (default 72h); a custom host can be configured instead.
 *
 * Custom host config: POST url with multipart/form-data (file field), plus
 * optional extra key=value fields (comma-separated, e.g. "time=72h") and a
 * response mode: "text" (response body IS the URL) or "json:<field>"
 * (extract the URL from a JSON object field).
 */
public final class ImageUploader {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String DEFAULT_URL = "https://litterbox.catbox.moe/resources/internals/api.php";
    public static final String DEFAULT_FIELD = "fileToUpload";
    // Litterbox requires reqtype=fileupload; omitting it returns 412 "No request type given"
    public static final String DEFAULT_EXTRA = "reqtype=fileupload,time=72h";
    public static final String DEFAULT_RESPONSE = "text";

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
        // Legacy configs saved the extra params without reqtype (412 on Litterbox);
        // inject it for the default host so old configs keep working.
        if (endpoint.equals(DEFAULT_URL) && !extraFields.contains("reqtype")) {
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

    /** Response → URL. text: the body is the URL. json:<field>: extract from a JSON object. */
    public static String extractUrl(String responseBody, String responseMode) {
        if (responseBody == null) return null;
        String body = responseBody.trim();
        if (body.isEmpty()) return null;
        String mode = (responseMode == null || responseMode.isBlank()) ? DEFAULT_RESPONSE : responseMode.trim();
        if (mode.startsWith("json:")) {
            String field = mode.substring(5).trim();
            try {
                var el = JsonParser.parseString(body);
                if (el.isJsonObject() && el.getAsJsonObject().has(field)
                        && el.getAsJsonObject().get(field).isJsonPrimitive()) {
                    String v = el.getAsJsonObject().get(field).getAsString().trim();
                    return isHttpUrl(v) ? v : null;
                }
            } catch (Throwable t) {
                return null;
            }
            return null;
        }
        // text mode: Litterbox/Catbox reply with the bare URL
        return isHttpUrl(body) ? body : null;
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
