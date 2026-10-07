# 森罗物语：家什（Kaleidoscope Kitchenware）设计文档

- 日期：2026-10-07
- 状态：已批准设计（v2 修订），待拆分实现计划
- 目标版本：Minecraft 1.21.1 / NeoForge 21.1.x / Java 21
- 依赖本体：森罗物语：厨房 Kaleidoscope Cookery 1.6.0（NeoForge 1.21.1）

## 1. 背景与目标

森罗物语：厨房本体提供了完整的烹饪流程（炒锅、汤锅、蒸笼、菜板、石磨、旋风烤肉塔）与少量装饰家具（厨具架、果篮、竹匾、厨娘凳、椅子、餐桌、八仙桌、长板凳、红灯笼），但缺少厨房空间里最基础的储物、操作与建材：没有水缸、没有碗柜、没有排烟设备，也没有成体系的墙地砖与瓦片可供搭出像样的中式厨房。

系列内其它模组覆盖情况：

- 森罗物语：国味 —— 冰箱、腌菜罐、盘架、横批/对联/福字
- 森罗物语：酒馆 —— 沙发、高脚凳、吧台、酒柜、酒架、酒杯架、黑板、展板、灯串、垂灯、挂画、香薰、酒桶、龙头
- 森罗物语：装饰（Kaleidoscope Deco） —— 仅面向 1.21.5 及以上，本插件的目标版本无法使用

本项目为森罗物语：厨房开发一个附属模组，以中式传统厨房家具与建材为核心内容，补齐水缸、碗柜、调料架、料理台、柴火灶与排烟设备，并提供一套可自由搭建厨房空间的墙地砖与瓦片。

目标：

1. 提供一批风格与本体一致的中式厨房家具与建材方块。
2. 自研柴火灶可被本体锅具直接识别为热源，无需修改本体代码。
3. 全部功能方块采用无 GUI 的沉浸式交互，符合本体系列的交互习惯。
4. 建立可复用的森罗美术风格生成能力（skill + 贴图生成脚本）。
5. 代码按可公开发布的标准组织（配置项、许可、语言文件、README 齐备），但首版不发布。

非目标（明确不做）：

- 不复制本体贴图像素（本体资源为 CC BY-NC-SA 4.0，本项目只学习风格规则）。
- 不新增烹饪配方体系，食物仍由本体提供。
- 首版不引入 Mixin。
- 不做任何容器 GUI 界面（含储物类方块）。

## 2. 命名与标识

| 项 | 值 |
| --- | --- |
| 中文名 | 森罗物语：家什 |
| 英文名 | Kaleidoscope Kitchenware |
| modId | `kaleidoscope_kitchenware` |
| 包名 | `com.kaleidoscope.kitchenware` |
| 工程路径 | `E:\document\workspace\AIProjects\kaleidoscope_kitchenware` |
| 许可 | 代码 BSD-3-Clause；资源 CC BY-NC-SA 4.0（与本体一致的双许可策略） |

命名风格沿用系列习惯：方块中文名走口语化中式命名（柴火灶、水缸、碗柜、调料架、料理台、烟囱、柴火堆、青砖、青瓦）。

## 3. 关键技术依据：热源接入机制

本体 `PotBlockEntity.hasHeatSource(Level)` 的实现（1.21.1-neoforge 分支）：

```java
public boolean hasHeatSource(Level level) {
    BlockState belowState = level.getBlockState(worldPosition.below());
    if (belowState.hasProperty(BlockStateProperties.LIT)) {
        return belowState.getValue(BlockStateProperties.LIT);
    }
    return belowState.is(TagMod.HEAT_SOURCE_BLOCKS_WITHOUT_LIT);
}
```

`IStockpot`、`ISteamer`、`ITeapot` 的同名方法实现一致。

推论：任何方块只要带有原版 `BlockStateProperties.LIT` 属性，并在点亮状态下位于锅具正下方，就会被本体认定为可用热源。柴火灶据此实现，不需要 Mixin、不需要数据包标签、不需要调用本体 API。

已知限制：

- 判定只看正下方一格，因此「一灶坐多锅」在本体机制下无法直接实现。
- 判定只读一个布尔值，因此「火力档影响熟制速度」无法在不修改本体的情况下实现。

两项限制对应的方案见第 11 节。

## 4. 美术风格规范

数据来源：本体 1.6.0 jar 内 `assets/kaleidoscope_cookery/textures/block` 共 220 张贴图与 1046 个模型文件（实测量取，用于生成新资源）。

