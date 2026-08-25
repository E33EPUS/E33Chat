# Text Selection in Chat Bubbles and System Messages — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add drag-to-select partial text in E33Chat bubbles/system messages and copy it with Ctrl+C, identically on Forge, NeoForge, and Fabric.

**Architecture:** Platform-independent `TextSpan` + `ChatTextSelection` classes record visible text lines and selection state. Each renderer records `TextSpan`s for selectable text, draws a highlight behind selected glyphs, and the screen classes translate mouse drags into selection updates. `Ctrl+C` copies the selected plain text.

**Tech Stack:** Java, Minecraft Forge 1.20.1 / NeoForge 1.21.1 / Fabric 1.21.1, JUnit 5 (existing test setup).

## Global Constraints

- All three repos must stay behaviorally identical: `D:\MDK`, `D:\MDK-1.21.1`, `D:\MDKF`.
- Commit messages in English, no emoji.
- Do not stage or commit `README.md` / `README_EN.md`.
- Do not push unless the user explicitly asks.
- Selection scope: sender names, bubble content, quote/reply blocks, system messages.
- Images, time separators, duplicate-count labels, and generated image status placeholders are NOT selectable.
- A simple click still triggers existing clickable-text behavior; only a drag selects.
- `Ctrl+C` copies selected text; selected lines are joined with `\n`.

---

### Task 1: Add pure selection model and unit tests (all three repos)

**Files:**
- Create (all three): `src/main/java/com/niuqu/chatbubble/render/TextSpan.java`
- Create (all three): `src/main/java/com/niuqu/chatbubble/render/ChatTextSelection.java`
- Create (all three): `src/test/java/com/niuqu/chatbubble/render/ChatTextSelectionTest.java`

**Interfaces:**
- Produces:
  - `record TextSpan(int messageIndex, int lineIndex, int kind, int x, int y, int w, int h, String text, float scale)`
  - `TextSpan.withPosition(int nx, int ny, int nw, int nh)`
  - `TextSpan.orderKey()`
  - constants `TextSpan.KIND_NAME = 0`, `TextSpan.KIND_CONTENT = 1`, `TextSpan.KIND_QUOTE = 2`
  - `ChatTextSelection.begin(int messageIndex, int lineIndex, int kind, int charIndex)`
  - `ChatTextSelection.update(int messageIndex, int lineIndex, int kind, int charIndex)`
  - `ChatTextSelection.endDrag()`
  - `ChatTextSelection.isDragActive()`
  - `ChatTextSelection.didMove()`
  - `ChatTextSelection.clear()`
  - `ChatTextSelection.hasSelection()`
  - `int[] ChatTextSelection.rangeFor(TextSpan span)` (returns `[start,end)` or `null`)
  - `String ChatTextSelection.copyText(List<TextSpan> spans)`

- [ ] **Step 1: Write the failing test**

Create `ChatTextSelectionTest.java` in each repo:

```java
package com.niuqu.chatbubble.render;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatTextSelectionTest {

    private static TextSpan span(int msg, int line, int kind, String text) {
        return new TextSpan(msg, line, kind, 0, 0, 10, 10, text, 1f);
    }

    @Test
    void sameLineForwardSelection() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 1);
        sel.update(0, 0, TextSpan.KIND_CONTENT, 4);
        assertArrayEquals(new int[]{1, 4},
            sel.rangeFor(span(0, 0, TextSpan.KIND_CONTENT, "hello")));
        assertEquals("ell", sel.copyText(List.of(
            span(0, 0, TextSpan.KIND_CONTENT, "hello"))));
    }

    @Test
    void sameLineReverseSelection() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 4);
        sel.update(0, 0, TextSpan.KIND_CONTENT, 1);
        assertArrayEquals(new int[]{1, 4},
            sel.rangeFor(span(0, 0, TextSpan.KIND_CONTENT, "hello")));
    }

    @Test
    void multiLineSelectionCopiesWithNewline() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 1);
        sel.update(0, 1, TextSpan.KIND_CONTENT, 2);
        List<TextSpan> spans = List.of(
            span(0, 0, TextSpan.KIND_CONTENT, "hello"),
            span(0, 1, TextSpan.KIND_CONTENT, "world"));
        assertEquals("ell\nwo", sel.copyText(spans));
    }

    @Test
    void multiMessageSelectionCopiesInVisualOrder() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(1, 0, TextSpan.KIND_CONTENT, 0);
        sel.update(2, 0, TextSpan.KIND_CONTENT, 3);
        List<TextSpan> spans = List.of(
            span(1, 0, TextSpan.KIND_CONTENT, "aaa"),
            span(2, 0, TextSpan.KIND_CONTENT, "bbbb"));
        assertEquals("aaa\nbbb", sel.copyText(spans));
    }

    @Test
    void emptySelectionReturnsEmptyString() {
        ChatTextSelection sel = new ChatTextSelection();
        assertFalse(sel.hasSelection());
        assertEquals("", sel.copyText(List.of(
            span(0, 0, TextSpan.KIND_CONTENT, "hello"))));
    }

    @Test
    void clearedSelectionHasNoRange() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 1);
        sel.update(0, 0, TextSpan.KIND_CONTENT, 3);
        sel.clear();
        assertNull(sel.rangeFor(span(0, 0, TextSpan.KIND_CONTENT, "hello")));
    }
}
```

