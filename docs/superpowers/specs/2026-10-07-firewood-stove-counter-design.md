# 柴火灶台与大铁锅 设计文档

- 日期：2026-10-07
- 状态：已批准设计，待拆分实现计划
- 分支：`feature/firewood-stove-refactor`（自 `main` @ `f794f98`）
- 目标版本：Minecraft 1.21.1 / NeoForge 21.1.256 / Java 21
- 依赖本体：森罗物语：厨房 Kaleidoscope Cookery 1.6.0（NeoForge 1.21.1）

## 1. 背景与目标

1.0.0 的柴火灶是单格方块，只是"带 `LIT` 的热源"，锅具由本体提供。本次重构把它做成真正的农村砖砌灶台，并补上配套的大铁锅：

1. 柴火灶改名为**柴火灶台**，方块由单格改为**两格整体结构**（左右并排，各一个锅眼）。
2. 灶台**前侧烧火、上方坐锅**：前侧面每格一个烧火口与灰坑，灶面每格一个锅眼。
3. 新增功能方块**大铁锅**，只能坐在灶台的锅眼上，**同时支持炒菜与烧汤**两种烹饪模式。
4. 贴图与模型按 `kaleidoscope-art-style` skill 生成，材质走**原版红砖砌体 + 黏土抹面**。

非目标：

- 不做锅盖：大铁锅烧汤不需要盖子（与本体汤锅的差异见 6.4）。
- 不做 HUD 叠加层：状态提示走动作栏文案（本体 `PotOverlay` 硬编码认自己的方块，无法复用）。
- 不做本体方块塞进本体 `BlockEntityType` 的注册技巧（见第 3 节路线取舍）。
- 不追求 1.0.0 存档兼容：已放置的旧灶台会变成"半截灶台"，玩家手动重摆。

## 2. 命名与标识

| 项 | 值 |
| --- | --- |
| 灶台注册名 | `firewood_stove`（沿用 1.0.0 的 ID） |
| 灶台中文名 / 英文名 | 柴火灶台 / Firewood Stove |
| 大铁锅注册名 | `iron_wok` |
| 大铁锅中文名 / 英文名 | 大铁锅 / Iron Wok |
| 新增贴图前缀 | `stove_*`、`wok_*` |

## 3. 关键技术依据与路线取舍

### 3.1 本体接入点（已实测源码）

| 事实 | 依据 |
| --- | --- |
| 本体方块侧只依赖接口：`PotBlock.useItemOn` 用 `instanceof IPot` 判断 | `block/kitchen/PotBlock.java` |
| 配方查表全部 public：`ModRecipes.POT_RECIPE` / `FLEX_POT_RECIPE` / `STOCKPOT_RECIPE` / `FLEX_STOCKPOT_RECIPE`（`RecipeType.simple`，非注册表对象） | `init/ModRecipes.java` |
| 输入容器：`SimpleInput(List<ItemStack>)`、`StockpotInput(NonNullList<ItemStack>, ResourceLocation soupBaseId)` | `crafting/container/` |
| 汤底体系公开：`SoupBaseManager.getAllSoupBases()` / `containsSoupBase()` / `getSoupBase()`，条目实现 `api/recipe/soupbase/ISoupBase` | `crafting/soupbase/SoupBaseManager.java` |
| 热源判据：方块正下方一格 `hasProperty(LIT)` 则取其值，否则查标签 `kaleidoscope_cookery:heat_source_blocks_without_lit` | `PotBlockEntity.hasHeatSource` |
| 一个方块只能对应一个 BlockEntityType；`BlockEntityTypeAddBlocksEvent` 要求新方块与已有底座方块有公共父类（`POT_BE` 要 `extends PotBlock`，`STOCKPOT_BE` 要 `extends StockpotBlock`） | NeoForge `BlockEntityType.java.patch` |
| 本体 `PotBlock` 破坏不掉锅内内容（无 `getDrops`） | `datagen/lootable/BlockLootTables.java` |
| 本体汤锅不盖锅盖不推进烹饪 | `StockpotBlockEntity.tick` |