### 4.1 调色板

| 用途 | 色值 |
| --- | --- |
| 木器暗部 | `#3F2910` `#543819` |
| 木器中间调 | `#6C4920` `#976A29` |
| 木器亮部 | `#B19D81` `#C7B497` |
| 描边 | `#111111` `#343232` |
| 金属 | `#747474` `#7F7F7F` `#8B8B8B` |
| 瓷白 | `#F5F5F5` `#EFEEE2` `#DAD7C3` |
| 点缀金 | `#BD9538` `#D2AB4C` |
| 砖石 | `#683A2F` `#4E3A39` |

建材扩展色板（本体未提供砖瓦类素材，以下色值按中式青砖与青瓦的常见配色新拟，仍遵循同一明度阶梯规则）：

| 用途 | 色值 |
| --- | --- |
| 青砖暗部 | `#2F3A3C` `#3C4A4C` |
| 青砖中间调 | `#4E5E60` `#617274` |
| 青砖亮部 | `#7A8B8C` `#93A3A3` |
| 青瓦暗部 | `#2A3138` `#38424A` |
| 青瓦亮部 | `#55636C` `#6E7C84` |
| 白灰墙 | `#D8D3C6` `#E7E3D8` `#F2EFE6` |
| 夯土墙 | `#8A6A47` `#A3814F` |

实测补充（后续并入风格 skill）：

- 系列木器家具共用一组暗部三色 `#82563F` `#6D442F` `#5E3723`，各树种只提供主体色阶，这是风格统一的关键。
- 单张贴图颜色数为 11 至 29，是连续色阶而非严格限色；简单建材按 4 至 5 阶绘制即可。
- 同一张贴图会被多个模型复用（本体 `chair/oak.png` 与 `cook_stool/oak.png` 字节相同），新模型优先复用同组材质。

派生规则：每个材质建立 4 至 5 阶明度阶梯，最暗阶作为轮廓线，最亮阶只用于顶面与高光。

### 4.2 尺寸规则

| 对象类型 | 贴图尺寸 | 实例 |
| --- | --- | --- |
| 平面方块与常规方块 | 16×16 | 炉灶、椅子、餐桌、厨具架、青砖、青瓦 |
| 立体小件 | 32×32 | 汤锅、茶壶、蒸笼、灯笼、盘子、水缸、柴火灶 |
| 大型家具 | 64×64 | 八仙桌、长板凳、蒸笼 |
| 巨件 | 128×128 | 石磨 |
| 染色变体 | 32×32 | 覆盖布（carpet/table、carpet/chair），走原版 16 色，背景 alpha 为 0 |

### 4.3 模型规则

- 由 Blockbench 生成，模型 JSON 顶层为 `elements` + `textures` + `display`。
- `elements` 的 `from`/`to` 允许两位小数精度（本体实例中出现 `1.8`、`11.75`）。
- 每个面显式声明 `uv`。
- 需要镂空的模型声明 `"render_type": "minecraft:cutout"`。
- 需要正面受光的声明 `"gui_light": "front"`。
- `display` 至少覆盖 gui、ground、fixed、thirdperson_righthand、thirdperson_lefthand、firstperson_righthand、firstperson_lefthand、head。
- 纯立方体方块可用 `parent: minecraft:block/cube` 并只声明六面贴图（本体炉灶即如此）。

### 4.4 blockstate 规则

- 朝向用 `facing`，平面轴向用 `axis`，点亮用 `lit`，含水用 `waterlogged`，结构状态用 `has_*` / `half` / `part` / `level` 等属性。
- 平面可拼接结构（餐桌、长板凳、料理台）使用 `position` 属性，取值 `single` / `left` / `middle` / `right`，模型按 `block/<topic>/<variant>` 分目录存放。
- 组合状态较多时使用 `variants`（例如本体蒸笼 64 个变体），部件化结构使用 `multipart`。

## 5. 方块清单

### 5.1 功能方块（8 个）

属性列中 `LIT` 表示原版 `BlockStateProperties.LIT`。