- [ ] **Step 2: Run the test and verify it fails**

Run in each repo:
- Forge/Neo: `cmd.exe //c ".\gradlew.bat test --offline -PrunTests"`
- Fabric: `cmd.exe //c ".\gradlew.bat test --offline"`

Expected: compile errors because `TextSpan` / `ChatTextSelection` do not exist.

- [ ] **Step 3: Implement `TextSpan`**

Create `src/main/java/com/niuqu/chatbubble/render/TextSpan.java`:

```java
package com.niuqu.chatbubble.render;

public record TextSpan(int messageIndex, int lineIndex, int kind,
                       int x, int y, int w, int h,
                       String text, float scale) {

    public static final int KIND_NAME = 0;
    public static final int KIND_CONTENT = 1;
    public static final int KIND_QUOTE = 2;

    public TextSpan withPosition(int nx, int ny, int nw, int nh) {
        return new TextSpan(messageIndex, lineIndex, kind,
            nx, ny, nw, nh, text, scale);
    }

    public long orderKey() {
        return (long) messageIndex * 100_000L + kind * 10_000L + lineIndex;
    }
}
```

- [ ] **Step 4: Implement `ChatTextSelection`**

Create `src/main/java/com/niuqu/chatbubble/render/ChatTextSelection.java`:

```java
package com.niuqu.chatbubble.render;

import java.util.List;

public final class ChatTextSelection {

    public static final int SELECTION_BG = 0x8033B5E5;

    private int anchorMsg = -1, anchorLine = -1, anchorKind = -1, anchorChar = -1;
    private int focusMsg = -1, focusLine = -1, focusKind = -1, focusChar = -1;
    private boolean dragActive;
    private boolean moved;

    public void begin(int msg, int line, int kind, int ch) {
        anchorMsg = focusMsg = msg;
        anchorLine = focusLine = line;
        anchorKind = focusKind = kind;
        anchorChar = focusChar = ch;
        dragActive = true;
        moved = false;
    }

    public void update(int msg, int line, int kind, int ch) {
        if (!dragActive) return;
        if (msg != focusMsg || line != focusLine || kind != focusKind || ch != focusChar) {
            moved = true;
        }
        focusMsg = msg;
        focusLine = line;
        focusKind = kind;
        focusChar = ch;
    }

    public void endDrag() {
        dragActive = false;
    }

    public boolean isDragActive() {
        return dragActive;
    }

    public boolean didMove() {
        return moved;
    }

    public void clear() {
        anchorMsg = focusMsg = -1;
        anchorLine = focusLine = -1;
        anchorKind = focusKind = -1;
        anchorChar = focusChar = -1;
        dragActive = false;
        moved = false;
    }

    public boolean hasSelection() {
        if (anchorMsg < 0 || focusMsg < 0) return false;
        if (anchorMsg != focusMsg || anchorLine != focusLine || anchorKind != focusKind) {
            return true;
        }
        return anchorChar != focusChar;
    }

    private static long key(int msg, int line, int kind) {
        return (long) msg * 100_000L + kind * 10_000L + line;
    }

    public int[] rangeFor(TextSpan span) {
        if (anchorMsg < 0 || focusMsg < 0) return null;
        long a = key(anchorMsg, anchorLine, anchorKind);
        long f = key(focusMsg, focusLine, focusKind);
        long s = span.orderKey();
        int len = span.text().length();
        int start;
        int end;
        if (a == f) {
            if (s != a) return null;
            start = Math.min(anchorChar, focusChar);
            end = Math.max(anchorChar, focusChar);
        } else if (a < f) {
            if (s < a || s > f) return null;
            start = (s == a) ? anchorChar : 0;
            end = (s == f) ? focusChar : len;
        } else {
            if (s < f || s > a) return null;
            start = (s == f) ? focusChar : 0;
            end = (s == a) ? anchorChar : len;
        }
        start = Math.max(0, Math.min(len, start));
        end = Math.max(0, Math.min(len, end));
        if (end <= start) return null;
        return new int[]{start, end};
    }

    public String copyText(List<TextSpan> spans) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (TextSpan span : spans) {
            int[] r = rangeFor(span);
            if (r == null) continue;
            if (!first) sb.append('\n');
            sb.append(span.text(), r[0], r[1]);
            first = false;
        }
        return sb.toString();
    }
}
```

- [ ] **Step 5: Run the test and verify it passes**

Run the same test commands as Step 2. Expected: BUILD SUCCESSFUL, `ChatTextSelectionTest` all green.

- [ ] **Step 6: Commit in all three repos**

```bash
git add src/main/java/com/niuqu/chatbubble/render/TextSpan.java \
        src/main/java/com/niuqu/chatbubble/render/ChatTextSelection.java \
        src/test/java/com/niuqu/chatbubble/render/ChatTextSelectionTest.java
git commit -m "feat: add text selection model and tests"
```

---

### Task 2: Forge/Neo renderer records selectable text spans

**Files:**
- Modify (Forge): `D:\MDK\src\main\java\com\niuqu\chatbubble\render\ChatMessageRenderer.java`
- Modify (NeoForge): `D:\MDK-1.21.1\src\main\java\com\niuqu\chatbubble\render\ChatMessageRenderer.java`
- Test: no new test; existing `ChatMessageRendererTest` still passes.