### 3.2 路线取舍

| 路线 | 结论 |
| --- | --- |
| A 继承 `PotBlock` + `BlockEntityTypeAddBlocksEvent` 挂进 `POT_BE` | 炒菜全免费，但**拿不到汤锅能力**（一块方块只能对一个 BE 类型），不满足需求 |
| **B 自研 Block + 自研 BlockEntity（采用）** | 同时实现炒与汤；代价是自写状态机、分发、渲染器 |
| C 在自研 BE 里委托真实 `PotBlockEntity` 实例 | 不可行：本体 BE 构造器把类型写死，`level`/`worldPosition` 是 `protected`，客户端数据包按类型解析会静默失配 |
| D Mixin 本体注册器/BE | 与 A 效果重叠而风险更高，本次不做 |

### 3.3 由此带来的、明确接受的限制

1. 本体 HUD（`PotOverlay`）与 Jade 物品存储面板**不会**显示大铁锅内容：两者按本体方块或 `PotBlockEntity` 类硬编码。替代：动作栏文案。
2. `KaleidoscopeCookery-Automation` 的机械臂按 `instanceof PotBlockEntity` 识别锅，**不会**识别大铁锅。
3. 本体非 `api` 包的类（`SimpleInput`、`QualityEvaluator`、`StockpotVisuals`）在小版本升级中可能改名；以固定本体依赖版本 + 编译期校验兜底。

## 4. 柴火灶台（`firewood_stove`）

### 4.1 结构与状态

- 方块类：`FirewoodStoveBlock extends HorizontalDirectionalBlock implements EntityBlock, SimpleWaterloggedBlock`（含水保留）。
- 状态：`facing`（前侧烧火面）、`lit`（该格是否在燃烧）、`part`（`left` / `right`，枚举属性）、`waterlogged`。
- 左右两格**沿垂直于 `facing` 的方向并排**；放置时一次性生成两格，主格由 `setPlacedBy` 生成副格。
- 破坏：任一格被破坏，两格一起消失，只掉落 **1 个**柴火灶台（用 `playerWillDestroy` 清副格 + 掉落表只给主格，避免双掉）。
- 需要正确的工具才能掉落（沿用 1.0.0 的 `stone()` 属性与 `requiresCorrectToolForDrops`）。

### 4.2 每格独立燃烧

- 每格各自一个 `FirewoodStoveBlockEntity`：燃料槽（1 格）、`burnTime`、`burnTimeTotal`、火力档（`FuelTier` 文火/中火/猛火）。两口锅各烧各的，不共享燃料。
- 保留 1.0.0 行为：投柴、打火石/火焰弹点燃、锅铲或铲类熄灭、空手查看剩余燃料与档位、雨天自灭（各格独立判定）、`stove.fuelBurnMultiplier` 配置项继续生效。
- 交互只作用于**被点击的那一格**。
- `syncLit()` 的区块加载校正逻辑保留。

### 4.3 模型与锅眼

- 灶面每格一个**锅眼**（模型开孔，孔口一圈砖沿）。
- 前侧面（`facing` 面）每格一个烧火口，口内有灰坑；点燃态单独贴图（口内亮 + 灰白）。
- 顶面与侧面贴图 32×32；模型为 elements 结构（非纯立方体），左右两格各一份模型，共 4 个（左右 × 点燃/熄灭）。

### 4.4 锅眼只坐大铁锅

- 用 NeoForge 的 `BlockEvent.EntityPlaceEvent`（可取消）拦截：若放置位置正下方是 `firewood_stove` 的锅眼格，而放置的方块**是本体厨房锅具**（`PotBlock` / `StockpotBlock` / `SteamerBlock` / `TeapotBlock` 的实例），取消放置并给动作栏提示。
- 其它方块（火把、灯笼等）不拦，避免方块被过度限制。
- 大铁锅不在此拦截列表内。

### 4.5 旧存档容错

