# Release Notes

发版约定：每个版本一段（## vX.Y.Z），中文摘要在前、英文摘要放段尾，块间空行分隔；
用「新增 / 修复 / 更改」等常规分类组织，写法自由，不要拿语言名当标题。
仓库 GitHub Release 正文取整段；Modrinth / CurseForge 的 changelog 取段尾英文块（英文内部不要空行）。

## v2.4.12

**修复：发出去的 GIF 不会动（2.4.10 功能的一半没兑现）**
- 现象：动图在表情面板里能动，一发出去就变成静止的首帧；而别人从外部传进来的 GIF 反而是动的
- 根因：上传管线用 `ImageIO.read` 读文件——它对动图只返回第一帧——再统一重编码成 PNG，还把这个单帧 PNG 标成 `image/png`。动画在离开客户端之前就已经被压掉了，接收端的动图解码器只能看到一帧
- 修复：动图源改为**原字节直传**（不解码、不重编码），content-type 按真实格式给；直传前先核对接收端能否渲染（≤512px、≤48 帧、≤8MB），超出则**明确拒绝并提示**是尺寸、帧数还是体积超限，而不是静默降级成静态图——「发了但不动」正是这次要消灭的现象
- 顺带：图片消息的 `[[CICode]]` 补上 `name=` 文件名，图床返回无扩展名 URL 时下载端也能认出是动图

**修复：从系统消息里进来的 QQ 群转发全变灰字**
- 现象：`[QQ群消息] 名字：内容` 这类转发被当系统消息渲染成灰字，偶尔又能正确识别成玩家消息
- 根因：EasyBot 解析器的正则要求名字带尖括号（`<名字>`），而本服模板用的是 `[标签] 名字：内容`（无尖括号）→ 解析器直接弃权，整行掉到文本启发式的兜底层，时对时错；更糟的是兜底层重建显示名时会把标签拼进名字（`[QQ群消息] dangdang0721`），而名字匹配是「长名优先」，这个复合名一旦入缓存就会被后续所有消息优先命中，越错越深
- 修复：解析器接受「带标签 + 冒号分隔」的形状（标签是必需的结构信号——裸的 `名字：内容` 属于普通聊天，仍交给玩家路径）。这条路径不再对已知玩家让位，因此在线玩家的转发也保留头像皮肤，同时不再污染名字缓存
- 说明：尖括号形状的行为完全不变（含「已知玩家让位给玩家路径」那条）

**修复：空输入按 Tab 后聊天框再也无法输入（1.21.1 两端）**
- 现象：打开聊天框直接按 Tab，之后键入、退格全部失效，必须关掉聊天框重开
- 根因：原版 `ChatScreen` 构造补全器后会调 `setAllowHiding(false)`（Fabric 侧是 `setCanLeave(false)`），E33Chat 漏了。缺这一句时空输入按 Tab 会让补全器的 `keyPressed` 返回 false，Tab 继续走到自实现的焦点导航，那里在「前方无控件」时会清空焦点——而输入框是 `canLoseFocus=false`，焦点清掉就再也拿不回来
- 修复：两端补上这一句（与原版一致）；另外给焦点导航里的清空动作加守卫——输入框仍持有焦点时不再清，堵住同类损耗的另一条路径（箭头键也会走到那里）
- 注意：1.20.1 的 `CommandSuggestions` 没有这个开关，其空输入按 Tab 本来就会打开补全列表，故 **Forge 端不受此 bug 影响**，只补了守卫