**Interfaces:**
- Consumes: `TextSpan`, `ChatTextSelection` from Task 1.
- Produces:
  - `ChatMessageRenderer.renderLineWithClicks(GuiGraphics, Font, FormattedCharSequence, int x, int y, int color, Style fallback, int messageIndex, int lineIndex, int kind, float scale, List<ClickableSpan>, List<TextSpan>, ChatTextSelection)`
  - `ChatMessageRenderer.renderLineWithClicks(...)` 6-arg overload remains unchanged for callers that do not need selection.

- [ ] **Step 1: Add the selection-aware overload**

Add a new overloaded method next to the existing `renderLineWithClicks`. Keep the existing 6-arg method delegating to it with `messageIndex = -1`, `lineIndex = -1`, `kind = TextSpan.KIND_CONTENT`, `scale = 1f`, `textSpans = null`, `selection = null`:

```java
public static void renderLineWithClicks(GuiGraphics g, Font font, FormattedCharSequence line,
                                         int x, int y, int color, Style fallback,
                                         int messageIndex, int lineIndex, int kind, float scale,
                                         List<ClickableSpan> clickableSpans,
                                         List<TextSpan> textSpans,
                                         ChatTextSelection selection) {
    List<Style> styles = new ArrayList<>();
    StringBuilder textBuilder = new StringBuilder();
    line.accept((i, st, cp) -> {
        styles.add(st);
        textBuilder.appendCodePoint(cp);
        return true;
    });
    String text = textBuilder.toString();

    if (textSpans != null && messageIndex >= 0) {
        int w = font.width(line);
        textSpans.add(new TextSpan(messageIndex, lineIndex, kind,
            x, y, w, font.lineHeight, text, scale));
        if (selection != null) {
            int[] range = selection.rangeFor(textSpans.get(textSpans.size() - 1));
            if (range != null) {
                int hx = x + font.width(text.substring(0, range[0]));
                int hw = Math.max(1, font.width(text.substring(range[0], range[1])));
                g.fill(hx, y, hx + hw, y + font.lineHeight, ChatTextSelection.SELECTION_BG);
            }
        }
    }

    int beforeCount = clickableSpans.size();
    int runStart = -1;
    Style runStyle = null;
    List<int[]> clickableCharRanges = new ArrayList<>();
    for (int idx = 0; idx <= styles.size(); idx++) {
        Style st = idx < styles.size() ? styles.get(idx) : null;
        boolean clickable = st != null && (st.getClickEvent() != null || st.getHoverEvent() != null);
        if (runStyle == null) {
            if (clickable) { runStart = idx; runStyle = st; }
        } else if (!clickable || !st.equals(runStyle)) {
            int x0 = prefixWidth(line, runStart, font);
            int x1 = prefixWidth(line, idx, font);
            clickableSpans.add(new ClickableSpan(x + x0, y, x1 - x0, font.lineHeight, runStyle));
            clickableCharRanges.add(new int[]{runStart, idx});
            runStart = clickable ? idx : -1;
            runStyle = clickable ? st : null;
        }
    }

    if (fallback != null && fallback.getClickEvent() != null) {
        if (clickableSpans.size() == beforeCount) {
            clickableSpans.add(new ClickableSpan(x, y, font.width(line), font.lineHeight,
                fallback.withUnderlined(true)));
            clickableCharRanges.add(new int[]{0, styles.size()});
        } else {
            for (int i = beforeCount; i < clickableSpans.size(); i++) {
                ClickableSpan s = clickableSpans.get(i);
                if (s.style().getClickEvent() == null) {
                    clickableSpans.set(i, new ClickableSpan(s.x(), s.y(), s.w(), s.h(),
                        s.style().withClickEvent(fallback.getClickEvent())));
                }
            }
        }
    }

    int styleLen = styles.size();
    boolean[] hasClickEvent = new boolean[styleLen];
    for (int ri = 0; ri < clickableCharRanges.size(); ri++) {
        int spanIdx = beforeCount + ri;
        if (spanIdx < clickableSpans.size()
            && clickableSpans.get(spanIdx).style().getClickEvent() != null) {
            int[] r = clickableCharRanges.get(ri);
            for (int i = r[0]; i < r[1]; i++) hasClickEvent[i] = true;
        }
    }

    int[] idx = {0};
    FormattedCharSequence decorated = sink -> line.accept((i, st, cp) -> {
        int pos = Math.min(idx[0]++, styleLen);
        boolean underline = pos < styleLen ? hasClickEvent[pos] : st.getClickEvent() != null;
        return sink.accept(i, underline && !st.isUnderlined() ? st.withUnderlined(true) : st, cp);
    });
    g.drawString(font, decorated, x, y, color, false);
}
```

Also change the existing 6-arg overload to delegate to the new method:

```java
public static void renderLineWithClicks(GuiGraphics g, Font font, FormattedCharSequence line,
                                         int x, int y, int color,
                                         List<ClickableSpan> clickableSpans) {
    renderLineWithClicks(g, font, line, x, y, color, null, -1, -1,
        TextSpan.KIND_CONTENT, 1f, clickableSpans, null, null);
}
```

Update the existing 7-arg overload (with `Style fallback`) similarly.

- [ ] **Step 2: Update `renderBubble` signature**

Add `List<TextSpan> textSpans` and `ChatTextSelection selection` parameters to `renderBubble` (and to `renderNoBubbleMessage` / `renderEmoteMessage`).