- 旧存档中的单格灶台没有 `part` 属性，加载时按 `part=left` 补默认值，不崩溃；区块加载只保留一格的燃烧状态。

## 5. 大铁锅（`iron_wok`）

### 5.1 放置与朝向

- 只能坐在灶台锅眼上：`getStateForPlacement` 在下方不是 `firewood_stove` 时返回 `null`（放不下），`canSurvive` 校验同一条件。
- `updateShape`：下方灶台消失或不再是灶台 → 掉落自身（含内容物）。
- `facing` 继承下方灶台的朝向（锅与灶面同向）。
- 模型向下探进灶台锅眼，视觉上锅口与灶面齐平；VoxelShape 取灶面上沿的一圈（可踩、不挡视线）。

### 5.2 热源

- 下方那格灶台 `lit=true` 才烹饪；判据与本体一致（读下方方块的 `LIT`），但由我们自己的 BE 实现，不复用本体的 `hasHeatSource`。

### 5.3 烹饪模式

- BE 内一个 `mode` 字段：`NONE` / `STIR_FRY` / `SOUP`。
- **模式由第一次投入决定**：先下油（本体油物品、带油锅铲、油壶）→ 炒；先下汤底（`SoupBaseManager` 认得的汤底物品，如水桶）→ 汤。
- 空锅可随时改模式；已有内容时不允许改（提示"锅里还有东西"）。
- 同时实现 `api.blockentity.IPot`：本模组的锅铲联动、调味盘下料、盛菜路径都通过该接口识别大铁锅，无需额外适配。

### 5.4 炒模式状态机（对齐本体 `PotBlockEntity`）

| 状态 | 含义 | 进入条件 | 计时 |
| --- | --- | --- | --- |
| `PUT_INGREDIENT`(0) | 已放油，等下料 | `onPlaceOil` 成功 | 1200 tick（60 s），超时空锅 → `reset()`；有料 → 进入 `COOKING` |
| `COOKING`(1) | 烹饪中 | 匹配到 `PotRecipe` / `FlexPotRecipe`；无配方 → 迷之炒菜 | 配方 `time`（默认 200） |
| `FINISHED`(2) | 完成 | 计时归零 | 800 tick（40 s），超时 → `BURNT` |
| `BURNT`(3) | 焦糊 | | 400 tick（20 s），结束后 `reset()` 并掉落木炭 1–3 个 |

- 配方查表：`level.getRecipeManager().getRecipeFor(ModRecipes.POT_RECIPE, new SimpleInput(inputs), level)`，未命中再查 `FLEX_POT_RECIPE`；`FlexPotRecipe` 命中时按本体的 `QualityEvaluator` / `QualityUtils` 写入品质（若实现成本过高则记录为待办，见第 10 节）。
- 下料：拒绝 `kaleidoscope_cookery:ingredient_blocklist` 标签物品；带容器的物品返还容器；每次 `split(1)` 进空格，共 9 格。
- 翻炒：锅铲右键 → `onShovelHit`：`PUT_INGREDIENT` 且有料 → 开始烹饪；`COOKING` 且 `stirFryCount > 0` → 递减。
- 出锅：`FINISHED` 用配方 `carrier`（碗/盘）取出；`BURNT` 给黑暗料理；无 carrier 的成品需**潜行 + 锅铲**取出；取出失败且下方是热源时按本体行为烫伤 1 点（本体如此，保持一致）。

### 5.5 汤模式状态机（对齐本体 `StockpotBlockEntity`，去掉盖子）

| 状态 | 含义 | 进入条件 | 计时 |
| --- | --- | --- | --- |
| `PUT_SOUP_BASE`(0) | 已放汤底，等放料 | `addSoupBase` 成功 | 不限时 |
| `PUT_INGREDIENT`(1) | 等下料 | 放入汤底后 | 每 5 tick 检查一次，有料 → `COOKING` |
| `COOKING`(2) | 烹饪中 | 匹配 `StockpotRecipe` / `FlexStockpotRecipe`；无配方 → 迷之炖汤 | 配方 `time`（默认 300） |
| `FINISHED`(3) | 完成，可取 `min(result.count, 9)` 份 | | 每次取出 1 份，归零回 `PUT_SOUP_BASE` 并清空 |

