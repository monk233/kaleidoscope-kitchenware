# 森罗物语：家什（Kaleidoscope Kitchenware）设计文档

- 日期：2026-10-07
- 状态：已批准设计，待拆分实现计划
- 目标版本：Minecraft 1.21.1 / NeoForge 21.1.x / Java 21
- 依赖本体：森罗物语：厨房 Kaleidoscope Cookery 1.6.0（NeoForge 1.21.1）

## 1. 背景与目标

森罗物语：厨房本体提供了完整的烹饪流程（炒锅、汤锅、蒸笼、菜板、石磨、旋风烤肉塔）与少量装饰家具（厨具架、果篮、竹匾、厨娘凳、椅子、餐桌、八仙桌、长板凳、红灯笼），但缺少厨房空间里最基础的储物与操作家具：没有橱柜、没有料理台面、没有水槽、没有排烟设备。

系列内其它模组覆盖情况：

- 森罗物语：国味 —— 冰箱、腌菜罐、盘架、横批/对联/福字
- 森罗物语：酒馆 —— 沙发、高脚凳、吧台、酒柜、酒架、酒杯架、黑板、展板、灯串、垂灯、挂画、香薰、酒桶、龙头
- 森罗物语：装饰（Kaleidoscope Deco） —— 仅面向 1.21.5 及以上，本插件的目标版本无法使用

本项目为森罗物语：厨房开发一个附属模组，以中式传统厨房家具为核心内容，补齐柜类、台面、排烟与调料收纳，并通过灶台与水槽、油烟机构成完整的厨房动线。

目标：

1. 提供一批风格与本体一致的中式厨房家具方块。
2. 自研柴火灶可被本体锅具直接识别为热源，无需修改本体代码。
3. 建立可复用的森罗美术风格生成能力（skill + 贴图生成脚本）。
4. 代码按可公开发布的标准组织（配置项、许可、语言文件、README 齐备），但首版不发布。

非目标（明确不做）：

- 不复制本体贴图像素（本体资源为 CC BY-NC-SA 4.0，本项目只学习风格规则）。
- 不新增烹饪配方体系，食物仍由本体提供。
- 首版不引入 Mixin。

## 2. 命名与标识

| 项 | 值 |
| --- | --- |
| 中文名 | 森罗物语：家什 |
| 英文名 | Kaleidoscope Kitchenware |
| modId | `kaleidoscope_kitchenware` |
| 包名 | `com.kaleidoscope.kitchenware` |
| 工程路径 | `E:\document\workspace\AIProjects\kaleidoscope_kitchenware` |
| 许可 | 代码 BSD-3-Clause；资源 CC BY-NC-SA 4.0（与本体一致的双许可策略） |

命名风格沿用系列习惯：方块中文名走口语化中式命名（柴火灶、水槽、碗柜、挂杆、调料架、料理台、烟囱、柴火堆）。

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

两项限制对应的方案见第 10 节。

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

派生规则：每个材质建立 4 至 5 阶明度阶梯，最暗阶作为轮廓线，最亮阶只用于顶面与高光；单张 16×16 贴图使用颜色不超过 12 种。

### 4.2 尺寸规则

| 对象类型 | 贴图尺寸 | 实例 |
| --- | --- | --- |
| 平面方块与常规方块 | 16×16 | 炉灶、椅子、餐桌、厨具架 |
| 立体小件 | 32×32 | 汤锅、茶壶、蒸笼、灯笼、盘子 |
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

- 朝向用 `facing`，平面轴向用 `axis`，点亮用 `lit`，含水用 `waterlogged`，结构状态用 `has_*` / `half` / `part` 等布尔属性。
- 平面可拼接结构（餐桌、长板凳）使用 `position` 属性，取值 `single` / `left` / `middle` / `right`，模型按 `block/<topic>/<variant>` 分目录存放。
- 组合状态较多时使用 `variants`（例如本体蒸笼 64 个变体），部件化结构使用 `multipart`。

## 5. 首版方块清单

共 9 个方块。属性列中 `LIT` 表示原版 `BlockStateProperties.LIT`。

