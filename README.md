[简体中文](README.md) | [English](README_EN.md)

<h1 align="center">E33Chat</h1>

<p align="center">
  <em>以聊天 APP 风格重铸原版聊天框</em>
</p>

<p align="center">
  <img alt="MC" src="https://img.shields.io/badge/MC-1.20.1--1.21.1-green">
  <img alt="Loader" src="https://img.shields.io/badge/Loader-Forge%20%7C%20NeoForge%20%7C%20Fabric-orange">
  <img alt="Side" src="https://img.shields.io/badge/Side-Client%20required,%20server%20optional-blue">
  <img alt="Java" src="https://img.shields.io/badge/Java-17%2B%20%7C%2021%2B-yellow">
  <img alt="Version" src="https://img.shields.io/badge/Version-2.3.16-informational">
  <img alt="Downloads" src="https://img.shields.io/github/downloads/E33EPUS/E33Chat/total">
  <img alt="License" src="https://img.shields.io/badge/License-MIT-brightgreen">
</p>

E33Chat 是一款聊天增强模组，把原版聊天 HUD 重做成聊天 APP 风格：气泡与头像、@ 提及、私聊侧边栏、搜索、表情与常用语、图片消息、引用回复、通知横幅、本地聊天记录，并自带一套重做的设置界面。

---

## 目录