- 汤底：遍历 `SoupBaseManager.getAllSoupBases()` 找 `isSoupBase(stack)`；放入后把 `soupBaseId` 换掉并返还容器（如空桶）。
- **不吃盖子**：不实现 `hasLid()` 门禁，汤直接开烧，靠粒子与渲染表现沸腾。这是与本体汤锅唯一的行为差异，见 6.4。
- 匹配前后可发本体的 `StockpotMatchRecipeEvent.Pre/Post`（照本体的兼容写法），本次只发、不订阅。

### 5.6 渲染与反馈

- 自研 `IronWokRenderer extends BlockEntityRenderer<IronWokBlockEntity>`：油/汤面、投入的食材、成品按状态渲染在锅内；焦糊态用 `OverlayTexture` 变黑。
- 同步照本体三件套：`refresh()`（`setChanged()` + `sendBlockUpdated`）、`getUpdateTag`、`getUpdatePacket`；状态变化后立即 `refresh()`。
- 提示走动作栏（`displayClientMessage(..., true)`），文案用本模组命名空间的中文键。

### 5.7 掉落

破坏大铁锅时掉落锅本身与锅内全部内容（食材、成品、汤底容器不返还）；不作弊地强制弹出到世界里（`Block.popResource`）。本体炒锅会吞内容，这里刻意不学它。

## 6. 交互总表

| 手部 / 物品 | 大铁锅（空锅） | 大铁锅（炒模式） | 大铁锅（汤模式） |
| --- | --- | --- | --- |
| 油 / 带油锅铲 | 放油，进入炒模式 | 拒绝（已有模式） | 拒绝 |
| 水桶等汤底 | 放汤底，进入汤模式 | 拒绝 | 已放汤底时拒绝 |
| 普通食材 | 拒绝（先放油或汤底） | 下料 | 下料 |
| 锅铲 | 无动作 | 翻炒（`onShovelHit`） | 无动作 |
| 空手 / 容器物品 | 无动作 | 取出食材 / 盛菜 | 取出食材 / 盛汤 |
| 潜行 + 锅铲 | 无动作 | 无 carrier 成品快速出锅 | — |
| 打火石等 | 不响应（交给下方灶台处理） | | |

灶台前侧（烧火口一面的两格）继续处理：投柴、点燃、熄灭、查看状态。

## 7. 美术规范

遵循 `kaleidoscope-art-style` skill：共享描边 `#5E3723`，单图 4–8 阶，顶面 1 行高光，非满方块外侧透明。

### 7.1 新增色阶（写回 skill 的 `palette.json`）

| 用途 | 色阶 |
| --- | --- |
| 红砖（原版砖体质感） | `#B4715A` `#9E5E48` `#8A4E3C` `#6E3C2E` `#4E2A20` |
| 黏土抹面 | `#C7B7A3` `#B3A18C` `#9C8A75` `#7F6F5C` |
| 生铁锅体 | 复用金属 `#606572` `#6E7179` `#747474` `#8B8B8B`，锅内壁亮一阶，锅底最暗 |

### 7.2 贴图（32×32）

| 名称 | 内容 |
| --- | --- |
| `stove_brick_side` | 红砖错缝砌体 |
| `stove_clay_side` | 黏土抹面（砖体上部收口） |
| `stove_front` / `stove_front_lit` | 前侧：抹面 + 烧火口（口内暗）+ 灰坑；点燃态口内亮、灰白 |
| `stove_top` | 灶面砖台 + 锅眼沿 |
| `stove_mouth` | 烧火口内壁（暗部 + 灰） |
| `wok_outer` | 生铁锅外壁（下暗上亮） |
| `wok_inner` | 锅内壁（更亮一阶） |
| `wok_bottom` | 锅底最暗 |

### 7.3 模型