| # | ID | 中文名 | BlockState | BlockEntity | 交互 | 贴图 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `firewood_stove` | 柴火灶 | `facing`, `LIT`, `waterlogged` | 是：燃料槽、燃烧时间、火力档 | 手持燃料右键投柴；打火石/火焰弹点燃；锅铲熄灭；空手右键查看档位 | 32×32，点燃态单独贴图 |
| 2 | `firewood_pile` | 柴火堆 | `facing`, `count`(1-4) | 否 | 手持柴火右键存入；空手右键取出一个；用尽退化为空堆 | 16×16 |
| 3 | `chimney` | 烟囱 | `axis` | 否 | 无交互，占用方块用于判定烟道连通 | 16×16 |
| 4 | `range_hood` | 抽油烟机 | 已移除（实测后用户要求删除排烟机制） | — | — | — |
| 5 | `water_vat` | 水缸 | `facing`, `level`(0-3) | 否 | 手持空桶/空瓶右键装满；空手右键提示；默认满水无限取用 | 32×32，四档水位贴图 |
| 6 | `cupboard` | 碗柜 | `facing`, `open` | 是：16 格，仅接受碗与花盆 | 手持可存物右键存入一个；空手右键取出一个；Shift + 右键强制取出一个 | 32×32，双开门两态 |
| 7 | `seasoning_tray` | 调味盘 | `facing` | 是：4 格 × 1024 | 按指向的格子分别存取；破坏后掉落 1 个带内容的自身，内容与位置无关 | 青瓷四格浅盘 |
| 8 | `kitchen_counter` | 料理台 | `axis` | 否 | 平铺台面，贴图无缝相接，排成一行即成一条长台面 | 16×16 |

配方思路（沿用本体材料体系）：柴火灶 = 圆石/石砖 + 营火 + 铁锭；柴火堆 = 木棍 + 原木；水缸 = 陶瓦 + 铁桶 + 石砖；碗柜 = 木板 + 木台阶 + 铁粒；抽油烟机 = 铁锭 + 漏斗 + 铜锭；烟囱 = 青砖；料理台 = 木板 + 石砖。

### 5.2 建材方块（16 个）

主要为装饰用途，无 BlockEntity，全部走标准方块与派生变体。

| 组别 | 方块 | 说明 |
| --- | --- | --- |
| 青砖 | 青砖块、青砖台阶、青砖楼梯、青砖墙 | 灶台基座、地面、烟囱主材 |
| 青瓦 | 青瓦块、青瓦台阶、青瓦楼梯、屋脊瓦 | 屋顶主材，屋脊瓦用于收边 |
| 墙地 | 抹灰墙、夯土墙、青石地砖、木地板 | 墙面与地面搭配，木地板走企口板贴图 |
| 木构 | 木梁、椽木、木格窗、竹帘 | 横梁与椽子用于屋顶结构，木格窗与竹帘用于隔断 |

建材数量可按第 12 节配置逐组开关，玩家或整合包作者可只保留需要的组。

## 6. 交互约定

### 6.1 全模组无 GUI

所有功能方块不打开任何界面，交互通过世界内右键完成，规则统一如下：

| 手部状态 | 右键 | Shift + 右键 |
| --- | --- | --- |
| 手持可存入物品 | 存入一个 | 存入一个 |
| 手持不可存入物品 | 不响应（保持原版行为） | 不响应 |
| 空手 | 取出一个 | 强制取出一个 |

「强制取出」指手持其它物品时也能取出，用于避免玩家为了取东西先清手；该行为仅对空手与手持非方块物品生效。

反馈依赖动作栏文本、音效与模型状态变化，不使用界面：存入播放放置音效，取出播放点击音效，满仓或空仓时动作栏提示。

### 6.2 柴火灶与本体锅具

- 柴火灶带 `LIT` 属性；点亮后位于其上方的本体炒锅、汤锅、蒸笼、茶壶直接工作。
- 燃料槽接受原版可燃物；燃烧进度写入 BlockEntity NBT，区块重载后保留。
- 燃料耗尽自动置 `LIT=false`，并播放熄火音效。
- 火力档由投入的燃料决定，分三档：文火（木板类）、中火（煤炭类）、猛火（烈焰粉、岩浆桶）。档位影响燃烧持续时长与燃料消耗率。
- 上方有水或降雨时自动熄灭（复用本体炉灶的判定思路）。

### 6.3 烟囱

- 青砖立柱，接在柴火灶上方充当烟道，纯装饰方块，不做机制判定。
- 原设计的抽油烟机与「油烟呛咳」效果在实测后移除：排烟机制收益低、打扰烹饪节奏，用户明确要求删除。

### 6.4 水缸

