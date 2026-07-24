# Changelog

## v2.1.2

**架构回退（同步 NeoForge 2.0.0→2.1.2）**
- 删除 `ClientLifecycleState`/`ChatRuntimeState`/`ChatListLayout`/`SidebarLayout`/`WhisperParser`/`RoundedRectangleGeometry`/`HintPolicy`/`MessagePipelineRules`/`WorldIdentity`/`ChatUiBehavior`/`ChatServerConfig`/`ChatMessage` 等 12 个死架构类及其测试
- `ChatMessageStore` 回退为直接单例模式，去掉 lifecycle 路由层，`setCurrentWorld` 只传连接 key
- `ChatBubbleClientSetup` 移除 `lifecycle` 引用，config 初始化为 `defaults()` 避免 null 守卫静默跳过音效
- `ChatListenerMixin` 重写消息处理逻辑，对齐 NeoForge 2.1.2

**新功能（NeoForge 2.1.x 同步）**
- 服务端配置同步：`ServerConfig`/`ServerConfigManager`/`ConfigSyncPayload`，从 `<world>/serverconfig/e33chat-server.json` 加载 `use_tpa` 和 `history_enabled`，首次加入时同步到客户端
- ModMenu 集成入口 `ModMenuIntegration`
- 配置新增：`preserveInput`（保留已输入文本）、`colorCodes`（&颜色代码本地解析）、`sidebarHidePatterns`（侧边栏通配符隐藏）
- 右键头像菜单：根据服务器配置显示 `/tpa` 或 `/tp`
- 通知栏新增"有人@你"快捷跳转按钮

**修复**
- 指令补全：`setWindowActive(true)` + Y 轴提到输入框上方 + X 轴防止宽建议（实体 ID）溢出负数被裁切
- HUD 红点：`drawIcon()` 增加 `g.draw()` flush，防止预览/强提示的着色器状态泄漏污染红点渲染
- 强提示：移除黄色/白色闪烁底色，统一白色文字
- 音效：config 默认值在类加载时初始化，不再依赖 null 守卫
- CI：JDK 25 → 21，修复 Fabric 1.21.1 + Gradle 8.8 不兼容

**Refactor (sync NeoForge 2.0.0→2.1.2)**
- Remove 12 dead architecture classes from Codex refactor era: `ClientLifecycleState`, `ChatRuntimeState`, `ChatListLayout`, `SidebarLayout`, `WhisperParser`, `RoundedRectangleGeometry`, `HintPolicy`, `MessagePipelineRules`, `WorldIdentity`, `ChatUiBehavior`, `ChatServerConfig`, `ChatMessage` and their tests
- `ChatMessageStore` back to direct singleton, remove lifecycle routing, `setCurrentWorld` takes connection key only
- `ChatBubbleClientSetup` remove lifecycle reference, init config to `defaults()` so null-guards don't silently skip sound
- `ChatListenerMixin` rewritten to match NeoForge 2.1.2 message handling

**Features (NeoForge 2.1.x sync)**
- Server config sync: `ServerConfig`/`ServerConfigManager`/`ConfigSyncPayload`, loads `use_tpa`/`history_enabled` from `<world>/serverconfig/e33chat-server.json`, syncs to client on join
- ModMenu integration entry `ModMenuIntegration`
- New config options: `preserveInput` (keep text on close), `colorCodes` (parse `&` format codes locally), `sidebarHidePatterns` (wildcard hide rules)
- Right-click avatar menu: shows `/tpa` or `/tp` based on server config
- "Mentions" quick-jump button on notification bar

**Fixes**
- Command suggestion: `setWindowActive(true)` + Y above input + X clamp for wide suggestions (entity IDs)
- HUD red dot: `g.draw()` flush in `drawIcon()` to isolate preview/hint shader state
- Strong hint: remove yellow/white flashing, use solid white text
- Sound: config defaults initialized at class-load, no longer guarded by null check
- CI: JDK 25 → 21 for Fabric 1.21.1 + Gradle 8.8

## v2.1.0

**架构重构（三版本统一）**
- 新增 `ClientLifecycleState` 聚合根：持有 ChatMessageStore + ChatRuntimeState，统一管理世界切换/断线/metadata
- Metadata 升级为暂存+重放+TTL 去重（30s/256 条），替代旧 5s 时间窗
- ChatMessageStore 构造器注入 ChatRuntimeState，getInstance() 通过 lifecycle 路由
- 提取纯函数类（零 MC 依赖）：ChatListLayout / SidebarLayout / WhisperParser / RoundedRectangleGeometry

**Fabric 修复**
- SDF 抗锯齿圆角气泡（自定义 shader）
- BedScreen 重写：STOP_SLEEPING 包 + 自动关屏 + 屏幕恢复
- 登录历史同步 / 伪装聊天处理 / 装饰名提取 / 私聊方向检测
- 命令建议位置修正 / strong hint 屏幕覆盖 / shader 重载
- 历史 JSON 样式保留 / 配置 tooltip + 范围校验 / 默认值对齐 / 图标同步
- 修复发消息时滚动条跳动 + "N 条新消息"误触发（wasAtBottom 用了旧 maxScroll 而非 prevMaxScroll）

**Architecture refactor (unified across all 3 versions)**
- Add `ClientLifecycleState` aggregation root: owns ChatMessageStore + ChatRuntimeState, manages world switch / disconnect / metadata
- Metadata upgraded to store-and-replay with TTL dedup (30s/256 entries), replacing old 5s window
- ChatMessageStore constructor-injected with ChatRuntimeState, getInstance() routes through lifecycle
- Extract pure-function classes (zero MC dependency): ChatListLayout / SidebarLayout / WhisperParser / RoundedRectangleGeometry

**Fabric fixes**
- SDF anti-aliased rounded bubbles (custom shader)
- BedScreen rewrite: STOP_SLEEPING packet + auto-close + screen restore
- Login history sync / disguised chat handler / decorated name extraction / whisper direction
- Command suggestion position fix / strong hint screen overlay / shader reload
- History JSON style preservation / config tooltips + range validation / defaults aligned / icons synced
- Fix scrollbar jump + bogus "N new messages" notification when sending messages (wasAtBottom compared against stale maxScroll instead of prevMaxScroll)

## v2.0.1

**修复**
- NCR 兼容：通用名字检测替代硬编码格式匹配，支持 `Steve >> hi` 等任意聊天格式
- 回声去重：玩家名匹配改用词边界检测，避免 "Alex" 误匹配 "Alexander"
- 反垃圾合并：当 rawPlayerName 不一致时拒绝合并，避免同名玩家消息被错误合并
- 通知文本：改用格式化字符串 `%s`，支持多语言语序
- 滚动状态：修复消息清除后滚动位置判断错误

**Fix**
- NCR compat: generic name detection replaces hardcoded format matching, supports any chat format including `Steve >> hi`
- Echo dedup: player name matching uses word boundary to prevent "Alex" matching "Alexander"
- Anti-spam merge: reject merge when rawPlayerName mismatches, preventing incorrect merge of same-name players
- Notification text: use format string `%s` for proper i18n
- Scroll state: fix scroll position detection after message cap purge

## v1.9.1

**初始**
- Fabric 1.21.1 移植版，基于 Forge 1.20.1 v1.9.1
- 侧边栏布局、聊天列表布局、富文本附件存储
- 多服务器/多维度会话隔离
- 快速聊天状态、提示策略、私聊解析器
