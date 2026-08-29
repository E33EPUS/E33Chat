package com.niuqu.chatbubble.chat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Declarative message-format templates configured by the server (/e33chat template).
 * A template declares the line structure with field placeholders; a match splits the
 * text into sender/display-name/content with character offsets for style slicing.
 * Runs before the heuristic guards: a template match is the strongest evidence.
 */
public class TemplateMatcher {

    /** Tells whether a matched name resolves to a known player (online / seen / self). */
    public interface NameResolver { boolean isKnown(String name); }

    public record CompiledTemplate(String raw, Pattern pattern, boolean whisper,
                                   boolean hasPrefix, boolean hasDisp, boolean hasSender, boolean hasTarget,
                                   boolean external, List<String> unknownFields) {}

    public record CompileResult(CompiledTemplate template, String error) {
        public static CompileResult ok(CompiledTemplate t) { return new CompileResult(t, null); }
        public static CompileResult fail(String error) { return new CompileResult(null, error); }
    }

    public record TemplateResult(CompiledTemplate template, String prefix, String displayName,
                                 String sender, String target, String content, String verifiedName,
                                 int nameStart, int nameEnd, int contentStart, int contentEnd,
                                 boolean whisper) {}

    private static final String PREFIX = "prefix";
    private static final String DISP = "display_name";
    private static final String NAME = "name";
    private static final String EXTERNAL = "external";
    private static final String CONTENT = "content";
    private static final String SENDER = "sender";
    private static final String TARGET = "target";
    private static final String SEP = "sep";
    private static final Set<String> FIELDS = Set.of(PREFIX, DISP, NAME, EXTERNAL, CONTENT, SENDER, TARGET, SEP);

    private record Token(String literal, String field) {}

    public static CompileResult compile(String raw) {
        if (raw == null || raw.isBlank()) return CompileResult.fail("模板不能为空");
        List<Token> tokens = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        parse(raw, tokens, unknown);

        List<String> fields = new ArrayList<>();
        for (Token t : tokens) if (t.field != null) fields.add(t.field);
        if (fields.isEmpty()) return CompileResult.fail("模板必须包含至少一个字段占位符");

        long contentCount = fields.stream().filter(CONTENT::equals).count();
        if (contentCount == 0) return CompileResult.fail("模板必须包含 {content}（消息正文）");
        if (contentCount > 1) return CompileResult.fail("模板只能包含一个 {content}");
        // 2.2.7: {content} 可在任意位置（后缀式格式如 "{display_name}: {content} [聊天]"）
        if (fields.contains(DISP) && fields.contains(NAME))
            return CompileResult.fail("不能同时使用 {display_name} 和 {name}，请二选一");
        // 2.4.3: {external} 是“外部/QQ 发送者”显示名，不要求名字能解析到已知玩家；
        // 与 {display_name}/{name} 互斥，也不用于私聊模板。
        if (fields.contains(EXTERNAL) && (fields.contains(DISP) || fields.contains(NAME)))
            return CompileResult.fail("{external} 不能与 {display_name}/{name} 同时使用");
        // 2.2.7: 其余字段重复会生成同名命名组 → PatternSyntaxException；显式拒绝
        for (String f : fields) {
            if (f.equals(CONTENT) || f.equals(SEP)) continue;
            if (java.util.Collections.frequency(fields, f) > 1)
                return CompileResult.fail("字段 {" + f + "} 只能出现一次");
        }
        boolean whisper = fields.contains(SENDER) || fields.contains(TARGET);
        if (whisper && fields.contains(EXTERNAL))
            return CompileResult.fail("私聊模板不能使用 {external}");
        if (!whisper && !fields.contains(DISP) && !fields.contains(NAME) && !fields.contains(EXTERNAL))
            return CompileResult.fail("聊天模板必须包含 {display_name}、{name} 或 {external}");

        StringBuilder regex = new StringBuilder();
        boolean hasPrefix = false, hasDisp = false, hasSender = false, hasTarget = false, external = false;
        for (Token t : tokens) {
            if (t.field == null) { regex.append(Pattern.quote(t.literal)); continue; }
            switch (t.field) {
                // 2.2.7: content 惰性匹配（支持后缀式字面锚定）；非末尾时靠后续字面量收敛
                case CONTENT -> regex.append("(?s:(?<content>.*?))");
                // 2.2.7: {sep} 可选分隔符——常见分隔符序列（>>/冒号/»/>）或纯空格，
                // 非捕获组（不产出值、可重复出现，无命名组冲突）
                case SEP -> regex.append("(?:\\s*>>\\s*|\\s*[:：»>]\\s*|\\s+)");
                case PREFIX -> { regex.append("(?s:(?<prefix>.*?))"); hasPrefix = true; }
                case DISP, NAME, EXTERNAL -> { regex.append("(?<disp>.+?)"); hasDisp = true; external |= t.field.equals(EXTERNAL); }
                case SENDER -> { regex.append("(?<sender>.+?)"); hasSender = true; }
                case TARGET -> { regex.append("(?<target>.+?)"); hasTarget = true; }
            }
        }
        try {
            return CompileResult.ok(new CompiledTemplate(raw, Pattern.compile(regex.toString()), whisper,
                hasPrefix, hasDisp, hasSender, hasTarget, external, unknown));
        } catch (java.util.regex.PatternSyntaxException e) {
            // 2.2.7: 兜底——编译失败返回错误而非崩溃（穿透命令/GUI/同步/保存）
            return CompileResult.fail("模板正则编译失败: " + e.getMessage());
        }
    }