| # | ID | 中文名 | BlockState | BlockEntity | 交互 | 贴图 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `firewood_stove` | 柴火灶 | `facing`, `LIT`, `waterlogged` | 是：燃料槽、燃烧时间、火力档 | 右键投柴、打火石点燃、锅铲熄灭、Shift 查看火力档 | 32×32，点燃态单独贴图 |
| 2 | `firewood_pile` | 柴火堆 | `facing`, `count`(1-4) | 否 | 右键存柴、空手右键取柴、用尽消失 | 16×16 |
| 3 | `chimney` | 烟囱 | `axis`, `has_stove` | 否 | 无交互（占用方块用于判定连通） | 16×16 |
| 4 | `range_hood` | 抽油烟机 | `facing`, `powered`, `active` | 是：检测下方灶、粒子吸收、档位加成 | 红石供能工作，Shift 查看状态 | 32×32，工作态单独贴图 |
| 5 | `sink` | 水槽 | `facing` | 否 | 右键取水（无限水源）、桶/瓶取水 | 32×32 |
| 6 | `cupboard` | 碗柜 | `facing`, `open` | 是：27 格容器 | 右键开关柜门与打开 GUI | 32×32，双开门两态 |
| 7 | `hanging_rack` | 挂杆 | `facing`, `slot_0..slot_3` | 是：4 个挂点 | 右键挂取物品，挂腊味可被灶烟熏干 | 32×32 |
| 8 | `spice_rack` | 调料架 | `facing`, `has_*` | 是：9 格容器 | 右键存取调料 | 32×32 |
| 9 | `kitchen_counter` | 料理台 | `axis`, `position`, `waterlogged` | 否 | 上方可放置物品不掉落 | 32×32 |

配方思路（沿用本体材料体系）：柴火灶 = 圆石 / 石砖 + 营火 + 铁锭；碗柜 = 木板 + 箱子 + 铁粒；水槽 = 铁锭 + 石砖 + 铁桶；抽油烟机 = 铁锭 + 漏斗 + 铜锭；其余走木板与木棍。

## 6. 联动机制

### 6.1 柴火灶与本体锅具

- 柴火灶带 `LIT` 属性；点亮后位于其上方的本体炒锅、汤锅、蒸笼、茶壶直接工作。
- 燃料槽接受原版可燃物；燃烧进度写入 BlockEntity NBT，区块重载后保留。
- 燃料耗尽自动置 `LIT=false`，并播放熄火音效。
- 火力档由投入的燃料决定，分三档：文火（木板类）、中火（煤炭类）、猛火（烈焰粉、岩浆桶）。档位影响燃烧持续时长与燃料消耗率。
- 上方有水或降雨时自动熄灭（复用本体炉灶的判定思路）。

### 6.2 抽油烟机与烟囱

- 抽油烟机检测正下方方块是否为柴火灶（`firewood_stove`）。
- 工作条件：红石信号 `powered=true`，且处于灶正上方同一柱。
- 工作时：吸收灶上方烟雾粒子，给灶的火力档临时 +1，并清除范围内玩家的「油烟呛咳」效果。
- 「油烟呛咳」是本模组新增的负面效果：玩家停留在已点燃但未被排烟的柴火灶附近时获得，油烟机工作范围内不再获得该效果。
- 若灶所在柱向上 3 格内存在 `chimney`，效率按 100% 计算；否则按 50% 计算。

### 6.3 水槽

- 无限水源，右键直接取水，无前置条件，与其他装饰方块一样不存水、不需要注水。
- 手持空桶或玻璃瓶右键，把手中的容器填充为水桶 / 水瓶。
- 不做流体存储，也不提供管道接口；`facing` 仅用于确定出水口朝向。

### 6.4 碗柜与调料架

- 均为标准容器：碗柜 27 格，调料架 9 格。
- 碗柜的 `open` 属性驱动柜门动画，玩家离开后自动关闭。
- 破坏方块时保留内容物（`BlockItem` 携带 NBT）。

### 6.5 挂杆

