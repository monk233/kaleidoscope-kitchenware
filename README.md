# 森罗物语：家什 / Kaleidoscope Kitchenware

[中文](#中文) · [English](#english)

给 [森罗物语：厨房 (Kaleidoscope Cookery)](https://modrinth.com/mod/kaleidoscope-cookery) 的附属模组。
本体有全套厨具，却缺了摆放、清洗和打水的地方 —— 这个模组补上这些。

An addon for [Kaleidoscope Cookery](https://modrinth.com/mod/kaleidoscope-cookery).
The base mod ships every pot and every knife; what it lacks is somewhere to stand the dishes, wash
them, and draw water. That is what this adds.

---

## 中文

### 特性

| 方块 | 说明 |
| --- | --- |
| **柴火灶台** | 原版红砖砌的两格灶台：左右各一个锅眼、前侧各一个烧火口，两格各自投柴、各自燃烧。燃料分文火（木板）、中火（煤炭）、猛火（烈焰粉）三档；雨天无遮挡会自灭，烧尽自动熄灭。**锅眼只坐大铁锅** |
| **大铁锅** | 坐进锅眼、锅口与灶面齐平。**一口锅两种功能**：先放油就炒菜，先放汤底就烧汤，都走本体的配方。锅里看得见食材与成品，打掉连锅带内容一起掉 |
| **水缸** | 陶土鼓腹水缸，四档水位。手持空桶或空瓶右键打水，默认是无限水源；锅铲也能舀水。缸口是空的，可以跳进去 |
| **调味盘** | 白瓷四格浅盘，一格见方。每格只收一种调料、可存 1024 个，右键某一格存取、全程无界面；打掉只掉一个调味盘，内容随物品走，换位置放下仍在 |
| **碗架** | 上下两层的木架，每层收 64 个碗或花盆。碗与花盆竖立朝外、从左边依次排开；右键放入、空手取出一层、潜行取一个 |

**锅铲的六条链路** —— 全部走本体已有的锅 API，不另起一套：

1. 从调味盘舀一份调料 → 右键炒锅下锅
2. 从搪瓷盆子舀油 → 交回本体处理
3. 从碗架取一只碗或花盆 → 拿着它
4. **带碗的锅铲右键做好的锅** → 盛出成品
5. 舀水：水缸、水方块、含水方块、满水的炼药锅都认；再右键水源可倒回
6. 带水的锅铲右键汤锅 → 加水

**物品栏里看得见锅铲拿着什么** —— 左上角画着所携带的调料、碗或水桶，而不是"有附魔光效但
不知道拿了啥"。

### 交互

**本模组没有任何容器界面。** 所有存取都在世界里用鼠标完成，反馈走动作栏文本与音效。

| 操作 | 行为 |
| --- | --- |
| 手持调料**右键调味盘某一格** | 整组存进**你指的那一格**（该格只收一种调料） |
| **空手右键某一格** | 取出一组（64） |
| **潜行 + 空手右键** | 取出一个 |
| 手持碗 / 花盆**右键碗架某一层** | 从左边放入，一层只收一种 |
| **空手右键某一层** | 取出该层全部 |
| **潜行 + 空手右键** | 取出一个 |
| **打掉调味盘** | 只掉 1 个，内容写在物品里，不掉散件 |
| **打掉碗架** | 内容物散落掉出，另掉 1 个碗架 |

**柴火灶台与大铁锅**

| 操作 | 行为 |
| --- | --- |
| 手持燃料**右键某一格灶台** | 那一格投柴，两格互不影响 |
| 打火石 / 火焰弹右键 | 点燃该格；锅铲或铲类右键熄灭；空手右键看火力与剩余燃料 |
| 把**本体**的炒锅 / 汤锅 / 蒸笼 / 茶壶放到锅眼上 | 放不下，动作栏提示锅眼上只能放大铁锅 |
| 手持大铁锅右键灶台顶面 | 坐进锅眼，朝向跟着灶台 |
| 手持油（或带油锅铲 / 油壶）右键大铁锅 | 放油，进入**炒菜**模式 |
| 手持水桶等汤底右键大铁锅 | 放汤底，进入**烧汤**模式 |
| 手持食材右键 | 下料；空锅时不能下料，得先放油或汤底 |
| 手持锅铲右键 | 翻炒，翻炒次数用完才算炒好，否则出锅的是迷之炒菜 |
| 空手右键 | 取回最后一次下的料 |
| 手持配方要的碗 / 盘右键 | 盛出成品（需要数量足够）；没有容器的菜要**潜行 + 锅铲**盛 |
| 打掉大铁锅 | 锅与锅里所有内容一起掉出 |

炒糊的锅会掉出木炭；在点燃的灶上翻锅失败会被烫一下 —— 两条都照本体炒锅的规矩。

### 配置

`config/kaleidoscope_kitchenware-common.toml`

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `stove.fuelBurnMultiplier` | `1.0` | 柴火灶台燃料燃烧时长倍率 |
| `waterVat.consumesLevel` | `false` | 打水是否消耗水位。关（默认）时水缸是无限水源 |

### 数据包扩展

- `kaleidoscope_kitchenware:seasoning` —— 调味盘收哪些调料。默认：本体的油脂、青红辣椒，以及原版糖。
- `kaleidoscope_kitchenware:cupboard_storable` —— 碗架收哪些东西。默认：`minecraft:bowl`、`minecraft:flower_pot`。

两者都能用数据包追加，不需要改代码。

### 环境要求

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 及以上 |
| **森罗物语：厨房** | **1.6.0 及以上（必需，不是可选）** |

**本体为什么是必需的**：大铁锅的炒菜与烧汤都查本体的配方（`ModRecipes` 的四套配方类型与汤底表），
锅铲、调味盘与盛菜的联动全走本体的 `IPot` 接口。灶台自己在点亮时提供热源，不需要本体的标签。

### 开发

```bash
./gradlew build          # 产出 build/libs/KaleidoscopeKitchenware-<mc>-<mod>.jar
./gradlew runClient      # 开发客户端（同时加载本体与 JEI）
./gradlew runServer      # 开发服务端，会跑一次启动自检
```

**前置 jar**：把本体放进 `libs/`，文件名必须与 `gradle.properties` 的
`deps.kaleidoscope_cookery` 一致（当前 `kaleidoscopecookery-1.6.0-neoforge+mc1.21.1.jar`）。
该 jar 不随仓库分发，CI 会自动从 Modrinth 拉取。

**美术资源由脚本生成，不手绘**：

```bash
python tools/gen_decor.py        # 重新生成全部贴图、模型、blockstate、配方与语言文件
python tools/check_resources.py  # 自检：JSON 合法性、文件齐全、语言条目一致、模型元素合法
PREVIEW_SCALE=20 python tools/preview_model.py out.png <模型.json> [x,y 偏移] ...   # 等距预览
```

`preview_model.py` 把模型按等距视角画成 PNG，贴图会真正映射上去 —— 开游戏之前就能看出锅眼
开在哪个面、烧火口贴图有没有贴错面。堆叠预览用偏移，例如大铁锅坐进灶台：

```bash
python tools/preview_model.py out.png src/main/resources/.../models/block/iron_wok.json 0,16 \
    src/main/resources/.../models/block/firewood_stove_right.json 0,0
```

改贴图**先读仓库内的 skill**：`.agents/skills/kaleidoscope-art-style/`。它写清了色板、尺寸、
绘制与模型规则，以及三条踩过的坑：

1. **贴图要均匀** —— 方块的面只采样贴图的一部分，横纹会按层重复、描边线会被裁到任意位置，
   看起来就是"莫名其妙的色块"；
2. **模型元素的旋转只有 45° 与 22.5°**，且不能越出方块边界；
3. **不复制本体的任何素材文件**，只学规律（见该 skill 第 0 节）。

**启动自检**：每次启动会跑一次 `TraySelfTest`，把调味盘的存储与拾取链路验一遍，日志里一行
`PASS` 或 `FAIL`。动过存储相关的代码先看它。

### 许可

- **代码与资源：[MIT](LICENSE)**
- 美术**风格**参考自 [森罗物语系列](https://modrinth.com/mod/kaleidoscope-cookery)（其美术为 CC BY-NC-SA 4.0）。
  本仓库**未使用该项目的任何素材文件**，只研究了它的视觉规律以保持一致。
- 二次使用本模组的美术时，注明"风格参考自森罗物语系列"即可。

---

## English

### Features

| Block | What it does |
| --- | --- |
| **Firewood Stove** | A two cell range of plain red brick: one burner and one fire mouth per cell, each half fuelled and lit on its own. Fuel comes in three grades — planks, coal, blaze powder. Rain puts it out unless covered, and it goes out when the fuel runs down. **The burners only take the iron wok** |
| **Iron Wok** | It sits in a burner with its rim flush with the brick counter. **One pan, two jobs**: oil first for a stir fry, a soup base first for a soup, both through the base mod's recipes. Ingredients and the finished dish show in the pan, and breaking it hands the wok and its contents back |
| **Water Vat** | A brown clay pot with four water levels. Right click with an empty bucket or bottle to draw water; unlimited by default. The shovel can scoop from it too, and the mouth is hollow, so you can climb in |
| **Seasoning Tray** | A white porcelain dish with four wells, one block square. Each well takes a single kind of seasoning, up to 1024, and you use the well you point at. No interface anywhere. Breaking it drops one tray carrying its contents, so it can be put down anywhere |
| **Dish Rack** | Two wooden shelves, 64 bowls or flower pots each. Pieces stand on edge facing out, filling from the left. Right click to put one on, empty hand to clear a shelf, sneak to take one |

**Six things the kitchen shovel can do**, all through the base mod's own pot API rather than a
parallel implementation:

1. Scoop seasoning from the tray, then right click a wok to add it
2. Scoop oil the way the base mod's enamel basin does
3. Lift a bowl or flower pot off the rack
4. **Right click a finished pot while carrying a bowl on the shovel** to serve the dish out
5. Draw water from a vat, a water block, a waterlogged block or a full cauldron; right click a
   source again to tip it back
6. Right click a stockpot while carrying water to fill it

**The shovel shows what it carries**: the top left of its inventory slot draws the seasoning, bowl
or water bucket, instead of an enchantment shimmer that says "something" without saying what.

### Controls

**There is no container interface.** Everything happens in the world with the mouse, and feedback
comes through the action bar and sound effects.

| Action | Result |
| --- | --- |
| Right click a tray well holding seasoning | The whole stack goes into **the well you point at**; a well takes one kind of thing |
| Right click a well empty-handed | Take out a stack (64) |
| Sneak + right click | Take out one |
| Right click a rack shelf holding a bowl or pot | Put it on, filling from the left; one kind per shelf |
| Right click a shelf empty-handed | Take the whole shelf |
| Sneak + right click | Take one |
| Break a seasoning tray | Drops **one** tray with its contents inside; nothing scatters |
| Break a dish rack | Contents drop loose, plus the rack |

**The range and the wok**

| Action | Result |
| --- | --- |
| Right click a stove cell holding fuel | That cell takes it; the two halves burn separately |
| Flint and steel / fire charge | Lights that cell; a shovel puts it out; an empty hand reports fuel and heat |
| Place a base mod wok, stockpot, steamer or teapot on a burner | Refused, with an action bar line: the burners only take the iron wok |
| Right click a stove top holding the iron wok | It sinks into the burner and takes the range's facing |
| Right click the wok with oil (the oil item, an oiled shovel, an oil pot) | Oiled, and the wok becomes a **stir fry** pan |
| Right click the wok with a soup base (a bucket of water, and the rest) | The wok becomes a **soup** pot |
| Right click with an ingredient | Goes in; an empty wok refuses until it is oiled or has a base |
| Right click with the kitchen shovel | Stirs. Stir it enough or the dish comes out as the suspicious stir fry |
| Right click empty-handed | Takes the last ingredient back out |
| Right click with the bowl or plate the recipe asks for | Serves the dish (enough of them in hand); dishes with no carrier need a sneaking shovel |
| Break the wok | The wok and everything in it drop |

A pan left too long drops charcoal, and fumbling a dish on a lit range burns you — both exactly as
the base mod's own pot behaves.

### Configuration

`config/kaleidoscope_kitchenware-common.toml`

| Option | Default | Meaning |
| --- | --- | --- |
| `stove.fuelBurnMultiplier` | `1.0` | How long stove fuel burns |
| `waterVat.consumesLevel` | `false` | Whether drawing water drains the vat; off means an endless source |

### Datapack

- `kaleidoscope_kitchenware:seasoning` — what the tray accepts. Defaults to the base mod's oil, red
  and green chilli, plus vanilla sugar.
- `kaleidoscope_kitchenware:cupboard_storable` — what the rack accepts. Defaults to
  `minecraft:bowl` and `minecraft:flower_pot`.

Both can be extended by a datapack; no code change needed.

### Requirements

| Item | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 or above |
| **Kaleidoscope Cookery** | **1.6.0 or above (required, not optional)** |

**Why the base mod is required**: the wok cooks stir fries and soups through the base mod's own
recipes (its four recipe types and its soup base table), and the shovel, the tray and the serving
paths all go through its `IPot` interface. The range provides heat on its own; it does not need the
base mod's heat source tag.

### Development

```bash
./gradlew build          # produces build/libs/KaleidoscopeKitchenware-<mc>-<mod>.jar
./gradlew runClient      # dev client, with the base mod and JEI loaded
./gradlew runServer      # dev server, runs the startup self test
```

**The base mod jar** goes in `libs/`, named to match `deps.kaleidoscope_cookery` in
`gradle.properties` (currently `kaleidoscopecookery-1.6.0-neoforge+mc1.21.1.jar`). It is not
redistributed here; CI fetches it from Modrinth.

**Art is generated, not drawn by hand**:

```bash
python tools/gen_decor.py        # regenerate every texture, model, blockstate, recipe and lang file
python tools/check_resources.py  # check json, missing files, lang keys and model elements
python tools/preview_model.py out.png <model.json> [x,y offset] ...   # isometric preview
```

`preview_model.py` draws a model to a PNG in isometric view with its textures mapped on, so a burner
cut into the wrong face or a fire mouth on the wrong side is visible before the game is ever
launched. Stack blocks by giving an offset:

```bash
python tools/preview_model.py out.png src/main/resources/.../models/block/iron_wok.json 0,16 \
    src/main/resources/.../models/block/firewood_stove_right.json 0,0
```

**Read the bundled skill before touching art**: `.agents/skills/kaleidoscope-art-style/`. It records
the palette, sizes, drawing and modelling rules, and three pitfalls worth knowing:

1. **Keep textures even.** A block face samples only part of the sheet, so horizontal bands repeat
   once per layer and outline lines land wherever a face crops them — which is exactly what "stray
   colour blocks" turn out to be.
2. **Model elements rotate only by 45° or 22.5°**, and may not leave the block.
3. **Copy no asset from the base mod**, only its rules (see section 0 of that skill).

**Startup self test**: `TraySelfTest` runs once per launch and checks the tray's storage and pickup
paths, logging a single `PASS` or `FAIL`. Look there first after touching storage code.

### Licence

- **Code and assets: [MIT](LICENSE)**
- The art **style** follows the [Kaleidoscope series](https://modrinth.com/mod/kaleidoscope-cookery),
  whose own art is CC BY-NC-SA 4.0. No asset file from that project is used here; only its visual
  rules were studied to stay consistent.
- If you reuse this project's art, crediting "style after the Kaleidoscope series" is appreciated.