    private static void parse(String raw, List<Token> tokens, List<String> unknown) {
        int i = 0;
        while (i < raw.length()) {
            int open = raw.indexOf('{', i);
            if (open < 0) { tokens.add(new Token(raw.substring(i), null)); return; }
            if (open > i) tokens.add(new Token(raw.substring(i, open), null));
            int close = raw.indexOf('}', open + 1);
            if (close < 0) { tokens.add(new Token(raw.substring(open), null)); return; }
            String name = raw.substring(open + 1, close);
            if (FIELDS.contains(name)) tokens.add(new Token(null, name));
            else {
                tokens.add(new Token(raw.substring(open, close + 1), null));
                unknown.add(name);
            }
            i = close + 1;
        }
    }

    /** Whisper templates first (more specific), then chat templates; first match wins. */
    public static Optional<TemplateResult> match(String text, List<CompiledTemplate> chatTpls,
            List<CompiledTemplate> whisperTpls, NameResolver resolver) {
        if (text == null || text.isEmpty()) return Optional.empty();
        for (CompiledTemplate t : whisperTpls) {
            TemplateResult r = tryMatch(text, t);
            if (r == null) continue;
            if (r.sender() != null && resolver.isKnown(r.sender()))
                return Optional.of(withVerified(r, r.sender()));
            if (r.target() != null && resolver.isKnown(r.target()))
                return Optional.of(withVerified(r, r.target()));
            // whisper hit but neither name resolves to a known player — fall through
        }
        for (CompiledTemplate t : chatTpls) {
            TemplateResult r = tryMatch(text, t);
            if (r == null) continue;
            // {external} templates trust the declared format and accept unknown
            // senders (EasyBot QQ relays); normal templates still require the
            // name to resolve to a known player.
            if (r.displayName() != null && (t.external() || resolver.isKnown(r.displayName())))
                return Optional.of(withVerified(r, r.displayName()));
        }
        return Optional.empty();
    }

    /**
     * Infers a template from a real chat line: locates the player name via the
     * name-anchor parser, then rewrites the line as
     * {@code <prefix>{display_name}<separator>{content}}. Lets a server admin
     * paste a real message instead of learning the syntax.
     */
    public static Optional<String> inferFromMessage(String text, Collection<String> knownNames) {
        if (text == null || text.isBlank()) return Optional.empty();
        var parsed = MessagePresentation.parseDecoratedPlayerLine(text, knownNames);
        if (parsed.isEmpty()) return Optional.empty();
        var pl = parsed.orElseThrow();
        // Locate the BARE name inside the decorated label: the decoration becomes
        // the literal prefix so {display_name} captures just the player name.
        // 偏移来自 parser（嵌色名也正确）
        int nameIdx = pl.nameStart();
        if (nameIdx < 0) return Optional.empty();
        int nameEnd = pl.nameEnd();
        int contentStart = pl.contentStart();
        if (contentStart >= text.length()) return Optional.empty();
        String tpl = text.substring(0, nameIdx)
            + "{display_name}"
            + text.substring(nameEnd, contentStart)
            + "{content}";
        return Optional.of(tpl);
    }

    private static TemplateResult withVerified(TemplateResult r, String verified) {
        return new TemplateResult(r.template(), r.prefix(), r.displayName(), r.sender(), r.target(),
            r.content(), verified, r.nameStart(), r.nameEnd(), r.contentStart(), r.contentEnd(), r.whisper());
    }

    private static TemplateResult tryMatch(String text, CompiledTemplate t) {
        Matcher m = t.pattern().matcher(text);
        if (!m.matches()) return null;
        String sender = t.hasSender ? m.group("sender") : null;
        String target = t.hasTarget ? m.group("target") : null;
        String prefix = t.hasPrefix ? m.group("prefix") : null;
        String disp = t.hasDisp ? m.group("disp") : null;
        if (t.whisper) {
            int ns, ne;
            if (t.hasSender) { ns = m.start("sender"); ne = m.end("sender"); }
            else { ns = m.start("target"); ne = m.end("target"); }
            return new TemplateResult(t, prefix, sender != null ? sender : target, sender, target,
                m.group("content"), null, ns, ne, m.start("content"), m.end("content"), true);
        }
        return new TemplateResult(t, prefix, disp, null, null, m.group("content"), null,
            t.hasPrefix ? m.start("prefix") : m.start("disp"), m.end("disp"),
            m.start("content"), m.end("content"), false);
    }
}