- 中式水缸造型，四档水位模型，放置时默认满水。
- 手持空桶或空玻璃瓶右键，把手中容器填充为水桶 / 水瓶；空手右键只给出动作栏提示，因为水无法直接拿在手上。
- 默认作为无限水源，取水不消耗水位。若把配置项 `waterVat.consumesLevel` 改为 true，则每次取水降低一档水位，水位降到 0 后需要手持水桶右键重新注水；`level` 属性即用于表现这一过程。
- 不做流体存储，也不提供管道接口。

### 6.5 碗柜与调味盘

- 两者都遵守第 6.1 节的无 GUI 存取规则。
- 碗柜容量 16 格，只接受碗类与花盆类物品，白名单由物品标签 `kaleidoscope_kitchenware:cupboard_storable` 定义，默认包含 `minecraft:bowl` 与 `minecraft:flower_pot`，整合包作者可通过数据包扩展。
- 调味盘是一块青瓷四格浅盘，四个格子各自独立，每格只收一种调料、上限 1024。白名单由物品标签 `kaleidoscope_kitchenware:seasoning` 定义。
- 交互按玩家指向的格子生效：手持调料右键存入整组，空手右键取出一组（64），潜行 + 空手右键取出一个。左右与远近都按玩家视角计算，俯视时以远近区分上下排。
- 每一格在内部由 16 个普通堆承载（16 × 64 = 1024），因为 `ItemStack` 的 count 序列化上限是 99，单个堆放不下 1024。
- 内容通过物品自身的 `CUSTOM_DATA` 组件随物品走，不使用原版方块实体数据组件（该组件的加载路径会自行改写容器尺寸并吞掉内容）。
- 调味盘被破坏时只掉落 1 个带内容的自身；掉落表留空，避免重复掉落。与放置位置无关：换到任何地方放下，四格内容原样都在。
- 内容变化通过 `ClientboundBlockEntityDataPacket` 广播给追踪该区块的玩家；`onDataPacket` 被覆盖为即使空 tag 也应用，否则"取空最后一格"不会更新客户端显示。
- 锅铲（标签 `kaleidoscope_cookery:kitchen_shovel`）右键某一格：空铲舀出一份，带料或带油则倒回该格；带调料右键炒锅走 `IPot.addIngredient`，带油交给本体处理，与搪瓷盆子一致。
- 合成：8 个云杉木板围一圈。

### 6.6 料理台

- 台面为可平铺方块（走 `axis` 属性），贴图设计为无缝相接，排成一行即为一条完整台面。
- 台面陈列物品需要方块实体渲染器，本版本不做，列入 v2 待办。

## 7. 工程结构

沿用本机既有 NeoForge 工程习惯（参考 `Flux-Networks-1.21`）：NeoForge ModDev Gradle 插件 + Kotlin DSL + Parchment 映射 + 阿里云镜像。

```
kaleidoscope_kitchenware/
├─ build.gradle.kts              # net.neoforged.moddev, Java 21, parchment
├─ settings.gradle.kts
├─ gradle.properties             # mod.* 与依赖版本集中在属性文件
├─ gradle/wrapper/               # gradlew, gradlew.bat, wrapper jar
├─ libs/kaleidoscopecookery-1.6.0-neoforge+mc1.21.1.jar   # compileOnly + runtime 本地依赖
├─ src/main/java/com/kaleidoscope/kitchenware/
│   ├─ KaleidoscopeKitchenware.java    # @Mod 主类
│   ├─ registry/{ModBlocks,ModItems,ModBlockEntities,ModEffects,ModCreativeTabs}.java
│   ├─ block/                     # 方块类（功能方块与建材分组）
│   ├─ blockentity/               # 方块实体类
│   ├─ config/                    # 配置项
│   └─ util/                      # 烟道、燃料档、无 GUI 存取工具
├─ src/main/resources/
│   ├─ assets/kaleidoscope_kitchenware/{blockstates,models,textures,lang}
│   └─ data/kaleidoscope_kitchenware/{recipe,loot_table,tags,advancement}
├─ tools/gen_textures.py          # 调色板驱动的贴图生成脚本
└─ docs/superpowers/specs/        # 本文档
```

`libs/` 中的本体 jar 由本脚本从整合包 mods 目录复制，不纳入版本库（写入 `.gitignore`）。

## 8. 美术风格 skill 交付

- 位置：`C:\Users\Buddh\.agents\skills\kaleidoscope-art-style\SKILL.md`
- 内容：第 4 节的调色板（含建材扩展色板）、尺寸、模型、blockstate 规则，加上生成脚本的调用方式、命名约定与自检清单（颜色数量、描边、顶面高光、透明背景）。
- 配套资源：`references/palette.json`（色板与明度阶梯）、`scripts/new_texture.py`（按类型生成 16/32/64 像素贴图的模板）。
- 用途：本项目后续所有贴图与模型按该 skill 生成，保证风格一致。