- 4 个挂点，各自保存一个 `ItemStack`。
- 挂在杆上的腊味类物品在灶烟熏范围内会累积烟熏进度，达到阈值转换为带烟熏风味的物品（风味标记仅本模组识别，不修改本体配方）。

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
│   ├─ registry/{ModBlocks,ModItems,ModBlockEntities,ModCreativeTabs}.java
│   ├─ block/                     # 方块类
│   ├─ blockentity/               # 方块实体类
│   ├─ menu/                      # 容器菜单
│   ├─ config/                    # 配置项
│   └─ util/                      # 烟道、燃料档等工具
├─ src/main/resources/
│   ├─ assets/kaleidoscope_kitchenware/{blockstates,models,textures,lang}
│   └─ data/kaleidoscope_kitchenware/{recipe,loot_table,tags,advancement}
├─ tools/gen_textures.py          # 调色板驱动的贴图生成脚本
└─ docs/superpowers/specs/        # 本文档
```

`libs/` 中的本体 jar 由本脚本从整合包 mods 目录复制，不纳入版本库（写入 `.gitignore`）。

## 8. 美术风格 skill 交付

- 位置：`C:\Users\Buddh\.agents\skills\kaleidoscope-art-style\SKILL.md`
- 内容：第 4 节的调色板、尺寸、模型、blockstate 规则，加上生成脚本的调用方式、命名约定与自检清单（颜色数量、描边、顶面高光、透明背景）。
- 配套资源：`references/palette.json`（色板与明度阶梯）、`scripts/new_texture.py`（按类型生成 16/32/64 像素贴图的模板）。
- 用途：本项目后续所有贴图与模型按该 skill 生成，保证风格一致。

## 9. 实现阶段

| 阶段 | 内容 | 产出 |
| --- | --- | --- |
| M1 | Gradle 骨架、主类、注册表、语言文件、本地本体依赖 | 可启动的空 mod |
| M2 | 美术风格 skill + 贴图生成脚本 | 9 个方块贴图与 blockstate/model JSON |
| M3 | 柴火灶 + 燃料 + 火力档 + 柴火堆 | 本体锅具可识别，形成最小闭环 |
| M4 | 烟囱 + 抽油烟机 + 水槽 | 排烟与水联动 |
| M5 | 碗柜 + 调料架 + 挂杆 + 料理台 | 储物与陈设补齐 |
| M6 | 配置项、README、许可文件、打包与真机验证 | 可交付构建 |

## 10. v2 待办（需 Mixin，作为可关闭模块）

1. 火力档影响熟制速度：Mixin `PotBlockEntity` / `StockpotBlockEntity` / `SteamerBlockEntity` 的 `tick`，按灶档位施加倍率（文火 0.75x、中火 1x、猛火 1.5x）。
2. 一灶多锅：Mixin `hasHeatSource`，改为检查灶方块周围 3 格内的锅具。
3. 两项均通过配置开关控制，默认关闭，并在 README 说明兼容风险。

## 11. 验收标准

1. 柴火灶投入燃料并点燃后，上方放置本体炒锅，可正常加油、下菜、出菜，本体不再提示「需要点燃的炉灶」。
2. 燃料耗尽后灶自动熄灭，炒锅停止工作；区块重载后燃烧进度与燃料槽内容保留。
3. 抽油烟机在红石供能且正下方为柴火灶时进入工作态，粒子被吸收，灶火力档 +1；无红石时退回原档。
4. 烟囱接在灶柱上时油烟机效率为 100%，缺失时为 50%。
5. 水槽可右键取水，可用空桶与玻璃瓶取水。
6. 碗柜与调料架 GUI 正常存取，破坏方块后内容物保留。
7. 挂杆可挂取 4 个物品，腊味在烟熏范围内进度增长。
8. 未安装本体时，游戏可启动，本模组方块仍可放置，仅热源联动失效并在日志给出提示。

## 12. 风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 程序生成贴图观感不达预期 | 视觉质量 | 先出 1 个方块做样张，确认后再批量生成 |
| 本体后续版本改动 `hasHeatSource` | 零侵入接入失效 | 固定本体依赖版本；在 M6 记录版本兼容矩阵 |
| 本体类名或包名变更 | 编译中断 | 只依赖必要类型；集中封装在 `util` 包内 |
| 容量与配置需求变化 | 返工 | 容量、倍率、效率等全部走配置项 |
| 与其它附属模组方块 ID 冲突 | 装载失败 | 命名空间独立，ID 前缀固定 |

## 13. 许可与署名

- 代码：BSD-3-Clause。
- 资源（贴图、模型、语言文件）：CC BY-NC-SA 4.0。
- 本项目不复制本体素材，仅参考其风格规则；README 中注明与森罗物语系列无官方关联，并感谢本体开发组（ysbbbbbb、tartaric_acid、Azumic、药水棒冰、CR_019）。