For `renderBubble`, add the two parameters after `List<ClickableSpan> clickableSpans`:

```java
public static void renderBubble(GuiGraphics g, Font font,
                                 ChatMessageStore.ChatMessage msg, int index,
                                 int baseY, int mouseX, int mouseY,
                                 int panelX, int panelW,
                                 int ownBubbleColor, int otherBubbleColor,
                                 int ownTextColor, int otherTextColor,
                                 boolean own, int cornerRadius,
                                 ChatBubbleTheme.Colors c,
                                 ResourceLocation skin,
                                 int searchHighlightIndex,
                                 int bubbleMaxW,
                                 List<int[]> bubbleRects,
                                 List<ClickableSpan> clickableSpans,
                                 List<TextSpan> textSpans,
                                 ChatTextSelection selection,
                                 float alpha, boolean showAvatar) {
```

- [ ] **Step 3: Record system message lines**

In the `msg.isSystem()` branch, replace:

```java
renderLineWithClicks(g, font, line, panelX + (panelW - lw) / 2, yy, sysColor, fb, clickableSpans);
```

with:

```java
for (int li = 0; li < lines.size(); li++) {
    var line = lines.get(li);
    int lw = font.width(line);
    renderLineWithClicks(g, font, line, panelX + (panelW - lw) / 2, yy, sysColor, fb,
        index, li, TextSpan.KIND_CONTENT, 1f, clickableSpans, textSpans, selection);
    yy += font.lineHeight;
}
```

(Use `FormattedCharSequence line` if `var` is not desired.)

- [ ] **Step 4: Record sender name in bubble path**

Replace the direct `g.drawString(font, nameSeq, ...)` call in the bubble path with:

```java
renderLineWithClicks(g, font, nameSeq, startX, nameY,
    ChatBubbleTheme.alphaBlend(c.nameColor(), (int)(255 * alpha)), null,
    index, 0, TextSpan.KIND_NAME, 1f, clickableSpans, textSpans, selection);
```

- [ ] **Step 5: Record bubble content lines and transform text spans**

In the bubble content loop, add `int beforeText = textSpans.size();` before `renderLineWithClicks`, pass `index`, `li`, `TextSpan.KIND_CONTENT`, `s`, `textSpans`, `selection`, then after `g.pose().popPose()` transform the newly added spans exactly like clickable spans:

```java
int beforeText = textSpans.size();
g.pose().pushPose();
g.pose().translate(textSX, textSY, 0);
if (s != 1f) g.pose().scale(s, s, 1f);
renderLineWithClicks(g, font, lines.get(li), 0, 0, fgA, fb,
    index, li, TextSpan.KIND_CONTENT, s, clickableSpans, textSpans, selection);
g.pose().popPose();
for (int i = beforeText; i < textSpans.size(); i++) {
    TextSpan sp = textSpans.get(i);
    textSpans.set(i, sp.withPosition(
        textSX + (int)(sp.x() * s),
        textSY + (int)(sp.y() * s),
        Math.max(1, (int)(sp.w() * s)),
        Math.max(1, (int)(sp.h() * s))));
}
```

- [ ] **Step 6: Record quote text in bubble path**

Replace the direct `g.drawString(font, Component.literal(quoteDisplay), 0, 0, ...)` inside the quote pose with:

```java
int beforeText = textSpans.size();
g.pose().pushPose();
g.pose().translate(quoteX + (int)(4 * s), quoteY + (int)(2 * s), 0);
if (s != 1f) g.pose().scale(s, s, 1f);
renderLineWithClicks(g, font, Component.literal(quoteDisplay).getVisualOrderText(),
    0, 0, ChatBubbleTheme.alphaBlend(c.textSecondary(), (int)(255 * alpha)), null,
    index, 0, TextSpan.KIND_QUOTE, s, clickableSpans, textSpans, selection);
g.pose().popPose();
for (int i = beforeText; i < textSpans.size(); i++) {
    TextSpan sp = textSpans.get(i);
    textSpans.set(i, sp.withPosition(
        quoteX + (int)(4 * s) + (int)(sp.x() * s),
        quoteY + (int)(2 * s) + (int)(sp.y() * s),
        Math.max(1, (int)(sp.w() * s)),
        Math.max(1, (int)(sp.h() * s))));
}
```

- [ ] **Step 7: Record sender name in bubble-less paths**

In `renderNoBubbleMessage` and `renderEmoteMessage`, replace the direct sender-name `g.drawString` calls with the same `renderLineWithClicks(... KIND_NAME ...)` call used in Step 4. Pass `index`, `0`, `TextSpan.KIND_NAME`, `1f`.

- [ ] **Step 8: Record text lines in bubble-less image-message path**

In `renderNoBubbleMessage`, replace the `renderLineWithClicks(g, font, lines.get(li), textX, y + li * font.lineHeight, fgA, fb, clickableSpans);` loop with an indexed loop that passes `index`, `li`, `TextSpan.KIND_CONTENT`, `1f`, `textSpans`, `selection`.

- [ ] **Step 9: Record quote text in bubble-less path**

In `renderNoBubbleMessage`, replace the direct quote `g.drawString` with:

```java
renderLineWithClicks(g, font, Component.literal(quoteDisplay).getVisualOrderText(),
    quoteX + 4, y + 2, ChatBubbleTheme.alphaBlend(c.textSecondary(), (int)(255 * alpha)),
    null, index, 0, TextSpan.KIND_QUOTE, 1f, clickableSpans, textSpans, selection);
```

