# 柴火灶台与大铁锅 实现计划

- 日期：2026-10-07
- 配套设计文档：`docs/superpowers/specs/2026-10-07-firewood-stove-counter-design.md`
- 分支：`feature/firewood-stove-refactor`
- 工程路径：`E:\document\workspace\AIProjects\kaleidoscope_kitchenware`

## 全局约定

- Java 21，NeoForge 21.1.256，`net.neoforged.moddev` Gradle 插件，Kotlin DSL，Parchment 映射。
- 资源（blockstate、model、贴图、lang、loot table、recipe）全部由 `tools/gen_decor.py` 生成，不引入 datagen。
- 生成脚本必须幂等；每次改完跑 `python tools/gen_decor.py` 与 `python tools/check_resources.py`。
- 每阶段结束必须 `./gradlew build` 通过。
- 一切交互保持本模组的无 GUI 约定。

## M1 柴火灶台两格重构

文件：

- `block/FirewoodStoveBlock.java`：重写。
  - 新属性 `PART = EnumProperty.create("part", StovePart.class)`（`StovePart` 为块内枚举 `LEFT`/`RIGHT`）。
  - `setPlacedBy`：放置主格后在垂直于 `facing` 的一侧生成副格（`facing` 左/右由 `facing.getCounterClockWise()` 决定）。
  - `playerWillDestroy`：破坏任一方格时把另一方格 `setBlock(AIR, UPDATE_ALL)`（`setBlock` 不产生掉落，保证只掉一个）。
  - `updateShape`：伙伴格不存在的孤儿格自我清除（爆炸、活塞等场景不留半截灶台）。
  - 保留：`LIT`、`WATERLOGGED`、点燃/熄灭/投柴/查看状态交互、`animateTick`、`randomTick` 雨天自灭。
- `blockentity/FirewoodStoveBlockEntity.java`：基本不动，确认与 `part` 无关。
- `block/StovePart.java`：`enum StovePart implements StringRepresentable`（供 `EnumProperty` 与 blockstate 名称）。
- `event/StoveBurnerEvents.java`（新）：`BlockEvent.EntityPlaceEvent` 监听——放置位置下方是灶台锅眼、且放置方块是本体 `PotBlock`/`StockpotBlock`/`SteamerBlock`/`TeapotBlock` 时取消放置并给动作栏提示。
- `registry/ModBlocks.java`：灶台属性改为 `noOcclusion()`（新模型有凹腔与开孔）。
- `tools/gen_decor.py`：
  - 新增 `RED_BRICK` / `CLAY_PLASTER` 色阶与 `red_brick`、`clay_plaster` 两个 pattern。
  - 新增 `build_stove_counter()`：生成 `stove_brick_side`、`stove_clay_side`、`stove_front`、`stove_front_lit`、`stove_top`、`stove_mouth` 六张贴图，`firewood_stove_left` / `_right` / `_left_lit` / `_right_lit` 四个模型，`firewood_stove.json` blockstate（`facing` × `part` × `lit` = 16 个变体），item 模型，掉落表。
  - 删除旧的 `stove_side` / `stove_front` / `stove_top` 生成分支与旧模型文件。
- `tools/check_resources.py`：灶台校验改为新资源清单。
- `assets/.../lang/{zh_cn,en_us}.json`：`firewood_stove` 中文名改「柴火灶台」，英文名 `Firewood Stove`；补锅眼拦截提示文案。

验证：

1. `python tools/gen_decor.py` 二次运行输出一致。
2. `python tools/check_resources.py` 通过。
3. `./gradlew build` 通过。
4. 实机：放置得两格整体灶台；左右格独立投柴点燃；破坏任一格只掉 1 个；本体炒锅放置被拦并提示。

## M2 大铁锅方块

文件：