- [安装](#安装)
- [快速开始](#快速开始)
- [功能](#功能)
- [使用说明](#使用说明)
- [服务端加成](#服务端加成)
- [兼容性](#兼容性)
- [已知限制](#已知限制)
- [隐私与数据](#隐私与数据)
- [常见问题](#常见问题)
- [故障排查](#故障排查)
- [开发与构建](#开发与构建)
- [更新日志](#更新日志)
- [问题反馈](#问题反馈)
- [许可证](#许可证)

---

## 安装

| 依赖 | 类型 | 说明 |
|---|---|---|
| Minecraft | 必需 | 1.20.1 (Forge) / 1.21.1 (NeoForge / Fabric) |
| Java | 必需 | 17+ (Forge 1.20.1) / 21+ (1.21.1) |
| Forge | 按平台 | 47.0.0+ (1.20.1) |
| NeoForge | 按平台 | 21.x (1.21.1) |
| Fabric Loader | 按平台 | 0.16.0+ (1.21.1) |
| Fabric API | 按平台 | 任意兼容版本 (1.21.1) |
| CustomSkinLoader | 可选 | 显示离线玩家头像 |

1. 从 [Releases](https://github.com/E33EPUS/E33Chat/releases) 下载对应平台的 JAR
2. 放入 `.minecraft/mods/`（与你的加载器匹配，勿混装多平台 JAR）
3. 启动游戏

---

## 快速开始

1. 打开聊天框即可看到 E33Chat 面板；点击左下角 **齿轮** → 菜单 → 设置 进入配置界面
2. 「聊天框」分类调整面板宽度（400–1600 物理像素，窗口缩放不影响实际宽度；可开「全屏面板」直接铺满屏幕）、气泡颜色、圆角、消息间距与头像大小（面板模糊默认关，需要时手动开）
3. 「通知」分类配置 @ 提示音、横幅、私聊音效与总音量；横幅位置可调（`banner_offset_x/y`）避开其他 HUD
4. 发图片：左侧**上传按钮** / **Ctrl+V 粘贴** / **拖图片进窗口**，上传完成后自动发送

---

## 功能

- 💬 **聊天气泡** — 带头像和名字，颜色 / 文字色 / 圆角 / 主题可调；头像顶对齐，同人连发默认只首条显示（QQ 式）；消息间距组内 4px、组间 12px
- 🖼️ **图片消息** — 气泡内原生渲染 `[[CICode]]` / `[[ChatUpgrade]]` 协议图片（与 ChatImage 互通），点击开原图；防刷屏限流 + 接收开关
- ☁️ **服务端媒体托管** — 服务器也装 E33Chat 时图片直接存服务器（永久），否则自动回退第三方图床
- 😀 **自定义表情包** — `config/e33chat/emotes/` 放图即用（最多 10 个），Ctrl+V 加剪贴板图片，点击即发
- @ **提及补全** — 输入 `@` 弹玩家列表，左键头像 @ta；被 @ / 引用时提示音 + 横幅
- 👥 **私聊侧边栏** — 在线玩家列表、未读红点、公屏 / 私聊分栏、NPC 隐藏名单
- 🔍 **搜索 & 表情 & 常用语** — 实时搜索（支持中文）、emoji / 颜文字面板、常用语一键填充
- 📋 **复制 & 引用回复** — 右键消息复制 / 引用；右键头像私聊 / 传送 / 屏蔽
- 🚫 **屏蔽玩家** — 消息完全消失（原版聊天框 / 气泡 / 横幅 / 音效），即刻生效，重进不恢复
- 🔔 **通知横幅** — 覆盖 @ / 引用 / 私聊 / 系统消息，总音量滑条 + 分类型开关 + 位置偏移
- 🎬 **动画风格** — 面板 / 横幅 / 弹层 / 消息四类独立配置（SLIDE / FADE / ZOOM / NONE），弹层带开合动画
- 🗨️ **原版聊天框保留** — 正常渲染（上移避开 HUD 图标），ChatHeads / ChatAnimation 等直接生效
- 💾 **聊天历史** — 按存档 / 服务器保存，JSONL 完整保留颜色与点击事件（默认关）；每 30 秒自动保存
- 🛠️ **配置界面** — 5 标签页 + 可折叠子分类 + 实时预览 + 快照式保存 / 退出；颜色 / 数值 / 开关全 GUI 可调
- ✅ 防刷屏合并计数 · 📝 关闭保留输入 · 🌈 `&` 颜色码本地渲染 · 🧩 服务端消息格式模板

---

## 使用说明

### 聊天显示

- 自己的气泡靠右、他人靠左，颜色各自可调；气泡圆角 0–10（默认 4）
- 私聊显示 `<玩家名>[私聊] 内容`，引用回复 `<玩家名>[引用] 内容`（黄色标签），保留服务器前缀装饰与团队颜色
- 消息间距：同发送者 5 分钟内连续消息 = `message_gap` × 2/3（默认 4px）；换人 / 超时 / 系统消息 / 时间分隔 = ×2（默认 12px）

### 图片与媒体

- **发送**：上传按钮 / Ctrl+V / 拖拽；自动缩放到 ≤2048px 并重编码；上传串行排队（最多 8 个），按一次回车即可，失败恢复输入框
- **图床**：默认 uguu.se（约 3 小时过期）；服务端托管开启时改存服务器（`e33chat://media/<id>` 永久）；自定义图床配置 `upload_url` 等四键（multipart POST，响应取整段文本或 `json:字段路径`）
- **接收**：原生渲染图片代码，历史旧图片自动兼容重载；防滥用 = 滑动窗口限流 + 64 条 LRU 纹理缓存 + 解码前缩放；「接收图片」开关关闭后显示纯文本 `[图片]` 且不下载

### 侧边栏与通知

- 侧边栏：点名字开私聊、未读跳动红点、搜索过滤、公屏 / 私聊分栏、通配符隐藏名单（如 `*[NPC]*`）
- 横幅：@ / 引用 / 私聊 / 系统消息四类，系统横幅默认开；「@ 我」快捷跳转按钮；位置偏移 ±1000px 避 HUD 重叠
- 可选「@ 必须带 @ 前缀」；自我通知开关（默认关，调试用）；总音量滑条统一调节

### 动画与外观

- 四类动画独立取值 SLIDE / FADE / ZOOM / NONE：面板（默认 SLIDE）、横幅（SLIDE）、弹层（FADE）、消息（FADE）；`animation=false` 全关
- 弹层打开 200ms 淡入、关闭 150ms 缓出（ESC / 图标切换 / 点击外部全路径生效）；横幅进入 250ms、退出 150ms
- 弹层族（设置 / 表情 / 常用语 / 搜索 / @ 弹层 / 右键菜单）SDF 圆角 + 阴影 + 1px 描边；引用块圆角 8
- 面板背景模糊 `blur_enabled` 默认关（性价比结论）

### 设置与纹理

- 深色 / 浅色主题；设置界面 5 标签页（聊天框 / HUD / 通知 / 侧边栏 / 高级），快照式保存 / 退出，ESC 确认放弃
- **资源包覆盖**：界面元素与图标走纹理渲染，路径 `assets/e33chat/textures/gui/{dark|light}/<元素名>.png`，F3+T 即时生效
- ⚠️ **弹层背景自 2.3.16 起不再走纹理**（SDF 化，改由主题语义色驱动）；聊天气泡 / 引用块 / @ 横幅本就是 SDF 渲染，两者均不可被资源包覆盖

---

## 服务端加成

服务端可不装。装上后额外激活：

- 引用回复同步、跨客户端 @ 提及同步（含中文名）
- 新玩家进服收到近期聊天历史（`history_enabled`，默认关）
- 头像传送改用 `/tpa`（`use_tpa`，默认关）
- **服务端图片托管**（`media_enabled`，默认开）：图片存服务器永久（8MB/文件、512MB 总配额、随机 UUID 防遍历、每玩家限速）
- **消息格式模板**：服务端声明聊天格式并同步全服——插件 / NCR 改过格式的消息也能正确解析（`/e33chat gui` 配置，「从消息生成」或预设一键加，占位符 `{display_name}` `{prefix}` `{external}` `{content}` `{sender}` `{target}` `{sep}`）
- **EasyBot 群消息兼容**（`easybot_compat`，默认开）：把 EasyBot 转发进游戏的 QQ 群消息解析成玩家气泡，并支持气泡内显示 EasyBot/ChatImage 的 CICode 图片。常见格式自动识别（2.4.8 起群名前缀与 QQ 号均可省略）；EasyBot 模板被改过时，可用 `{external}` 聊天模板覆盖（服务端配置预设已带 EasyBot 格式，详见 [EasyBot 模板说明](#easybot-模板说明)）

服务端配置：`saves/<世界名>/serverconfig/e33chat-server.toml`（Fabric 为 `.json`）｜ OP 命令：`/e33chat template list|set|remove|clear|test`｜ `/e33chat gui` 图形化配置

### EasyBot 模板说明

- E33Chat 内置识别常见 EasyBot 格式：`[群名] <昵称(QQ号)> 内容`、`[群名] <昵称> 内容`、`<昵称> 内容`、`<昵称（群名片）> 内容`（2.4.8 起群名前缀与 QQ 号均非必需），`easybot_compat` 默认开启即可直接用
- 如果你在 EasyBot 主程序里改过「同步模板(到服务器)」，请到 `/e33chat gui` → 聊天模板，加一条 `{external}` 模板覆盖
- 常用示例：

| EasyBot 同步模板效果 | E33Chat 聊天模板 |
|---|---|
| `[群名] <昵称(QQ号)> 内容` | `[{prefix}] <{external}> {content}` |
| `[群名] 昵称: 内容` | `[{prefix}] {external}{sep}{content}` |
| `昵称 >> 内容` | `{external}{sep}{content}` |
| `<昵称> 内容` | `<{external}> {content}` |

- 也可以直接命令添加：`/e33chat template set chat "[{prefix}] <{external}> {content}"`

---

## 兼容性

| 模组 / 插件 | 状态 |
|---|---|
| No Chat Reports 等禁用举报插件 | 自 2.1.0 起自动兼容，无需配置 |
| CustomSkinLoader | 安装后显示离线玩家头像 |
| ChatImage / ChatUpgrade（图片协议） | 原生互通 |
| EasyBot（QQ 群服互通） | `easybot_compat` 默认开启，群消息自动解析为玩家气泡；CICode 图片在气泡内显示 |
| IMBlocker | 自动适配（命令输入自动切英文） |
| ModernUI | 可点击文本下划线 / 点击区域边界兼容 |
| Quark 等物品分享 | 系统消息物品图标正常渲染 |
| ChatHeads, ChatAnimation | 默认生效 |
| 昵称插件 | 部分支持，见 [常见问题](#常见问题) |
| 改聊天格式插件（EssentialsChat / CMI / DeluxeChat 等） | 服务端配置模板适配（含常见格式预设） |

---

## 已知限制

1. 仅支持 Forge 1.20.1、NeoForge 1.21.1、Fabric 1.21.1
2. 昵称与真名毫无关联、且插件没挂「点击私聊」也没同步 Tab 名时，消息显示为系统灰字（模板也救不了）
3. 名字与内容纯空格无分隔符的格式无法识别；NCR 加密聊天显示密文
4. 默认图床文件约 3 小时过期（服务端托管开启后永久，但总配额 512MB，需留意清理）
5. 弹层背景纹理覆盖自 2.3.16 起不生效（见 [使用说明](#使用说明)）；滚动条保持纯色填充

---

## 隐私与数据

> [!WARNING]
> 聊天记录以明文保存在本机 `.minecraft/e33chat/history/`（JSONL）。**请勿在公共电脑或不受信任的环境中使用**。

- 聊天记录只存本机、默认关闭、不上传；含凭据的敏感命令（`/login` `/register` 等）自动跳过
- **图片**：你发送的图片会上传到第三方图床（默认 uguu.se，约 3 小时过期）或（服务端托管开启时）服务器存储；客户端不会主动上传任何数据
- 服务端模组仅转发（@ / 引用 / 历史 / 媒体），不收集客户端数据；保存历史、同步、托管均可在配置中关闭

---

## 常见问题

**服务器需要装吗？** 不必须。装上额外解锁引用同步、@ 同步、历史同步、`/tpa` 传送、图片托管。

**怎么打开配置？** 面板左下角齿轮 → 菜单 → 设置。客户端配置 `config/e33chat/e33chat-client.toml`（Fabric 为 `.json`），所有项均可 GUI 调整。

**聊天历史默认保存吗？** 不保存（`chat_history = false`），在 设置 → 聊天框 → 聊天历史 开启；每 30 秒自动保存，正常退出也保存，崩溃最多丢 30 秒。

**怎么开背景模糊？** `blur_enabled` 自 2.3.16 起默认关（面板级模糊性价比最低，2.3.5 曾致降帧），在设置中手动打开。

**面板宽度和全屏怎么调？** `panel_width`（默认 1000）按屏幕物理像素计：改成 800，面板在任意窗口 / 缩放档下都稳定占 800 物理像素宽，不会随窗口变宽变窄或错位（窗口比面板窄时会夹紧到窗宽）。范围 400–1600。想让它直接铺满屏幕，开 `panel_fullscreen`（默认关）即可，此时忽略 `panel_width`，侧边栏保留可点。

**我发的图片去哪了？** 服务端托管开启（默认）→ 存服务器永久；否则 → 第三方图床（uguu.se，约 3 小时过期）。均可配置更换。

**为什么某条消息是灰字？** 客户端没把握它是玩家说的就保守归灰字（见 [已知限制](#已知限制)），昵称插件 / 特殊广播格式是常见原因。

**支持昵称插件吗？** 部分支持：昵称挂「点击私聊」事件或同步 Tab 名时可正确归属，否则灰字。

**服务器改了聊天格式，消息对不上？** 用消息格式模板：OP 输入 `/e33chat gui`，「从消息生成」粘贴一条真实聊天行或预设一键加，保存即同步全服；模板留空恢复守卫识别。

**怎么恢复原版聊天？** 设置 → 聊天框 → 关闭「启用 E33Chat」（`enabled = false`）；移除 mod 完全还原。

**可以放进整合包吗？** 可以，无需额外授权。

---

## 故障排查

1. 确认 MC 版本、加载器与 JAR 平台匹配，未混装多平台 JAR
2. 备份后删除 `config/e33chat/e33chat-client.toml` 测试配置损坏；只保留 E33Chat 排查冲突
3. 图片上传失败：检查 `upload_url` 与网络；默认图床 uguu.se 部分网络不可达，可换自定义图床或开服务端托管
4. 查看 `.minecraft/logs/latest.log` 中 `[e33chat]` 相关错误
5. 提交 issue 时附上版本号、模组列表、`latest.log`、截图和复现步骤

---

## 开发与构建

```bash
git clone https://github.com/E33EPUS/E33Chat.git
cd E33Chat

# Forge 1.20.1（默认分支）
./gradlew build

# NeoForge 1.21.1
git checkout Neoforge-1.21.1
./gradlew build

# Fabric 1.21.1
git checkout Fabric-1.21.1
./gradlew build
```

- Forge 1.20.1：Java 17+，支持 `--offline`；NeoForge / Fabric：Java 21+
- 运行测试：`./gradlew cleanTest test --offline -PrunTests`（Forge / NeoForge 需 `-PrunTests`，Fabric 不需要）
- 三端同构，改动请同步三个分支（README 亦三端同文）

---

## 更新日志

完整双语变更记录见 [CHANGELOG.md](CHANGELOG.md)。

---

## 问题反馈

到 [Issues](https://github.com/E33EPUS/E33Chat/issues) 提交，附上版本号、加载器、模组列表、`latest.log`、截图或视频与复现步骤。

---

## 许可证

[MIT License](LICENSE)

Copyright &copy; 2026 E33EPUS
