# 森罗物语：家什 实现计划

- 日期：2026-10-07
- 配套设计文档：`docs/superpowers/specs/2026-10-07-kaleidoscope-kitchenware-design.md`
- 工程路径：`E:\document\workspace\AIProjects\kaleidoscope_kitchenware`

## 全局约定

- Java 21，NeoForge 21.1.x，`net.neoforged.moddev` Gradle 插件，Kotlin DSL，Parchment 映射。
- 本体依赖通过 `libs/` 下的本地 jar 提供：`compileOnly` + 开发运行时 `runtimeOnly`。
- 资源（blockstate、model、lang、tag、loot table、recipe）全部由 `tools/gen_resources.py` 生成，不引入 datagen，理由：生成脚本同时产出贴图，统一入口更省事。
- 一切交互遵守设计文档 6.1 的无 GUI 约定。
- 每个阶段结束必须能 `./gradlew build` 通过，并在 `runClient` 中实机验证。

## M1 工程骨架

目标：空 mod 可编译可启动。

文件：

- `gradlew`、`gradlew.bat`、`gradle/wrapper/*`：从 `Flux-Networks-1.21` 复制（Gradle 8.9）。
- `settings.gradle.kts`：pluginManagement 仓库（gradlePluginPortal、neoforged、parchmentmc）。
- `gradle.properties`：mod.id / mod.name / mod.version / mod.group_id / mod.license、neoforge 21.1.x、parchment 版本、阿里云镜像地址。
- `build.gradle.kts`：moddev 插件、Java 21、本地 jar 依赖、`neoForge { runs { client, server } }`。
- `libs/kaleidoscopecookery-1.6.0-neoforge+mc1.21.1.jar`：从整合包 mods 目录复制。
- `src/main/java/com/kaleidoscope/kitchenware/KaleidoscopeKitchenware.java`：`@Mod` 主类。
- `src/main/java/com/kaleidoscope/kitchenware/registry/ModBlocks.java`、`ModItems.java`、`ModBlockEntities.java`、`ModCreativeTabs.java`：先建空壳骨架。
- `src/main/resources/assets/kaleidoscope_kitchenware/lang/{zh_cn,en_us}.json`。
- `src/main/resources/META-INF/neoforge.mods.toml`：声明本体为可选依赖（`type = "optional"`，因为无本体时仍可启动）。

验证：

1. `./gradlew build` 成功。
2. `./gradlew runClient` 启动到主菜单，日志出现模组加载记录。
3. 断开本体 jar 后仍可启动，日志出现联动失效提示。

## M2 美术风格 skill 与建材方块

目标：风格生成能力落地，24 个方块的建材部分可玩。

文件：

- `C:\Users\Buddh\.agents\skills\kaleidoscope-art-style\SKILL.md`：风格规范（设计文档第 4 节）、脚本用法、自检清单。
- 同目录 `references/palette.json`：木器、砖瓦、瓷白、金属色板与明度阶梯。
- 同目录 `scripts/new_texture.py`：按类型（建材砖纹、木器、瓦片）生成 16×16 / 32×32 PNG。
- 工程 `tools/gen_resources.py`：读取方块清单，批量生成贴图、blockstate JSON、model JSON、物品模型、语言条目、合成配方、掉落表。
- `src/main/java/.../block/decor/*.java`：建材方块类（主要复用 `Block`、`StairBlock`、`SlabBlock`、`WallBlock`）。

验证：

1. 生成脚本可重复执行，二次运行结果一致（幂等）。
2. 创造模式物品栏能取到全部 16 个建材。
3. 楼梯朝向、台阶合并、墙体连接正确。
4. 用四组建材搭出一间封闭小厨房，贴图无明显接缝错位。

## M3 柴火灶与柴火堆

目标：最小烹饪闭环跑通，本体锅具认灶为热源。

文件：

- `block/FirewoodStoveBlock.java`：`facing`、`LIT`、`waterlogged`，点燃与熄灭交互，雨天自灭。
- `blockentity/FirewoodStoveBlockEntity.java`：燃料槽、燃烧剩余时间、火力档（文火/中火/猛火）。
- `block/FirewoodPileBlock.java`：`count` 1-4，存取柴火。
- `util/FuelTier.java`：燃料到档位的映射。

验证：

1. 投入木板点燃，灶上方放本体炒锅，炒锅可加油、下菜、出菜。
2. 燃料耗尽自动熄灭，炒锅立即停。
3. 退出重进后燃烧进度与燃料槽内容保留。
4. 猛火档（烈焰粉）燃烧时长与中火档（煤炭）不同。

## M4 烟囱、抽油烟机与油烟呛咳

目标：排烟联动成立。

文件：

- `block/ChimneyBlock.java`：`axis`，用于烟道连通判定。
- `block/RangeHoodBlock.java` + `blockentity/RangeHoodBlockEntity.java`：红石供能、检测下方灶、吸收粒子、档位加成。
- `effect/SmokeCoughEffect.java` + 注册：靠近未排烟的点燃灶获得。
- 客户端：粒子吸收效果与工作态模型切换。

验证：

1. 红石供能后进入工作态，灶档位 +1；断电能退回。
2. 有烟囱 100%、无烟囱 50%（按动作栏与日志核对数值）。
3. 未排烟时玩家获得呛咳效果，油烟机工作范围内不获得。
4. `rangeHood.enableSmokeCough=false` 后不再获得效果。

## M5 水缸、碗柜、调料架、料理台

目标：无 GUI 储物与陈设体系完成。

文件：

- `block/WaterVatBlock.java`：四档水位、容器取水、可选消耗水位。
- `block/CupboardBlock.java` + `blockentity/CupboardBlockEntity.java`：16 格、物品标签白名单、存取与开门动画。
- `block/SpiceRackBlock.java` + `blockentity/SpiceRackBlockEntity.java`：8 格、按存量切换模型。
- `block/KitchenCounterBlock.java`：可拼接台面。
- `data/kaleidoscope_kitchenware/tags/item/cupboard_storable.json`：默认 `minecraft:bowl`、`minecraft:flower_pot`。
- `util/NoGuiStorage.java`：三者共用的存取逻辑（手持存入 / 空手取出 / Shift 强制取出）。

验证：

1. 水缸取水与注水符合配置项行为。
2. 碗柜存入碗与花盆成功、存入石头失败并提示；Shift + 右键在手持杂物时也能取。
3. 破坏碗柜与调料架后内容物保留。
4. 调料架模型随存量变化。
5. 全流程无任何界面弹出。

## M6 配置、文档与交付

目标：可交付构建。

文件：

- `config/KitchenwareConfig.java`：设计文档第 10 节的 10 项配置。
- `README.md`：功能说明、交互图示、兼容性说明、致谢。
- `LICENSE`：BSD-3-Clause（代码）与资源许可声明。
- `.gitignore` 补充 `libs/`。

验证：

1. 每项配置改动后行为随之改变。
2. `./gradlew build` 产出 jar，放入整合包可正常加载。
3. 与本体 1.6.0 同时加载无报错，实例内完成一次完整炒菜流程。

## 完成定义

- M1 至 M6 全部验收项通过。
- 设计文档第 12 节的 10 条验收标准逐条核对通过。
- 仓库提交历史按阶段划分，每个阶段一次提交。