- [ ] **Step 10: Update `ChatBubbleScreen.renderBubble` call site**

In Forge/Neo `ChatBubbleScreen.renderBubble`, pass `textSpans` and `textSelection` to `ChatMessageRenderer.renderBubble(...)`:

```java
ChatMessageRenderer.renderBubble(g, font, msg, index, baseY, mouseX, mouseY,
    panelX, panelW, ownBg, otherBg, ownFg, otherFg, own,
    ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get(), c(), skin,
    searchHighlightIndex, bubbleMaxW, bubbleRects, clickableSpans,
    textSpans, textSelection, alpha, showAvatar);
```

- [ ] **Step 11: Clear `textSpans` each frame**

In Forge/Neo `ChatBubbleScreen.renderMessages`, next to `clickableSpans.clear();`, add `textSpans.clear();`.

- [ ] **Step 12: Run compile/tests**

Run Forge/Neo test commands. Expected: BUILD SUCCESSFUL.

- [ ] **Step 13: Commit in Forge and NeoForge**

```bash
git add src/main/java/com/niuqu/chatbubble/render/ChatMessageRenderer.java \
        src/main/java/com/niuqu/chatbubble/render/ChatBubbleScreen.java
git commit -m "feat: record selectable text spans in forge and neoforge renderer"
```

---

### Task 3: Fabric renderer records selectable text spans

**Files:**
- Modify: `D:\MDKF\src\main\java\com\niuqu\chatbubble\ChatBubbleScreen.java`

**Interfaces:**
- Consumes: `TextSpan`, `ChatTextSelection` from Task 1.
- Produces: private helper `renderLineWithClicks(DrawContext, OrderedText, int x, int y, int color, Style fallback, int messageIndex, int lineIndex, int kind, float scale, ChatTextSelection selection)`.

- [ ] **Step 1: Add selection-aware `renderLineWithClicks`**

Replace the existing private `renderLineWithClicks(DrawContext, OrderedText, int x, int y, int color, Style fallback)` with this full implementation (keep a 6-arg overload delegating with `messageIndex = -1`, `lineIndex = -1`, `kind = TextSpan.KIND_CONTENT`, `scale = 1f`, `selection = null`):

```java
private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color) {
    renderLineWithClicks(g, line, x, y, color, null);
}

private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color, Style fallback) {
    renderLineWithClicks(g, line, x, y, color, fallback, -1, -1,
        TextSpan.KIND_CONTENT, 1f, null);
}

private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color,
                                  Style fallback, int messageIndex, int lineIndex,
                                  int kind, float scale, ChatTextSelection selection) {
    final List<Style> styles = new ArrayList<>();
    StringBuilder textBuilder = new StringBuilder();
    line.accept((i, st, cp) -> {
        styles.add(st);
        textBuilder.appendCodePoint(cp);
        return true;
    });
    String text = textBuilder.toString();

    if (messageIndex >= 0) {
        int w = textRenderer.getWidth(line);
        textSpans.add(new TextSpan(messageIndex, lineIndex, kind,
            x, y, w, textRenderer.fontHeight, text, scale));
        if (selection != null) {
            int[] range = selection.rangeFor(textSpans.get(textSpans.size() - 1));
            if (range != null) {
                int hx = x + textRenderer.getWidth(text.substring(0, range[0]));
                int hw = Math.max(1, textRenderer.getWidth(text.substring(range[0], range[1])));
                g.fill(hx, y, hx + hw, y + textRenderer.fontHeight, ChatTextSelection.SELECTION_BG);
            }
        }
    }

    final int beforeCount = clickableSpans.size();
    int runStart = -1;
    Style runStyle = null;
    List<int[]> clickableCharRanges = new ArrayList<>();
    for (int idx = 0; idx <= styles.size(); idx++) {
        Style st = idx < styles.size() ? styles.get(idx) : null;
        boolean clickable = st != null && (st.getClickEvent() != null || st.getHoverEvent() != null);
        if (runStyle == null) {
            if (clickable) { runStart = idx; runStyle = st; }
        } else if (!clickable || !st.equals(runStyle)) {
            int x0 = prefixWidth(line, runStart);
            int x1 = prefixWidth(line, idx);
            clickableSpans.add(new ClickableSpan(x + x0, y, x1 - x0, textRenderer.fontHeight, runStyle));
            clickableCharRanges.add(new int[]{runStart, idx});
            runStart = clickable ? idx : -1;
            runStyle = clickable ? st : null;
        }
    }

    if (fallback != null && fallback.getClickEvent() != null) {
        if (clickableSpans.size() == beforeCount) {
            clickableSpans.add(new ClickableSpan(x, y, textRenderer.getWidth(line), textRenderer.fontHeight,
                fallback.withUnderline(true)));
            clickableCharRanges.add(new int[]{0, styles.size()});
        } else {
            for (int i = beforeCount; i < clickableSpans.size(); i++) {
                ClickableSpan s = clickableSpans.get(i);
                if (s.style.getClickEvent() == null) {
                    clickableSpans.set(i, new ClickableSpan(s.x, s.y, s.w, s.h,
                        s.style.withClickEvent(fallback.getClickEvent())));
                }
            }
        }
    }

    int styleLen = styles.size();
    boolean[] hasClickEvent = new boolean[styleLen];
    for (int ri = 0; ri < clickableCharRanges.size(); ri++) {
        int spanIdx = beforeCount + ri;
        if (spanIdx < clickableSpans.size()
            && clickableSpans.get(spanIdx).style.getClickEvent() != null) {
            int[] r = clickableCharRanges.get(ri);
            for (int i = r[0]; i < r[1]; i++) hasClickEvent[i] = true;
        }
    }

    int[] idx = {0};
    OrderedText decorated = sink -> line.accept((i, st, cp) -> {
        int pos = Math.min(idx[0]++, styleLen);
        boolean underline = pos < styleLen ? hasClickEvent[pos] : st.getClickEvent() != null;
        return sink.accept(i, underline && !st.isUnderlined() ? st.withUnderline(true) : st, cp);
    });
    g.drawText(textRenderer, decorated, x, y, color, false);
}
```