- 灶台左右两格各一套 elements 模型（`firewood_stove_left` / `_right`，点燃态各一份，共 4 个）：砖体、顶部锅眼开孔、前侧烧火口凹腔。
- 大铁锅：半球锅体用 8 段环形 box 近似（复用 `water_vat` 的环形建模手法），锅沿一圈平口；无耳（按参考图 4 的纯生铁大锅）。
- 物品模型：灶台用一格视角展示整段灶体；大铁锅用 `gui` 45° 俯视。

### 7.4 生成脚本

- `tools/gen_decor.py` 新增：`red_brick` / `clay_plaster` 两种 pattern、`stove_counter` 与 `iron_wok` 两个模型构建函数、对应 blockstate / item model / loot table / lang / recipe 输出。
- `tools/check_resources.py` 新增这两个方块的资源完整性校验。
- 脚本保持幂等（同参数二次运行字节一致）。

## 8. 配方

| 产物 | 配方 |
| --- | --- |
| 柴火灶台 | 原版红砖 ×8 围一圈 + 黏土 ×1 中心（输出 1 个，放置后占 2 格） |
| 大铁锅 | 铁锭 ×5（上排 3 + 中排左右 2，凹形） |

## 9. 配置项

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `stove.fuelBurnMultiplier` | `1.0` | 保留：灶台燃料燃烧时长倍率 |

不新增配置项：模式切换、汤锅时间等沿用本体配方数据，避免重复开配置面。

## 10. 待办（本次不做）

1. `FlexPotRecipe` / `FlexStockpotRecipe` 的品质（`QualityEvaluator`）写入：若实现中新版接口变动导致成本过高，则先只支持 `PotRecipe` / `StockpotRecipe`，品质作为待办记录在 README。
2. 大铁锅的 HUD 叠加层（自己注册 `LayeredDraw` layer）。
3. 大铁锅对 `KaleidoscopeCookery-Automation` 机械臂的适配。

## 11. 里程碑与验收

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| M1 | 灶台 2 格重构 + 贴图模型 + 掉落 + 交互 + 锅眼拦截 | `gradlew build` 通过；实机放下得 2 格整体灶台；两口锅各自烧各自；本体锅具放不上去并收到提示 |
| M2 | 大铁锅方块：放置限制、朝向、热源判定、破坏掉落、贴图模型 | 锅只能坐锅眼；下方灶台熄灭则锅不再工作；灶台被破锅掉落 |
| M3 | 双模式烹饪：`IPot` + 配方接入 + 渲染器 + 动作栏提示 | 大铁锅能炒本体菜谱、能烧本体汤；锅铲翻炒与盛菜、调味盘下料可用 |
| M4 | README、发布说明、`check_resources.py` 扩展、最终验证 | 资源自检与 `gradlew build` 全绿，README 交互说明更新 |

## 12. 风险

| 风险 | 影响 | 应对 |
| --- | --- | --- |
| 本体非 api 包类改名 | 编译失败 | 固定本体版本；失败时代码集中在适配层，便于替换 |
| 两格灶台的掉落/复制漏洞 | 物品复制 | 副格掉落表为空，断开逻辑走 `playerWillDestroy`；实机专项验证 |
| 大铁锅渲染在锅眼内的 z-fighting | 观感 | 锅体与锅眼留 0.05 格间隙；实机微调 |
| 旧存档半截灶台观感 | 玩家困惑 | README 写清 1.0.0 → 1.1.0 的搬迁说明 |
| 状态机复制本体的 600 行逻辑出错 | 烹饪异常 | 照抄本体常量与分支顺序；每个状态写一只自检或脚本核对关键阈值 |

## 13. 许可与署名

- 代码：BSD-3-Clause。
- 资源：CC BY-NC-SA 4.0（风格参考自 Kaleidoscope 森罗物语系列）。
- 适配层参考了 Kaleidoscope Cookery 1.6.0（代码许可 BSD 3-Clause）的状态机常量与分支顺序，README 致谢中注明。