**修复：打开聊天面板时物品栏 HUD 消失（2.4.9 回归的一半）**
- 背景：2.4.9 为 issue #16「半透明面板透出快捷栏/药水/Jade 提示框」在打开 E33Chat 界面时隐藏整个 HUD 层；2.4.11 修掉了它连带关掉第一人称手的问题，但物品栏仍被隐藏
- 判定：聊天面板是**左对齐的一条窄列**（默认约占屏宽 40%），物品栏在屏幕底部居中、状态效果在右上，本来就在面板之外，隐藏它们没有换来任何观感收益，却拿走了玩家看快捷栏的能力
- 修复：**聊天面板不再隐藏 HUD**；两个配置屏（客户端/服务端，全屏半透明底）保持隐藏
- 说明：原版 `Gui` 把物品栏、经验条、准星、药水图标挂在同一个图层组里一起渲染，取消这一个事件必然整组消失——本版不做「只藏物品栏留药水」的分层
- 铁律不变：隐藏 HUD 永不使用 `options.hideGui`（那是 F1，会连带第一人称手）

**新增：自定义面板背景图的取景**
- 现象/需求：背景图是「等比居中裁剪」，图没变形，但没法选裁哪一块——面板又高又窄，宽图左右被切掉一大截，想露的主体常常不在中间
- 实现：设置 → 聊天框 → 面板 的图片行改为「调整取景 / 清除」；选完图直接进取景编辑器：整图预览 + 一个**锁定面板宽高比**的选取框，框内拖动平移、滚轮缩放，确定后按此取景铺满
- 存法：取景保存为归一化值（中心点 + 缩放）而非绝对矩形，之后改面板宽度、改窗口大小或开铺满模式都能自动适配，不会画歪；清除图片会一并清掉取景
- 加载失败/未配置仍回退默认面板纹理

**更改：自定义表情包上限 10 → 32**
- 表情面板本来就可以滚动，10 张只是当初拍的数；上限提到 32，文案同步

**调试：指令补全列表鼠标点不了（临时埋点，未定案）**
- 现象：补全列表出现后无法用鼠标点击选择（原版可以）
- 现状：代码上点击是有接线的，且渲染与命中判定用的是同一个矩形，静态分析读不出原因
- 本版加入了临时诊断日志（记录点击落点、面板偏移、以及补全列表构造出的矩形），需在实机复现一次以区分「点击被更早的分支吃掉」与「绘制与命中矩形不一致」；**开启 `debug_log` 后复现一次即可定位**，修复留待下一版

**Fixed: sent GIFs did not animate (half of the 2.4.10 feature never landed)**
- Symptom: a GIF animated in the emote panel but froze on its first frame once sent, while GIFs arriving from outside did animate
- Root cause: the upload path read the file with `ImageIO.read` (which returns only the first frame for animated images) and re-encoded everything as PNG, announcing it as `image/png`. The animation was destroyed inside the client, so the receiver's animated decoder only ever saw one frame
- Fix: animated sources are now sent **byte-for-byte** (no decode, no re-encode) with their real content type. Before sending, the file is checked against what the receiver can render (<=512px, <=48 frames, <=8MB); anything heavier is **rejected with a specific reason** (dimension / frame count / size) instead of being silently downgraded to a still image, since "sent but frozen" is exactly the bug being fixed
- Also: image messages now carry a `name=` hint in their `[[CICode]]`, so extension-less host URLs are still recognised as animations on download

**Fixed: QQ group relays arriving through the system channel all rendered as grey system text**
- Symptom: `[QQ group] name: content` relays showed as grey system messages, while occasionally the same shape was recognised correctly as a player message
- Root cause: the EasyBot parser required angle brackets around the name (`<name>`), but this server's template uses `[label] name: content` (no brackets) — so the parser declined and every line fell through to the text-heuristic fallback, which was right only sometimes. Worse, that fallback rebuilds the display name as "everything before the name + name" (`[QQ group] dangdang0721`), and since name matching prefers the longest match, one bad name poisoned the cache and then won every later match
- Fix: the parser now accepts the labeled colon shape. The label is required — a bare `name: content` is ordinary chat and still belongs to the player path. This path no longer steps aside for known players, so relays from online players keep their skin and stop poisoning the name cache
- Note: the angle-bracket shapes behave exactly as before, including the "known player steps aside for the player path" rule