- [ ] **Step 2: Update system message loop**

Replace the Fabric `msg.isSystem()` loop with:

```java
for (int li = 0; li < lines.size(); li++) {
    OrderedText line = lines.get(li);
    int lw = textRenderer.getWidth(line);
    renderLineWithClicks(g, line, panelX + (panelW - lw) / 2, yy, sysColor, fb,
        index, li, TextSpan.KIND_CONTENT, 1f, textSelection);
    yy += textRenderer.fontHeight;
}
```

- [ ] **Step 3: Record sender name in all three render paths**

Replace direct `g.drawText(textRenderer, nameSeq, ...)` calls (bubble, no-bubble image message, emote-only message) with:

```java
renderLineWithClicks(g, nameSeq, startX, nameY,
    ChatBubbleTheme.alphaBlend(c().nameColor(), (int) (255 * alpha)), null,
    index, 0, TextSpan.KIND_NAME, 1f, textSelection);
```

- [ ] **Step 4: Record bubble content lines and transform spans**

In the Fabric bubble content loop, add `int beforeText = textSpans.size();`, pass `index`, `li`, `TextSpan.KIND_CONTENT`, `s`, `textSelection`, and after `g.getMatrices().pop()` transform:

```java
for (int i = beforeText; i < textSpans.size(); i++) {
    TextSpan sp = textSpans.get(i);
    textSpans.set(i, sp.withPosition(
        textSX + (int)(sp.x() * s),
        textSY + (int)(sp.y() * s),
        Math.max(1, (int)(sp.w() * s)),
        Math.max(1, (int)(sp.h() * s))));
}
```

- [ ] **Step 5: Record quote text in bubble path**

Replace the direct quote `g.drawText(textRenderer, quoteDisplay, 0, 0, ...)` inside the quote pose with:

```java
int beforeText = textSpans.size();
g.getMatrices().push();
g.getMatrices().translate(quoteX + (int)(4 * s), quoteY + (int)(2 * s), 0);
if (s != 1f) g.getMatrices().scale(s, s, 1f);
renderLineWithClicks(g, Text.literal(quoteDisplay).asOrderedText(), 0, 0,
    ChatBubbleTheme.alphaBlend(c().textSecondary(), (int) (255 * alpha)), null,
    index, 0, TextSpan.KIND_QUOTE, s, textSelection);
g.getMatrices().pop();
for (int i = beforeText; i < textSpans.size(); i++) {
    TextSpan sp = textSpans.get(i);
    textSpans.set(i, sp.withPosition(
        quoteX + (int)(4 * s) + (int)(sp.x() * s),
        quoteY + (int)(2 * s) + (int)(sp.y() * s),
        Math.max(1, (int)(sp.w() * s)),
        Math.max(1, (int)(sp.h() * s))));
}
```

- [ ] **Step 6: Record text lines and quote in bubble-less image-message path**

For content lines in `renderNoBubbleMessage`, use:

```java
renderLineWithClicks(g, lines.get(li), textX, y + li * textRenderer.fontHeight, fgA, fb,
    index, li, TextSpan.KIND_CONTENT, 1f, textSelection);
```

For the quote text in `renderNoBubbleMessage`, use:

```java
renderLineWithClicks(g, Text.literal(quoteDisplay).asOrderedText(),
    quoteX + 4, y + 2, ChatBubbleTheme.alphaBlend(c().textSecondary(), (int) (255 * alpha)),
    null, index, 0, TextSpan.KIND_QUOTE, 1f, textSelection);
```

- [ ] **Step 7: Clear `textSpans` each frame**

In Fabric `renderMessages`, add `textSpans.clear();` next to `clickableSpans.clear();`.

- [ ] **Step 8: Run Fabric tests**

