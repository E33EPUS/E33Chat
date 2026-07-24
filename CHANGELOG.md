# Changelog

## v2.1.2

**修复**
- 指令补全：修复 `windowActive` 未启用导致补全窗口不显示
- 指令补全：修复补全窗口 Y 轴位置（提到输入框上方）
- 指令补全：修复宽建议（如实体 ID）X 轴溢出到负数被裁切
- HUD 红点：修复 `drawIcon()` 手动 GL 状态调用泄漏，导致红点渲染在图标下方
- CI：JDK 25 → 21，修复 Fabric 1.21.1 + Gradle 8.8 不兼容

**Fix**
- Command suggestion: fix `windowActive` not enabled causing suggestion window to never appear
- Command suggestion: fix Y position (place above input field)
- Command suggestion: fix X clamping for wide suggestions (entity IDs) causing negative offset
- HUD red dot: fix `drawIcon()` manual GL state leak pushing red dot behind icon
- CI: JDK 25 → 21, fix Fabric 1.21.1 + Gradle 8.8 incompatibility

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
