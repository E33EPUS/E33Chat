# 仓库架构：单分支多目标（AtomChat 同款）

一个 `main` 分支，出三个加载器的 jar。旧的三个平台分支（`Forge-1.20.1` /
`Neoforge-1.21.1` / `Fabric-1.21.1`）保留为旧布局的归档，不再演进。

## 目录

```
gradle.properties        仓库级身份（mod_version 等）—— 唯一一份，平台不许定义
versions/
  targets.json           目标矩阵：每个 MC 版本 × 加载器一条；buildable 决定发不发
  layers.json            共享层声明（since / loader / mappings 三轴谓词）
  mapping-aliases.json   映射层↔孪生副本的路径别名（当前为空，备忘在注释里）
  resource-paths.json    每目标的 shader / lang 落点（放错命名空间是静默失效）
  server-side.json       服务端类不许引用客户端类型的标记与匹配式
  pinned-equal.json      钉等清单：多平台同名副本必须逐字相同的显式状态
  third-party-apis.json  shared/ 与 layers/ 的第三方 import 声明表
shared/src/              纯 Java + 映射中立：三端共用，测试三端各跑一遍
layers/mapping/official/ Mojang 官方名、跨 1.20.1 / 1.21.1 编译的代码；Forge+Neo 挂载
platforms/
  1.20.1-forge/          ForgeGradle 6 / Gradle 8.8 / Java 17
  1.21.1-neoforge/       ModDevGradle / Gradle 9.2.1 / Java 21（配置缓存开）
  1.21.1-fabric/         Loom / Gradle 8.8 / Java 21 / Yarn 映射
gradle/e33chat-layers.gradle   挂载脚本：读根身份，按 targets.json 把 shared+层接进源集
tools/
  verify_targets.py      守卫闸（本地与 CI 同一份）
  build_all.sh           本地全端构建（先跑守卫闸）
  collect_jars.sh        把构建产物收进 dist/<版本>/
```

## 常用命令

```bash
# 全端自检（推送前跑这个）
bash tools/build_all.sh

# 单端构建（在平台目录里）
cd platforms/1.20.1-forge && cmd //c "gradlew.bat build -PrunTests --offline"

# 守卫闸 + 出 CI 矩阵
python tools/verify_targets.py --matrix-out _matrix.json
```

## 三条铁律

1. **身份只有一份**：`mod_version` 等只在仓库根；平台 `gradle.properties` 只放
   平台事实（loader 版本、JVM 参数）。守卫闸抓第二份。
2. **挂载即声明**：目标挂哪几层写在 `targets.json`，谓词不满足在配置期就红；
   目录由声明的轴推出，声明与目录无法互相矛盾。
3. **副本必须有闸**：同一段代码出现两份，要么进 `shared/`、要么进映射层+孪生
   （孪生守卫盯缺与重）、要么进钉等清单（钉等守卫盯漂移）。没有第四种状态。

## 映射家族的现实

Fabric 用 Yarn，Forge / NeoForge 用 Mojang 官方名。同一逻辑的两份"只差名字"
代码：官方名进 `layers/mapping/official/`（Forge+Neo 共用），Fabric 在自己平台
目录保留同路径孪生副本。改到这些类要落两遍 —— 孪生守卫保证少改会红，但逻辑
漂移（两边各自改出不同行为）只有测试能抓，改动时心里要有这根弦。

哪天想消灭孪生：把 Fabric 迁到官方映射，然后删掉这个层。

## 已知的债（都有闸盯着，见 versions/pinned-equal.json 的注释）

- `ChatBars` / `ChatContextMenus` / `ChatLayout`：Forge=Neo 逐字相同，但 Fabric
  把功能并进了它的 `ChatBubbleScreen`，进不了层，两端钉等。
- `AnimationStyle` / `GroupChannelState`：三端相同、本可进 shared/，但引用平台类
  （配置与消息存储），shared/ 不许向下依赖，三端钉等。
- 层里十几个类引用平台层（`ChatBubbleConfig`、`ChatMessageStore` 等）：靠
  「家族内各端同名」的依赖方向守卫盯着。彻底解耦走 facade 化（参考 AtomChat 的
  `Platform` / `Net` / `Host` 门面），解一个就能把对应钉等条目转正。