Run `cmd.exe //c ".\gradlew.bat test --offline"`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/niuqu/chatbubble/ChatBubbleScreen.java
git commit -m "feat: record selectable text spans in fabric renderer"
```

---

### Task 4: Forge/Neo mouse interaction and Ctrl+C copy

**Files:**
- Modify (Forge): `D:\MDK\src\main\java\com\niuqu\chatbubble\render\ChatBubbleScreen.java`
- Modify (NeoForge): `D:\MDK-1.21.1\src\main\java\com\niuqu\chatbubble\render\ChatBubbleScreen.java`

**Interfaces:**
- Consumes: `textSpans`, `textSelection` fields from Task 2, `TextSpan`, `ChatTextSelection`.
- Produces: private helpers:
  - `TextSpan findTextSpanAt(double mouseX, double mouseY)`
  - `int charAt(TextSpan span, double mouseX)`
  - `void executeClickAction(double mouseX, double mouseY)`

- [ ] **Step 1: Add fields**

Add next to `clickableSpans`:

```java
private final List<TextSpan> textSpans = new ArrayList<>();
private final ChatTextSelection textSelection = new ChatTextSelection();
```

- [ ] **Step 2: Add hit-testing helpers**

```java
private TextSpan findTextSpanAt(double mouseX, double mouseY) {
    for (int i = textSpans.size() - 1; i >= 0; i--) {
        TextSpan s = textSpans.get(i);
        if (mouseX >= s.x() && mouseX <= s.x() + s.w()
            && mouseY >= s.y() && mouseY <= s.y() + s.h()) {
            return s;
        }
    }
    return null;
}

private int charAt(TextSpan span, double mouseX) {
    String text = span.text();
    if (text.isEmpty()) return 0;
    double localX = (mouseX - span.x()) / span.scale();
    int lo = 0;
    int hi = text.length();
    while (lo < hi) {
        int mid = (lo + hi + 1) >>> 1;
        if (font.width(text.substring(0, mid)) <= localX) {
            lo = mid;
        } else {
            hi = mid - 1;
        }
    }
    return lo;
}
```

- [ ] **Step 3: Add click execution helper**

Extract the existing left-click style handling block into this helper:

```java
private void executeClickAction(double mouseX, double mouseY) {
    Style style = getHoveredStyle(mouseX, mouseY);
    if (style != null && style.getClickEvent() != null) {
        ClickEvent click = style.getClickEvent();
        if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            input.setValue(click.getValue());
        } else if (click.getAction() == ClickEvent.Action.OPEN_FILE) {
            java.io.File file = new java.io.File(click.getValue());
            net.minecraft.Util.getPlatform().openFile(file);
        } else if (click.getAction() == ClickEvent.Action.OPEN_URL) {
            String clickUrl = click.getValue();
            if (clickUrl != null && (clickUrl.startsWith("http://") || clickUrl.startsWith("https://"))) {
                handleComponentClicked(style);
            }
        } else {
            handleComponentClicked(style);
        }
    }
}
```

- [ ] **Step 4: Start selection in `mouseClicked`**

In `mouseClicked`, after the existing context/notification/scrollbar/suggestions/panel handling and before the existing `if (button == 0) { for (int[] r : bubbleRects) ... avatar ... }` block, insert:

```java
if (button == 0) {
    TextSpan hit = findTextSpanAt(mouseX, mouseY);
    if (hit != null) {
        if (textSelection.hasSelection()) textSelection.clear();
        textSelection.begin(hit.messageIndex(), hit.lineIndex(), hit.kind(),
            charAt(hit, mouseX));
        return true;
    }
    if (textSelection.hasSelection() || textSelection.isDragActive()) {
        textSelection.clear();
    }
}
```

Keep the existing immediate click handling for non-text clickable spans (images) below this block.

Remove the old `if (button == 0) { Style style = getHoveredStyle... }` immediate click block from `mouseClicked`; it is replaced by deferred execution in `mouseReleased`.

- [ ] **Step 5: Update selection in `mouseDragged`**

At the top of `mouseDragged`, before the scrollbar handling:

```java
if (textSelection.isDragActive()) {
    double mx = mouseX;
    if (isPanelSliding()) mx -= currentPanelOffset();
    TextSpan hit = findTextSpanAt(mx, mouseY);
    if (hit != null) {
        textSelection.update(hit.messageIndex(), hit.lineIndex(), hit.kind(), charAt(hit, mx));
    }
    return true;
}
```

- [ ] **Step 6: Finish selection in `mouseReleased`**

At the top of `mouseReleased`:

```java
if (textSelection.isDragActive()) {
    textSelection.endDrag();
    if (!textSelection.didMove()) {
        double mx = mouseX;
        if (isPanelSliding()) mx -= currentPanelOffset();
        executeClickAction(mx, mouseY);
        textSelection.clear();
    }
    return true;
}
```

- [ ] **Step 7: Add Ctrl+C copy in `keyPressed`**

At the top of `keyPressed`, before the Ctrl+V block:

```java
if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C && (modifiers & 0x2) != 0
    && textSelection.hasSelection()) {
    String copied = textSelection.copyText(textSpans);
    if (!copied.isEmpty()) {
        minecraft.keyboardHandler.setClipboard(copied);
        copyToastTicks = 30;
    }
    return true;
}
```

- [ ] **Step 8: Run Forge/Neo tests**

Run Forge/Neo test commands. Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit in Forge and NeoForge**

```bash
git add src/main/java/com/niuqu/chatbubble/render/ChatBubbleScreen.java
git commit -m "feat: drag-select and ctrl-c for chat text on forge and neoforge"
```

---

### Task 5: Fabric mouse interaction and Ctrl+C copy

**Files:**
- Modify: `D:\MDKF\src\main\java\com\niuqu\chatbubble\ChatBubbleScreen.java`

**Interfaces:**
- Consumes: `textSpans`, `textSelection` fields from Task 3, `TextSpan`, `ChatTextSelection`.
- Produces: same helpers as Task 4, using Fabric APIs.

- [ ] **Step 1: Add fields**

Next to `clickableSpans`:

```java
private final List<TextSpan> textSpans = new ArrayList<>();
private final ChatTextSelection textSelection = new ChatTextSelection();
```

- [ ] **Step 2: Add hit-testing helpers**

```java
private TextSpan findTextSpanAt(double mouseX, double mouseY) {
    for (int i = textSpans.size() - 1; i >= 0; i--) {
        TextSpan s = textSpans.get(i);
        if (mouseX >= s.x() && mouseX <= s.x() + s.w()
            && mouseY >= s.y() && mouseY <= s.y() + s.h()) {
            return s;
        }
    }
    return null;
}