## 9. 实现阶段

| 阶段 | 内容 | 产出 |
| --- | --- | --- |
| M1 | Gradle 骨架、主类、注册表、语言文件、本地本体依赖 | 可启动的空 mod |
| M2 | 美术风格 skill + 贴图生成脚本；建材方块（16 个） | 建材可搭出完整厨房空间 |
| M3 | 柴火灶 + 燃料 + 火力档 + 柴火堆 | 本体锅具可识别，形成最小闭环 |
| M4 | 烟囱 + 抽油烟机 + 「油烟呛咳」效果 | 排烟联动 |
| M5 | 水缸 + 碗柜 + 调料架 + 料理台 | 无 GUI 存取体系 |
| M6 | 配置项、README、许可文件、打包与真机验证 | 可交付构建 |

## 10. 配置项

`config/kaleidoscope_kitchenware-common.toml`，共 8 项。

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `stove.fuelBurnMultiplier` | 1.0 | 柴火灶燃料燃烧时长倍率 |
| `waterVat.consumesLevel` | false | 打水是否消耗水位 |
| `storage.cupboardCapacity` | 16 | 碗柜格数（上限 27） |

建材分组开关不提供配置项：注册阶段读取配置不安全，且关掉方块注册会让已放置的方块变成空气。需要裁剪建材时建议用数据包或整合包工具处理。

## 11. v2 待办

1. 火力档影响熟制速度：Mixin `PotBlockEntity` / `StockpotBlockEntity` / `SteamerBlockEntity` 的 `tick`，按灶档位施加倍率（文火 0.75x、中火 1x、猛火 1.5x）。
2. 一灶多锅：Mixin `hasHeatSource`，改为检查灶方块周围 3 格内的锅具。
3. 料理台陈列物品：加方块实体与渲染器，让台面能摆放菜品。
4. 以上三项均通过配置开关控制，默认关闭，并在 README 说明兼容风险。

## 12. 验收标准

1. 柴火灶投入燃料并点燃后，上方放置本体炒锅，可正常加油、下菜、出菜，本体不再提示「需要点燃的炉灶」。
2. 燃料耗尽后灶自动熄灭，炒锅停止工作；区块重载后燃烧进度与燃料槽内容保留。
3. 烟囱可正常放置与连接，作为装饰无机制副作用；排烟相关验收项已随抽油烟机的移除作废。
5. 水缸装满时，手持空桶与空玻璃瓶右键可被装满；把 `waterVat.consumesLevel` 改为 true 后，取水逐档降低水位，降空后可用水桶重新注满。
6. 碗柜手持碗右键存入一个、空手右键取出一个、Shift + 右键强制取出一个；存入花盆成功，存入石头失败并提示。
7. 调料架存取正常，架面模型随存量变化。
8. 全模组无任何容器界面；所有功能性方块仅通过世界内右键交互。
9. 建材方块可用青砖、青瓦、墙地、木构四组搭出封闭厨房空间，楼梯与台阶朝向、连接正确。
10. 未安装本体时，游戏可启动，本模组方块仍可放置，仅热源联动失效并在日志给出提示。

## 13. 风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 程序生成贴图观感不达预期 | 视觉质量 | 先出 1 个方块做样张，确认后再批量生成 |
| 建材方块数量膨胀 | 工期与维护成本 | 全部走数据驱动的注册循环，同组共用贴图与模型模板 |
| 无 GUI 存取的可用性 | 玩家上手成本 | 动作栏提示 + 音效 + 模型状态三重反馈；README 附交互图示 |
| 本体后续版本改动 `hasHeatSource` | 零侵入接入失效 | 固定本体依赖版本；在 M6 记录版本兼容矩阵 |
| 容量与配置需求变化 | 返工 | 容量、倍率、效率等全部走配置项 |
| 与其它附属模组方块 ID 冲突 | 装载失败 | 命名空间独立，ID 前缀固定 |

## 14. 许可与署名

- 代码：BSD-3-Clause。
- 资源（贴图、模型、语言文件）：CC BY-NC-SA 4.0。
- 本项目不复制本体素材，仅参考其风格规则；README 中注明与森罗物语系列无官方关联，并感谢本体开发组（ysbbbbbb、tartaric_acid、Azumic、药水棒冰、CR_019）。