**Fixed: after pressing Tab on an empty chat input, typing stopped working (both 1.21.1 loaders)**
- Symptom: opening chat and pressing Tab straight away killed typing and backspace until the chat screen was closed and reopened
- Root cause: vanilla `ChatScreen` calls `setAllowHiding(false)` on its suggestor (Fabric: `setCanLeave(false)`) and E33Chat never did. Without it, Tab on an empty input makes the suggestor's `keyPressed` return false, so Tab fell through to the self-implemented focus navigation, which clears the focus when there is nowhere to go — and the chat field is built with `canLoseFocus=false`, so the focus never comes back
- Fix: both loaders now make the vanilla call; the focus-clearing step also gained a guard so it never clears while the chat field still holds focus (arrow keys reach the same code)
- Note: 1.20.1's `CommandSuggestions` has no such switch and opens the list on Tab anyway, so **Forge was never affected** and only received the guard

**Fixed: the hotbar HUD disappeared while the chat panel was open (half of the 2.4.9 regression)**
- Background: 2.4.9 hid the whole HUD layer behind E33Chat screens to fix issue #16 (the hotbar, potion icons and Jade tooltips showing through the translucent panel). 2.4.11 stopped it from also hiding the first-person hand, but the hotbar stayed hidden
- Judgement: the chat panel is a **left-aligned narrow column** (~40% of the screen by default), while the hotbar sits centred at the bottom and status effects at the top right — both outside the panel. Hiding them bought nothing visually and cost the player sight of their hotbar
- Fix: **the chat panel no longer hides the HUD**; the two config screens (full-width translucent backgrounds) still do
- Note: vanilla `Gui` renders the hotbar, experience bar, crosshair and status effects as one layer group, so cancelling that event necessarily removes them all; per-layer hiding is out of scope for this release
- Standing rule: hiding the HUD never uses `options.hideGui` (that is F1 and also removes the first-person hand)

**Added: framing for the custom panel background**
- Problem: the background was "aspect-preserving center-crop" — undistorted, but with no way to choose which part survives. The panel is tall and narrow, so a wide picture loses its sides and the subject is often off-centre
- Implementation: Settings -> Chat panel now shows "Adjust framing" + "Clear" once a picture is set. Picking a picture opens the framing editor directly: whole-picture preview with a selection box **locked to the panel's aspect ratio**; drag inside the box to pan, scroll to zoom. Confirm applies it
- Storage: framing is saved as normalized values (centre + zoom) rather than an absolute rectangle, so changing the panel width, resizing the window or enabling fullscreen re-fits automatically instead of drifting. Clearing the picture clears the framing with it
- A missing or unreadable picture still falls back to the default panel texture

**Changed: custom emote pack limit 10 -> 32**
- The emote grid was always scrollable, so 10 was an arbitrary number; the cap is now 32 and the docs match

**Debug: the command suggestion list does not respond to mouse clicks (temporary instrumentation, not yet resolved)**
- Symptom: the suggestion list cannot be clicked with the mouse (vanilla allows it)
- Status: the click is wired up in code and the render and hit-test use the same rectangle, so static analysis could not explain it
- This release adds temporary diagnostics (click point, panel offset, and the rectangle the list is built with) so one in-game reproduction can separate "an earlier branch eats the click" from "the drawn rect and the hit rect disagree". **Enable `debug_log` and reproduce once**; the fix is deferred to the next version

## v2.4.11

新增：聊天群组（mod 内置服务端路由 + 页签管理）、自定义面板背景图、GIF/WebP 动图消息、玩家资料卡。
修复：打开聊天面板崩溃、面板打开时 HUD 遮挡第一人称手部、服务器图片连发被限流、群组弹层点击闪烁、中文群名发送失败、名字误加下划线与点击事件。

Added: in-mod chat groups (in-mod server routing with tab management), custom panel background, animated GIF/WebP messages, player profile card.
Fixed: chat-open crash, HUD hiding the first-person hand while the panel is open, rate-limited server images, group popup flicker, CJK group-name send failures, stray name underlines.