private int charAt(TextSpan span, double mouseX) {
    String text = span.text();
    if (text.isEmpty()) return 0;
    double localX = (mouseX - span.x()) / span.scale();
    int lo = 0;
    int hi = text.length();
    while (lo < hi) {
        int mid = (lo + hi + 1) >>> 1;
        if (textRenderer.getWidth(text.substring(0, mid)) <= localX) {
            lo = mid;
        } else {
            hi = mid - 1;
        }
    }
    return lo;
}
```

- [ ] **Step 3: Add click execution helper**

Extract Fabric's existing left-click style handling into:

```java
private void executeClickAction(double mouseX, double mouseY) {
    Style style = getHoveredStyle(mouseX, mouseY);
    if (style != null && style.getClickEvent() != null) {
        ClickEvent click = style.getClickEvent();
        if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
            chatField.setText(click.getValue());
        } else if (click.getAction() == ClickEvent.Action.OPEN_FILE) {
            java.io.File file = new java.io.File(click.getValue());
            Util.getOperatingSystem().open(file);
        } else if (click.getAction() == ClickEvent.Action.OPEN_URL) {
            String clickUrl = click.getValue();
            if (clickUrl != null && (clickUrl.startsWith("http://") || clickUrl.startsWith("https://"))) {
                handleComponentClicked(style);
            }
        } else {
            handleComponentClicked(style);
        }
    }
}
```

- [ ] **Step 4: Start selection in `mouseClicked`**

In `mouseClicked`, after the existing context/notification/scrollbar/suggestions/panel handling and before the existing `if (button == 0) { for (int[] r : bubbleRects) ... avatar ... }` block, insert:

```java
if (button == 0) {
    TextSpan hit = findTextSpanAt(mouseX, mouseY);
    if (hit != null) {
        if (textSelection.hasSelection()) textSelection.clear();
        textSelection.begin(hit.messageIndex(), hit.lineIndex(), hit.kind(),
            charAt(hit, mouseX));
        return true;
    }
    if (textSelection.hasSelection() || textSelection.isDragActive()) {
        textSelection.clear();
    }
}
```

Keep the existing immediate click handling for non-text clickable spans (images) below this block.

Remove the old `if (button == 0) { Style style = getHoveredStyle... }` immediate click block from `mouseClicked`; it is replaced by deferred execution in `mouseReleased`.

- [ ] **Step 5: Update selection in `mouseDragged`**

At the top of `mouseDragged`, before the scrollbar handling:

```java
if (textSelection.isDragActive()) {
    double mx = mouseX;
    if (isPanelSliding()) mx -= currentPanelOffset();
    TextSpan hit = findTextSpanAt(mx, mouseY);
    if (hit != null) {
        textSelection.update(hit.messageIndex(), hit.lineIndex(), hit.kind(), charAt(hit, mx));
    }
    return true;
}
```

- [ ] **Step 6: Finish selection in `mouseReleased`**

At the top of `mouseReleased`:

```java
if (textSelection.isDragActive()) {
    textSelection.endDrag();
    if (!textSelection.didMove()) {
        double mx = mouseX;
        if (isPanelSliding()) mx -= currentPanelOffset();
        executeClickAction(mx, mouseY);
        textSelection.clear();
    }
    return true;
}
```

- [ ] **Step 7: Add Ctrl+C copy in `keyPressed`**

At the top of `keyPressed`, before the Ctrl+V block:

```java
if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C && (modifiers & 0x2) != 0
    && textSelection.hasSelection()) {
    String copied = textSelection.copyText(textSpans);
    if (!copied.isEmpty()) {
        client.keyboard.setClipboard(copied);
        copyToastTicks = 30;
    }
    return true;
}
```

- [ ] **Step 8: Run Fabric tests**

Run `cmd.exe //c ".\gradlew.bat test --offline"`. Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/niuqu/chatbubble/ChatBubbleScreen.java
git commit -m "feat: drag-select and ctrl-c for chat text on fabric"
```

---

### Task 6: Final verification and packaging

**Files:** no source changes.

- [ ] **Step 1: Run full tests on all three repos**

- Forge: `cmd.exe //c ".\gradlew.bat test --offline -PrunTests"`
- NeoForge: `cmd.exe //c ".\gradlew.bat test --offline -PrunTests"`
- Fabric: `cmd.exe //c ".\gradlew.bat test --offline"`

Expected: BUILD SUCCESSFUL on all three.

- [ ] **Step 2: Build jars**

Use each repo's existing build task (e.g. `cmd.exe //c ".\gradlew.bat build --offline"` or the project's jar task) and copy the resulting jars to `D:\Claude_ds\e33chat-dist\2.4.2\`, overwriting the current 2.4.2 artifacts.

- [ ] **Step 3: Report**

Report the new commits, test results, and updated jar MD5s to the user. Do not push.
