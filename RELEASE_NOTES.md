# Release Notes

发版约定：每个版本一段（## vX.Y.Z），中文摘要在前、英文摘要放段尾，块间空行分隔；
用「新增 / 修复 / 更改」等常规分类组织，写法自由，不要拿语言名当标题。
仓库 GitHub Release 正文取整段；Modrinth / CurseForge 的 changelog 取段尾英文块（英文内部不要空行）。

**Fixed: your own images would "randomly fail to load"**
- Symptom: after pasting a few images in a row, one of them reported a load failure; the log said `timed out after 30s` while only 6ms had passed, and the same file loaded fine moments later
- Root cause (three things stacked):
  1. **Every image wasted a download slot** — the animated probe fired a request for every `e33chat://media/` URL even when the CICode already carried `name=1.jpg` (a JPEG cannot animate), and each image already costs "one upload plus one download by its own sender"
  2. **NeoForge never got the 2.4.11 de-duplication fix** — its `fetch` did a plain `FETCHES.put()`, so the second request for a mediaId (the probe and the static loader both fetch) replaced the first caller's future; the first then waited out its full 30-second timeout and reported a failure for an image that had already downloaded. The 2.4.11 merge fix only ever landed on Forge
  3. **The log lied** — the server answers "rate limited" and "file missing" with the same sentinel, and the client reported both as a 30-second timeout, pointing every investigation at the network
- Fix: (1) `animatedEntry` short-circuits `.jpg/.jpeg/.bmp` (those cannot animate; `.png` is still probed because APNG shares the extension), (2) `MediaClient` keeps a **local cache of our own uploads** (24-entry LRU) so the sender's own images come from memory — no download slot, nothing to fail, (3) NeoForge and Fabric now use `computeIfAbsent` to merge concurrent fetches like Forge does, (4) timeouts and refusals are logged as the different things they are, (5) the server rate limit goes from 4 to 16 per 10 seconds (still bounding abuse, no longer punishing a normal paste-a-few session)

**Fixed: the background framing screen was blank (bug in the new framing feature)**
- Symptom: choosing a panel background and then opening the framing editor showed nothing at all
- Root cause: `PanelBackground.ensureLoaded()` was only ever called while the chat panel rendered. The settings screen and the framing screen do not draw the chat panel, so a picture chosen there never started loading and `imageWidth()` stayed 0
- Fix: the framing screen starts the load itself, polls for the size while it loads and re-lays out once it arrives, and shows "Loading the background image...", a **red specific reason** on failure (unsupported format / missing file) or "No background image set" — no more staring at an empty screen

**Changed: animation limits aligned with AtomChat (48 -> 120 frames, trimming instead of rejecting)**
- Symptom: a 66-frame and a 72-frame GIF were refused here while AtomChat accepted the same files
- Root cause: E33Chat used "48-frame hard cap, reject past it"; AtomChat uses "120-frame cap plus an 8M-pixel-per-image budget", trimming the tail instead of refusing
- Fix: the frame cap is now 120, with an 8M-pixel **decoded budget per image** (~32 MB of GPU memory) that keeps the first frames when a longer animation would exceed it — a trimmed GIF still plays and still reads, while a rejection is a dead end the user cannot act on. The 512px dimension cap is unchanged
- Note: this is also why **GIFs in the emote panel are now still thumbnails** — with the cap at 120, animating 32 grid cells would burn frame time and GPU memory for a 26px square you cannot read anyway, while the sent message still animates. Animated emotes carry a `GIF` badge so it is clear they move once sent

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

**修复：自己发的图片会"莫名其妙加载失败"**
- 现象：连发几张图后，中间某张报加载失败；日志说 `timed out after 30s`，但耗时只有 6ms，同一个文件过一会儿又能正常加载
- 根因（三层叠加）：
  1. **每张图白花一次下载额度**——动图探测对 `e33chat://media/` 无条件发请求，哪怕 CICode 里已经写着 `name=1.jpg`（jpg 不可能是动图）。服务端额度是 4 次/10 秒、上传和下载共用，而每条图本身就要花掉"上传 1 次 + 自己的客户端再下载 1 次"
  2. **NeoForge 端 2.4.11 的去重修复从未落地**——Neo 的 `fetch` 用 `FETCHES.put()` 直接覆盖，同一 mediaId 的第二个请求（探测 + 静态加载器各发一次）会把第一个的 future 顶掉，第一个只能等满 30 秒超时并报失败，而图片其实早就下好了。2.4.11 的"同 id 请求合并"只改到了 Forge
  3. **日志说谎**——服务端对"被限流"和"文件不存在"回同一个哨兵，客户端 `catch (Exception)` 一律打成"超时 30s"，把排查引向网络
- 修复：①`animatedEntry` 对 `.jpg/.jpeg/.bmp` 直接短路（这些扩展名不可能动，`.png` 仍探测因为 APNG 共用扩展名）；② `MediaClient` 新增**自己上传的本地缓存**（24 条 LRU）——自己发的图直接命中内存，不花下载额度、也不可能"加载失败"；③NeoForge/Fabric 的 `fetch` 改为 `computeIfAbsent` 合并并发请求（与 Forge 对齐）；④超时与拒绝分开记录日志；⑤服务端限流 4 → 16 次/10 秒（16 仍能挡住滥用，但不再惩罚正常的连发几张）

**修复：壁纸取景界面一片空白（新功能的 bug）**
- 现象：选完背景图后进入取景界面，什么都没有，看不到图
- 根因：`PanelBackground.ensureLoaded()` **只在聊天面板渲染时被调用**。配置屏和取景屏都不渲染聊天面板，所以刚选完图时纹理从未开始加载，`imageWidth()` 为 0，自然什么都画不出来
- 修复：取景屏自己触发加载，并在加载期间每 tick 轮询尺寸、就绪后重排布局；界面在未就绪时显示「正在加载背景图…」，失败时显示**红色的具体原因**（格式不支持/文件不存在），未设置时提示"尚未设置背景图"——不再让用户对着空白界面猜

**更改：动图上限对齐 AtomChat（帧数 48 → 120，并改用像素预算裁帧）**
- 现象：用户 66 帧 / 72 帧的 GIF 被拒绝，而同一个文件 AtomChat 能发
- 根因：E33Chat 的动图限制是"48 帧硬上限 + 超出直接拒绝"，AtomChat 用的是"120 帧上限 + 8M 像素/图的预算"，超出部分**裁帧而不是拒绝**
- 修复：帧数上限提到 120，并引入 8M 像素的**单图解码预算**（约 32MB 显存）——超预算时保留前若干帧（被裁短的 GIF 仍然会动、仍然能看清内容，而"拒绝"是用户无法处理的死路）。512px 尺寸上限不变
- 说明：这也是为什么**表情面板里的 GIF 改成静态缩略图**——帧数上限提到 120 后，32 个格子同时逐帧动画会白白吃掉帧时间和显存（26px 的格子本来也看不清动画），而发到聊天里的消息仍然逐帧播放。GIF 表情在格子上会有 `GIF` 角标提示它是动图

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
