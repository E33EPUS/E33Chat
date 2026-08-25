# Text Selection in Chat Bubbles and System Messages — Design

Date: 2026-08-25
Status: Approved
Scope: Forge 1.20.1 / NeoForge 1.21.1 / Fabric 1.21.1 (E33Chat)

## Overview

Allow the player to drag-select partial text inside E33Chat bubbles and system
messages, matching vanilla Minecraft's chat text-selection behavior. The
selection is visual, persists while the chat panel is open, and can be copied
with Ctrl+C.

## Requirements

- Drag with the left mouse button selects partial text inside:
  - bubble sender names
  - bubble message content
  - bubble quote/reply blocks
  - system message text
- Selection may span multiple lines and multiple messages.
- A simple click (no drag) still triggers the existing click behavior for
  clickable text (links, commands, hover events).
- Ctrl+C copies the selected plain text. Selected lines are joined with `\n`.
- A semi-transparent blue highlight is drawn behind selected glyphs.
- Clicking outside the selection clears it.
- The feature behaves identically on Forge, NeoForge, and Fabric.

## Design

### Data model (platform-independent)

Add two small classes in `com.niuqu.chatbubble.render` on every loader:

- `TextSpan`
  - `int messageIndex` — index into `ChatMessageStore.getMessages()`
  - `int lineIndex` — visual line index within the message
  - `int kind` — `NAME`, `CONTENT`, or `QUOTE`
  - `int x, y, w, h` — line rectangle in panel-local coordinates
  - `String text` — visible text of the line
  - `float scale` — bubble scale factor used to convert local coords to screen
- `ChatTextSelection`
  - stores `anchor` and `focus` as `(messageIndex, lineIndex, kind, charIndex)`
  - tracks whether a drag is active and whether the pointer moved
  - `rangeFor(TextSpan)` returns the selected `[start, end)` character range
    for that span, or `null`
  - `copyText(List<TextSpan>)` concatenates selected substrings in visual order,
    joining selected lines with `\n`

`TextSpan.orderKey()` defines visual order within one message:
`messageIndex * 100_000 + kind * 10_000 + lineIndex`.

### Rendering integration

- `renderMessages` clears `textSpans` each frame, alongside `clickableSpans`.
- Every selectable draw site records a `TextSpan` and draws selection highlight
  before the glyphs:
  - Forge/Neo: `ChatMessageRenderer.renderLineWithClicks(...)` gains an
    overload that takes `messageIndex`, `lineIndex`, `kind`, `scale`,
    `List<TextSpan>`, and `ChatTextSelection`.
  - Fabric: the private `ChatBubbleScreen.renderLineWithClicks(...)` gets the
    same parameters.
- Sender names and quote text also go through the same helper so they become
  selectable.
- Bubble content uses the existing `bubble_size` scaled pose. `TextSpan`s are
  recorded in local matrix coordinates, then transformed to panel-local screen
  coordinates exactly like `ClickableSpan`s already are.
- Selection highlight is drawn in the current matrix space before the text, so
  it stays aligned under scaled bubble text.

### Mouse interaction

- `mouseClicked(button == 0)`:
  - If the pointer hits a `TextSpan`, start a selection drag (`anchor = focus =
    hit position`) and return `true` so drag events are captured.
  - Do not execute clickable-text actions on press; they are deferred to
    release.
  - If the pointer hits a non-text clickable span (e.g. an image), keep the
    existing immediate click behavior.
  - If the pointer hits no text span, clear the current selection.
- `mouseDragged`:
  - While a selection drag is active, update `focus` from the current pointer
    position and return `true`.
  - Mark the drag as "moved" when the pointer leaves the starting cell.
- `mouseReleased`:
  - If a selection drag was active and it did not move, run the existing
    clickable-text action (links/commands) at the press/release position and
    clear the zero-length selection.
  - If it moved, keep the selection.
  - Clear the active-drag flag.

Panel slide offset is handled the same way as existing clickable spans: hit
testing uses panel-local coordinates by subtracting `currentPanelOffset()`.

### Copy behavior

- In `keyPressed`, intercept `Ctrl+C` before the focused EditBox when
  `ChatTextSelection.hasSelection()` is true.
- Copy the selected plain text via `ChatTextSelection.copyText(textSpans)`.
- Set the system clipboard and show the existing copy toast.
- If the EditBox itself has a text selection and the chat text selection is
  empty, normal EditBox Ctrl+C behavior is unchanged.

### Out of scope

- No right-click "Copy selected" menu entry.
- No auto-scroll while dragging beyond the visible area.
- Images, time separators, duplicate-count labels, and generated image status
  placeholders are not selectable.

## Testing

- Add `ChatTextSelectionTest` to all three loaders:
  - same-line forward and reverse selection
  - multi-line selection
  - multi-message selection and copy order
  - empty/no selection returns empty string
  - newline joining behavior
- Run existing test suites:
  - Forge/Neo: `test --offline -PrunTests`
  - Fabric: `test --offline`
- Build jars and copy them to the 2.4.x distribution folder after green tests.
