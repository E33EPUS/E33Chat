# Release Notes

发版约定：每个版本一段（## vX.Y.Z），中文摘要在前、英文摘要放段尾，块间空行分隔；
用「新增 / 修复 / 更改」等常规分类组织，写法自由，不要拿语言名当标题。
仓库 GitHub Release 正文取整段；Modrinth / CurseForge 的 changelog 取段尾英文块（英文内部不要空行）。

## v2.4.12

修复：发出的 GIF 不会动（动图改为原字节直传，超限明确提示而不是静默变静态）、QQ 群转发整行变灰字（解析器接受「[标签] 名字：内容」形状，在线玩家保留头像）、1.21.1 两端空输入按 Tab 后无法输入、打开聊天面板时物品栏 HUD 消失（聊天面板不再隐藏 HUD）、自己发的图片随机“加载失败”（JPEG 不再白发探测 + 自己上传的本地缓存 + NeoForge/Fabric 补上去重修复 + 服务端限流 4→16）、壁纸取景界面一片空白。
新增：自定义面板背景图的取景编辑器——锁定面板宽高比的选取框，拖动平移、滚轮缩放，改面板宽度/窗口大小自动适配。
更改：动图上限对齐 AtomChat——帧数 48→120、改用 8M 像素单图预算裁帧而不是拒绝；表情包上限 10→32；表情面板里的 GIF 改为静态缩略图 + GIF 角标（发到聊天里照常动）。
调试：指令补全列表无法鼠标点击——本版加入临时诊断日志，开 `debug_log` 复现一次即可定位，修复留待下一版。

Fixed: sent GIFs losing their animation (animated sources now pass through as raw bytes, with a clear toast instead of a silent still image when over the limits), QQ group relays rendering as grey system text (the parser accepts the "[tag] name: content" shape and keeps online players' avatars), Tab on an empty chat input locking all typing on both 1.21.1 loaders, the hotbar HUD vanishing while the chat panel was open (the panel no longer hides the HUD), our own uploaded images randomly "failing to load" (no wasted probes on JPEGs, a local cache of own uploads, the de-duplication fix finally landed on NeoForge and Fabric, server rate limit 4 -> 16), and the blank background-framing screen. Added: a framing editor for the custom panel background (a selection box locked to the panel's aspect ratio; drag to pan, scroll to zoom, re-fits automatically when the panel width changes). Changed: animation limits aligned with AtomChat (120 frames with an 8M-pixel decoded budget that trims instead of rejecting), custom emote cap raised from 10 to 32, still GIF thumbnails with a badge in the emote panel (sent messages keep animating). Debug: temporary diagnostics for the suggestion list not responding to mouse clicks (enable debug_log and reproduce once; the fix lands next version).

## v2.4.11

新增：聊天群组（mod 内置服务端路由 + 页签管理）、自定义面板背景图、GIF/WebP 动图消息、玩家资料卡。
修复：打开聊天面板崩溃、面板打开时 HUD 遮挡第一人称手部、服务器图片连发被限流、群组弹层点击闪烁、中文群名发送失败、名字误加下划线与点击事件。

Added: in-mod chat groups (in-mod server routing with tab management), custom panel background, animated GIF/WebP messages, player profile card.
Fixed: chat-open crash, HUD hiding the first-person hand while the panel is open, rate-limited server images, group popup flicker, CJK group-name send failures, stray name underlines.
