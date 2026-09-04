# Changelog

## v2.4.7

**修复：上键翻阅历史被指令补全抢占（四端同步）**
- 上键从历史记录翻回一条指令（如 `/time set 1`）后，补全窗口会自动弹出，下一次按上键被补全列表吃掉，无法继续向上翻历史；原版聊天框只在按 Tab 时才进入补全
- 现在与原版一致：从历史填入文本时关闭补全自动弹出（原版 `setAllowSuggestions(false)` 同款），重新编辑文本或按 Tab 时补全照常恢复
- 同修：从历史翻回含 `@` 的消息时，@提及候选列表也不再自动弹出抢占上下键

**Fixed: Up-arrow history navigation no longer hijacked by command completion (all four builds)**
- Pressing Up to recall a command from history (e.g. `/time set 1`) auto-opened the suggestion window, and the next Up press cycled the suggestion list instead of going further back in history; vanilla chat only enters completion on Tab
- Now matches vanilla: text filled from history disables the auto-suggestion popup (vanilla's own `setAllowSuggestions(false)` behaviour); suggestions return as soon as you edit the text again, or on Tab
- Also fixed: recalling a history entry containing `@` no longer auto-opens the @mention candidate list (same Up/Down hijack)

**新增：清空聊天历史按钮（设置菜单）**
- 齿轮设置菜单新增“清空聊天历史”（垃圾桶图标）：清空当前世界的内存消息 + 本地历史文件（含 legacy 文件），不影响其他世界
- 两击确认防误触：第一次点击后该行变红“确认清空？”，1 秒内再点同一行才执行；点别处 / ESC / 超时 / 点其他菜单项都会取消
- 空历史时第一次点击直接提示“没有可清空的历史”，不进入确认状态
- 清空成功后复用现有 toast 提示“已清空聊天历史”
- 异步保存防复活：清空时递增 generation，旧快照不再把已删除的历史文件写回

**Added: Clear chat history button (settings menu)**
- New “Clear Chat History” item in the gear settings menu (trash icon): clears the current world’s in-memory messages and local history file (including legacy files); other worlds are untouched
- Two-click confirm to prevent accidents: the first click turns the row red (“Confirm clear?”), and a second click within 1 second executes; clicking elsewhere / ESC / timeout / choosing another item cancels
- On empty history the first click directly shows “No history to clear” and does not arm the confirm state
- Reuses the existing toast for “Chat history cleared”
- Async-save resurrection guard: clearing bumps a generation counter so stale snapshots cannot rewrite the deleted history file

**修复：EasyBot 群消息没被解析成玩家气泡（三端同步）**
- 转发行是机器人侧拼装的，`[群名]` 前缀并不固定：实测服的模板就是 `<昵称> 内容`（既没有群名前缀，也没有 QQ 号）。旧解析器强制要求开头有 `[标签]`，这类行全部落回灰色系统消息（issue #15）
- 现在 `[群名]` 前缀改为可选，只要 `<名字> 内容` 结构成立就按玩家气泡解析；`<昵称（群名片）>` 这类后缀保留在显示名里，全角括号 `（123456789）` 也识别为 QQ 号
- 兼容开关改为默认开启：服务端装了 e33chat 时仍以服务端开关为准（可显式关闭）；服务端没装 / 版本旧到不会同步该开关时，客户端按开启处理，不再因为等不到服务端指令而什么都不做
- 防误判不变：正文为空、名字超过 32 字、以及 `<系统>` / `[公告] <Server>` 这类通用广播标签仍保持系统消息；名字能精确匹配到在线玩家时让位给玩家解析路径，保留真实 UUID 与皮肤
- 调试日志新增 `System(EasyBot miss)`：开 debug 后，形状像转发但没被采纳的行会单独打一条，方便直接判断模板是否匹配
- 新增 7 例 EasyBotParser 单测（无群名前缀 / 群名片后缀 / 全角括号 QQ / 空正文 / 广播名 / 尖括号不在行首 / 超长名）

**Fixed: EasyBot group relays were not parsed as player bubbles (all three builds)**
- The relay line is assembled bot-side, so the `[群名]` prefix is not guaranteed — the reporting server's template is simply `<nick> content` (no group label, no QQ number). The old parser required a leading `[label]`, so those lines fell back to grey system messages (issue #15)
- The `[群名]` prefix is now optional: any `<name> content` line parses as a player bubble. A `<nick（group card）>` suffix stays part of the display name, and full-width `（123456789）` parentheses are recognised as the QQ number too
- Compatibility is now on by default: servers running e33chat still decide (the server toggle can explicitly disable it); when the server runs no (or an older) e33chat and never syncs the flag, the client treats it as enabled instead of silently doing nothing
- False-positive guards unchanged: blank content, names longer than 32 chars, and generic broadcast labels (`<系统>`, `[公告] <Server> ...`) stay system messages; a name that exactly matches an online player is handed back to the player path so its real UUID and skin are preserved
- New `System(EasyBot miss)` debug line: relay-shaped lines the parser declined are logged individually, so a template mismatch is diagnosable from the log alone
- Added 7 EasyBotParser unit tests (no group label / group-card suffix / full-width QQ parens / blank content / broadcast name / angle brackets not at line start / over-long name)

**修复：装了 ChatImage 后，聊天里的图片在气泡中不显示（三端同步）**
- ChatImage 用 `@ModifyVariable` 直接改写 `ChatHud.addMessage` 的组件参数：消息里的 `[[CICode,url=...]]` 被替换成它自己的组件（文本变占位、URL 藏进自定义 `show_chatimage` hover，value 是 `ChatImageCode` 对象）。e33chat 捕获时括号码已经没了，而 `BracketCodec` 的 hover 取值只认 JSON / String 两种，`ChatImageCode` 对象取不出 URL → 气泡里没有图
- 现在 hover value 不是 JSON/String 时，会按 `ChatImageCode.toString()`（就是原始的 `[[CICode,url=...]]`）把 URL 解析回来；零反射、不依赖 ChatImage 的类
- hover action 识别加兜底：`toString()` 不是 action id 时，改看 value 的类名是否含 `chatimage`
- 捕获路径补一刀：行文本已不含原始内容、但原始内容里能解析出图片时，保留服务端原始内容，避免气泡重复显示发送者名
- 原版聊天栏不受影响：装了 ChatImage 就由 ChatImage 自己画图，没装则沿用 `[图片]` 占位
- 新增 3 例 BracketCodecUrlTest（ChatImageCode 字符串还原 / 裸 URL 透传 / 非 URL 拒绝）

**Fixed: images no longer render in bubbles when ChatImage is installed (all three builds)**
- ChatImage rewrites the `ChatHud.addMessage` component argument via `@ModifyVariable`: the `[[CICode,url=...]]` in a message is replaced by its own component (placeholder text, the URL hidden inside a custom `show_chatimage` hover whose value is a `ChatImageCode` object). By the time e33chat captured the line the bracket was gone, and `BracketCodec` only understood JSON / String hover values — so the URL was unreachable and the bubble showed no image
- When the hover value is neither JSON nor a String, the URL is now parsed back out of `ChatImageCode.toString()` (which is the original `[[CICode,url=...]]`); no reflection, no dependency on ChatImage classes
- Hover-action detection gained a fallback: when `toString()` is not the action id, the payload's class name is checked for `chatimage`
- Capture path hardened: when the final line no longer contains the raw content but an image can still be parsed out of it, the pristine server-sent content is kept so the bubble does not repeat the sender name
- The vanilla surface is untouched: with ChatImage installed ChatImage still draws its own image, without it the `[图片]` placeholder remains
- Added 3 BracketCodecUrlTest cases (ChatImageCode string recovery / bare URL passthrough / non-URL rejection)


## v2.4.6

**消息紧凑分组（三端同步：Fabric / Forge / NeoForge）**
- 配置“隐藏连续消息头像”升级为**消息紧凑分组**，默认值 true → false：开启后同一人 5 分钟内的连续消息只在首条显示头像和名字，后续只留气泡（类 Discord/Telegram）；此前只隐藏头像，名字和间距都不变
- 紧凑分组时组内后续消息：不再绘制名字行（每条省出名字行高度）、气泡顶对齐行顶、横向保持原气泡列对齐、与上一条间距收紧为 max(2, message_gap/3)（默认 6→2px）
- 表情消息、图片消息同样适用紧凑分组；分组仍被系统消息和时间分隔线打断；头像的 @提及左键与右键菜单命中区随头像一起隐藏
- 三处布局循环（总高/渲染/跳转）同步更新，滚动与“跳到下一条”定位一致；新增 groupedGap 纯函数单测

**Compact message groups (all three loaders: Fabric / Forge / NeoForge)**
- The "hide repeated avatars" toggle is upgraded to **compact message groups**, default flipped true → false: when on, only the first message of a same-sender run (within 5 min) shows avatar and name; the rest render as bubbles only (Discord/Telegram-style). Previously only the avatar was hidden while names and spacing stayed
- For grouped follow-up messages: the name row is not drawn (saving its height per message), the bubble starts at the row top, horizontal bubble columns stay aligned, and the gap to the previous message tightens to max(2, message_gap/3) (6 → 2px at defaults)
- Emote and image messages follow the same compact treatment; groups are still broken by system messages and time separators; the avatar's @-mention left-click and right-click context-menu hit regions hide together with the avatar
- All three layout loops (total height / render / jump) updated in sync so scrolling and jump-to-message stay aligned; added groupedGap pure-function unit tests

## v2.4.5

**窗口模式面板宽度上限（三端同步：Fabric / Forge / NeoForge）**
- 窗口模式下聊天面板不再超过窗口宽度的 40%：此前面板按固定物理像素宽渲染（如 1000px），在较小的窗口里会占据大半屏幕（实测 2184px 宽窗口、GUI 缩放 5 时占 66%）；现在自动收缩到 40%，2560 宽全屏下 1000px 面板（39%）不受影响
- 上限按窗口宽度比例计算，与 GUI 缩放无关；`panel_fullscreen` 铺满模式不受此上限约束
- 布局单测从 12 例扩到 17 例（窗口占比上限 / 缩放无关性 / 全屏豁免 / 下限优先级）
- 文字选区高亮改为固定"选中蓝"（#2D6FD6，近不透明，文字反白）：原白/黑半透明选区在深色气泡上呈中灰糊块，与灰色系统消息重色、不明显；新选区与灰字色相天然区分，深浅背景都可读
- 修复弹层关闭动画（设置/表情/常用语/搜索）：关闭改为**倒放打开曲线**，与打开方向完全对称（原关闭用另一套缓动、无 ZOOM 回弹，观感像另一种动画且错位）；alpha 低于 0.02 时整层跳绘——修复淡出最后一帧文本/表情突然恢复不透明的闪烁（vanilla `Font.adjustColor` 会把 alpha≤3 的颜色强制为全不透明，面板背景走 SDF 着色器不受影响）
- Forge/NeoForge 同步修复关闭动画时钟错配：关闭时间戳原来用 `System.currentTimeMillis()` 而进度计算用 `Util.getMillis()`（两个纪元相差约 1.7 万亿 ms，关闭进度恒为 0），导致关闭动画从未真正播放——满透明度冻结 150ms 后瞬间消失；现对齐 Fabric 全链路同钟

**Windowed-mode panel width cap (all three loaders: Fabric / Forge / NeoForge)**
- In windowed mode the chat panel no longer exceeds 40% of the window width: the panel previously rendered at a fixed physical pixel width (e.g. 1000px), dominating smaller windows (measured 66% of a 2184px window at GUI scale 5); it now shrinks automatically to 40%, while a 1000px panel on a 2560px fullscreen display (39%) is unaffected
- The cap is proportional to the window width and independent of GUI scale; the `panel_fullscreen` fill mode is exempt from the cap
- Layout unit tests expanded from 12 to 17 cases (window cap / scale invariance / fullscreen exemption / floor precedence)
- Text selection highlight changed to a fixed "selection blue" (#2D6FD6, near-opaque, white text): the old white/black translucent overlays rendered as mid-grey patches on dark bubbles that blended with grey system messages; the new hue-distinct blue reads clearly on any background
- Fixed popup close animations (settings/emoji/quick-chat/search): closing now **replays the open curve in reverse**, fully symmetric with opening (the old close used a different ease-in curve without the ZOOM overshoot, reading as a different, misaligned animation); the whole layer is skipped below alpha 0.02 — fixing text/emoji snapping back to opaque on the last fade frame (vanilla `Font.adjustColor` forces colors with alpha <= 3 to fully opaque, while the panel background goes through the SDF shader and faded correctly)
- Forge/NeoForge also fix a clock mismatch that disabled their close animations entirely: the close timestamp used `System.currentTimeMillis()` while progress math used `Util.getMillis()` (~1.7e12 ms epoch difference, progress stuck at 0), leaving popups frozen at full alpha for 150ms before vanishing; both now use one clock end-to-end, matching Fabric

## v2.4.4

**面板限制与像素修复（NeoForge 先行）**
- 面板宽度范围 800–1600 → **400–1600**（默认仍 1000），小窗口也可用细面板
- 新增客户端配置 `panel_fullscreen`（默认关）：开启后聊天面板铺满整个屏幕宽度、忽略 `panel_width`，侧边栏保留可点
- 修复面板像素宽不真实的问题：宽度换算改用精确（可能为小数的）GUI 缩放值，不再四舍五入到整数——面板物理宽度现在与窗口 / 缩放无关，改动窗口大小不再漂移或错位（窗口比面板窄时仍夹紧到窗宽）
- 修复背景模糊（`blur_enabled`）在分数缩放下的错位：模糊区域改用精确缩放换算，与面板矩形严格对齐
- 新增 `computePanelWidth` 布局单测 12 例（分数缩放精确性 / 全屏铺满 / 小窗口夹紧 / 下限保护）

**Panel limits & pixel fix (NeoForge first)**
- Panel width range widened from 800–1600 to **400–1600** (default stays 1000), so small windows can use a narrower panel
- New client config `panel_fullscreen` (off by default): fills the whole screen width with the chat panel, ignoring `panel_width`, while keeping the sidebar usable
- Fixed the panel's real width not matching its setting: the width is now converted with the exact (possibly fractional) GUI scale instead of a rounded int — the physical width no longer drifts or misaligns when the window is resized (it still clamps to the window width when the window is narrower)
- Fixed the background blur (`blur_enabled`) misaligning at fractional scales: the blur region now uses exact scaling and matches the panel rectangle
- Added 12 `computePanelWidth` layout unit tests (fractional-scale exactness / fullscreen fill / small-window clamp / floor guard)

## v2.4.3-beta

**EasyBot 群消息兼容（NeoForge 先行）**
- 新增服务端配置 `easybot_compat`（默认开）：把 EasyBot 转发进游戏的 QQ 群消息（默认格式 `[群名] <昵称(QQ号)> 内容`）解析为 E33Chat 玩家气泡，不再显示为灰色系统消息；不需要可手动关闭
- 支持 EasyBot/ChatImage 的 CICode 图片：气泡内可直接显示 QQ 图片卡片（从 SHOW_TEXT hover 里的 `[[CICode,...]]` 提取）
- 服务端模板新增 `{external}` 占位符：用于“外部/QQ 发送者”，不要求名字能解析为已知玩家；`/e33chat gui` 聊天模板预设区已加入 EasyBot 默认格式，方便服主在 EasyBot 模板被自定义时覆盖
- README / README_EN 增加 EasyBot 兼容说明

**EasyBot group-message compatibility (NeoForge first)**
- New server config `easybot_compat` (on by default): QQ group messages relayed by EasyBot (default format `[Group] <Nick(QQ#)> content`) are parsed as E33Chat player bubbles instead of grey system messages; turn it off if you prefer the old system-message style
- EasyBot/ChatImage CICode images are supported: QQ images render as cards inside bubbles (extracted from `[[CICode,...]]` in SHOW_TEXT hover)
- Server templates gained `{external}` for external/QQ senders that do not need to resolve to known players; the `/e33chat gui` chat-template presets now include the EasyBot default format so server owners can override customized EasyBot templates
- README / README_EN updated with EasyBot compatibility docs

## v2.4.2

**横幅堆叠机制（三端同步：Fabric / Forge / NeoForge）**
- 通知横幅改为手机式堆叠：新横幅立即出现在最上方，旧横幅 200ms 平滑下移并缩小到 75%，保留头像+标题+首行内容
- 同时最多显示 3 条（可配置 1–5），满员时最旧一条 150ms 缩小淡出被顶掉，其余自动上移补位
- 旧横幅被顶下/自然退场都使用平滑动画，新横幅入场仍沿用 SLIDE/FADE/ZOOM/NONE 动画风格；系统横幅也参与同一堆叠
- 新增配置 `banner_max_stack`（默认 3，范围 1–5）

**Stacked notification banners (all loaders: Fabric / Forge / NeoForge)**
- Notification banners now stack like phone notifications: new banners appear immediately at the top, older ones smoothly move down over 200ms and shrink to 75%, keeping avatar + title + first content line
- Up to 3 banners are shown at once (configurable 1–5); when full, the oldest shrinks and fades out over 150ms while the rest slide up to fill
- Push-down and natural exit animations are smooth; new banner entrance still uses the selected SLIDE/FADE/ZOOM/NONE style; system banners join the same stack
- Added `banner_max_stack` config (default 3, range 1–5)

**键盘快捷键修复（三端同步：Fabric / Forge / NeoForge）**
- 修复聊天输入框鼠标拖选失效导致 Ctrl+C 无法复制的问题：自定义鼠标点击处理绕过容器事件后，现在会正确设置拖拽状态
- Ctrl+V 粘贴文本时不再启动后台图片剪贴板探测，避免干扰文本粘贴；剪贴板为图片时仍走图片上传/表情包添加

**Keyboard shortcut fix (all loaders: Fabric / Forge / NeoForge)**
- Fixed mouse text selection in the chat input being broken (which made Ctrl+C copy nothing) by restoring the container dragging state after custom mouse click handling
- Ctrl+V with text no longer starts the background image-clipboard probe, so text paste is not disturbed; image clipboard still uploads / adds to the emote pack

**横幅堆叠视觉调整（三端同步：Fabric / Forge / NeoForge）**
- 最新横幅现在会遮住上一条横幅的上半部分，旧横幅只从底部露出一半，更像手机通知堆叠

**Banner stack overlap tweak (all loaders: Fabric / Forge / NeoForge)**
- The newest banner now covers the top half of the banner below it, so older banners peek out from the bottom like a phone notification stack

**横幅倒计时与音效防重（三端同步：Fabric / Forge / NeoForge）**
- 修复横幅无法消失：每条横幅独立从出现起倒计时，到期后按当前 BANNER_ANIM_STYLE 退场（SLIDE 上滑 / FADE 淡出 / ZOOM 缩小淡出 / NONE 瞬间消失），被顶下不重置计时
- 通知音效全局 2 秒内只响一次：短时间大量通知时只有第一条响，横幅仍全部正常弹出

**Banner countdown and sound dedupe (all loaders: Fabric / Forge / NeoForge)**
- Fixed banners never disappearing: each banner counts down independently from appearance and exits with the selected BANNER_ANIM_STYLE (SLIDE up / FADE out / ZOOM shrink-fade / NONE instant); being pushed down does not reset the timer
- Notification sounds are globally deduplicated to one per 2 seconds: burst notifications only play the first sound while all banners still appear

**文本选择功能（三端同步：Fabric / Forge / NeoForge）**
- 新增鼠标拖选文本：气泡内发送者名字、正文、引用块、系统消息都可逐字符选取，支持跨行/跨消息，Ctrl+C 复制选中内容（行间以换行拼接）
- 选区高亮按背景亮度自适应：暗背景用半透明白遮罩+黑色选中文字，亮背景用半透明黑遮罩+白色选中文字，不再与蓝色气泡/引用块重色
- 拖选经过行间空隙时自动吸附最近文本，拖到消息区边缘自动滚动聊天；普通点击仍正常触发链接/指令
- 聊天主输入框、快捷短语、搜索、侧边栏搜索框均支持鼠标拖选文本
- 修复滚动条与下栏之间出现间隙的问题：消息底部呼吸空间改为计入内容高度，滚动条轨道仍贴到下栏

**Text selection (all loaders: Fabric / Forge / NeoForge)**
- Added mouse drag selection: sender names, bubble content, quote blocks, and system messages can be selected character-by-character across lines/messages; Ctrl+C copies the selection (lines joined with newlines)
- Selection highlight adapts to background brightness: dark backgrounds use a light overlay with black selected text, light backgrounds use a dark overlay with white selected text; no more clashing with blue bubbles or quote blocks
- Dragging through gaps snaps to the nearest text, and dragging near the chat edges auto-scrolls; plain clicks still trigger links/commands
- Main chat input, quick-chat, search, and sidebar search inputs all support mouse drag selection
- Fixed the gap between the scrollbar and the bottom bar: the bottom breathing room now lives in the content height while the scrollbar track stays flush with the bottom bar

## v2.4.1

**ModernUI emoji 短码支持（三端同步：Fabric / Forge / NeoForge）**
- 聊天输入框里输入 `:pig2:` 等 ModernUI 短码时，现在会随输入实时转换为对应 emoji（仅当 ModernUI 已安装且其 emoji shortcodes 选项开启时；命令输入不转换）
- 默认表情面板补充猪脸 / 猪 `🐷` `🐖`

**ModernUI emoji shortcode support (all loaders: Fabric / Forge / NeoForge)**
- Typing ModernUI shortcodes such as `:pig2:` in the chat input now converts them to the matching emoji as you type (only when ModernUI is installed and its emoji shortcodes option is enabled; commands are left untouched)
- Added pig face / pig `🐷` `🐖` to the default emoji panel

**点击文本下划线修复（三端同步：Fabric / Forge / NeoForge）**
- 只有实际带 clickEvent 的文本才会补下划线，不再因消息里其他段落的点击事件把整行/无关行都划线
- 修正多样式段下划线映射用错 `i` 下标导致的断线/错位

**Clickable text underline fix (all loaders: Fabric / Forge / NeoForge)**
- Only text that actually has a click event is underlined; unrelated sibling click events no longer underline whole lines or unrelated segments
- Fixed the broken/offset underline mapping that used the segment-local `i` index against the global style list

**右键引用在无服务端 E33Chat 时不再阻断发送（三端同步：Fabric / Forge / NeoForge）**
- 服务端未协商 `e33chat:quote_sync` 通道时，右键“引用”不再发送该可选包，聊天消息照常发出，避免 NeoForge `NetworkRegistry.checkPacket` 抛错导致发送中断
- 服务端装有 E33Chat 时行为不变，引用块照常同步

**Right-click quote no longer blocks sending when the server lacks E33Chat (all loaders: Fabric / Forge / NeoForge)**
- When the server has not negotiated the `e33chat:quote_sync` channel, right-click Quote skips the optional payload and the chat message sends normally, avoiding NeoForge `NetworkRegistry.checkPacket` throwing and interrupting the send
- Behavior is unchanged on servers with E33Chat installed; quote blocks still sync as before

## v2.4.0

**v2.4.0 为 2.3.17 全量内容的正式版本号**（2.3.17 曾以四次重发迭代：fix1-fix3、历史存盘异步化、bubble_size、banner_opacity 修正）

**2.3.16 回归修复 + UI 回退 + 新功能 + 性能优化（三端同步：Fabric / Forge / NeoForge）**

Bug 修复：
- **气泡间距错位**：2.3.16 渲染循环 off-by-one——间距先算 screenY 后加 contentY，与高度/跳转循环不一致，首对消息间距错位；已修正
- **头像全部消失**：2.3.16 引入 `hide_repeated_avatars` 时自比判定写反（先赋值再比较恒为 true），开启时连组内首条头像也被隐藏；已修正
- **上方向键历史跳级**：mod 发送后不关闭聊天屏（原版关闭重开），历史游标只在开屏时初始化，发送新消息后游标停在旧位置，按上键会跳过刚发的消息；现在每次发送后重同步游标
- **头像锚点回退**：2.3.16 把头像锚点从名字行顶改到气泡顶，实机观感不对，回退到旧锚点

UI 回退（2.3.16 风格改动按实机反馈回退，行为不变）：
- **弹层族回退旧样式**：设置/表情/常用语/搜索面板、@提及弹层、右键菜单全部回到纹理背景 + 直角外框
- **间距回退均匀**：两档组内/组间间距回退为均匀 `message_gap`
- **引用块圆角跟随配置**：不再硬编码，跟随 `bubble_corner_radius`
- `message_gap` 设置说明补上范围 0–12

新功能：
- **close_chat_on_send（客户端，默认关）**：发送消息后关闭聊天框（原版行为），默认关闭方便连发
- **banner_opacity（客户端，0–100 默认 100）**：通知横幅背景不透明度可调（文本与头像保持清晰）
- **media_auto_clean（服务端，默认开）**：服务器配置界面新增开关，自动清理超过 7 天的托管图片（开服时清理一次，之后每 6 小时最多一次）
- **bubble_size（客户端，5–14 px 默认 9）**：气泡大小可调——气泡内文字的目标高度（像素），气泡框/引用块/xN 角标等比缩放，名字行与头像保持原大小

性能：
- **历史存盘异步化**：聊天历史的定期全量重写（10000 条上限下可达几十 MB）移到专用后台 IO 线程，渲染线程只保留毫秒级的列表快照；原子替换机制不变，写入中断时旧文件完好

**注意**：服务端配置网络包新增字段，客户端与服务端需同时升级，否则打开服务端配置界面会报错。

测试：Forge 310 / NeoForge 309 / Fabric 284 全绿

**2.3.16 regression fixes + UI rollbacks + new features + a performance tweak (all loaders: Fabric / Forge / NeoForge)**

**v2.4.0 is the formal release number for all of the 2.3.17 iterations** (four re-issues: fix1-fix3, async history saves, bubble_size, banner_opacity fix)

Bug fixes:
- **Message spacing off by one**: the 2.3.16 render loop applied the gap after computing screenY but before contentY, out of sync with the layout/jump loops - first-pair spacing was wrong; fixed
- **Avatars all hidden**: the 2.3.16 `hide_repeated_avatars` check compared a message against itself (assigned before comparing, always true), hiding even the first avatar of a group when enabled; fixed
- **Up-arrow history skipping**: the mod keeps the chat screen open after sending (vanilla closes and reopens it), and the history cursor was only initialized on open, so pressing Up after sending jumped over the freshly sent messages; the cursor now re-syncs after every send
- **Avatar anchor rollback**: 2.3.16 moved the avatar hit anchor from the name-row top to the bubble top; it felt off in-game, reverted to the old anchor

UI rollbacks (2.3.16 style changes reverted after hands-on feedback, behavior unchanged):
- **Popup family back to the old look**: settings/emoji/quick-chat/search panels, the @mention popup and context menus return to textured backgrounds with square outlines
- **Uniform spacing**: the two-tier in-group/section spacing is back to a uniform `message_gap`
- **Quote-block radius follows config**: no longer hardcoded, follows `bubble_corner_radius`
- `message_gap` setting description now documents the 0-12 range

New features:
- **close_chat_on_send (client, default off)**: close the chat screen after sending a message (vanilla behaviour); off by default so you can send several messages in a row
- **banner_opacity (client, 0-100, default 100)**: notification banner background opacity is now adjustable (text and avatar stay readable)
- **media_auto_clean (server, default on)**: new toggle in the server config screen - automatically deletes server-hosted chat images older than 7 days (once on server start, then at most every 6 hours)
- **bubble_size (client, 5-14 px, default 9)**: adjustable bubble size - sets the target height of the bubble text in pixels; the bubble frame, quote block and xN badge scale proportionally while the sender-name row and avatar keep their size

Performance:
- **History saves off the render thread**: the periodic full-history rewrite (tens of megabytes at the 10000-message cap) now runs on a dedicated background IO thread; the client thread only takes a millisecond-fast list snapshot. The atomic tmp+move scheme is unchanged - an interrupted write still leaves the previous file intact

**Note**: the server-config network packet gained a field; client and server must be upgraded together, or the server config screen will fail to open.

Tests: Forge 310 / NeoForge 309 / Fabric 284 all green

## v2.3.16

**UI 风格统一（00e D1/D2/D07 拍板，三端同步：Fabric / Forge / NeoForge）**
- **视觉 token 最小表（UiTokens）**：2 档圆角（中 8 / 大 16）+ 2 档阴影（面板/弹层）+ 1px 描边 + 基础间距；颜色仍走资源包主题，config 不加颜色项
- **SDF 圆角覆盖弹层族**：设置菜单 / 表情 / 常用语 / 搜索面板、@提及弹层、右键菜单全部改 SDF 圆角 + 阴影 + 1px 描边（CONTENT_BG/POPUP_BG/CONTEXT_MENU_BG 纹理覆盖对这些弹层不再生效，改由主题语义色驱动）；滚动条按 2.3.13 结论保持纯色填充
- **组内/组间间距分离**：同一发送者 5 分钟内连续消息间距 = message_gap × 2/3（默认 4）；换人 / 超时 / 系统消息 / 时间分隔 = message_gap × 2（默认 12）。内部比例实现，`message_gap` 键与范围不动
- **头像对齐 + 隐头像**：头像与气泡/内容顶部对齐；新增 `hide_repeated_avatars`（默认开）——同一人连续消息只首条显示头像
- **横幅退出动画**：退出独立 150ms ease-in 淡出（进入 250ms 不变）
- **面板 blur 默认关闭**：`blur_enabled` 默认改 false（面板级 blur 性价比最低，2.3.5 降帧教训）；键保留，老配置不受影响
- **弹层关闭动画**：打开 150→200ms，关闭新增 150ms ease-in（ESC / 图标切换 / 互斥 / 点击外部 / 选择项全路径）
- 观感变化：引用块圆角 3→8（token 中档）、弹层打开时长 150→200ms
- 测试：Forge 313 / NeoForge 312 / Fabric 287 全绿

**UI style unification (00e D1/D2/D07, all loaders: Fabric / Forge / NeoForge)**
- **Minimal visual token table (UiTokens)**: 2 corner-radius tiers (medium 8 / large 16) + 2 shadow tiers (panel/popup) + 1px border language + base spacing; colors stay resource-pack themed, no new config color items
- **SDF rounded corners for the popup family**: settings/emoji/quick-chat/search panels, the @mention popup and context menus now render with SDF radius + popup shadow + 1px border (CONTENT_BG/POPUP_BG/CONTEXT_MENU_BG texture overrides no longer affect these popups - semantic theme colors are used instead); scrollbar stays plain fill (2.3.13 finding)
- **In-group vs section message spacing**: consecutive same-sender messages within 5 minutes use message_gap x 2/3 (default 4); sender change / timeout / system messages / time separators use message_gap x 2 (default 12). Internal ratio - the message_gap key and range are untouched
- **Avatar alignment + repeated-avatar hiding**: avatars align to the bubble/content top; new `hide_repeated_avatars` (default on) shows the avatar only on the first message of a consecutive same-sender run
- **Banner exit animation**: dismissal runs on its own 150ms ease-in fade (entrance 250ms unchanged)
- **Panel blur off by default**: `blur_enabled` now defaults to false (panel-wide blur is the lowest value-per-cost effect, 2.3.5 lesson); key preserved, existing configs unaffected
- **Popup close animation**: open 150→200ms, close is now animated 150ms ease-in (ESC / icon toggle / mutual exclusion / outside click / item select)
- Visual changes: quote-block radius 3→8 (medium tier), popup open 150→200ms
- Tests: Forge 313 / NeoForge 312 / Fabric 287 green

## v2.3.15

**结构重构 + 服务端配置收拢（三端同步：Fabric / Forge / NeoForge）**
- **配置屏 OptionDef 注册表**：GUI 行 / 退出回滚快照 / 色板点击全部由注册表派生，删手写清单（toml 键名/默认值/范围红线不动）
- 快照缺口修复：`message_gap` / `avatar_size` 现在退出时正确回滚（2.3.13 起有 GUI 行但漏进 snapshotAll）
- `resolveOnlinePlayer` 改名（行为不变）
- **ServerConfigDto 共享**：服务端配置屏/保存两包共用一份字段定义与字节序（网络格式不变）
- **sendServerConfigTriple 收拢**：进服与广播复用同一三包组合；广播现在也刷新已在线玩家的媒体托管能力（对齐 Forge）
- **通知/声音副作用上移**：ChatMessageStore 不再直接调 Minecraft 单例，横幅/提示音经观察者委托（行为不变）
- 测试：Forge 303 / NeoForge 302 / Fabric 277 全绿
- 性能：D3 定口径 + 基线记录表（`docs/11-perf-baseline.md`），本版不做优化

**Structure refactor + server-config consolidation (all loaders: Fabric / Forge / NeoForge)**
- **OptionDef registry for the client config screen**: GUI rows, exit-rollback snapshot and palette clicks all derive from one registry; hand-written lists removed (toml keys/defaults/ranges untouched)
- Snapshot gap fix: `message_gap` and `avatar_size` now roll back on Exit like every other row (they had GUI rows but were missing from snapshotAll since 2.3.13)
- `resolveOnlinePlayer` rename (no behavior change)
- **Shared ServerConfigDto**: the server-config screen/save packets share one field definition and byte order (wire format unchanged)
- **sendServerConfigTriple consolidation**: join and broadcast reuse the same 3-payload combination; broadcast now also refreshes the media-hosting capability for online players (parity with Forge)
- **Notification/sound side effects moved out of ChatMessageStore**: banners and chimes now flow through a MessageEffectObserver (same behavior)
- Tests: Forge 303 / NeoForge 302 / Fabric 277 green
- Performance: D3 measurement spec + baseline sheet (`docs/11-perf-baseline.md`); no optimization in this release

## v2.3.14

**纯结构重构（行为零变化，三端同步：Fabric / Forge / NeoForge）**
- 包结构领域化：ui / compat / render / network / server / config / store 共 13 个子包，根包只剩入口类
- ChatMessageStore 拆解（1374→926 行）：BlockList / HistoryStore / EchoTracker + wither 助手
- ChatListenerMixin 薄壳化（782→295 行）：新增 chat/capture 六守卫；~50×2 行复制粘贴收敛
- 渲染/UI 收敛：SkinResolver（双皮肤缓存合并）/ SmoothScrollPane（两配置屏滚动收敛）/ UploadQueue / MediaService；死代码清理
- 行为修复：英文私聊词回显抑制 `\b` 退格符 bug；配置屏退出回滚缺口（banner_offset 不回滚 / upload_* 误计）
- 测试：网络包 round-trip 15 例 + WhisperSignal 3 例首次守护包格式；Forge 302 / NeoForge 302 / Fabric 276 全绿
- 兼容性：配置键 / 网络包 / 文件格式 / mixin 注入点零改动

**Pure-structure refactor (zero behavior change, all loaders: Fabric / Forge / NeoForge)**
- Domain packages: ui / compat / render / network / server / config / store (13 subpackages); only entry classes remain at the root
- ChatMessageStore split (1374→926 lines): BlockList / HistoryStore / EchoTracker + wither helpers
- ChatListenerMixin slimmed (782→295 lines): six chat/capture guards; duplicated ~50×2 block merged
- Rendering/UI consolidation: SkinResolver (merged dual skin caches), SmoothScrollPane (shared config-screen scrolling), UploadQueue, MediaService; dead code removed
- Behavior fixes: English whisper-echo suppression `\b` backspace bug; config-screen exit rollback gap (banner_offset not rolled back / upload_* false positive)
- Tests: packet round-trip (15) + WhisperSignal (3) now guard the wire format; Forge 302 / NeoForge 302 / Fabric 276 green
- Compatibility: config keys / packet wire format / file formats / mixin injection points unchanged


## v2.3.13

**新功能（2.3.13，三端同步：Fabric / Forge / NeoForge）**
- **外观快照化**：聊天界面颜色统一为「外观快照」取色入口（Appearance），渲染层不再散落硬编码取色
- **消息间距 / 头像大小可调**：新增「消息间距」（聊天框 → 消息显示）与「头像大小」（聊天框 → 面板，12–32 像素）；头像大小同时作用于布局与渲染尺寸
- **服务端图片托管开关**：服务端配置界面「常规」分类新增「服务端图片托管」开关（默认开）；关闭后图片不进服务端，只能走第三方图床（此前只能手改配置文件）

**New (2.3.13, all loaders: Fabric / Forge / NeoForge)**
- **Appearance snapshot**: chat UI colors unified behind an appearance snapshot entry point; renderers no longer read scattered hardcoded values
- **Configurable message gap & avatar size**: new "Message Gap" (Chat → Message Display) and "Avatar Size" (Chat → Panel, 12–32 px) options; avatar size affects both layout and render size
- **Server media hosting toggle**: the server-config GUI's General tab gains a "Server Media Hosting" toggle (default on); off = images never reach the server and go through the third-party host only (previously config-file only)

**修复（2.3.13 补发，三端同步）**
- 上下栏背景：渐显渐隐改为线性曲线（150ms，跟面板开合动画同步）——此前 easeOutCubic 前 75ms 就接近不透明，观感为"瞬间出现/消失"
- 滚动条渐显修复（此前只显示最后一帧）：改用纯色填充渲染，绕开消息渲染路径的 blend/flush 状态污染；明/暗主题色保留
- 滚动后滚动条短暂保持显示恢复正常（时间戳时钟错配修复）

**Fixes (2.3.13 hotfix, all loaders)**
- Title/bottom bar fade: linear curve (150ms, synced with the panel animation) — the previous easeOutCubic reached ~87% opacity within the first 75ms and looked like an instant pop-in/out
- Scrollbar fade-in fixed (it used to show only the final frame): now rendered with solid fill to bypass blend/flush state pollution from the message render path; dark/light theme colors preserved
- Scrollbar now stays visible briefly after scrolling (clock mismatch fix)

## v2.3.12

**新功能（2.3.12，三端同步：Fabric / Forge / NeoForge）**
- **自定义表情包**：表情面板新增「表情包」标签页，从 `config/e33chat/emotes/` 目录加载最多 10 个表情图片（png/jpg/gif 首帧）；点击表情立即发送（无气泡小图）；悬停显示删除角标；点「+」打开系统文件选择器添加；表情标签页打开时 Ctrl+V 直接把剪贴板图片加入表情包
- **图片消息无气泡化**：所有图片消息改为无气泡渲染（保留头像与昵称），图片按真实尺寸缩放（小图不放大、大图按面板宽度自适应，窄窗口不再溢出）；多图纵向堆叠；点击图片打开原图 URL，悬停显示链接
- **图床切换**：默认图床上传从 Litterbox 改为 uguu.se——Litterbox 的下载 CDN 在部分网络不可达（图传得上去但永远加载不出来），uguu.se 上传/下载全链路可达；自定义图床的响应解析新增 JSON 路径语法（如 `json:files[0].url`）
- **上传链路重构**：上传任务排队串行（最多 8 个），回车一次即可——上传完成后自动发送，不再需要按第二次回车；上传失败恢复输入框内容，可重试
- **配置修复**：音量 0（静音）/面板透明度 0/圆角 0 等合法设置不再被重置为默认值；配置写入改为原子替换（游戏崩溃不再损坏配置文件，损坏文件保留 .bak 供恢复）
- **配置目录**：客户端配置统一到 `config/e33chat/e33chat-client.toml`（Forge / NeoForge）与 `config/e33chat/e33chat-client.json`（Fabric），旧路径自动迁移，设置不丢；聊天历史仍在 `runDir/e33chat/history/`

**New (2.3.12, all loaders: Fabric / Forge / NeoForge)**
- **Custom emote pack**: the emoji panel gains an "Emote Pack" tab loading up to 10 images from `config/e33chat/emotes/` (png/jpg/gif first frame); clicking an emote sends it immediately as a bubble-less small image; hover shows a delete badge; the "+" slot opens the OS file picker; with the emote tab open, Ctrl+V adds the clipboard image to the pack
- **Bubble-less image messages**: image messages now render without a bubble (avatar + name kept), scaled to their real size (small images never upscaled, large images clamped to the panel width); multiple images stack vertically; clicking an image opens the original URL, hover shows it
- **Default image host switched**: uploads now go to uguu.se by default — Litterbox's download CDN is unreachable on some networks (uploads succeed but the image can never load), uguu.se is reachable end-to-end; custom hosts gain JSON path response parsing (e.g. `json:files[0].url`)
- **Upload pipeline rework**: uploads queue and run serially (up to 8), and one Enter press is enough — the message sends automatically once the upload finishes; failed uploads restore the input text so you can retry
- **Config fixes**: zero values (muted volume / fully transparent panel / square corners) are no longer reset to defaults; config writes are atomic (a crash can no longer corrupt the file, and a corrupt file is kept as .bak)
- **Config dir**: client config unified to `config/e33chat/e33chat-client.toml` (Forge / NeoForge) and `config/e33chat/e33chat-client.json` (Fabric), auto-migrated from legacy paths, nothing lost; chat history stays in `runDir/e33chat/history/`

**修复（2.3.12 补发）**
- 修复服务器媒体直传单块图（<512KB）永不回执、客户端上传挂起 30 秒
- 修复上传 worker 异常卡死队列、上传中提示样式错位
- 修复原版聊天框把图片代码渲染成长 URL 刷屏（改为绿色 `[图片]` 占位符）
- 修复上传队列满时静默丢消息且误弹「稍候再发」（现恢复输入框并提示队列已满）
- 修复 4 项内存/状态债：皮肤缓存无上限、待定引用元数据不过期、待定回声按索引而非内容匹配、私聊回声不排队
- 修复服务器媒体存储缓存不随世界切换重置、上传会话/临时文件断线泄漏
- 修复 NeoForge 媒体包解码无长度上限（恶意包可致内存耗尽）
- 修复下载分块重组缺块时崩溃（缺块丢弃该媒体而非空指针）
- 服务器媒体直传加每玩家限速（10 秒 4 次）；剪贴板图片解码移出渲染线程
- `[引用]`/`[私聊]` 原版标签改走本地化；图床文档对齐 uguu.se、删除失效配置说明

**Fixes (2.3.12 respin)**
- Fixed single-chunk server media uploads (<512KB) never acking, hanging the client for 30s
- Fixed the upload worker crashing the queue, and the upload-in-progress hint styling
- Fixed the vanilla chat rendering image codes as a long URL (now a green `[Image]` placeholder)
- Fixed upload-queue overflow silently dropping the message with a misleading "wait" hint (input restored + queue-full hint)
- Fixed 4 memory/state debts: unbounded skin cache, non-expiring pending-quote metadata, pending-echo matching by index instead of content, whisper echoes not queued
- Fixed the server media store cache not resetting on world switch, and upload sessions/temp files leaking on disconnect
- Fixed NeoForge media packet decoding with no length cap (a hostile packet could exhaust memory)
- Fixed download-chunk reassembly crashing on a missing chunk (dropped instead of a null-pointer)
- Added per-player throttling for server media transfers (4 per 10s); clipboard image decoding moved off the render thread
- `[引用]`/`[私聊]` vanilla tags now localize; upload host docs aligned with uguu.se and stale entries removed

## v2.3.11

**新功能（2.3.11）**
- **本地图片上传**：聊天输入框旁的上传按钮 / Ctrl+V 粘贴 / **直接拖图片进窗口**，自动缩放到 ≤2048 边长并重新编码，上传到图床（默认 Litterbox，可配置），插入 `[[CICode,url=...]]` 发送；上传失败有提示，不会插入死链接
- **服务端媒体直传**：当服务器也装了 e33chat 时，聊天图片直接存到服务器（`e33chat://media/<id>`，永久不过期），不再受第三方图床 72h 过期限制；服务器没装/未开启时自动回退图床，无感。服务端配置 `media_enabled`（默认 true）控制；单文件上限 8MB、总配额 512MB、随机 UUID 媒体 ID 防遍历；能力探测走独立协议类型，新旧客户端/服务端混用不炸
- **图片渲染升级**（继承 2.3.10 管线）：无卡片背景/边框，图片直接绘制在气泡上，上边缘与文本齐平，图间 2px 间隙

**修复（2.3.11）**
- 修复 Litterbox 上传 HTTP 412（缺 `reqtype=fileupload`，旧配置自动注入）
- 修复拖拽图片后输入框键盘失效（OS 拖放抢焦点，自动归还）
- 修复 chatimage 等 mod 在拖拽/粘贴时插入的本地 `file://` 死链：e33chat 上传完成后自动替换为真实 URL；file:// 未替换时按回车会阻止发送并提示「图片上传中」，同时自动开始上传
- 修复点击 file:// 图片消息抛 URISyntaxException（仅 http(s) 链接响应点击）

**New (2.3.11)**
- **Local image upload**: upload button / Ctrl+V paste / **drag & drop onto the window**, auto-scaled to ≤2048px and re-encoded, uploaded to a host (default Litterbox, configurable), inserted as `[[CICode,url=...]]`; failures show a hint and never insert dead links
- **Server-side media hosting**: when the server also runs e33chat, chat images are stored on the server (`e33chat://media/<id>`, permanent) instead of the third-party host's 72h expiry; falls back to the host automatically when the server lacks/disabled it. Toggle `media_enabled` (default true); 8MB/file, 512MB quota, random UUID media IDs; capability detection uses a separate protocol type so mixed client/server versions never desync
- **Image rendering upgrade** (on the 2.3.10 pipeline): no card background/border, drawn directly on the bubble, top edge flush with text, 2px gap between images

**Fixes (2.3.11)**
- Fixed Litterbox upload HTTP 412 (missing `reqtype=fileupload`; legacy configs get it injected)
- Fixed chat input keyboard dying after dragging an image (OS drop steals focus; now returned)
- Fixed dead `file://` links inserted by mods like chatimage on drag/paste: e33chat replaces them with the real upload URL once done; pressing Enter before the replacement blocks the send with a "wait" hint and auto-starts the upload
- Fixed clicking file:// image messages throwing URISyntaxException (only http(s) links respond to clicks)

## v2.3.10

**新功能（2.3.10）**
- **气泡内图片渲染（三端）**：聊天消息里的 `[[CICode,url=...]]`（ChatImage 协议）和 `[[ChatUpgrade,url=...,type=image]]`（第三方富文本协议）图片代码现在直接在气泡内原生渲染成图片卡片——不依赖任何 mod，协议层与 ChatImage 等第三方 mod 互通（它们发的图我们能显示，我们发的代码它们也能解析）。历史记录里的旧图片消息（含 ChatImage 转换前的样式组件）自动兼容，重进存档后图片重新加载显示。点击图片卡片用系统浏览器打开原图，悬停显示完整 URL
- **图片防刷屏（三端）**：聊天图片下载入口是攻击者可控制的，内置三层防护——滑动窗口限流（10 秒最多 4 个新下载，超出的排队，队列满则显示"限流"占位）+ 64 条 LRU 缓存（逐出时销毁 GPU 纹理）+ 上传前等比缩放到 ≤320×180（恶意 16MB 大图只占约 230KB 显存）。下载失败 10 秒后自动重试
- **接收图片开关（三端）**：设置界面「聊天」标签新增「接收图片」开关（默认开）。关闭后图片代码显示为纯文本 `[图片]`，不发起任何下载

**修复（2.3.10）**
- **图标/标题文字被面板透明度调制变浅（三端，2.3.7 回归）**：2.3.7 动画更新把 `panelOpacity`（默认 0.8）误当作图标和文字的 alpha——四个图标（菜单/设置/表情/发送）和标题栏文字被永久调制到 80% 不透明度，浅色主题下明显比 PNG 原色浅。现在内容（图标/文字）只跟随面板开合动画，动画结束后 100% 原色；面板透明度只管背景
- **JPEG/GIF 图片红蓝互换（三端）**：AWT 解码的非 PNG 图片像素按 ARGB 字节序写入 NativeImage 的 ABGR 内存布局，红色和蓝色通道互换。已按 ABGR32 布局转换，JPEG/GIF/BMP 图片颜色正确

**New (2.3.10)**
- **Native image rendering in bubbles (all platforms)**: `[[CICode,url=...]]` (ChatImage protocol) and `[[ChatUpgrade,url=...,type=image]]` (third-party rich-message protocol) codes now render as image cards directly inside chat bubbles with zero mod dependencies — protocol-level interop with ChatImage and other third-party mods (their images display in our bubbles, our codes parse in theirs). Legacy history lines (ChatImage-converted styled components) are extracted automatically and re-downloaded on world re-entry. Clicking a card opens the original URL in the system browser, hovering shows the full URL
- **Image anti-flood (all platforms)**: chat images are an attacker-controlled download trigger, so three layers of protection are built in — sliding-window rate limit (max 4 new downloads per 10s, excess queued, queue-full renders a "rate limited" placeholder) + 64-entry LRU cache (GPU textures destroyed on eviction) + pre-upload scaling to ≤320×180 (a hostile 16MB image costs ~230KB of VRAM). Failed downloads auto-retry after 10 seconds
- **Receive-images toggle (all platforms)**: new "Receive images" switch in the config screen's Chat tab (default on). When off, image codes render as plain `[Image]` text and nothing is ever downloaded

**Fixes (2.3.10)**
- **Icons/title text tinted by panel opacity (all platforms, 2.3.7 regression)**: the 2.3.7 animation update fed `panelOpacity` (default 0.8) into the icon/text alpha — the four icons (menu/settings/emoji/send) and title-bar text rendered permanently 80% opaque, visibly lighter than the PNG colour on light themes. Content (icons/text) now follows only the panel open/close animation and returns to 100% original colour afterwards; panel opacity affects the background only
- **JPEG/GIF images rendered with red and blue swapped (all platforms)**: AWT-decoded non-PNG pixels were written as big-endian ARGB into NativeImage's ABGR memory layout, swapping the R and B channels. Pixels are now converted to ABGR32, so JPEG/GIF/BMP colours are correct

## v2.3.9

**修复（2.3.9）**
- **聊天记录纯文本问题（三端）**：历史记录此前存为纯文本 TSV（v2.2.3 起为可读日志而有意降级）——重进存档后聊天记录只剩文本，颜色/点击事件/悬停提示/下划线全部丢失，头像也无法按玩家解析。现在历史改为 JSONL 完整组件：`senderJson`/`contentJson` 用原版组件序列化保存（颜色/click/hover 完整保留），并新增 `uuid` 列持久化发送者 UUID——重进存档后离线玩家头像按 UUID 正确解析（在线玩家走 Tab，离线走正版皮肤服务）。旧格式（纯文本行/旧 JSON/旧 JSONL）自动兼容读取
- **离线玩家头像默认脸问题（三端）**：历史消息重载后发送者 UUID 丢失时头像回落为 Steve/Alex。现在头像查询新增按名字的常驻缓存：在线见过的玩家即使离线（UUID 查不到/盗版服）也能复用其真实皮肤；结合 UUID 持久化，正版离线玩家直接经皮肤服务解析

**Fixes (2.3.9)**
- Chat history lost all styling (all three platforms): history was stored as plain-text TSV (deliberately downgraded in v2.2.3 for human-readable logs), so after re-entering a world the history showed bare text — colors, click events, hover tooltips and underlines were gone, and avatars couldn't resolve to the real player. History is now JSONL with full components: `senderJson`/`contentJson` are serialized with the vanilla component codec (colors/click/hover fully preserved), plus a new `uuid` column persisting the sender UUID — after re-entering, offline players' avatars resolve correctly by UUID (online via the tab list, offline via the skin service). Old formats (plain-text lines / legacy JSON / legacy JSONL) still load automatically
- Offline players showed Steve/Alex avatars (all three platforms): reloaded history without a sender UUID fell back to the default skin. Avatar lookup now has a name-keyed persistent cache: players you've seen online keep their real skin even offline (UUID lookup failure / cracked servers), and with UUID persistence, offline players on online-mode servers resolve straight from the skin service

## v2.3.8


**修复（2.3.8）**
- **tpa 请求被误判为私聊（三端）**：whisper 检测词表里的裸 "to you" 太宽松——"wants to teleport to you"（Essentials 系 tpa 请求）恰好含 "to you"，被误判成私聊：`[Essentials]` 前缀被剥、渲染成私聊玩家气泡、触发私聊横幅、`[Yes]/[No]` 按钮样式丢失。现在裸 "to you" 不再算私聊词（真私聊由 whisper/悄悄/对你说/PM 词表覆盖），tpa 请求正确显示为系统消息；echo 抑制误判（对方请求刚好在 /msg 某人之后到达，被当成自己回显吞掉）同步消除
- **长系统消息文本重叠（Forge/Neo）**：系统消息高度计算用 999 宽度（几乎不换行→按 1 行算），实际绘制用面板内宽（长文本画 2-3 行）——高度预算不足，下一条消息压上来重叠。现在高度计算与绘制同宽（`panelW - PAD*2 - 20`），每条系统消息独立占行不再互相覆盖

**Fixes (2.3.8)**
- tpa requests misread as private messages (all three platforms): the bare "to you" keyword in the whisper detector was too loose — "wants to teleport to you" (Essentials-style tpa requests) contains it and was claimed as a whisper: the `[Essentials]` prefix was stripped, the message rendered as a private-player bubble, a whisper banner fired and the `[Yes]/[No]` buttons lost their styling. Bare "to you" is no longer a whisper keyword (real whispers are covered by whisper/悄悄/对你说 and the PM word list); tpa requests now show as system messages, and the echo-suppression misread (a request arriving right after you /msg someone was suppressed as your own echo) is gone with it
- Long system messages overlapped (Forge/Neo): the height computation wrapped content at width 999 (nearly one line) while rendering wrapped at the panel-inner width (2-3 lines) — the height budget fell short and the next message drew on top. Height now uses the same wrap width as rendering (`panelW - PAD*2 - 20`), so each system message occupies its own rows

## v2.3.7

## v2.3.7

**修复（2.3.7 补发 9）**
- **横幅头像不随横幅淡入（三端）**：横幅 FADE 动画时背景/文字淡入、头像瞬间出现——头像用 `setShaderColor` 包 `blit`（对 `POSITION_TEX` 无效）。头像改 `drawWithAlpha` 随横幅一起淡入
- **右键菜单/提及弹窗/回复栏/通知栏/私聊条不随面板淡入（三端）**：这些元素在面板 FADE 打开时背景/边框/图标瞬间实心出现（vanilla `blit` 不吃 `setShaderColor`）。全部改 `drawWithAlpha`（+ 边框 alphaBlend、图标走带 alpha 路径），面板 FADE/ZOOM 时与整体一起淡入/缩放

**Fixes (2.3.7 follow-up 9)**
- Banner avatar didn't fade with the banner (all three platforms): under FADE the background/text faded in but the avatar popped in — it used `setShaderColor` around `blit` (ineffective for `POSITION_TEX`). The avatar now uses `drawWithAlpha` and fades in with the banner
- Context menus / mention popup / reply bar / notification bar / whisper bar didn't follow the panel fade (all three platforms): their backgrounds/borders/icons popped in solid under FADE (vanilla `blit` ignores `setShaderColor`). All switched to `drawWithAlpha` (borders via alphaBlend, icons via the alpha path), so they fade/scale with the panel

## v2.3.7

**修复（2.3.7 补发 8）**
- **上下栏/侧边栏无法淡入（Forge/Neo，Fabric 上下栏已修）**：`ChatBars` 用 `setShaderColor(1,1,1,alpha)` 包 vanilla `blit`——但 `blit` 走 `POSITION_TEX` 着色器（顶点只有位置+UV，无颜色通道），alpha 对背景纹理完全无效，只有文字（独立管线 alphaBlend）能淡。现在 Forge/Neo 上下栏背景/边框/图标全部改 `drawWithAlpha`（带颜色通道的渲染路径），对齐 Fabric
- **侧边栏淡入无效（三端）**：侧边栏的 `fadeSidebar` 也是 `setShaderColor` 包整个侧边栏——内部全是无颜色通道的 `drawTexture`/`blit`，背景/头像/图标从不淡，只有位移停住。现在侧边栏渲染加 `float alpha` 参数，背景/选中/悬停/图标/玩家头像全部走带 alpha 路径——FADE 下侧边栏真正原地淡入，ZOOM 下缩放+淡入与面板节奏一致
- **消息气泡头像不随消息淡入（三端）**：消息进入动画淡入时头像用 `setShaderColor` 包 `blit`（无效），头像瞬间显示。现在头像改 `drawWithAlpha`，随气泡一起淡入

**Fixes (2.3.7 follow-up 8)**
- Title/bottom bar couldn't fade on Forge/Neo (Fabric's bars were already fixed): `ChatBars` wrapped vanilla `blit` with `setShaderColor(1,1,1,alpha)`, but `blit` uses the `POSITION_TEX` shader (vertices carry position+UV only, no color channel), so the alpha never reached the background textures — only text (its own alphaBlend pipeline) faded. The bars' backgrounds/borders/icons now use `drawWithAlpha` (the color-channel path), matching Fabric
- Sidebar fade didn't work (all three platforms): `fadeSidebar` also wrapped the whole sidebar in `setShaderColor`, but internally it drew with color-less `drawTexture`/`blit` — the background/avatar/icons never faded, only the offset stopped. The sidebar render now takes a `float alpha` and draws its background/selection/hover/icons/player-heads through the alpha path — under FADE it truly fades in place, and under ZOOM it scales + fades in sync with the panel
- Message-bubble avatars didn't fade with the message (all three platforms): the enter-animation avatar used `setShaderColor` around `blit` (ineffective), so it popped in. Avatars now use `drawWithAlpha` and fade in with the bubble

## v2.3.7

**修复（2.3.7 补发 7）**
- **FADE 面板下汉堡切换侧边栏无动画（三端）**：点汉堡切换侧边栏时，面板风格为 FADE 时 `fadeSidebar` 判定未区分"面板开合动画"与"汉堡切换"——它只看面板风格不看 `sidebarAnimating`，强制侧边栏位移为 0（slide 进度被丢弃），透明度又用面板开合进度（此时早已结束 = 1，淡入也没有）→ 侧边栏完全静止。修复：`fadeSidebar` 仅在面板自己的开合动画时生效，汉堡切换永远滑动（与 ZOOM/SLIDE 面板下行为一致）

**Fixes (2.3.7 follow-up 7)**
- Sidebar toggle had no animation under a FADE panel (all three platforms): the `fadeSidebar` check only looked at the panel style, not whether a hamburger toggle was animating — under FADE it forced the sidebar offset to 0 (dropping the slide progress) and used the panel's own open/close progress for alpha (already finished = 1, so no fade either), leaving the sidebar completely static. Fixed: `fadeSidebar` now applies only to the panel's own open/close animation; the hamburger toggle always slides (matching the ZOOM/SLIDE behavior)

## v2.3.7

**修复（2.3.7 补发 6）**
- **Fabric 侧边栏在 SLIDE 下不跟随面板滑动**：`getSidebarAnimProgress` 分支写反——侧边栏开启时直接返回 1f（原地不动），面板却还在滑动，SLIDE 下面板与侧边栏割裂。修复：对齐 Forge/Neo 的判定（侧边栏未开返回 0，开启时跟随面板开合动画进度），SLIDE 下侧边栏与面板一体滑动
- **NeoForge mod 列表描述乱码（仅 NeoForge）**：`gradle.properties` 的 `mod_description` 中文部分 UTF-8 尾字节损坏，游戏内 mod 列表显示乱码。修复：统一为与 Forge/Fabric 一致的纯英文描述

**Fixes (2.3.7 follow-up 6)**
- Fabric sidebar no longer detached under SLIDE: `getSidebarAnimProgress` had an inverted branch — with the sidebar open it returned 1f (fixed in place) while the panel kept sliding, so the panel and sidebar split under SLIDE. Fixed to match Forge/Neo (0 when closed, follow the panel's open-animation progress when open); the sidebar now slides as one unit with the panel under SLIDE
- NeoForge mod-list description was garbled (NeoForge only): the Chinese part of `mod_description` in `gradle.properties` had a corrupted UTF-8 trailing byte, showing mojibake in-game. Fixed: unified to the same plain-English description as Forge/Fabric

## v2.3.7

**修复（2.3.7 补发 5）**
- **Fabric 打开聊天框时面板被 slide 污染（仅 Fabric）**：Fabric 的 `init()` 在侧边栏开启时强制 `sidebarAnimating = true`，每帧把侧边栏滑动进度写进 `panelX`（所有面板内容的布局左边界），FADE/ZOOM 下面板矩阵不位移、但内容被 `panelX` 拖着水平滑动——叠成 slide + fade/zoom。Forge/Neo 的 `init()` 是 `sidebarAnimating = false`（侧边栏直接就位），从根上没有污染。修复：Fabric `init()` 对齐 Forge/Neo。**侧边栏的进入动画本就由面板开合动画承载**（打开=FADE 原地淡入 / ZOOM 缩放 / SLIDE 一起滑），不需要侧边栏状态机在打开时重新驱动
- **侧边栏搜索框位置（三端）**：打开聊天框时搜索框初始 `setX(2 - SIDEBAR_W)` 藏在屏幕外（原为配合侧边栏滑入动画），但打开方向侧边栏不走自己动画、进入动画由面板承载——Forge/Neo 下搜索框停在屏幕外不可点、不可见。修复：初始直接就位 `setX(2)`

**Fixes (2.3.7 follow-up 5)**
- Fabric panel content no longer slides under FADE/ZOOM when opening the chat (Fabric only): `init()` force-set `sidebarAnimating = true` with the sidebar open, and the tick wrote the sidebar slide progress into `panelX` — the layout origin of all panel content — so the whole panel slid horizontally under FADE/ZOOM (no matrix displacement). Forge/Neo never had this: their `init()` leaves `sidebarAnimating = false`. Fixed: Fabric `init()` now matches Forge/Neo. The sidebar's entrance is carried by the panel's open animation (FADE fades in place / ZOOM scales / SLIDE slides together); the sidebar state machine shouldn't re-drive it when the screen opens
- Sidebar search box position (all three platforms): it was initialized at `setX(2 - SIDEBAR_W)` (off-screen, for the sidebar's own slide-in) but the sidebar no longer plays its own animation on open — on Forge/Neo it stayed off-screen, invisible and unclickable. Fixed: initialized at `setX(2)`

## v2.3.7

**修复（2.3.7 补发 4）**
- **侧边栏开启时面板背景 FADE/ZOOM 下滑入**：侧边栏开启时面板背景左缘按动画进度从屏幕左缘水平长到侧边栏右缘（原为配合 SLIDE 面板滑入），但 FADE/ZOOM 下 `panelOffset` 无位移，只有背景还在水平生长——叠成"slide + fade/zoom"两重效果。现在只有 SLIDE 时背景才水平生长；FADE/ZOOM 时背景固定画在面板区原地淡入/缩放，与侧边栏一起作为整体

**Fixes (2.3.7 follow-up 4)**
- Panel background no longer slides in under FADE/ZOOM when the sidebar is open: its left edge grew from the screen's left edge to the sidebar's right edge (designed for the SLIDE panel), but under FADE/ZOOM the panel doesn't move — only the background did, stacking slide + fade/zoom. The background now only grows under SLIDE; under FADE/ZOOM it stays in the panel region and fades/scales in place with the sidebar as one unit

## v2.3.7

**修复（2.3.7 补发 3）**
- **ZOOM 下面板与侧边栏割裂**：面板 ZOOM 缩放时侧边栏仍保留水平位移，与缩放矩阵叠加成"滑动+缩放"两重效果。现在侧边栏去掉位移，与面板一起绕面板中心缩放 + 同步淡入，成为整体
- **FADE 下侧边栏切换无动画**：点汉堡切换侧边栏开关时，FADE/NONE 进度被短路成 0/1，切换瞬间无任何动画。现在汉堡切换永远走滑动动画（侧边栏是独立个体，切换动画不随面板动画风格）

**Fixes (2.3.7 follow-up 3)**
- Sidebar no longer slides under panel ZOOM: it kept its horizontal offset while the panel scaled, stacking slide + zoom. It now drops the offset and scales with the panel around the panel center, fading in sync
- Sidebar toggle had no animation under FADE: the FADE/NONE progress shortcut snapped to 0/1, so the hamburger toggle was instant. The toggle now always slides, independent of the panel animation style

## v2.3.7

**修复（2.3.7 补发 2）**
- **弹层 SLIDE 上滑+淡入（三端）**：此前弹层 SLIDE 与 FADE 效果几乎一样（只有淡入、无位移），预期是从下往上滑入。现在 SLIDE 弹出时上滑 10px + 淡入
- **侧边栏 FADE 关闭方向走滑动**：面板 FADE 关闭时侧边栏仍硬编码向左滑出（关闭分支绕过了动画风格分派，只有打开方向是原地淡入）。现在 FADE 下关闭方向同样原地淡出
- **侧边栏 ZOOM 不跟随面板**：ZOOM 的缩放矩阵只包住聊天面板，侧边栏在矩阵外永远走滑动，与面板割裂。现在侧边栏并入面板缩放矩阵，一起绕面板中心缩放弹入

**Fixes (2.3.7 follow-up 2)**
- Popup SLIDE now rises up 10px while fading in (all three platforms): previously SLIDE looked almost identical to FADE (fade only, no displacement)
- Sidebar FADE closing no longer slides out: the closing branch hard-coded the slide offset and bypassed the animation-style dispatch (only the opening direction faded in place). It now fades in place in both directions
- Sidebar ZOOM now scales with the panel: the ZOOM matrix only wrapped the chat panel, so the sidebar (outside the matrix) always slid and looked detached. It is now inside the same matrix, zooming around the panel center

**新功能**
- **UI 多风格动画（三端）**：此前聊天界面/横幅/弹层只有一种滑入滑出动画，且缓动曲线写死在代码里。现在每个元素可独立选择动画风格：`滑动`（保持原有滑入滑出）、`淡入淡出`（仅透明度，vanilla Toast 同款 quad 曲线）、`缩放弹入`（绕中心缩放 + 过冲回弹）、`无动画`（关闭该元素动画）
- **消息条目进入动画（三端，默认开启）**：新消息气泡出现时上滑 8px + 淡入（250ms），连续多条消息逐条交错 40ms 延迟——聊天界面更有"消息流"的层次感
- **弹层弹出动画（三端，默认开启）**：设置/表情/快捷/搜索面板弹出时淡入或缩放弹入（关闭保持即时）
- **新配置项 ×4**：`panel_anim_style`（面板，默认滑动）/ `banner_anim_style`（横幅，默认滑动）/ `popup_anim_style`（弹层，默认淡入淡出）/ `message_anim_style`（消息，默认淡入淡出），设置界面循环按钮切换；全局"动画"总开关仍然有效

**Fixes**
- Multi-style UI animations (all three platforms): the chat panel/banner/popups previously had a single slide-in animation with hard-coded easing. Each element now picks its own style independently: Slide (original), Fade (opacity only, vanilla-toast quad easing), Zoom (scale-in around center with overshoot), or None
- Message enter animation (all three platforms, on by default): new bubbles slide up 8px + fade in over 250ms, staggered 40ms between consecutive messages
- Popup open animation (all three platforms, on by default): settings/emoji/quick-chat/search panels fade or zoom in when opened (closing stays instant)
- New settings ×4: `panel_anim_style` / `banner_anim_style` / `popup_anim_style` / `message_anim_style`, cycled via buttons in the config screen; the global "Animation" toggle still applies
- Tests: Forge 241 / NeoForge 241 / Fabric 222 all green

**修复（2.3.7 补发）**
- **消息动画三风格（三端）**：此前消息进入动画只有上滑+淡入一种效果（三种风格视觉几乎一样）。现在：`滑动` = QQ 式横向滑入+淡入（自己的气泡从右往左、别人的从左往右）；`淡入淡出` = 纯淡入无位移；`缩放弹入` = 绕气泡中心缩放弹入（过冲回弹）
- **弹层动画改为完整淡入（三端）**：此前弹层 FADE 只有背景淡入、文字/图标直接出现（vanilla blit 走无颜色通道的 shader，setShaderColor 对它们无效）。现在设置/表情/快捷/搜索面板的背景、文字、图标全部逐元素 alpha 淡入
- **侧边栏跟随面板淡入（三端）**：面板 FADE 打开时侧边栏不再只做滑动，而是跟随面板原地淡入（打开和关闭两个方向都生效）
- **新消息气泡/头像永久消失（三端）**：消息进入动画把 MC 渲染时钟（`getMillis`，nanoTime 基准）与消息时间戳（`System.currentTimeMillis`，epoch 基准）直接相减——两个时钟差约等于 JVM 运行时长，算出巨大负进度 → 透明度恒 0 → 气泡/头像永不可见。修复：动画 now 侧改用 `System.currentTimeMillis()`（与消息时间戳同源）
- **上栏/下栏不随面板淡入**：标题栏/底栏背景从不乘面板透明度（此前 SLIDE 靠位移掩盖），FADE/ZOOM 下原形毕露。修复：两栏背景改带 alpha 绘制、文字/图标/边框逐元素 alphaBlend，跟随面板淡入淡出
- **侧边栏动画与面板割裂**：侧边栏硬编码 slide 曲线。修复：侧边栏进度走面板动画风格曲线；FADE 下侧边栏原地淡入（不位移）

**Fixes (2.3.7 follow-up)**
- Message enter animation now has three distinct styles (all three platforms): Slide = QQ-style horizontal slide-in + fade (own bubbles from right to left, others from left to right); Fade = pure fade, no displacement; Zoom = scale-in around the bubble center with overshoot
- Popup animation is now a full fade (all three platforms): vanilla blit uses a color-less shader so setShaderColor never affected them — the settings/emoji/quick-chat/search panels now fade their backgrounds, text and icons per-element
- Sidebar now fades in place with the panel under FADE in both directions
- New-message bubbles/avatars permanently invisible (all three platforms): the message enter animation subtracted the MC render clock (`getMillis`, nanoTime-based) from the message timestamp (`System.currentTimeMillis`, epoch-based) — two unrelated clocks, off by roughly the JVM uptime, so the progress went hugely negative and the alpha stayed 0 forever. Fixed: the animation now uses `System.currentTimeMillis()` on the "now" side, matching the timestamp
- Title bar / bottom bar no longer followed the panel fade: their backgrounds never multiplied the panel opacity (the old slide hid it), which FADE/ZOOM exposed. Fixed: both bars render with alpha and their text/icons/borders use per-element alphaBlend
- Sidebar animation felt detached from the panel: it used a hard-coded slide curve. Fixed: the sidebar follows the panel's animation style curve, and fades in place (no displacement) under FADE

## v2.3.6

**修复**
- **@ 补全回车误输入补全名**：输入 `@` 弹出玩家补全后直接按回车，会把候选名字插进输入框（如 `/tp @s` 场景把含 "s" 的玩家名补进命令）。现在必须先用 ↑/↓ 或滚轮选中候选，回车才应用补全；没选过直接回车 = 发送当前文本。另外命令（`/` 开头）里不再弹玩家补全——`@s`/`@p` 是原版选择器不是玩家名，命令输入走原版指令建议框
- **自 /msg 不弹横幅/音效（三端，开启"自己私聊通知"仍不弹）**：自己 /msg 自己的消息走"本地发送反馈"气泡，而 whisper 横幅/音效入口被 `!localSend` 无条件挡住——即使开启 `own_whisper_notify` 也不弹横幅和音效。现在该配置开启时本地反馈气泡也放行给通知控制器，自 /msg 正常弹横幅 + 音效（需对应开启私聊横幅/音效配置）

**新功能**
- **横幅位置偏移配置（三端）**：通知横幅默认固定在屏幕顶部中央，与 Jade 等 HUD mod 的显示区域重叠时无法挪开。新增 `banner_offset_x` / `banner_offset_y` 两个配置项（设置 → 通知 → 横幅通用），水平/垂直微调横幅位置，避开其他 HUD 元素
- **IMBlocker 输入法适配（三端）**：装了 IMBlocker 后，命令输入会自动切换到英文输入法、退出命令恢复中文——与装原版聊天框的行为一致。e33chat 的自定义聊天框绕过了 IMBlocker 监听的 vanilla 回调，现在通过反射桥补上同一钩子；没装 IMBlocker 时完全无影响

**Fixes**
- Mention completion no longer applied by raw Enter: after `@` pops the player list, Enter used to insert the highlighted candidate (e.g. typing `/tp @s` injected a matching player name into the command). Now Enter only applies the candidate after you actually selected it with ↑/↓ or the scroll wheel; otherwise Enter sends the text as-is. Player-name completion is also disabled inside commands (`/`-prefixed) — `@s`/`@p` are vanilla selectors, and command input is handled by the vanilla suggestion window
- Self-whisper banner/sound never fired even with own-whisper notify enabled (all three platforms): a self /msg creates a local-send feedback bubble, and the whisper-notification entry was gated by `!localSend` unconditionally, so neither banner nor sound ever fired. The gate now lets the local bubble through when own-whisper notify is on (the controller already gates on isOwn/selfNotify, and the whisper banner/sound follow their own toggles)

**Features**
- Configurable banner position: the notification banner is fixed at top-center and could not be moved out of the way of HUD mods such as Jade. New `banner_offset_x` / `banner_offset_y` settings (Settings → Notifications → Banner (Shared)) nudge the banner horizontally/vertically to clear other HUD elements
- IMBlocker IME support (all three platforms): with IMBlocker installed, typing a command auto-switches the input method to English and back to Chinese on exit — matching vanilla chat. e33chat's custom chat screen bypasses the vanilla callback IMBlocker listens to; a reflection bridge now re-attaches the same hook, and does nothing when IMBlocker is absent
- Tests: Forge 241 / NeoForge 241 / Fabric 222 all green

## v2.3.5

**修复**
- **聊天界面掉帧（核显/低配明显，Intel Arc 130T 实测复现）**：面板背景模糊（blur）在聊天界面打开期间每一帧都执行完整 5 级金字塔（10 次全屏 blit）——`panelOpacity` 默认 80 使 `panelOpacity < 0.999` 的触发条件在滑入动画结束后永真，模糊停不下来。核显 + GL 转译层扛不住每帧全屏 blit。现在完整金字塔每 2 帧刷新一次，中间帧直接重贴上一帧的模糊缓存（1 次 blit），开销减半以上；窗口缩放重建强制刷新，不残留脏模糊。模糊背景无高频细节，降帧视觉几乎无感

**Fixes**
- Chat UI stutter on integrated/low-end GPUs (Intel Arc 130T reproduced): the panel blur ran the full 5-level pyramid (10 full-screen blits) every frame while the chat was open — `panelOpacity` defaults to 80, so the `panelOpacity < 0.999` blur guard never exits after the slide-in animation. The pyramid now refreshes every other frame and in-between frames replay the cached blur (1 blit), more than halving the cost; window-resize rebuilds force a full refresh so no stale blur lingers. The blur has no high-frequency detail, so the skipped frames are visually indistinguishable
- Tests: Forge 241 / NeoForge 241 / Fabric 222 all green

## v2.3.4

**修复**
- **离线玩家引用块同步（三端）**：离线服/内网穿透场景下，未通过用户名验证的玩家（`Failed to verify username`）在接收端消息的 senderUUID 会落成 `UUID(0,0)`，与服务器广播的真实 UUID 失配——A 引用 B 的消息时，B 端永远补不上引用块。现在 ChatMeta 包附带发送者原始名字，匹配放宽为「UUID 精确 或 原始玩家名相等」，离线玩家也能正常同步引用块。网络包格式已变更，两端必须同升 2.3.4，老客户端混用会丢 meta

**Fixes**
- Quote block sync for offline players: on offline/LAN-forwarded servers, players who fail username verification (`Failed to verify username`) arrive on the receiving side with a `UUID(0,0)` sender, so the UUID in the broadcast ChatMeta never matched and the quoted player never saw the quote block. ChatMeta now carries the sender's raw name and matching accepts either an exact UUID or an equal raw player name. The packet format changed — both ends must run 2.3.4 together; mixing with an old client drops the meta
- Tests: Forge 85 / NeoForge 85 / Fabric 85 all green

## v2.3.3

**修复**
- **重复消息残留引用块**：先发一条引用回复"妈妈"，紧接着发一条同样的"妈妈"（不引用），anti-spam 会把两条合并成一个气泡，但合并前的实现原样拷贝了第一条的引用块——第二条明明没引用却显示引用块。现在合并气泡的引用块只反映本条消息自己的引用状态；仍带引用继续连发同内容时引用块正常保留

**新功能**
- **ChatImage 图片兼容（三端）**：装 ChatImage 后，气泡内可直接显示图片——支持 `[[CICode,url=...]]`（含 CQ 码转换）和 `https/http` 图片链接两种格式，文本变为绿色 `[Image]` 并带悬浮预览，与聊天框行为一致；自己发送的图片即时预览。不装 ChatImage 时原样显示文本，完全不影响原有功能

**Features**
- ChatImage image support (all three platforms): with ChatImage installed, images render inside bubbles — both `[[CICode,url=...]]` (including CQ code conversion) and `https/http` image links become green `[Image]` text with a hover preview, matching the vanilla chat; your own sent images preview immediately. Without ChatImage the codes stay plain text and nothing else changes

**Fixes**
- Stale quote block on duplicated messages: send a quoted "妈妈", then an identical unquoted "妈妈" — anti-spam collapsed both into one bubble, but the merge copied the first bubble's quote block, so the second (unquoted) message wrongly showed one. The merged bubble now reflects only this send's own quote state; consecutive re-quoted duplicates keep their quote block
- Tests: NeoForge 236 all green

## v2.3.2

**新功能**
- **屏蔽玩家**：右键玩家头像菜单新增「屏蔽玩家」项（已屏蔽则显示「取消屏蔽」），或在设置 → 聊天框 → 屏蔽列表直接编辑（逗号分隔精确名字）。被屏蔽玩家的消息**完全消失**——原版聊天框、气泡、横幅、音效全部没有；屏蔽即刻生效并清除该玩家已加载的历史消息，重进服也不会从历史恢复。匹配不区分大小写、自动忽略 § 颜色码；以玩家真实名称为主键、tab 列表显示名兜底，昵称插件/离线服也能命中

**调整**
- **屏蔽入口移到头像菜单**：此前临时加在消息右键菜单（复制/引用/屏蔽），现改为头像菜单第三项（传送/私聊/屏蔽），消息菜单恢复「复制/引用」两项，交互入口统一

**Features**
- Block players: a new "Block Player" item on the right-click avatar menu (shows "Unblock" once blocked), or edit the Blocked List directly in Settings → Chat → Blocked List (comma-separated exact names). A blocked player's messages vanish completely — vanilla chat, bubbles, banners and sounds. Blocking takes effect immediately and purges the player's already-loaded history; rejoining never restores it. Matching is case-insensitive, ignores § color codes, keys off the real player name with the tab-list display name as fallback (nickname plugins / offline servers)

**Changes**
- Block entry moved to the avatar menu: the temporary third item on the message menu (Copy/Quote/Block) is gone — the message menu is back to Copy/Quote, block now lives on the avatar menu (Teleport/Whisper/Block) as a single unified entry point

**Fixes**
- Tests: Forge 78 / NeoForge 78 / Fabric 78 all green

## v2.3.1

**修复**
- **图标采样窗口修正（2.2.8 回归）**：图标纹理约定 16×16（内容居中占 14×14，四周 1px 透明边），但 2.2.8 的"防边缘切割"补丁把采样窗口写成了 `size`（右键菜单 12px）→ 每个图标右 2 列 + 下 2 行被切掉，复制图标的叠加页右页被吞最明显。三端 9 处（ChatBars / ChatContextMenus / ChatSidebar / ChatBubbleScreen.drawTextureIcon）改为采样完整 14×14 内容区，图标完整显示
- **Fabric 客户端配置文件对齐**：`config/e33chat.json` → `config/e33chat-client.json`（与 Forge/Neo 的 `e33chat-client.toml` 对齐）；检测到旧文件自动迁移继承设置，老用户不丢配置

**Fixes**
- Icon sampling window fixed (2.2.8 regression): icon textures are 16×16 (content centered at 14×14 with a 1px transparent border), but the 2.2.8 "anti-clipping" patch sampled only `size` (12px in the context menu) → the right 2 columns and bottom 2 rows of every icon were cut off, most visibly the copy icon's overlay page. All 9 sites across the three platforms (ChatBars / ChatContextMenus / ChatSidebar / ChatBubbleScreen.drawTextureIcon) now sample the full 14×14 content area
- Fabric client config file renamed `config/e33chat.json` → `config/e33chat-client.json` (aligned with Forge/Neo's `e33chat-client.toml`); a legacy file is auto-migrated so existing settings carry over

## v2.3.0

**全面审计修复（2.2.0 → 2.3.0，三端同步）**
- **私聊回显抑制词表对齐检测词表**：`PM to X: hi` / `Msg to X` 式出站回显此前不被抑制 → 误判为入站私聊（重复气泡+横幅），且 pendingEcho 残留会在 10s 内把 partner 的真实回复当回声吞掉（违反"宁重复不吞消息"）。补全 `pm/message/msg/tell/私信/密谈/对你说/to you` 词表 + 正则单词边界
- **Fabric 服务端三处同步丢失修复**（2.1.0 重构把 `ChatServerListener` 并入 `ChatBubbleMod` 时未完整移植）：①mention 正则 `@(\w+)` → `@([\p{L}\p{N}_]+)`（中文玩家名跨客户端 @ 通知恢复）②引用 10s 过期（被反垃圾插件拦截的过期引用不再错误标记下一条消息）③`/msg`/`/tell`/`/w`/`/whisper` 私聊命令消费引用并广播引用元数据（Fabric API 无命令执行事件，用 `CommandManager.execute` mixin 等价实现）
- **配置界面取消语义修复**：`HISTORY_RETENTION_DAYS` 漏进 snapshotAll → 改历史保留天数后退出/ESC 不回滚且无"已更改"提示
- **100ms 同文本去重改对象同一性**：原先两条 100ms 内内容完全相同的真实消息（如双人连发 "gg"）第二条被吞 + PendingMeta 错配；vanilla 1-arg→3-arg 递归传的是同一 Component 对象，按对象身份去重即可
- **指令补全列表跟光标**：Forge/Neo 的 `fixSuggestionsX` 无条件钉死输入框左端（v1.0 起），改为 `max(x, inputX)` 保底——恢复 vanilla 光标锚定，数学闭环保证列表右缘不超面板
- **键盘焦点修复**：焦点在侧边栏搜索/常用语/搜索框时，上下键不再改主输入框历史
- **quick chat 面板高缩放溢出**：固定宽 140 无 clamp，6x 缩放下左溢出屏幕 20px（emoji/search 修过它漏网）
- **HistoryPacket 反 OOM**：decode count 上限 200（服务端本就封顶 50）
- **Fabric debugLog 默认值对齐**：false（此前新装默认刷聊天 debug 日志）
- ~~**性能**：面板模糊（8 次全屏 blit）每 3 帧重算节流~~ —— 已回退（见下：模糊内容是实时世界，跳过帧显示清晰世界）
- **GL blend 配对**：HUD 图标/红点等 5 处 enableBlend 后补 disableBlend
- **HUD 键名提示色随主题**（此前硬编码白字，浅色背景下对比差）
- **清理**：4 个死 lang 键（cancel/gen_hint/preview_hint/template_placeholder）
- 测试：Forge 225 / NeoForge 225 / Fabric 196 全绿

**Audit fixes (2.2.0 → 2.3.0, all platforms)**
- Whisper echo keyword table aligned with the detector: "PM to X: hi"-style outgoing echoes were not suppressed → misclassified as incoming whispers, and the stale pendingEcho swallowed the partner's real reply within 10s. Added `pm/message/msg/tell/私信/密谈/对你说/to you` + word-boundary regex
- Fabric server parity (lost when the 2.1.0 refactor merged `ChatServerListener` into `ChatBubbleMod`): ① mention regex `@(\w+)` → `@([\p{L}\p{N}_]+)` (Chinese player names work again) ② 10s quote expiry (blocked quotes no longer tag unrelated messages) ③ `/msg` `/tell` `/w` `/whisper` consume quotes and broadcast quote meta (Fabric has no command-execution event, so a `CommandManager.execute` mixin provides the equivalent)
- Config screen cancel semantics: `HISTORY_RETENTION_DAYS` was missing from the snapshot, so changing it could not be reverted on ESC
- 100ms same-text dedup replaced with object identity: two genuinely identical messages within 100ms (e.g. double "gg") were swallowed and misattributed; vanilla's 1-arg→3-arg recursion passes the same Component object, so identity works
- Command suggestions follow the caret: Forge/Neo `fixSuggestionsX` pinned the list to the input's left edge (since v1.0); now `max(x, inputX)` restores vanilla caret anchoring with a provable no-overflow bound
- Keyboard focus: Up/Down no longer moves the main input history while a sidebar/quick-chat/search field is focused
- Quick-chat panel overflows at high GUI scale (fixed 140px, no clamp) — same fix as emoji/search
- HistoryPacket OOM guard: decode count capped at 200 (server already caps at 50)
- Fabric debugLog default aligned to false
- ~~Performance: panel blur recomputed every 3rd frame~~ — reverted (see below: the blur content is the live world, so skipped frames showed the clear world)
- GL blend pairing: added disableBlend after 5 HUD icon/dot draws
- HUD key-hint color follows the theme (was hardcoded white)
- Cleanup: 4 dead lang keys removed
- Tests: Forge 225 / NeoForge 225 / Fabric 196 all green
- **服务端命令走 lang 翻译**：`/e33chat template` 系列与模板保存校验的回复此前硬编码中文（英文客户端显示中文）；全部改为 lang 键（含测试输出/校验错误/聊天私聊类别名），系统横幅 lang 描述同步为"默认开启"
- **服务端命令走 lang 翻译**：`/e33chat template` 系列与模板保存校验的回复此前硬编码中文（英文客户端显示中文）；全部改为 lang 键（含测试输出/校验错误/聊天私聊类别名），系统横幅 lang 描述同步为"默认开启"
- **通知音效参数统一**：Forge/Neo 的 `forUI(sound, pitch, volume)` 参数顺序用错（0.8×v 传进 pitch、volume 钉死 0.25 → 配置音量完全失效）；Fabric `master` 传 volume=0.64 过响且音高 0.25 低沉。统一 pitch=0.25、volume=0.25×配置系数（配置 80 → 0.2），小声且音量滑条有效
- **配置界面恢复半透明**：`config_bg` 烘焙 75% 不透明（0xC0），被无 alpha 顶点的 blit/drawTexture 丢弃 → 画成不透明深灰；改走带 alpha 顶点绘制，世界重新透出
- **HUD 键名提示回退纯白**：2.3.0 审计误把它改成随主题，恢复 `0xFFFFFFFF`（图标下按键提示不随聊天主题）
- **常用语/搜索输入框被弹层盖住**（2.2.9 `5bb740e` z 提升回归）：输入框 widget 在 z=50 渲染，被 z=100 的不透明面板背景盖住文字/光标 → 看起来"无法聚焦输入"（实际聚焦正常，边框/搜索都生效）；面板打开时在同 z 重画输入框 widget 修复
- **面板模糊节流回退**：2.3.0 的"每 3 帧重算"假设模糊结果跨帧保留，但模糊内容是实时世界、每帧重绘 → 跳过帧显示清晰世界，模糊看起来失效；回退为每帧重算
- **Server commands now localize**: `/e33chat template` replies and template-save validation were hardcoded Chinese (English clients saw Chinese); all moved to lang keys (including test output, validation errors, chat/whisper kind names), and the system-banner description now says "on by default"
- **Server commands now localize**: `/e33chat template` replies and template-save validation were hardcoded Chinese (English clients saw Chinese); all moved to lang keys (including test output, validation errors, chat/whisper kind names), and the system-banner description now says "on by default"
- **Notification sound unified**: Forge/Neo `forUI(sound, pitch, volume)` had the arguments swapped (0.8×v went into pitch, volume stuck at 0.25, so the volume slider did nothing); Fabric passed volume 0.64 (too loud) with a low 0.25 pitch. Now pitch=0.25, volume=0.25×config (80 → 0.2) on all platforms
- **Config screen transparency restored**: `config_bg` bakes at 75% alpha (0xC0) but plain blit/drawTexture drops the alpha (no color vertex) → solid dark grey; now drawn with alpha-aware vertices so the world shows through
- **HUD key hint back to pure white**: 2.3.0 audit wrongly made it follow the theme; reverted to `0xFFFFFFFF`
- **Quick-chat/search inputs no longer hidden** (2.2.9 `5bb740e` z-lift regression): the input widgets render at z=50 but the opaque panel overlay at z=100 covered their text/caret, looking like focus was broken (it was never broken — the border and search both worked); the widgets are redrawn at the panel's z when open
- **Blur throttle reverted**: the 2.3.0 "recompute every 3rd frame" assumed the blur result persists across frames, but the blur content is the live world which repaints every frame — skipped frames showed the clear world, so the blur looked off; back to every-frame blur


***

## v2.2.9

**WATUT 兼容（ChatBubbleScreen 改继承 ChatScreen）**
- `ChatBubbleScreen` 从 `extends Screen` 改为 `extends ChatScreen`（三端）——WATUT（What Are They Up To）靠 `instanceof ChatScreen` + 读取 `input.getValue()` 检测玩家打字/GUI 状态，原 `extends Screen` 导致全部判定失败，玩家打开聊天时别人看不到打字动画
- 输入框复用父类 `protected EditBox input`（yarn: `chatField`），WATUT 的 AT 已 public 化该字段，可直接读到我们的输入内容
- 绕开父类方法（`keyPressed`/`mouseClicked`/`render` 访问 package-private `commandSuggestions`/`chatInputSuggestor`，跨包子类无法初始化）：自实现等价逻辑，父类输入框/建议框不会出现（保持 cancel 原版输入框）
- `moveInHistory` 改走父类实现；配置保存/预设输入等逻辑不变
- 版本号 2.2.8 → 2.2.9；三端同步，编译+测试全绿
- **修复聊天键未响应**：extends ChatScreen 后 ChatBubbleScreen 命中自身 ScreenEvent.Opening 拦截（instanceof ChatScreen）→ setScreen 无限递归；拦截逻辑排除 ChatBubbleScreen 自身
- **修复打字崩溃（NPE: CommandSuggestions null）**：父类 ChatScreen 的 onEdited/moveInHistory/resize 访问 package-private commandSuggestions（跨包子类无法初始化 = null）——responder 改绑自有方法、override moveInHistory（历史记录上/下键）、override resize；Fabric 端 yarn 字段 chatInputSuggestor 同源问题一并处理

**WATUT compatibility (ChatBubbleScreen now extends ChatScreen)**
- `ChatBubbleScreen` changed from `extends Screen` to `extends ChatScreen` on all three platforms — WATUT detects typing/GUI state via `instanceof ChatScreen` + reading `input.getValue()`; extending plain Screen made every check fail, so other players never saw the typing animation
- The edit box now uses the parent's `protected EditBox input` (yarn: `chatField`); WATUT's access transformer already publicizes that field, so it reads our input content directly
- Parent methods that touch package-private `commandSuggestions`/`chatInputSuggestor` (uninitializable from a cross-package subclass) are bypassed with equivalent local logic — the vanilla input box / suggestor never appear (original cancel preserved)
- `moveInHistory` now uses the parent implementation; config save / preset input unchanged
- Version 2.2.8 → 2.2.9; synced across all platforms, build + tests green

***

## v2.2.8（纹理 API 迁移）

**全 UI 纹理走原版资源 API（删手工加载层）**
- 所有可纹理化 UI（组件 + 图标 + 状态高亮）改走 `blit(ResourceLocation)` 懒加载——`TextureManager.getTexture` 无缓存时自动 `new SimpleTexture` 从资源栈读取，**用户资源包 > mod jar 内置 PNG**
- 默认纹理改为 jar 内置 16×16 纯色 PNG（`assets/e33chat/textures/gui/{dark|light}/` 23 元素 × 2 主题），色值与原代码生成完全一致，零资源包时外观零变化
- **新增 6 个状态高亮纹理**：`hover_bg` / `sidebar_selected` / `sidebar_hover` / `context_hover` / `close_bg` / `close_hover`——hover/选中/关闭按钮背景全部从 `g.fill(颜色)` 改为纹理 blit，资源包可覆盖
- **删除手工加载层**：`UiTextureManager.loadOrGenerate`/`preloadAll`、`loadIconTextures`/`loadIconTexture`/`ensureIconsLoaded`、`TextureGenerators` + 测试（7 例）
- **F3+T 即时生效**：改资源包 PNG → F3+T 重载 → 界面立即变（SimpleTexture 重读新 PNG；旧 DynamicTexture 不会）
- 时间分隔线/调色板/@提及弹窗选中行保持 `g.fill`（半透明 blend 语义不变，避免滚动条式 blend 回归）
- 测试 Forge/NeoForge 204 → 197、Fabric 175 → 168；三端同步
- **修复提及检测崩溃（社区 PR #10 by Spagles）**：消息以玩家名开头 + requireAt 关闭时 `text.charAt(-1)` 抛 `StringIndexOutOfBoundsException`；移除冗余的 `charAt(idx-1) != '@'` 检查（该分支恒为 true），新增 `MentionDetectorTest` 7 例回归
- **2.2.8 收尾三端同步审计**：NeoForge/Fabric 补上 Forge 的面板滑入 blur 偏移补偿（`blurPanel(panelOffset + fillLeft, ...)`——blur 区跟随滑入动画，否则动画中 blur 左缘与内容错位）；Fabric lang 同步最新分类 key
- **服务端配置审计 G1-G4（2.2.8 收尾）**：
  - G1 插件私聊词：`hasWhisperKeywordBeforeColon` 扩词——私信/密谈 + 英文 pm/message/msg/tell（词边界防 hepm/msgbox 误判）；WhisperFormatsTest 补 4 例
  - G2 广播仿冒：`parseGeneric` 拒绝名字前出现聊天分隔符（`系统>>Steve`/`公告»Steve`/`系统：Steve` 不再误归属成玩家）；MessagePresentationTest 补 3 例
  - G3 多色 § 嵌名：`parseDecoratedPlayerLine` 双侧剥 § + 偏移映射（`S§6t§beve` 命中 Steve，偏移指向原文供样式切片），守卫1/守卫3/inferFromMessage 全部接新偏移；MessagePresentationTest 补 4 例（含偏移断言）
  - G4 模板 miss 诊断：`logTemplateMiss` 日志含已配置模板列表（chat/whisper 原始串）；系统消息灰字兜底加 `guard fallback -> gray` 日志

**Server-config audit G1-G4 (2.2.8 wrap-up)**: plugin whisper keywords (私信/密谈 + pm/message/msg/tell with word boundaries); broadcast-spoof guard (names preceded by chat separators no longer attributed); multi-color §-embedded names matched via dual-side strip + offset mapping (offsets point at the original text for style slicing); template-miss diagnostics now include the configured template list, and gray-fallback logging added

**Fixed mention-detection crash (community PR #10 by Spagles)**: messages starting with the player's name with requireAt off threw `StringIndexOutOfBoundsException` at `text.charAt(-1)`; removed the redundant `charAt(idx-1) != '@'` check (always true in that branch), added 7 `MentionDetectorTest` regression cases
- **修复上线 missing-texture**：`blit(rl)` 懒加载的 RL 必须带 `.png` 后缀（SimpleTexture 原样查资源，不自动补）；Fabric `drawTexture` 组件背景改 11 参分离版防 UV 越界

**Fixed launch missing-texture**: lazy-load RLs must carry the `.png` suffix (SimpleTexture looks up the path verbatim, no auto-suffix); Fabric component backgrounds switched to the 11-arg split form to avoid UV overflow

**All UI textures migrated to vanilla resource API (manual loading layer removed)**
- All texturable UI (components + icons + state highlights) now render via `blit(ResourceLocation)` lazy loading — `TextureManager.getTexture` auto-creates a `SimpleTexture` on cache miss, reading from the resource stack: **user resource pack > mod-jar PNG**
- Default textures are now jar-embedded 16×16 solid-color PNGs (23 elements × 2 themes) with colors identical to the old generated ones — zero visual change without a resource pack
- **6 new state-highlight textures**: `hover_bg` / `sidebar_selected` / `sidebar_hover` / `context_hover` / `close_bg` / `close_hover` — hover/selected/close-button backgrounds switched from `g.fill(color)` to texture blits, overridable
- **Removed manual loading**: `UiTextureManager.loadOrGenerate`/`preloadAll`, `loadIconTextures`/`loadIconTexture`/`ensureIconsLoaded`, `TextureGenerators` + its 7 tests
- **F3+T hot reload**: edit a pack PNG → F3+T → UI updates instantly (SimpleTexture re-reads; old DynamicTexture didn't)
- Time separator / palette / @mention selected row keep `g.fill` (translucent blend semantics unchanged — no scrollbar-style blend regression)
- Tests Forge/NeoForge 204 → 197, Fabric 175 → 168; synced across all three platforms

***

## v2.2.8（修订）

**圆角组件回退 SDF（2.2.8 首版 9-slice 纹理化撤销）**
- 聊天气泡 / 引用回复块 / @提及横幅背景回退 `RoundRectRenderer`（SDF shader）——任何圆角配置平滑、配置实时生效
- 撤销原因：9-slice 纹理采样与贴图尺寸失配（border 写死/传参 vs 贴图实际尺寸），导致圆角局部放大、锯齿、方形；两轮修复后仍不可靠，回退保稳定
- 删除 `NineSliceRenderer` 与圆角纹理生成（`bubble_bg`/`quote_bg`/`banner_bg` 元素、`roundedRect`、ROUNDED 类型）
- **保留的纹理化成果**：toast 黑块根因修复（烘焙不透明 + drawWithAlpha）、时间分隔符、HUD 强提示条、常用语滚动条、设置保存后重烘焙、1×1 拉伸组件全部不受影响
- **代价**：气泡/引用/横幅不再可被资源包覆盖（SDF 是代码画）
- 测试 207 → 204（删 3 个圆角纹理测试）；三端同步

**Rounded components reverted to SDF (2.2.8 first-release 9-slice texturing undone)**
- Chat bubbles / quote blocks / @-mention banner backgrounds back to `RoundRectRenderer` (SDF shader) — smooth at any corner-radius config, config-driven live
- Why: 9-slice texture sampling mismatched the texture size (hardcoded/parameterized border vs actual texture), causing upscaled corners, jaggies and squares; unreliable after two fix rounds, reverted for stability
- `NineSliceRenderer` and rounded-texture generation removed (`bubble_bg`/`quote_bg`/`banner_bg` elements, `roundedRect`, ROUNDED kinds)
- **Kept texture work**: toast black-block root-cause fix (opaque bake + drawWithAlpha), time separator, strong-hint bar, quick-chat scrollbar, config-save re-bake, all 1×1 stretch elements unaffected
- **Cost**: bubbles/quotes/banner are no longer resource-pack overridable (SDF is code-drawn)
- Tests 207 → 204 (3 rounded-texture tests removed); synced across all three platforms

***

## v2.2.8

**纹理化覆盖全部动态尺寸组件（9-slice，三端同步）**
- 新增 `NineSliceRenderer`：自写 stretch 版 9-slice——四角不拉伸、边单向拉伸、中心双向拉伸（与 vanilla tile 平铺不同，渐变/图案不变形）；贴图约定 16×16、四角区 4px，1×1 纯色元素自动退化纯拉伸
- 聊天气泡 / 引用回复块 / @提及横幅背景改为纹理渲染：白色圆角纹理（半径跟随配置）× tint 用户色——零资源包视觉不变，资源包可覆盖圆角/边框/图案；横幅阴影仍代码绘制
- **toast 黑块根因修复**：2.2.4 纹理化时 `TOAST_BG` 烘焙不透明 `toastBg`（dark=纯黑）+ blit 无 alpha 通道 → 一整块不透明黑块；现烘焙强制不透明 + `drawWithAlpha` 动态 alpha 通道，纹理可覆盖、透明度可控
- 时间分隔符 / HUD 强提示条改为纹理渲染（`time_sep_bg` / `strong_hint_bg`），资源包可覆盖底色
- 常用语面板滚动条改为纹理：白色贴图 × tint（主题色 + hover 态）——`quick_scrollbar_track` / `quick_scrollbar_thumb`
- 设置界面保存后重新烘焙默认纹理：气泡/横幅圆角配置修改即时生效
- 新增纹理元素：`bubble_bg` / `quote_bg` / `banner_bg` / `time_sep_bg` / `strong_hint_bg` / `quick_scrollbar_track` / `quick_scrollbar_thumb`
- `TextureGenerators.roundedRect` 纯函数（零 MC 依赖，可单测）；测试 Forge 204 / NeoForge 204 / Fabric 178 全绿
- 附带示例资源包 `E33Chat-Texture-Demo`（已放入测试服 resourcepacks/）：气泡边框、彩色横幅等 9-slice 覆盖演示

**Texture-driven all dynamic-size components (9-slice, all three platforms)**
- New `NineSliceRenderer`: self-written stretch 9-slice — corners fixed, edges stretched one-way, center both ways (unlike vanilla tile tiling, gradients/patterns don't distort); 16×16 texture convention with a 4px corner area, 1×1 solids degrade to plain stretch automatically
- Chat bubbles / quote blocks / @-mention banner now render from textures: white rounded-rect (radius follows config) × tint of user color — zero visual change without a resource pack, shapes/borders/patterns overridable; banner shadow stays code-drawn
- **Toast black-block root cause fixed**: in 2.2.4 `TOAST_BG` baked opaque `toastBg` (pure black in dark) and the blit had no alpha channel → one opaque black block; now baked opaque + `drawWithAlpha` dynamic alpha — texture overridable, opacity controllable
- Time separators / strong-hint bar now textured (`time_sep_bg` / `strong_hint_bg`), base color overridable
- Quick-chat scrollbar now textured: white image × tint (theme color + hover state) — `quick_scrollbar_track` / `quick_scrollbar_thumb`
- Settings screen re-bakes default textures on save: bubble/banner radius changes take effect immediately
- New texture elements: `bubble_bg` / `quote_bg` / `banner_bg` / `time_sep_bg` / `strong_hint_bg` / `quick_scrollbar_track` / `quick_scrollbar_thumb`
- `TextureGenerators.roundedRect` pure function (zero MC deps, unit-testable); tests Forge 204 / NeoForge 204 / Fabric 178 all green
- Bundled demo pack `E33Chat-Texture-Demo` (placed in the test server's resourcepacks/): bubble borders, colored banners — 9-slice override demos

***

## v2.2.7

**模板引擎加固与插件生态适配（Forge 先行）**
- 修复：模板编译崩溃——同字段重复（`{prefix}{prefix}`）会生成重复正则命名组并抛 `PatternSyntaxException`，穿透命令/GUI/同步/保存全部入口；现显式拒绝并兜底 try/catch
- 修复：`extractWhisperContent` 两处实现不一致（Store 版 lastIndexOf 会在内容含冒号时截断）；统一为首分隔符语义
- 修复：模板路径的字面 § 色码（插件原样下发 `§6`）现在用 `parseStyledText` 还原成真实颜色，与守卫路径一致（不再显示裸 `§6` 字形）
- 模板语法增强：`{content}` 可位于模板任意位置（支持后缀式格式，如 `{display_name}: {content} [聊天]`）；新增 `{sep}` 占位符——匹配 `>>` / 冒号 / `»` / `>` 或纯空格，一条模板覆盖多种分隔符风格；旧模板全部兼容
- 预设库补真实插件默认格式：EssentialsX（`<{display_name}> {content}`、带前后缀示例）、DeluxeChat（`[Guest] {display_name} > {content}`）、CMI 私聊（`[/msg from {sender}] {content}`）等
- 新增测试覆盖：模板崩溃回归、后缀式 content、`{sep}`、真实插件格式（含 § 码字面量）、多冒号内容不截断——测试 195 → 204
- 仅 Forge 1.20.1（NeoForge / Fabric 后续同步）

**Template engine hardening & plugin-ecosystem adaptation (Forge first)**
- Fix: template compile crash — duplicated fields (`{prefix}{prefix}`) produced duplicate regex named groups and threw `PatternSyntaxException` through every entry point (command/GUI/sync/save); now rejected explicitly with a try/catch fallback
- Fix: `extractWhisperContent` had two inconsistent implementations (the Store version truncated content containing colons via lastIndexOf); unified to first-separator semantics
- Fix: literal §-codes in template output (plugins sending raw `§6`) are now rebuilt into real colors via `parseStyledText`, matching the guard path
- Syntax: `{content}` may sit anywhere in a template (suffix styles like `{display_name}: {content} [聊天]` now work); new `{sep}` placeholder matches `>>`/colons/`»`/`>` or plain spaces — one template covers many separator styles; all old templates remain valid
- Presets extended with real plugin defaults: EssentialsX (`<{display_name}> {content}`, prefixed example), DeluxeChat (`[Guest] {display_name} > {content}`), CMI whisper (`[/msg from {sender}] {content}`)
- Tests 195 → 204 (crash regression, suffix content, `{sep}`, real plugin formats incl. literal §-codes, colon-rich content)
- Forge 1.20.1 only (NeoForge / Fabric sync later)

***

## v2.2.6

**服务端消息格式模板（声明式解析，Forge 先行）**
- 新增模板层：服务端声明消息格式（字段占位符），客户端按声明精确剖开 sender/装饰名/内容/私聊方向——模板命中 = 证据最强，直接跳过启发式守卫
- 模板语法：`{prefix}` `{display_name}` `{name}` `{content}` `{sender}` `{target}`；字面量分隔符原样转义（`»` `：` `>>` 等）
- 服务端配置（`config/e33chat-server.toml`）：`chat_templates` / `whisper_templates` / `template_debug`；空列表 = 关闭模板，回到守卫识别
- 游戏内命令（需 OP）：`/e33chat template list` / `set chat|whisper <模板>` / `remove <index>` / `clear` / `test <index> <文本>`（即时匹配预览），改完自动广播并写回 toml
- 名称可解析门槛：姓名匹配不到在线/见过的玩家即视为未命中，`Server: 重启中` 这类系统消息不会被误判
- 匹配失败自动回落三层守卫；`template_debug` 开启后记录失败样本（每分钟最多 5 条）与未知占位符提示
- 同步走新通道（ConfigSyncV2Packet id 4），旧版客户端不受影响；`/reload` 与换世界自动重同步
- 私聊模板支持 incoming/outgoing 方向解析（`{sender}`/`{target}`），回显抑制复用现有机制
- **服务端配置 GUI**：`/e33chat gui`（OP）打开图形界面，可视化编辑全部服务端配置（use_tpa / 聊天历史 / 聊天与私聊模板 / 模板诊断），保存后服务端校验、写回 toml 并广播；界面视觉对齐客户端设置（主题控件/分割线/平滑滚动）
- **模板简化**：GUI 内"从消息生成"（粘贴真实消息自动推断模板）、实时预览（输入示例消息即时显示解析结果）、常见格式预设、右上角教程（语法/示例/测试/FAQ，可滚动）
- 仅 Forge 1.20.1（NeoForge / Fabric 后续同步）
- 测试 158 → 195

***

**Server-configured message-format templates (declarative parsing, Forge first)**
- New template layer: the server declares its message format (field placeholders); the client splits sender / decorated name / content / whisper direction exactly per the declaration — a template match is the strongest evidence and bypasses the heuristic guards
- Template syntax: `{prefix}` `{display_name}` `{name}` `{content}` `{sender}` `{target}`; literal separators are escaped verbatim (`»` `：` `>>` …)
- Server config (`config/e33chat-server.toml`): `chat_templates` / `whisper_templates` / `template_debug`; empty list = disabled, guards take over
- In-game commands (OP required): `/e33chat template list` / `set chat|whisper <template>` / `remove <index>` / `clear` / `test <index> <text>` (instant match preview); changes broadcast immediately and persist to the toml
- Name-resolution gate: a name that resolves to no online/seen player is not a match — system lines like `Server: restarting` are never misattributed
- Failed matches fall back to the three-layer guards; `template_debug` logs miss samples (max 5/min) and unknown-placeholder warnings
- Synced over a new channel (ConfigSyncV2Packet id 4); old clients unaffected; resyncs on `/reload` and world change
- Whisper templates resolve incoming/outgoing direction via `{sender}`/`{target}`; echo suppression reuses the existing mechanism
- **Server-config GUI**: `/e33chat gui` (OP) opens an in-game screen to edit every server setting (use_tpa / join history / chat & whisper templates / template debug); saving validates server-side, persists to the toml and rebroadcasts; visual language matches the client settings screen (themed widgets / dividers / smooth scrolling)
- **Template simplifications**: in-GUI "generate from message" (paste a real chat line, the template is inferred), live preview (type a sample message, see the parsed fields instantly), common-format presets, and a scrollable tutorial (syntax / examples / testing / FAQ) at the top-right
- Forge 1.20.1 only (NeoForge / Fabric follow-up)
- Tests 158 → 195

***

## v2.2.5

**纹理化覆盖全部结构色元素**
- 右键菜单（底色 + 边框）、@ 提及弹窗底、复制提示、私聊模式横条改为纹理渲染
- 设置界面：全屏背景、左右树分割线、选项分隔线、预览区分割线、双滚动条（track/thumb 走动态 alpha 通道）
- 表情面板（tab 栏 / 分割线 / 内容区）、常用语面板（面板底 / 输入框）、搜索面板（面板底 / 输入框）、设置菜单底统一纹理化
- 新增纹理元素：`context_menu_bg` / `popup_bg` / `toast_bg` / `whisper_bar` / `config_bg` / `content_bg`
- hover/选中状态色（菜单项高亮、关闭按钮、树选中竖条等）保持代码渲染，不纹理化
- 三端同步：Forge / NeoForge / Fabric 共用同一套资源包路径约定
- 测试 155 → 158

***

**Texture-driven all structural elements**
- Context menu (bg + borders), @ mention popup, copy toast, whisper mode bar now render from textures
- Config screen: full background, tree/option/preview dividers, both scrollbars (track/thumb via alpha channel)
- Emoji (tab bar / divider / content), quick-chat (panel / input), search (panel / input), settings menu backgrounds unified
- New texture elements: `context_menu_bg` / `popup_bg` / `toast_bg` / `whisper_bar` / `config_bg` / `content_bg`
- Hover/selected state colors stay code-rendered, not textured
- Synced across Forge / NeoForge / Fabric with one resource-pack path convention
- Tests 155 → 158

***

## v2.2.4

**纹理驱动 UI（资源包可覆盖）**
- 面板结构元素从硬编码色块改为纹理渲染：面板背景 / 标题栏 / 底栏 / 侧边栏背景 / 分割线 / 输入框背景 / 滚动条轨道与滑块
- 纹理路径约定：`assets/e33chat/textures/gui/{dark|light}/panel_bg.png` 等——丢进资源包即可覆盖任意元素外观（渐变、图案、配色都可以）
- 默认纹理由代码生成（主题色烘焙），零资源包时视觉完全不变；资源包覆盖自动优先
- 切换主题（dark/light）即时生效——纹理在启动时全部预注册，主题切换零卡顿
- 透明度动态元素（面板开屏淡入、滚动条淡入淡出）走带 alpha 渲染通道，行为不变
- 修正 `pack.mcmeta` 描述（原为 Player Carry 时代残留文案；Forge 的 mod 资源包必须有 pack.mcmeta，删掉会导致资源包整体不加载）
- 测试 151 → 155
- **资源包热重载**：游戏内 F3+T 或切换资源包后 UI 纹理立即跟随更新，无需重启游戏

***

**Texture-driven UI (resource-pack overridable)**
- Panel elements switched from hardcoded color fills to texture rendering: panel background / title bar / bottom bar / sidebar background / dividers / input background / scrollbar track & thumb
- Path convention: `assets/e33chat/textures/gui/{dark|light}/panel_bg.png` etc. — drop files into a resource pack to override any element (gradients, patterns, custom colors)
- Default textures are code-generated (theme colors baked); zero visual change without a resource pack; resource-pack overrides take priority automatically
- Theme switching (dark/light) is instant — all textures pre-registered at startup, no reload hitch
- Dynamic-alpha elements (panel fade-in, scrollbar fade) render through an alpha channel; behavior unchanged
- Fixed the `pack.mcmeta` description (was a leftover from the Player Carry era; Forge's mod resource pack requires pack.mcmeta — removing it disabled the whole resource pack)
- Tests 151 → 155
- **Hot reload**: UI textures re-register on F3+T / resource-pack switch — no restart needed

***

## v2.2.3

**聊天记录改造**
- 时间戳从"时分秒"升级为完整时间（epoch millis，含日期）：聊天面板时间分隔线当天显示 `15:30`，隔天显示 `07-31 15:30`，跨年显示 `2025-12-31 15:30`（微信同款）
- 记录文件改为**纯文本日志格式**（每行 `时间	发送者	内容	标记`，记事本直接可读；标记 M=自己 S=系统 W=私聊），原子写入（先写临时文件再替换，写到一半崩溃不会损坏文件）
- **崩溃保护**：每 30 秒自动保存一次，游戏崩溃/闪退最多丢 30 秒的聊天记录（此前只在换世界/退服时保存，崩溃全丢）
- 旧格式记录自动迁移：旧 JSON 文件加载时按保存日期补齐缺失的日期（跨午夜自动回推一天），下次保存自动转为新格式；历史记录文件名保留中文世界名（旧文件仍兼容读取）
- 服务器分发（新玩家登录补发最近聊天）时间戳同步升级为完整时间——**客户端与服务器需同时升级**（网络协议变更）
- mod 描述修复乱码（Mod 列表统一显示英文）
- 敏感命令不进历史记录（`/login` `/register` 等含凭据命令写入时跳过）
- 历史记录保留天数（`history_retention_days`，0 = 永久保留）：进入世界时自动删除超过保留期的历史文件

***

**Chat history rework**
- Timestamps upgraded from time-of-day to full epoch millis (with date): the chat separator shows `15:30` same-day, `07-31 15:30` next-day, `2025-12-31 15:30` across years (WeChat-style)
- History files switched to a **plain-text log format** (one line per message: `time\tsender\tcontent\tflags`, readable in any text editor; flags M=own S=system W=whisper), written atomically (tmp file + replace, a crash mid-write never corrupts the file)
- **Crash protection**: auto-save every 30 seconds — a crash now loses at most 30s of chat (previously only saved on world switch / quit, so crashes lost everything)
- Legacy files migrate automatically: old JSON files get dates back-filled from the file's save date (midnight crossings roll back a day), rewritten to the new format on next save; history filenames keep Chinese world names (old filenames still load)
- Server distribution (recent chat sent to joining players) timestamp upgraded to full epoch millis — **client and server must upgrade together** (network protocol change)
- Mod description mojibake fixed (English in the Mod List)
- Sensitive commands never land in the history file (`/login` `/register` and similar credential-carrying commands are skipped when written)
- History retention days (`history_retention_days`, 0 = keep forever): history files older than the limit are deleted automatically on world join

***

## v2.2.2

**消息预览改为原版聊天框**
- 删除自定义 HUD 消息预览（约 140 行，含 PreviewEntry/tickPreview/buildPreviewText），原版 `ChatComponent` 恢复渲染并上移 8px 避开 HUD 聊天图标
- 兼容性红利：ChatHeads、ChatAnimation 等改造原版聊天框的 mod 自动生效（此前被 E33Chat 取消渲染）
- 删除配置项：`preview_enabled` / `preview_lines` / `preview_width`（旧配置自动忽略）

**原版聊天框统一消息格式**
- 私聊消息（进/出/自己）显示为 `<发送者>[私聊] 内容` 玩家格式，替代原版系统格式（"你悄悄地对 X 说：..."）
- 引用回复显示为 `<发送者>[引用] 内容`（黄色标签）——引用走普通聊天通道，靠 echo 记录携带引用标记识别
- 发送者名字保留服务器前缀装饰与团队颜色（如 `[称号]E33EPUS`）
- 服务器双回显（签名出站 + 入站两条）自动去重，只显示一行
- 原版系统格式的消息全部抑制，不再混显

**其他**
- 高级页新增"自我私聊通知"（`own_whisper_notify`，默认关）：给自己发 /msg 时是否弹横幅与音效（测试用）
- 测试 92 → 120 例：新增私聊内容提取、引用标记传递、repost 去重、装饰名模板提取（中英各 2 种）

***

**Message preview replaced by vanilla chat**
- Custom HUD message preview removed (~140 lines: PreviewEntry/tickPreview/buildPreviewText); vanilla `ChatComponent` renders again, shifted 8px up to clear the HUD chat icon
- Compatibility bonus: mods that restyle the vanilla chat (ChatHeads, ChatAnimation) work automatically (previously cancelled by E33Chat)
- Removed config keys: `preview_enabled` / `preview_lines` / `preview_width` (old configs are ignored)

**Unified vanilla-chat message format**
- Whispers (in/out/self) now show as `<sender>[私聊] content` instead of the vanilla system line ("You whisper to X: ...")
- Quote replies show as `<sender>[引用] content` (yellow tag) — quotes travel the plain-chat channel, identified via a quote flag carried on the echo record
- Sender names keep server prefix decorations and team colors (e.g. `[称号]E33EPUS`)
- Server double-echo (signed outgoing + incoming) is deduplicated to a single line
- Vanilla system-format lines are fully suppressed, no mixed display

**Other**
- Advanced tab: new "Self-Whisper Notification" (`own_whisper_notify`, default off) — banner + sound when you /msg yourself (testing aid)
- Tests 92 → 120: whisper content extraction, quote-flag propagation, repost dedup, decorated-name template extraction (2 zh + 2 en)

***

## v2.2.1

**修复**
- 提及横幅渲染到聊天面板之上（此前被面板遮挡）
- 压缩 mod logo，减小 jar 体积

***

**Fixes**
- Mention banner renders above the chat panel (was previously covered by it)
- Compressed mod logo, smaller jar

***

## v2.2.0

**面板背景模糊**
- 聊天面板背景可选是否启用模糊效果（`glBlitFramebuffer` 多 pass 降采样，兼容 Oculus/Embeddium）
- 面板不透明度可调（0-100%，默认 60%）
- 配置项：`blur_enabled`（开关）、`panel_opacity`（0-100）
- 注册至外观 category：面板区域

**审计修复（全代码审计，双端同步）**

*消息分类重构（守卫架构）*
- 广播识别从分隔符白名单改为结构规则：名字与内容间纯空格 = 广播。`[+] Steve 加入了游戏` 继续拦截，而 `Steve|hi`、`Steve-hi`、`Steve >> hi` 等格式不再被误判为系统灰字
- 新增识别格式：legacy § 颜色码（`§6Steve§r: hi`）、后缀称号（`Steve[LV.10]: hi` / `Steve[AFK]: hi` / `Steve(VIP): hi`）、裸短名/中文短名+冒号（`小明: 你好`，离线服）
- 服务端 @ 提取支持非 ASCII 名字（中文名离线服此前无法触发跨客户端 @ 通知）
- 4 处分隔符跳过逻辑统一为 `MessagePresentation.skipSeparators`（§ 对与整对括号跳过）

*身份判定（离线服同名玩家系列修复）*
- 所有"是否本人"判定改为 UUID 优先、名字兜底：修复离线服同名玩家的消息显示成自己的气泡、@/私聊通知丢失、回声被误吞
- 回声名字匹配从子串包含改为整词边界：`SteveAdmin` 和带 `[VIP]` 前缀的玩家不再被当成自己的回声吞掉
- 回声抑制与归属便签加过期时间，修复残留状态吞消息

*私聊*
- 修复 NCR 服公屏含"私聊/whisper"等词的消息被误判为私聊（关键词须出现在首个冒号之前）
- 修复私聊内容含多个冒号（引用内容）时从最后一个冒号截断
- 修复上箭头历史回放泄漏隐形拼接的 `/msg` 指令（现记录用户实际输入）
- 修复设置里的私聊音效开关无效（内部误接到 @ 音效开关）

*聊天历史*
- 修复进服时旧历史记录加载到 MOTD 等早到消息之上（顺序颠倒）
- 修复服务端引用挂起被插件拦截后把旧引用挂到下一条消息（10 秒过期）

*通知与界面*
- 高级标签页新增"自我@通知"与"自我引用通知"开关（默认关，测试用）
- 表情/颜文字改为在光标处插入（原固定在末尾）
- 聊天记录搜索同时匹配发送者名字
- 修复左键自己头像无法插入 @（点击区域与渲染位置相反）
- 修复玩家列表收缩时侧边栏可滚出空白（Forge 回传 NeoForge 的 clamp）
- 修复侧边栏图标加载失败保护实际无效（catch 只是重复同一调用）
- 玩家缓存 LRU 上限 512；NeoForge tell-click 名字范围与 Forge 同步（长称号服）

*测试*
- 62 → 92 例：删除同义反复的侧边栏假测试，新增格式解析/整词边界/私聊格式/Animation 真覆盖

**配置界面重做与 HUD 未读红点**
- 设置界面按 UI 元素重排为 5 个标签页（聊天框 / HUD / 通知 / 侧边栏 / 高级），各页内再分子分类
- 左侧改为可折叠、可滚动的子分类树（全展开时不再被挤出屏幕）；右侧分区标题去掉黑底橙字，改为灰字左对齐 + 右侧延伸细线，行高与选项对齐
- 颜色行新增行内预设色板；“气泡与字体”子分类新增气泡预览带，随圆角实时变化
- 控件按反馈换回原版开/关开关、选中条改回白色；数值项保留可手输输入框，仅音效音量用滑条
- 编辑模型：打开时快照、实时生效，底部“保存 / 退出”并显示改动条数，ESC 弹确认放弃
- 新增“音效总音量”并接线到全部 4 类提示音；面板不透明度默认 60 → 80
- 提示文案统一为作用句通俗风格、数值项标注范围，删除无主键、修正私聊音效文案
- 按反馈移除搜索框与恢复默认按钮
- 标签页与选项列表加常驻滚动条与缓出平滑滚动：可拖拽滑块、点击轨道翻页
- HUD 未读指示器换成与侧边栏私聊同款的跳动红点：裁出红点 nearest 放大后骑在聊天图标右上角

**Panel background blur**
- Optional blur effect behind the chat panel (GL blit multi-pass downscale, compatible with Oculus/Embeddium)
- Configurable panel opacity (0-100%, default 60%)
- Config options: `blur_enabled` (toggle), `panel_opacity` (0-100)
- Registered under Appearance category: Panel section

**Audit fixes (full-codebase audit, both loaders)**

*Message classification rework (guard architecture)*
- Broadcast detection changed from a separator whitelist to a structural rule: a whitespace-only gap between name and content = broadcast. `[+] Steve joined the game` stays blocked, while `Steve|hi`, `Steve-hi`, `Steve >> hi` style formats no longer misclassify as system text
- Newly recognized formats: legacy § color codes (`§6Steve§r: hi`), name-suffix titles (`Steve[LV.10]: hi` / `Steve[AFK]: hi` / `Steve(VIP): hi`), bare short / Chinese short names with a colon (`小明: 你好`, cracked servers)
- Server-side @ extraction now supports non-ASCII names (Chinese-named players on cracked servers previously got no cross-client @ notifications)
- The four copy-pasted separator loops are unified into `MessagePresentation.skipSeparators` (§ pairs and whole bracket pairs skipped)

*Identity detection (same-name players on cracked servers)*
- All "sent by self" detection is now UUID-first with name fallback: fixes same-named players' messages rendering as your own bubbles, @/whisper notifications going missing, and echoes being mis-swallowed
- Echo name matching changed from substring contains to whole-word boundary: `SteveAdmin` and `[VIP]`-prefixed players are no longer swallowed as your own echoes
- Echo suppression and attribution notes now expire, fixing stale-state message swallowing

*Whisper*
- Fixed public chat containing words like 私聊/whisper being claimed as a whisper on NCR servers (the keyword must now come before the first colon)
- Fixed whisper content with multiple colons (quoted text) truncating at the last colon
- Fixed up-arrow history leaking the behind-the-scenes `/msg` splice (now records what you actually typed)
- Fixed the whisper sound switch in settings having no effect (it was miswired to the @ sound switch)

*Chat history*
- Fixed saved history loading above early messages like MOTD on join (reversed chronology)
- Fixed a server-side pending quote blocked by a plugin tagging a later message with the stale quote (10s expiry)

*Notifications & UI*
- New advanced-tab switches "Self-@ Notification" and "Self-Quote Notification" (off by default, testing aid)
- Emoji/kaomoji now insert at the cursor (previously appended at the end)
- Chat search now matches sender names too
- Fixed left-clicking your own avatar not inserting @ (the hit region was the mirror of the rendered position)
- Fixed the sidebar scrolling past into blank space when the player list shrinks (Forge picked up NeoForge's clamp)
- Fixed the sidebar icon crash protection being a no-op (the catch just repeated the same failing call)
- Player cache LRU-capped at 512; NeoForge tell-click name range synced with Forge (long-title servers)

*Tests*
- 62 → 92 cases: tautological sidebar tests removed; real coverage added for format parsing, word boundaries, whisper formats and Animation

**Settings UI rebuild & HUD unread dot**
- Settings regrouped into 5 UI-element tabs (Chat Screen / HUD / Notifications / Sidebar / Advanced), each with sub-categories
- The left column is now a collapsible, scrollable sub-category tree (a fully expanded tree no longer overflows the screen); right-side section headers drop the black bar / orange text for a gray left-aligned label with a trailing hairline, row height matched to options
- Color rows gain an inline preset palette; the Bubbles & Text sub-category gains a bubble preview band that follows the corner radius live
- Toggles reverted to vanilla on/off and the selection bar to white per feedback; numeric options keep type-in boxes, only the sound volume uses a slider
- Edit model: snapshot on open, live edits, Save / Exit with a changed-count and an ESC confirm-discard prompt
- New master sound volume wired to all four notification chimes; panel opacity default 60 → 80
- Tooltip copy unified into a verb-led plain style with ranges on numeric options; orphan keys dropped and the whisper-sound wording fixed
- Search box and restore-defaults button removed per feedback
- Category tree and option list gain always-on scrollbars with eased smooth scrolling: drag the thumb or click the track to page
- HUD unread indicator replaced with the sidebar's bouncing red dot: the red core is cropped and nearest-upscaled, perched on the chat icon's top-right corner

## v2.1.9

**Quark 兼容（仅物品分享）**
- 分享物品图标：聊天栏分享物品时，消息旁渲染物品图标（通过 HoverEvent.SHOW_ITEM 检测，任何使用标准物品分享机制的 mod 均适用）
- 表情按钮：暂不支持。Quark 表情按钮通过 Mixin 注入 `ChatScreen`，E33Chat 因类加载器冲突（log4j `MessageSupplier` LinkageError）无法继承 `ChatScreen`，表情按钮需额外方案

**Crash 修复**
- ModernUI 兼容：`renderLineWithClicks` 字符索引越界，ModernUI 文本引擎访问的字符索引超出样式列表长度，增加边界检查解决
- ChatScreen 继承回退：`extends ChatScreen` 在 Quark 等 mod 触发事件监听器时引发 log4j `MessageSupplier` 类加载器冲突（LinkageError），回退为 `extends Screen`

**Mod 描述编码**
- `gradle.properties` 添加 `-Dfile.encoding=UTF-8`，修复 mod 描述中文乱码

**Quark compatibility (item sharing only)**
- Item sharing icons: shared items in chat now render with item icons next to messages (via HoverEvent.Action.SHOW_ITEM detection, compatible with any mod using the standard item-sharing pattern)
- Emote buttons: not supported. Quark's emote buttons are injected via Mixin targeting `ChatScreen`; E33Chat cannot extend `ChatScreen` due to a log4j classloader conflict (`MessageSupplier` LinkageError). Emote buttons require a separate integration approach.

**Crash fixes**
- ModernUI compat: added bounds check in `renderLineWithClicks` — ModernUI's text engine visits more character indices than the styles list, causing ArrayIndexOutOfBoundsException
- Reverted `extends ChatScreen`: triggered log4j `MessageSupplier` classloader conflict (LinkageError) when Quark fires mod event listeners; reverted to `extends Screen`

**Mod description encoding**
- Added `-Dfile.encoding=UTF-8` to `gradle.properties` to fix Chinese character encoding in mod description

## v2.1.8

**通知横幅重设计**
- SDF 圆角渲染：横幅从平直矩形改为圆角（可配置半径 0-10），带投影
- 类型前缀：@提及显示 [@]、引用回复显示 [回复]、私聊显示 [私聊]，bake 进 `labeledName` 统一排版
- easeOutBack 过冲动画：横幅滑入带弹性过冲效果，视觉更活泼
- 移除左边色条，头像位置微调

**配置界面重组**
- 主题按钮改用 `Component.translatable()` 加载翻译 key
- 预览行数从循环按钮改为 3-10 数字输入框
- 通知标签页拆分为"@与引用"和"私聊"两个子区域，添加 `banner_corner_radius` 配置
- 渲染循环增加区域分隔线

**ClickEvent 修复**
- issue #9：`renderLineWithClicks` 中父级 ClickEvent 不会自动传播到子级字符样式，增加 fallback 合并逻辑——无 ClickEvent 的 span 从父级继承

**私聊点击放宽**
- tell-click 玩家名长度限制从 32 放宽为 `max(32, text.length()/3)`，适应长昵称

**Notification banner redesign**
- SDF rounded corners via `RoundRectRenderer.fill()` with custom GLSL shader, configurable radius (0-10), plus drop shadow
- Type prefixes baked into `labeledName`: [@] for mention, [回复] for quote reply, [私聊] for whisper
- easeOutBack overshoot animation: `1 + c*(t-1)³ + c*(t-1)²` with c=1.70158
- Removed left color bar, adjusted avatar position

**Config screen reorganization**
- Theme button uses `Component.translatable()` for proper localization
- `preview_lines` changed from cycle button to 3-10 integer input field
- Notifications tab split into "@与引用" and "私聊" sub-sections, added `banner_corner_radius`
- Section dividers in render loop

**ClickEvent fix**
- Issue #9 (Lucid Advancements compat): fallback ClickEvent from parent component now merges into child spans that lack their own ClickEvent

**Tell-click limit relaxed**
- Player name length limit for tell-click detection relaxed from 32 to `max(32, text.length()/3)` for long nickname servers

## v2.1.7

**架构拆分：ChatBubbleScreen 2125→1600 行**
- 提取 `ChatScrollbar`：滚动条渲染、拖拽、alpha 淡入淡出
- 提取 `ChatContextMenus`：右键菜单（复制/引用/TP/私聊）
- 提取 `ChatBars`：标题栏 + 底部栏渲染
- 提取 `ChatSidebar`：侧边栏渲染、搜索框、玩家列表滚动、动画状态
- 提取 `ChatMessageRenderer`：消息泡泡渲染、换行、时间分隔符、可点击文本

**测试基础设施**
- 新增 `ChatScrollbarTest`（13 个）：thumb 高度/位置、alpha 状态、hover 判定
- 新增 `ChatMessageRendererTest`（10 个）：timeKey 取整、findClickStyle 遍历
- 新增 `ChatContextMenusTest`（7 个）：菜单位置 clamp、点击判定
- debugLog 改为 Supplier 延迟求值

**Architecture split: ChatBubbleScreen 2125→1600 lines**
- Extracted `ChatScrollbar`: scrollbar rendering, drag, alpha fade
- Extracted `ChatContextMenus`: right-click menus (copy/quote/TP/whisper)
- Extracted `ChatBars`: title bar + bottom bar rendering
- Extracted `ChatSidebar`: sidebar rendering, search box, player list scroll, animation state
- Extracted `ChatMessageRenderer`: bubble rendering, word wrap, time separators, clickable text

**Test infrastructure**
- Added `ChatScrollbarTest` (13 tests): thumb height/position, alpha states, hover detection
- Added `ChatMessageRendererTest` (10 tests): timeKey rounding, findClickStyle traversal
- Added `ChatContextMenusTest` (7 tests): menu position clamping, click detection
- debugLog refactored to Supplier-based lazy evaluation

## v2.1.6

**通知横幅修复**
- 空玩家名崩溃：服务端插件发送的广播消息（无 sender UUID）不再触发 NPE
- isOwn 颜色编码：`isOwn` 判定中的 color code 格式消息正确关联到发送者
- 横幅双重 tick：修复 `tick()` 内 banner 计时被调用两次导致加速消失的问题

**通知横幅重构**
- 提取 `MentionNotificationController` 为独立控制器，队列管理与渲染解耦
- 提取 `MentionNotificationBanner` 为独立横幅组件

**Banner crash fix**
- Empty player name crash: broadcast messages from server plugins (no sender UUID) no longer trigger NPE
- isOwn color code bug: color-code formatted messages now correctly associate with sender for `isOwn` detection
- Banner double-tick: fixed banner timer being ticked twice per frame causing accelerated dismissal

**Banner refactor**
- Extracted `MentionNotificationController` as standalone controller, decoupling queue management from rendering
- Extracted `MentionNotificationBanner` as standalone banner component

## v2.1.5

**@Mention 通知系统**
- 手机同款通知横幅：被 @ 时从屏幕顶部滑入，4 秒后自动消失，多条排队依次显示
- @ 高亮：聊天泡泡中 @你 的消息文本变色（默认金黄 #FFD700）
- 场景区分：聊天打开时仅高亮+音效，关闭时横幅+音效
- 词边界检测：@PlayerName 精确匹配，不误触发 @PlayerName123
- 配置项：横幅开关/颜色/时长、高亮开关/颜色、音效开关、@ 前缀要求

**@Mention notification system**
- Phone-style notification banner: slides in from top of screen when mentioned, auto-dismisses after 4s, multiple mentions queued
- @highlight in chat bubbles: mentioned messages get distinct text color (default gold #FFD700)
- Context-aware: highlight+sound only when chat is open, banner+sound when closed
- Word-boundary detection: @PlayerName matches exactly, no false positives on @PlayerName123
- Configurable: banner toggle/color/duration, highlight toggle/color, sound toggle, @req toggle

## v2.1.4

**NCR 插件改装广播误识别修复**
- player line parser 增加聊天分隔符检查：要求玩家名和内容之间至少有一个聊天分隔符（`:`、`：`、`>`、`»`），纯空格分隔视为广播而非聊天
- 修复 `[+] PlayerName 加入了游戏` 等插件改装加入消息被误渲染为聊天气泡的问题（离开消息因翻译键存活不受影响）
- 纵深防御层序优化：tell-click（结构级，读 clickEvent）提至 player line parser（文本级，正则匹配）之前，确定性强的防线先行，覆盖 `handleSystemMessage` 和 `handleDisguisedChatMessage` 两条路径

**NCR 私聊多道防线**
- `handleDisguisedChatMessage` 增加关键词私聊检测兜底：chat type 被 NCR 剥离后，通过 whisper 关键词 + 在线/离线玩家名扫描识别私聊，覆盖 `detectWhisperInSystemMessage` 已有的离线缓存能力

**NCR plugin broadcast misclassification fix**
- Player line parser now requires at least one chat-specific separator (`:`, `：`, `>`, `»`) between player name and content; whitespace-only gaps are treated as broadcasts
- Fixes plugin-modified join messages like `[+] PlayerName joined the game` being rendered as chat bubbles
- Defense layer reorder: tell-click (structural, reads clickEvent) now runs before player line parser (text-level heuristic) in both `handleSystemMessage` and `handleDisguisedChatMessage`

**NCR whisper multi-layer defense**
- Added keyword-based whisper fallback in `handleDisguisedChatMessage` for servers that strip chat type, reusing existing `detectWhisperInSystemMessage` with online + cached-offline player coverage

## v2.1.3

**私聊系统审计修复**
- 回波抑制不再基于时间盲吞：发出私聊后，检查系统消息中是否出现其他在线玩家名来决定抑制还是放行，避免误吞别人的私聊
- 排除私聊目标名：回波中目标玩家名不再导致误判
- 玩家名匹配改用 `nameCandidates`：昵称服 display name 变体也能正确识别
- 扩展私聊内容分隔符：支持 `->`, `>>`, `»`, `|` 等插件服常见格式
- Whiper mode 含空格玩家名修复：已知私聊对象时直接用，不靠 split 解析
- 新增 `/whisper` 命令支持
- debugLog 改为延迟拼接，日志关时不产生额外开销

**Whisper system audit & fixes**
- Echo suppression no longer uses blind time window — incoming system messages are checked for other online player names before suppressing, preventing accidental swallow of incoming whispers
- Whisper target name excluded from echo check
- Player name matching upgraded to `nameCandidates` for nickname-server display name variants
- Extended whisper content separators: `->`, `>>`, `»`, `|` for plugin-formatted PMs
- Whisper mode fix for player names containing spaces
- Added `/whisper` command support
- debugLog refactored to Supplier-based lazy evaluation

## v2.1.2

**侧边栏玩家名黑名单**
- 新增设置项"侧边栏隐藏规则"：支持通配符模式（`*`），匹配的玩家名从侧边栏私聊列表隐藏，解决 TAB 等 NPC 填位插件创建假玩家（`Islot_*`）干扰私聊列表的问题
- 逗号分隔多规则，空默认不隐藏

**配置标题与 mod 描述修正**
- 配置界面标题从 "ChatBubble" 更正为 "E33Chat"
- mod 描述改为从语言文件加载，支持多语言（中文：以聊天APP风格重铸原版聊天框 / 英文：Rebuilds the vanilla chat HUD in chat-app style）

**Sidebar player name blacklist**
- New config option "Sidebar Hide Patterns": wildcard patterns (`*`) to hide matching player names from the sidebar whisper list, fixes fake NPC players (e.g. `Islot_*`) from TAB and similar tab-filler plugins cluttering the whisper list
- Comma-separated patterns, empty by default (no hiding)

**Config title & mod description fixes**
- Config screen title corrected from "ChatBubble" to "E33Chat"
- Mod description now loaded from lang files for proper localization (zh: 以聊天APP风格重铸原版聊天框 / en: Rebuilds the vanilla chat HUD in chat-app style)

## v2.1.1

**离线玩家消息识别 + 格式解析鲁棒性**
- 新增"见过玩家"缓存：所有实时识别/聊天历史的玩家自动记录（UUID + profile 名 + 显示名），在线名单查不到时用缓存回退。掉线/隐身/Tab 截断的玩家消息现在也能正确识别出气泡和真 UUID
- 格式解析器放宽阈值：超长称号前缀（`[超级至尊VIP]Steve`）不再因长度被拒；短名（1-2 字符）在 `<a>` 角括号或 `[T]a:` 方括号+冒号结构下可靠识别，裸短名（无括号）仍保守拒绝
- 不碰广播判定（join/death/advancement 仍是系统消息）、不碰去重层（自己回声消除不受影响）

**Offline player message recognition + parser robustness**
- New "seen player" cache: every player seen in real-time chat and chat history is automatically recorded (UUID + profile name + display name). When the online player list misses (offline/vanished/Tab-truncated), the cache provides fallback identification — offline player messages now render as bubbles with real UUIDs
- Format parser thresholds relaxed: long decorative title prefixes (`[SuperVIP]Steve`) no longer block recognition by length; short names (1-2 chars) are now reliably detected when wrapped in angle brackets (`<a>`) or bracket+colon structure (`[T]a:`). Bare short names without structure remain conservatively rejected
- Broadcast classification untouched (join/death/advancement stay as system messages); dedup layer untouched (self-echo elimination unaffected)

## v2.1.0

**消息预览 / 强提示 重做**
- 预览与强提示**保留带样式文本的颜色**：玩家消息、系统消息、mod 内置文本、昵称、称号前缀等，进预览/强提示时与聊天气泡里看到的颜色一致（不再被碾成白字）
- 预览**每行独立淡出**：最老的消息先消失、新的后消失（不再全部一起消失）；开框时预览隐藏但计时继续
- 强提示弹窗**开聊天框时也显示**（联机时别人触发的系统 / 被@ 消息，开着框也会盖在框上弹出）

**出站彩色文本（`&` 颜色码）改为安全实现**
- 不再把 `&` 转成 `§` 发送（那是被踢的根因，连单人原版服都踢）；改为**只在自己气泡本地上色**，原文（含 `&`）原样发出，**永不踢人**
- 装了颜色插件的服会把 `&` 转色给所有人；没插件的服别人看到原样 `&`
- `彩色文本 (& 颜色码)` 配置语义改为"本地解释"，**默认关闭**（避免 `B&B` / `Q&A` 这类正常 `&` 被本地上色）

**New**
- **Message preview / strong-hint rework**
- Preview and strong hint now **preserve styled text colors**: player messages, system messages, mod-built text, nicknames and title prefixes keep the same per-segment colors as in the chat bubbles (no longer flattened to white)
- Preview lines **fade independently**: the oldest line disappears first, newer ones later (no longer all vanishing at once); the preview is hidden while chat is open but keeps counting down
- The strong-hint popup **also shows while the chat screen is open** (online, a system / @mention triggered by others pops over the open screen)
- **Outgoing colored text (`&` codes) reimplemented safely**: no longer converts `&` to `§` on send (the kick cause — kicks even on vanilla singleplayer); instead it **colors only your own bubble locally** and sends the raw text (with `&`) unchanged, so it **never kicks**. Servers with a color plugin translate `&` for everyone; plain servers show the literal `&` to others. The "Color Codes (& codes)" option now means "interpret locally" and is **off by default** (so normal `&` in text like `B&B` / `Q&A` isn't colored locally)

## v2.0.9

**修复**
- 修复消息里的换行符被渲染成 "LF" 方块：聊天列表（气泡 + 灰字系统行）现在把 `\n` 渲染成**真正的换行**，多行公告正常多行显示，并保留每段样式与点击/悬浮事件
- 服务器用纯换行刷屏清屏的消息直接丢弃（不再产生空气泡 / 预览 / 提示）
- 修复"消息预览"逻辑，对齐原版聊天框：计时**锚定最后一条消息**、每个 tick 都扣（开框也扣、不冻结），5 秒末衰减淡出；关框时若距最后一条仍在 5 秒内就显示剩余时间、到点消失，早就过期则关框也不闪。与强提示**互斥**：默认开强提示时，系统/被@消息只弹强提示、不续预览；自己发送会续预览。预览单行、含自己与私聊
- 所有单行场合（预览 / 强提示 / 侧栏最近消息 / 引用横幅）把 `\n` 转空格，杜绝 LF 方块

**Fix**
- Fixed message newlines rendering as "LF" boxes: the chat list (bubbles + gray system lines) now renders `\n` as **real line breaks**, so multi-line announcements show on multiple lines, preserving each run's style and click/hover events
- Server chat-clear messages made of nothing but newlines are now dropped entirely (no empty bubble / preview / hint)
- Reworked the message preview to match the vanilla chat log: the countdown is **anchored to the last message** and ticks every game tick (also while chat is open — not frozen), fading out at the end; closing chat shows the remaining time only if the last message was within 5s, otherwise nothing flashes. **Mutual exclusion** with the strong hint: with the strong hint on (default), system / @mention messages only pop the strong hint and don't extend the preview; your own messages do extend it. The preview is single-line and includes own + whisper messages
- All single-line contexts (preview / strong hint / sidebar recent message / reply banner) flatten `\n` to a space so they never draw LF boxes

## v2.0.8

**修复**
- 头像加载：正版玩家自己的头像和在线玩家头像之前长时间卡在 Steve/Alex（身体皮肤却正常）。原因是首次查询拿到的默认皮肤被缓存后再也不刷新——`PlayerInfo.getSkinLocation()` 首次返回默认皮肤并异步下载，完成后才原地更新，但我们把首次的默认值缓存死了。现在在线玩家每帧读最新皮肤，下载完成后头像即跟上（CSL 皮肤同样走这条路）。离线/崩溃端玩家本就没有皮肤可取，仍为默认（属预期，除非 CSL 本地皮肤）
- 菜单里「搜索」项的图标之前写死成齿轮（settings），是早期 search 图标没画好时的占位，从没改回来。现在正确显示 search 图标

**新增**
- 配置项「彩色文本 (& 颜色码)」（默认关闭）：控制发送时是否把 `&c`/`&l` 等转成 § 富文本。默认关闭，因为很多服务器会拒绝 § 字符并踢人——2.0.5 的转换由此改为可选，需要时在「行为」分类开启

**Fix**
- Head loading: a paid-account player's own head and other online players' heads used to stay stuck on Steve/Alex for a long time even though the body skin was correct. Cause: the first lookup returned the default skin, which got cached and never refreshed — `PlayerInfo.getSkinLocation()` returns the default and downloads asynchronously, only updating in place when done, but we cached that initial default. Online players' skins are now read fresh each frame, so the head catches up once the download finishes (CSL skins flow through the same path). Offline/cracked players have no skin to fetch and stay on the default (expected, unless CSL provides a local skin)
- The "Search" item in the menu had its icon hardcoded to the gear (settings) — a leftover placeholder from when the search icon hadn't been drawn yet, never changed back. It now shows the search icon correctly

**New**
- "Color Codes (& codes)" config option (default off): controls whether `&c`/`&l` etc. are converted to § rich text on send. Off by default because many servers reject the § character and kick — the 2.0.5 conversion is now opt-in; enable it under the "Behavior" category when needed

## v2.0.7

**新增**
- 服务端配置 `use_tpa`（`e33chat-server.toml`，默认 false）：开启后右键玩家头像菜单的传送改用 `/tpa`（请求式）而非 `/tp`。设置进服时同步给客户端，菜单标签随之显示"请求传送"；未收到同步（单人/服务器没装 mod）回退 `/tp`

**New**
- Server config `use_tpa` (`e33chat-server.toml`, default false): when enabled, the player-head menu teleports via `/tpa` (request) instead of `/tp`. The setting is synced to clients on join and the menu label switches to "Request TP"; without a sync (singleplayer / server without the mod) it falls back to `/tp`

## v2.0.6

**优化**
- 配置界面排版重排：按功能聚合、开关在前细节在后。音效并入「通知与音效」分类，「兼容」分类撤销（仅剩的 debug_log 移入新「高级」分类）

**Polish**
- Config screen reordered: options grouped by function with toggles before their detail params. Sound merged into "Notifications & Sound"; the "Compat" category is retired (its only remaining item, debug_log, moves to a new "Advanced" category)

## v2.0.5

**新增**
- 发送消息支持 `&` 颜色/格式码：输入 `&c`、`&l`、`&o` 等（同 § 码表，0-9a-fk-or）发送时转成富文本。仅 `&` 后跟有效码字符才转换，单独的 `&`（如 `tom & jerry`）不受影响。注意：是否生效取决于服务器——很多服会剥掉 § 码或要求权限，Essentials 类插件可能自行转换 `&`

**New**
- Outgoing messages now support `&` color/format codes: typing `&c`, `&l`, `&o`, etc. (same code set as §, 0-9a-fk-or) is translated to rich text on send. Only `&` followed by a valid code char is converted; a bare `&` (e.g. `tom & jerry`) is left alone. Note: whether it takes effect is up to the server — many strip § codes or require permission, and Essentials-style plugins may convert `&` themselves

## v2.0.4

**新增**
- 配置项"保留已输入文本"：控制关闭聊天框时是否保留输入框里未发送的文本（默认开启，与之前行为一致）

**New**
- "Preserve Typed Text" config option: controls whether unsent text in the input box is kept when chat closes (default on, matching prior behavior)

## v2.0.3

**修复**
- 恢复进度完成消息的悬浮描述：原版进度名带 HoverEvent（悬停显示进度详情），mod 之前只追踪带点击事件的文本段，进度名被漏掉导致悬浮窗失效。现在带悬浮事件的文本段也被追踪

**Fix**
- Restored hover descriptions on advancement messages: vanilla advancement names carry a HoverEvent (tooltip with advancement details), but the mod only tracked text segments with click events, so advancement names were skipped and the tooltip broke. Segments with hover events are now tracked too

## v2.0.2

**修复**
- 头像皮肤渲染兼容 CustomSkinLoader：离线玩家/NCR 纯文本玩家不再固定回退 Steve/Alex。未知玩家改走原版 SkinManager 按名字查询（CSL 按名字接管，导入的离线皮肤可正确显示；未装 CSL 时回退原版——正版玩家正常、离线回退默认）
- 头像皮肤按 UUID 缓存，不再每帧重复查询 SkinManager

**Fix**
- Head skin rendering now compatible with CustomSkinLoader: offline players / NCR plain-text players no longer always fall back to Steve/Alex. Unknown players are resolved through the vanilla SkinManager keyed by name (CSL intercepts by name, so imported offline skins display correctly; without CSL it falls back to vanilla — real skins for paid accounts, default otherwise)
- Head skins cached per UUID instead of re-querying the SkinManager every frame

## v2.0.1

**NCR 兼容（常态生效）**
- 移除"禁用聊天举报兼容"配置开关，玩家识别默认启用（大部分服务器只装 NCR 但不了解这个开关，导致玩家消息全部渲染成系统灰字）
- 提取 MessagePresentation 格式解析器（纯函数 + 单元测试）：按在线玩家名锚点 + 通用分隔符识别，支持 `Steve: hi`、`<Steve> hi`、`Steve >> hi`、`[VIP]Steve: hi`、`<[VIP]Steve> hi`、全角冒号 `Steve： 你好`、`Steve » hi` 等格式，名字按长度降序匹配避免前缀名误抢
- 分层识别：私聊检测前置（修复私聊被误判成公屏气泡）→ 格式解析 → tell-click 归属 → 系统消息兜底
- disguised 通道：发送者名为空时也尝试按格式解析，救回该类玩家消息

**修复**
- 输入框开关面板动画期间不跟随面板移动

**NCR compat (always active)**
- Removed the "No Chat Reports compat" config toggle; player detection is now always on (most servers run NCR without users knowing about the toggle, leaving every player message rendered as gray system text)
- Extracted MessagePresentation format parser (pure function + unit tests): anchors on online player names + generic separator detection; handles `Steve: hi`, `<Steve> hi`, `Steve >> hi`, `[VIP]Steve: hi`, `<[VIP]Steve> hi`, full-width colon `Steve： 你好`, `Steve » hi`, etc. Names matched longest-first to prevent prefix-name misattribution
- Layered detection: whisper check first (fixes whispers misclassified as public bubbles) → format parse → tell-click attribution → system fallback
- Disguised channel: when the sender name is empty, still tries format parsing to recover those player messages

**Fix**
- Input box now follows the panel during the open/close animation

## v2.0.0

**更名**
- 显示名从 E33EPUS's ChatScreen 改为 **E33Chat**

**Rename**
- Display name changed from E33EPUS's ChatScreen to **E33Chat**

**新增**
- 服务端聊天历史分发：新玩家进服自动同步最近 50 条聊天记录

**修复**
- 关闭动画配置后侧边栏不再有动画，打开/关闭均为即时切换

**New**
- Server-side chat history: new players receive the last 50 messages on join

**Fix**
- Sidebar no longer animates when animation config is disabled

## v1.9.9

**重构**
- ChatBubbleScreen 拆分为 5 个类：ChatEmojiPanel（表情）、ChatQuickChatPanel（常用语）、ChatSettingsMenu（设置菜单）、ChatSearchPanel（搜索）、ChatBubbleScreen（编排层），从 2220 行瘦至 1945 行
- drawTextureIcon / iconTex / BAR_H 改为包内可见，面板类直接引用
- 修复 F3+T 资源重载后图标纹理丢失导致渲染崩溃的问题

**Refactor**
- Split ChatBubbleScreen into 5 classes: ChatEmojiPanel (emoji picker), ChatQuickChatPanel (quick phrases), ChatSettingsMenu (gear menu), ChatSearchPanel (search bar), ChatBubbleScreen (orchestrator); reduced from 2220 to 1945 lines
- drawTextureIcon / iconTex / BAR_H relaxed to package-private for panel access
- Fixed icon texture crash after F3+T resource reload (missing try-catch in drawTextureIcon)

## v1.9.8

**新增**
- 聊天搜索：浮动输入框，实时子串匹配，上下箭头/滚轮切换匹配项，黄色高亮边框，计数器显示
- 设置菜单重铸：从 3 列横排改为 4 行竖排上拉，图标居左文字居右，英文字段自适应截断

**修复**
- 多条系统消息（tellraw）同时到达时强提示不再互相覆盖，改为排队依次显示

**New**
- Chat search: floating input above bottom bar, real-time substring matching, up/down/scroll to cycle matches, yellow highlight border, match counter
- Settings menu redesigned: vertical 4-row popup (was horizontal 3-col), icons left + text right, auto-truncate long English labels

**Fix**
- Multiple simultaneous system messages (tellraw) no longer overwrite each other's strong hint; now queued and displayed in sequence

## v1.9.7

**音效**
- 新增"音效"配置分类，四种消息类型可独立开关提示音：系统消息、@/引用消息、私聊消息、公屏消息
- 默认 @/引用消息和私聊消息触发提示音，系统消息和公屏消息不触发
- 防刷屏选项默认关闭

**Sound**
- New "Sound" config category with independent notification sound toggles for 4 message types: system, @/quote, whisper, public
- Default: @/quote and whisper trigger sounds, system and public do not
- Anti-spam now defaults to off

## v1.9.6

**动画**
- 聊天面板打开：背景不透明度渐入（easeOutCubic），关闭：淡出（easeInQuad）
- 滚屏系统重构：壁钟驱动 easeOutCubic 丝滑动画，滚轮 40px/120ms，拖拽滑动块 80ms，底部自动滚屏 150ms
- 滑动块自动浮现：滚动时显示，停止滚动 1 秒后淡出；悬停/拖拽时常驻
- 新消息到达时列表底部丝滑滚屏，不再瞬移；首次打开直接跳底，无回弹动画

**Animation**
- Panel open: background fades in (easeOutCubic), close: fades out (easeInQuad)
- Scroll system rebuilt: wall-clock-driven easeOutCubic animations — wheel 40px/120ms, drag 80ms, auto-scroll 150ms
- Scrollbar auto-appear: visible while scrolling, fades out 1s after stop; always visible on hover/drag
- Smooth auto-scroll when new messages arrive; instant jump-to-bottom on first open, no bounce

## v1.9.5

**重构**
- 新增 `Animation` 工具类：统一 easeOutCubic/lerpTo/fadeIn/fadeOut/fadeInOut 动画函数
- 新增 `UiLayout` 工具类：统一 centerX/clampX/clampW 布局计算
- ChatBubbleScreen 和 ChatBubbleHudOverlay 动画计算统一使用 Animation 方法

**Refactor**
- Added `Animation` utility class: unified easeOutCubic/lerpTo/fadeIn/fadeOut/fadeInOut animation functions
- Added `UiLayout` utility class: unified centerX/clampX/clampW layout helpers
- ChatBubbleScreen and ChatBubbleHudOverlay animation math now uses Animation methods

## v1.9.4

**优化**
- renderMessages 消息列表遍历从三趟合并为两趟，减少重复迭代
- 替换 indexOf 全列表查找为双指针扫描，消息索引查找从 O(v*m) 降为 O(m)
- HUD 消息预览逐行独立淡出，旧行不再突然消失
- 引用块改为微信风格：置于气泡下方、宽度独立跟随文本、单行省略号
- 去掉聊天面板打开时的游戏背景变暗效果

**Optimizations**
- Merged renderMessages height-calculation passes from two loops into one
- Replaced per-message indexOf full-list scan with two-pointer tracking (O(v*m) → O(m))
- HUD message preview now fades each line independently; old lines fade out instead of vanishing
- Quote block redesigned to WeChat style: below bubble, independent width, single-line with ellipsis
- Removed dark background overlay when chat panel is open

## v1.9.3

**修复**
- 强提示弹窗（热键栏上方的系统消息推送）现在正确保留文本颜色，与聊天气泡/消息预览一致
- 面板宽度默认值提高到 1000 物理像素，最小值提高到 800（400 会挡住部分 UI）；面板宽度计算使用四舍五入避免 GUI 自动缩放下的像素偏差
- 侧边栏玩家头像与频道图标对齐，滚动上限精确计算不再可无限滚出空白
- 配置界面数字输入框自适应位数（面板宽度 4 位、预览宽度 3 位、圆角半径 2 位）

**其他**
- 多处 `printStackTrace` / `Exception ignored` 改为 `LogUtils.getLogger()` 统一日志输出
- NeoForge 1.21.1 同步上述全部改动；两版代码基线合并

***

**Fixes**
- Strong hint popups (system message pushes above the hotbar) now correctly preserve text colors, matching chat bubbles and message preview
- Panel width default raised to 1000 physical pixels, minimum raised to 800 (400 blocked parts of the UI); panel width calculation now rounds guiScale to avoid pixel drift under auto GUI scaling
- Sidebar player avatars now align with the public channel icon; scroll bound is computed accurately and no longer scrolls endlessly into blank space
- Config screen number inputs adapt their max length (4 digits for panel width, 3 for preview width, 2 for corner radius)

**Other**
- Replaced multiple `printStackTrace` / `Exception ignored` with `LogUtils.getLogger()` for unified logging
- NeoForge 1.21.1 synced with all the above; both codebases merged to parity

## v1.9.2

**修复**
- 起床按钮回来了：睡觉时显示原版样式的"起床"按钮（v1.1 屏蔽睡觉强制聊天框时的误伤，此后一直无法提前起床）；ESC 直接起床，按 T 仍可打开聊天
- 可点击文本的下划线在 ModernUI 等字体替换 mod 下不再过粗、错位、超出文本刺穿气泡边框——下划线改回由字体渲染器自绘（1.9.1 的手动补画在亚像素字宽下必然漂移），浮层防刺穿改为整体抬高浮层 z 层实现，顺带修复了删除线刮浮层的同类问题
- 可点击文本的点击判定区域在 ModernUI 下不再右偏（同一根因）

***

**Fixes**
- The Leave Bed button is back: sleeping now shows a vanilla-style Leave Bed button (collateral damage of the v1.1 forced-chat-screen fix — getting up early had been impossible since); ESC wakes you up, T still opens chat
- Underlines on clickable text no longer render too thick, misplaced, or overshooting past the bubble border under font-replacing mods (e.g. ModernUI) — underlines are drawn by the font renderer again (the 1.9.1 manual repainting inevitably drifts with sub-pixel advances); overlay bleed-through is now prevented by z-lifting overlays instead, which also fixes the same strikethrough issue
- Click hitboxes on clickable text are no longer shifted right under ModernUI (same root cause)

## v1.9.1

**修复**
- 自带下划线样式的消息（如 Xaero 路径点分享）不再出现双下划线，其下划线也不再刮破表情菜单等浮层——现在聊天列表中的所有下划线都由 mod 按绘制顺序自绘

***

**Fixes**
- Messages with intrinsic underline styling (e.g. Xaero waypoint shares) no longer show a double underline, and their underlines no longer bleed through overlay panels — all underlines in the chat list are now repainted by the mod in plain paint order

## v1.9

**新功能**
- 配置界面重做：左侧分类栏（外观/通知/行为/兼容），悬停选项名显示详细说明，调试日志开关加入界面
- 配置项支持第三方配置界面（如 Configured）的本地化显示

**修复**
- 三处输入框（主输入、常用语、侧边栏搜索）文字垂直居中，占位符与输入文字位置完全一致，聚焦不再跳位
- 可点击文本的下划线不再穿透浮层面板（此前打开表情菜单等浮层时会有一道"划痕"）
- 发送指令不再生成本地气泡——与原版一致，指令文本不进聊天记录（私聊指令除外）

**其他**
- 配置文件注释全部改为英文（生态惯例）；文件结构与键名不变，现有配置无缝保留

***

**New Features**
- Reworked config screen: category sidebar (Appearance / Notifications / Behavior / Compat), hover an option name for a detailed description, debug log toggle now in the GUI
- Config options now localize in third-party config UIs (e.g. Configured)

**Fixes**
- Text in all three input boxes (main input, quick chat, sidebar search) is now vertically centered; placeholder and typed text share the exact same position, no more jump on focus
- Underlines on clickable text no longer bleed through overlay panels (previously visible as a "scratch" across the emoji panel and other overlays)
- Sending a command no longer creates a local bubble — matching vanilla, command text stays out of the chat log (whisper commands excepted)

**Misc**
- Config file comments are now in English (ecosystem convention); file structure and keys unchanged, existing configs carry over seamlessly

## v1.8

**消息分类重构**
- 新增"翻译键"确定性分类层：私聊、公屏聊天、指令反馈、原版广播（进度/死亡/进出服）、OP 回显、/say、/me、队伍消息按原版翻译键精确路由，不再依赖文本内容猜测；键在 NCR/FreedomChat 转换后依然保留，因此转换服同样受益
- 未知格式自动回落到原有启发式识别（插件自定义格式不受影响）

**修复**
- /tp、/kill 等指令的 OP 回显（`[名字: ...]`）不再被误判为该玩家的聊天气泡
- 转换服上的私聊名字与内容直接取自消息结构（保留样式与颜色），且不再依赖客户端语言
- 你发出私聊后 10 秒内对方的回复不再可能被回显抑制误吞
- Xaero 地图路径点分享在聊天转换服务器上不再被渲染成气泡

***

**Message Classification Rework**
- New deterministic classification layer based on vanilla translation keys: whispers, public chat, command feedback, vanilla broadcasts (advancements / deaths / joins), op echoes, /say, /me, and team messages are routed by their exact translation keys instead of text guessing; keys survive NCR/FreedomChat conversion, so converted servers benefit equally
- Unknown formats fall back to the existing heuristics (custom plugin formats unaffected)

**Fixes**
- Op echoes of commands like /tp and /kill (`[Name: ...]`) are no longer misattributed as that player's chat bubble
- Whisper sender and content on converted servers are taken directly from the message structure (styles and colors preserved), independent of client language
- A partner's reply within 10 seconds of your outgoing whisper can no longer be swallowed by echo suppression
- Xaero waypoint shares are no longer rendered as bubbles on chat-converting servers

## v1.7

**新功能**
- 圆角气泡：SDF shader 实现，边缘逐像素抗锯齿，任意 GUI 缩放下均平滑
- 新配置项"气泡圆角半径"（0-10，默认 4，0 = 原来的方角）
- shader 加载失败时自动回退方角渲染，不影响使用
- 昵称类插件支持：消息归属额外尝试匹配 tab 列表显示名（覆盖"聊天名=tab名"的常见配置，未在真实昵称服实测）
- "点击私聊"事件归属：识别插件挂在名字上的 `/tell`/`/msg` 点击事件，从中拿到真实档案名——昵称服上的零猜测归属通道
- 名字匹配兼容 `§` 颜色码（提供原始/剥离双版本候选）
- 默认配色更新：自己的气泡 #1E90FF 蓝底白字（仅对新生成的配置生效）

**修复**
- 聊天历史保存加固：单条消息序列化失败自动降级为纯文本，不再可能因一条异常消息丢失整个历史

**其他**
- 消息处理调试日志改为配置开关 `debug_log`（默认关闭）——正式版不再把聊天内容写入 latest.log，排查问题时可在配置文件中开启

***

**New Features**
- Rounded bubble corners: SDF shader with per-pixel anti-aliased edges, smooth at any GUI scale
- New config option "Bubble Corner Radius" (0-10, default 4, 0 = classic square corners)
- Automatically falls back to square rendering if the shader fails to load
- Nickname plugin support: message attribution also tries tab-list display names (covers the common "chat name = tab name" setup; not yet field-tested on real nickname servers)
- "Click to whisper" attribution: reads the real profile name from `/tell`/`/msg` click events plugins attach to sender names — a zero-guess attribution channel on nickname servers
- Name matching tolerates `§` color codes (raw and stripped candidate variants)
- New default colors: own bubble #1E90FF with white text (applies to fresh configs only)

**Fixes**
- Chat history saving hardened: a message that fails to serialize degrades to plain text instead of aborting the save — one bad message can no longer wipe the whole history

**Misc**
- Message-pipeline debug logging is now gated behind the `debug_log` config option (off by default) — release builds no longer write chat content to latest.log; enable it in the config file when troubleshooting

## v1.6

**新功能**
- 服务器称号/前缀显示：插件添加的 `[称号]`/`[群组]` 等前缀现在带原色显示在玩家名旁，玩家消息与系统消息通道均支持，兼容 `[前缀]<名字>` 与 `<[前缀]名字>` 两种格式（提取失败自动回退裸名）
- 自己的称号自己也可见——服务器回显到达后自动补全到本地气泡
- 消息预览保留消息原有颜色与样式（称号颜色、mod 彩色文本等）

**修复**
- 回显记录改为 10 秒过期，且仅对聊天和 `/msg` `/tell` `/w` `/me` `/say` 记账——修复发送无回显指令后计数残留、误吞后续署名为自己的消息
- 私聊回显旗标同样 10 秒过期——修复自定义私聊格式服务器上残留旗标可能误吞后续收到的私聊
- 聊天历史现在保存带样式的发送者名（旧存档兼容读取）
- 玩家名过长截断时保留颜色
- 网络频道版本校验放宽（`acceptMissingOr`）——连接装了 Forge 但没装本 mod 的服务器不再可能被拒连，"服务端可选"更彻底

**其他**
- 身份判定（own/@提及/引用）与显示名解耦，装饰名不影响消息归属
- 日志前缀统一为 `[e33chat]`

***

**New Features**
- Server title/prefix display: plugin-added prefixes like `[Title]`/`[Group]` now show next to player names with their original colors, on both player and system message channels, supporting both `[Prefix]<Name>` and `<[Prefix]Name>` formats (falls back to bare name if extraction fails)
- Your own title is now visible to yourself — patched into the local bubble once the server echo arrives
- Message previews keep original colors and styles (title colors, mod-colored text, etc.)

**Fixes**
- Pending echoes now expire after 10s and are only tracked for chat and `/msg` `/tell` `/w` `/me` `/say` — fixes stale counters from no-echo commands swallowing later self-attributed messages
- Whisper echo flag also expires after 10s — fixes stale flag potentially swallowing incoming whispers on servers with custom whisper formats
- Chat history now saves styled sender names (old saves still load)
- Long player names keep their colors when truncated
- Relaxed network channel version check (`acceptMissingOr`) — joining Forge servers without this mod can no longer be rejected, making "server optional" truly hold

**Misc**
- Identity logic (own/@mention/quote) decoupled from display names — decorated names never affect message attribution
- Log prefix unified to `[e33chat]`

## v1.5

**新功能**
- 颜色主题切换：深色（默认）/ 浅色，配置界面一键切换
- 新增 `ChatBubbleTheme` 主题系统，所有 UI 颜色集中管理

***

**New Features**
- Color theme toggle: Dark (default) / Light, switchable in config screen
- New `ChatBubbleTheme` system: all UI colors managed in one place

## v1.4

**新功能**
- 侧边栏搜索框：按名字筛选在线玩家
- 右键头像菜单：传送 + 私聊快捷操作
- 侧边栏无在线玩家插画
- 私聊未读闪烁提示：侧边栏玩家列表紫色闪烁标记
- 侧边栏滑入/滑出动画（ease-out cubic）
- 公屏最新消息预览显示在侧边栏"世界频道"行
- 消息预览宽度可配置（`preview_width`，50-400px）

**修复**
- 私聊输入框不再穿帮——`/msg` 拼接完全在背后完成
- 私聊消息不再泄漏到公屏——系统回显三层拦截（标记→检测→吞除）
- 私聊回复不再复读——本地显示与服务端转发完全隔离
- 引用私聊消息不再错位——全量索引追踪，不受过滤视图影响
- NCR 兼容开关开启/关闭均可正确处理私聊

***

**New Features**
- Sidebar search box: filter online players by name
- Avatar right-click menu: Teleport + Whisper quick actions
- No online players illustration in sidebar
- Unread whisper blinking indicator: purple pulsing dot in sidebar player list
- Sidebar slide-in/out animation (ease-out cubic)
- Latest public message preview under "Public" entry in sidebar
- Configurable message preview width (`preview_width`, 50-400px)

**Fixes**
- Input box no longer exposes `/msg` — command splicing is fully behind-the-scenes
- Whisper messages no longer leak to public chat — three-layer system echo suppression
- Whisper replies no longer echo back — local display fully isolated from server forwarding
- Quoting whisper messages no longer mis-tracks — global index tracking unaffected by filtered views
- NCR compat on/off both handle whispers correctly

## v1.3

**新功能**
- 私聊侧边栏：左侧在线玩家列表，显示头像+名字+最新私聊预览，点击切换私聊模式
- 侧边栏收起/展开：标题栏左侧汉堡按钮，收起后聊天面板占满
- 私聊过滤：点击玩家只显示与该玩家的私聊记录，顶部紫色模式指示条；点击"公屏"返回
- 私聊发件隐形拼接：输入框不显示 `/msg`，发送时背后自动拼接，不露破绽

***

**New Features**
- Whisper sidebar: online player list on the left with avatar + name + latest whisper preview, click to switch to whisper mode
- Sidebar toggle: hamburger button at the top-left of the title bar, chat panel fills width when collapsed
- Whisper filtering: clicking a player shows only whisper messages with them, with a purple mode indicator bar; click "Public" to return
- Invisible whisper splicing: `/msg` is prepended behind the scenes on send, never shown in the input box

## v1.2

**修复**
- 修复标准服务器（未安装 No Chat Reports）开启 `chat_report_compat` 后，`[头衔] <玩家名>` 格式的服务器前缀/称号无法提取到发送者显示名的问题

**新功能**
- 消息区域右侧新增滚动条：显示当前位置、点击空白区域翻页、拖拽滑块滚动

***

**Fixes**
- Fixed server prefix/title extraction for standard servers (without No Chat Reports): when `chat_report_compat` is enabled, prefixes like `[VIP] <PlayerName>` in player chat messages are now correctly extracted to the sender display name

**New Features**
- Scrollbar on the right side of the message area: shows scroll position, click empty track to page up/down, drag thumb to scroll

## v1.1

**修复**
- 修复 Xaero 地图连续分享多个不同坐标后坐标丢失
- 修复 emoji 码点截断导致的宽度测量错误（代理对字符如 😀 使用 `Character.toChars`）
- 修复睡觉时聊天框无法关闭（阻止原版强制弹框，手动 T 键正常打开/ESC 关闭，醒来恢复原状态）
- 修复不同存档聊天记录互相覆盖/泄漏（文件名加 hash 防中文世界名碰撞 + 存/读条件修正）
- 修复醒来时 `setScreen` 跨线程崩溃（`PlayerWakeUpEvent` 服务端线程 → `mc.execute()`）

**新功能**
- Emoji 表情面板：双标签（😊 Emoji + ✧ 颜文字），点击插入输入框
- 时间分隔符间隔可配置（`time_separator_minutes`，1/5/10/15/30分钟/关闭，默认 5 分钟）

**UI**
- 底栏新增 emoji 按钮，设置图标左移，输入框空间优化
- @补全面板 70% 不透明度，表情面板完全不透明

***

**Fixes**
- Fixed Xaero map consecutive waypoint shares not displaying (removed aggressive `<>` dedup + changed `consumeEchoBySystemChat` from `contains` to `equals`)
- Fixed emoji code point truncation causing width measurement errors (surrogate pair characters like 😀 now use `Character.toChars`)
- Fixed chat screen unclosable during sleep (block vanilla forced open, T key works normally, ESC closes, restores state on wake)
- Fixed chat history leaking/wrongly overwriting between saves (file name now includes hash to prevent Chinese world name collision + save/load condition fix)
- Fixed cross-thread crash on wake (`PlayerWakeUpEvent` on server thread → `mc.execute()`)

**New Features**
- Emoji picker panel: two tabs (😊 Emoji + ✧ Kaomoji), click to insert into input
- Time separator interval configurable (`time_separator_minutes`, 1/5/10/15/30 min/off, default 5 min)

**UI**
- Bottom bar: emoji button added, gear icon shifted left, input box width optimized
- @mention popup 70% opacity, emoji panel fully opaque

## v1.0

**修复**
- 修复 `ChatComponent.addMessage` 双重触发导致的@/引用重复音效
- 引用提示音改为风铃声 (`NOTE_BLOCK_CHIME`)——和被@一致
- 修复聊天框打开时强提示/弹窗被隐藏
- 修复指令补全界面 X 坐标错位（不同 GUI 缩放下偏移不同）
- 修复引用预览竖线颜色不统一（统一白色）
- 修复 `isRecentDuplicate` 回显抑制回归（`CHAT_REPORT_COMPAT` 下发送消息被误吞）
- 修复配置界面"兼容性选项"标题滚动时偏移不同步

**UI 优化**
- 气泡宽度完全跟随文本（去掉最小宽度限制），横向内边距 8→6，纵向 5→4
- 头像位置对齐玩家名顶部
- 标题栏顶部缝隙消除，底栏高度 30→26，输入框高度 20→14，图标 16→14
- 标题编辑框尺寸贴合文本，不再错位
- 配置界面重构为数据驱动（增删配置项无需手改索引）
- 动画改为 ease-out 三次方缓出

**新功能**
- 输入 `@` 弹出在线玩家名补全列表（Tab/Enter 选中，Esc 关闭）
- 聊天记录按存档持久化（`chat_history` 配置，默认关闭）
- 关闭聊天框后保留已输入文本
- 预览行数上限 3→8
- 被@/引用播放风铃提示音

**配置**
- 新增 `chat_history`——保留每个存档的聊天记录
- `anti_spam`、`chat_history` 描述精简
- jar 命名格式改为 `e33chat-Forge-1.20.1-1.0`

***

**Fixes**
- Fixed double sound on @mention/quote caused by `ChatComponent.addMessage` dual trigger
- Quote notification now uses `NOTE_BLOCK_CHIME` (same wind chime as @mention)
- Fixed strong hints being hidden when chat screen is open
- Fixed command suggestion X offset (misaligned at different GUI scales)
- Fixed quote bar accent color inconsistency (always white now)
- Fixed `isRecentDuplicate` echo suppression regression (messages swallowed under `CHAT_REPORT_COMPAT`)
- Fixed config screen "Compatibility" header drifting on scroll

**UI**
- Bubble width follows text exactly (removed min width), padding X 8→6, Y 5→4
- Avatar aligned to player name top
- Title bar gap removed, bottom bar 30→26, input height 20→14, icons 16→14
- Title edit box sized to match text, no more misalignment
- Config screen refactored to data-driven entries
- Animation switched to ease-out cubic

**New Features**
- `@` autocomplete popup with online player names (Tab/Enter to select, Esc to close)
- Per-world chat history persistence (`chat_history` config, disabled by default)
- Input text preserved when closing/reopening chat
- Preview lines cap 3→8
- Wind chime sound on @mention/quote

**Config**
- Added `chat_history` — saves chat history per world
- Simplified `anti_spam` and `chat_history` labels
- Jar naming: `e33chat-Forge-1.20.1-1.0`

## v0.2.4-beta

增强 `chat_report_compat` 匹配——改为扫描在线玩家列表而非要求 `<` 开头，支持服务器前缀/称号（如 `【称号】 <PlayerName> 消息`）。匹配到的前缀保留到发送者显示名中。修复服务端兼容性——客户端可加入无 mod 服务端，双端安装时服务端也不再因客户端类加载崩溃（`displayTest="NONE"` + 客户端初始化抽离到 `@OnlyIn(Dist.CLIENT)` 类）。

Enhanced `chat_report_compat` matching — scans for online player names instead of requiring `<` at string start, supporting server prefixes/titles (e.g. `【Title】 <PlayerName> message`). Prefix text preserved in sender display name. Fixed server compatibility — client can now join servers without the mod, and server no longer crashes from client class loading when installed on both sides (`displayTest="NONE"` + client init moved to `@OnlyIn(Dist.CLIENT)` class).

## v0.2.3-beta

新增 `chat_report_compat` 配置项——开启后自动解析 `<玩家名>` 格式的系统消息，提取真实发送者和皮肤，兼容"禁用聊天举报"类模组。修复回显去重在 `system_chat_as_bubble=true` 时失效的问题。配置界面重组为「常规选项」和「兼容性选项」两个分组。

Added `chat_report_compat` config option — automatically parses `<PlayerName>` format system messages to extract real sender and skin, compatible with "No Chat Reports" type mods. Fixed echo dedup failing when `system_chat_as_bubble=true`. Config screen reorganized into two sections.

## v0.2.2-beta

新增 `anti_spam` 配置项——连续相同消息合并为一条，黄色 `xN` 标注重复次数。修复悬停 tooltip 不渲染（进度信息等 HoverEvent）。修复聊天界面内点击事件（RUN_COMMAND）不弹出 GUI 的问题。修复配置界面标签错位。

Added `anti_spam` config option — consecutive identical messages merge into one with yellow `xN` count label. Fixed hover tooltips not rendering (advancement info, etc.). Fixed click events (RUN_COMMAND) not opening GUI while chat screen is active. Fixed swapped config labels.

## v0.2.1-beta

新增配置项 `system_chat_as_bubble`（默认关闭）——开启后所有系统消息也渲染为聊天气泡。修复伪装聊天通道（`handleDisguisedChatMessage`）的去重失效问题，改用发送者身份匹配替代内容 hash 匹配；同时伪装聊天的 `isSystem` 改为根据 `bound.name()` 自动判断。

Added `system_chat_as_bubble` config option (off by default). Fixed echo deduplication for disguised chat channel — replaced content hash matching with sender identity matching. Disguised chat messages now auto-detect system vs player via `bound.name()`.

## v0.2.0-beta

重构了聊天消息拦截架构——不再 cancel 原版消息处理管线，而是改在 `ChatComponent.addMessage` 末端捕获所有 mod 处理后的最终消息，从根本上解决了与其他 mod（Xaero、FTB Team 等）的兼容性问题。系统消息的点击事件（如 FTB 邀请的"接受/拒绝"按钮）现在完整保留；Xaero 路径点分享正确显示为系统消息，点击事件正常；局域网开放提示不再丢失；重写去重机制，用待消费回显队列替代内容扫描，自己连续发重复消息不会被误吞。

Rebuilt the chat interception architecture — instead of cancelling the vanilla message pipeline, messages are now captured from `ChatComponent.addMessage` after all mods have processed them, resolving compatibility issues with other mods (Xaero, FTB Teams, etc.) at the root. Click events on system messages (e.g. FTB invite accept/decline buttons) are fully preserved. Xaero waypoint sharing displays correctly as a system message with working click actions. LAN "Open to LAN" notifications no longer lost. Deduplication rewritten with a pending-echo queue instead of content scanning — sending the same message twice in a row no longer swallows the second one.

## v0.1.8-beta

- **Notification bar** — when scrolled up in chat, a bar appears above the input showing "x new messages" on the left and "You were mentioned" on the right (yellow text, click to jump).
- **@mention strong hint** — getting @mentioned or quoted now triggers a strong hint popup above the hotbar ("You were mentioned" in yellow), configurable via new `mention_strong_hint` option.
- **@mention via avatar** — left-click any player's avatar (including your own) in chat to insert `@playername` into the input.
- **Network layer** — quote and @mention metadata are now synced between players via packets (`ChatMetaPacket`, `QuoteSyncPacket`). The mod now requires **both client and server** installation.
- **World detection refactor** — per-tick world tracking moved to `ChatBubbleClientListener`, fixing a race where mod system messages (e.g. CustomNPCs update notifications) were cleared before the chat screen first opened.
- **Fixed** — sending a message while at the bottom of chat no longer incorrectly triggers the notification bar.
- **Removed** — deprecated gold @mention border config options.

## v0.1.7-beta

- Fix mod system messages being wiped from chat on first open.

## v0.1.4-beta

- Fix world switch message leak.