- `block/IronWokBlock.java`（新）：`facing`；`getStateForPlacement` 下方不是灶台返回 `null`；`canSurvive` 同判；`updateShape` 下方失效时 `Block.popResource` 掉落自身与内容；VoxelShape 一圈低台；`useItemOn` 把交互交给 BE。
- `blockentity/IronWokBlockEntity.java`（新）：M2 先落 `mode`、`hasHeatSource`、内容容器字段与 NBT 读写、`refresh()`/`getUpdateTag`/`getUpdatePacket`；M3 填状态机。
- `registry/ModBlocks.java`、`registry/ModBlockEntities.java`：注册 `iron_wok` 方块、方块实体（`stone()` 系属性 + `noOcclusion()`）。
- `client/KitchenwareClient.java`：注册 `IronWokRenderer`（M3 起真正绘制内容）。
- `tools/gen_decor.py`：新增 `build_iron_wok()`（`wok_outer`、`wok_inner`、`wok_bottom` 贴图 + 8 段环形锅体模型 + blockstate + item 模型 + 掉落表 + 配方）。
- `tools/check_resources.py`：加入 `iron_wok` 校验。

验证：

1. 资源自检与 `./gradlew build` 通过。
2. 实机：锅只能坐在锅眼上；下方灶台被破则锅掉落；无灶台处放置失败。

## M3 双模式烹饪

文件：

- `blockentity/IronWokBlockEntity.java`：实现 `IPot`，补汤模式。
  - 字段：`mode`、`inputs`（9 格 `NonNullList`）、`carrier`、`result`、`status`、`currentTick`、`stirFryCount`、`seed`、`soupBaseId`、`takeoutCount`。
  - 常量照抄本体：`PUT_INGREDIENT_TIME = 1200`、`TAKEOUT_TIME = 800`、`BURNT_TIME = 400`、迷之炒菜 `200`、`MAX_TAKEOUT_COUNT = 9`。
  - 炒：`ModRecipes.POT_RECIPE` → `FLEX_POT_RECIPE`；容器 `SimpleInput`；`onPlaceOil` / `addIngredient` / `removeIngredient` / `onShovelHit` / `takeOutProduct`。
  - 汤：`SoupBaseManager` 认汤底；`ModRecipes.STOCKPOT_RECIPE` → `FLEX_STOCKPOT_RECIPE`；容器 `StockpotInput`；`addSoupBase` / `removeSoupBase`；取出递减 `takeoutCount`。
  - `serverTick`：热源门禁 → 按 `mode` 分发到两套状态机。
- `client/IronWokRenderer.java`（新）：渲染油面/汤面、食材、成品，焦糊变黑。
- `block/IronWokBlock.java`：`useItemOn` 分发（先汤底/取出/下料，再油/锅铲），动作栏文案。
- `assets/.../lang/{zh_cn,en_us}.json`：模式、下料、成品、煮汤等提示键。
- `KaleidoscopeKitchenware.java` 与事件注册：确保新事件类挂上 NeoForge 事件总线。

验证：

1. `./gradlew build` 通过。
2. 实机：大铁锅放油后下料能出本体菜谱成品；放水桶后下料能出本体汤；锅铲翻炒与盛菜可用；调味盘把调料下锅可用；焦糊掉木炭。

## M4 文档与交付

文件：

- `README.md`：灶台与大铁锅的中英说明、交互表、1.0.0 → 1.1.0 搬迁说明、已知限制（HUD、机械臂、汤锅无盖）。
- `docs/release-notes-1.1.0.md`：中英双语发布说明。
- `gradle.properties`：`mod.version` 提升到 `1.1.0`。
- `tools/check_resources.py`：补齐全部新资源校验。

验证：

1. `python tools/check_resources.py` 与 `./gradlew build` 全绿。
2. 按设计文档第 11 节逐条过验收。

## 提交划分

| 提交 | 内容 |
| --- | --- |
| 1 | 设计文档与本实现计划 |
| 2 | M1 灶台重构（代码 + 生成资源） |
| 3 | M2 大铁锅方块（代码 + 生成资源） |
| 4 | M3 双模式烹饪与渲染 |
| 5 | M4 文档、版本与自检脚本 |
