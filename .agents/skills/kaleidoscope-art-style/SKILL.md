---
name: kaleidoscope-art-style
description: >
  Generate Minecraft 16x16 / 32x32 pixel textures and block models that match the
  Kaleidoscope (森罗物语) series art style, reverse engineered from Kaleidoscope
  Cookery. Use when creating or reviewing textures, block models, blockstates or
  item models for a Kaleidoscope addon — 森罗物语, Kaleidoscope Cookery, 附属模组,
  厨房mod, 中式家具贴图, Blockbench 模型. Triggers include requests to draw a new
  block texture "in the Kaleidoscope style", to keep an addon's art consistent with
  the base mod, or to convert a colour idea into an in-style texture.
---

# 森罗物语美术风格

把"看起来像森罗物语"翻译成可执行的规则。数据来源：Kaleidoscope Cookery 1.6.0 jar 内
`assets/kaleidoscope_cookery/textures/block` 的 220 张贴图与 1046 个模型，全部实测提取。

只学规则，不要复制本体像素。本体资源为 CC BY-NC-SA 4.0。

## 0. 协议边界

本 skill 是从 Kaleidoscope Cookery 的成品**反推出的风格规律**，来源只有观察与统计，**不含该模组的任何素材文件**。

- 本体美术资源是 `LICENSE-ASSETS`，采用 **CC BY-NC-SA 4.0**（署名 / 非商业 / 相同方式共享）：
  - **署名**：用到这套风格时，注明"风格参考自 Kaleidoscope 森罗物语系列"。
  - **非商业**：**不得用于任何商业用途**（包括付费整合包、付费材质包、接单作品）。
  - **相同方式共享**：基于该风格产出的新素材，必须以 **CC BY-NC-SA 4.0** 发布。
- 本体代码是 `LICENSE-CODE`（BSD 3-Clause），与本 skill 无关。
- **不要做**：把本体的 png 拷进工程；拿本体贴图当模板描摹；把生成结果放进任何收费分发。
- **可以做**：照规律自己画、按色板自己配色、按结构自己搭模型。

## 1. 调色板

### 1.1 共享描边与中性色

所有树种、所有家具共用同一组暗部三色，这是让系列风格统一的关键：

| 用途 | 色值 |
| --- | --- |
| 最深描边 | `#5E3723` |
| 次深阴影 | `#6D442F` |
| 暗部过渡 | `#82563F` |

金属与瓷器：

| 用途 | 色值 |
| --- | --- |
| 金属暗 | `#606572` |
| 金属亮 | `#6E7179` `#747474` `#7F7F7F` `#8B8B8B` |
| 瓷白 | `#F5F5F5` `#EFEEE2` `#DAD7C3` |
| 点缀金 | `#BD9538` `#D2AB4C` |

### 1.2 木种色阶（由亮到暗）

| 木种 | 色阶 |
| --- | --- |
| oak | `#C1A266` `#BA975A` `#AE8E52` `#A38249` `#937544` |
| birch | `#D5C78A` `#CFBD81` `#C8B277` `#BAA36A` `#B19860` `#A68E57` |
| spruce | `#8A663A` `#7D5D37` `#6C5532` `#5F4F2B` |
| jungle | `#BF9165` `#B4865A` `#A58A52` `#A08048` `#907640` |
| acacia | `#C46340` `#AC603F` `#A25937` |
| dark_oak | `#82563F` `#6D442F` `#554229` `#483924` `#3B3020` |
| mangrove | `#7A3731` `#6F302A` `#5D241E` |
| cherry | `#DF9CA4` `#D99499` `#D2898D` `#C88185` `#BE7470` `#B26A67` |
| crimson | `#A5486C` `#963E62` `#89395B` `#7E3452` `#6F2843` |
| warped | `#3DA9A6` `#3B9997` `#318D8C` `#2E8578` `#28786C` `#21685D` |
| bamboo | `#B9DB5F` `#ADBE4D` `#98B145` `#8A9A3D` `#809038` `#758333` `#657030` |

木种色阶与共享暗部三色拼起来使用：主体用木种色阶，轮廓与投影切到共享三色。

### 1.3 砖瓦色板（本体无素材，新拟，遵循同一明度阶梯）

| 用途 | 色阶 |
| --- | --- |
| 青砖 | `#7A8B8C` `#617274` `#4E5E60` `#3C4A4C` `#2F3A3C` |
| 青瓦 | `#6E7C84` `#55636C` `#38424A` `#2A3138` |
| 白灰墙 | `#F2EFE6` `#E7E3D8` `#D8D3C6` |
| 夯土 | `#A3814F` `#8A6A47` |
| 灶砖 | `#683A2F` `#4E3A39` |

## 2. 尺寸规则

| 对象 | 尺寸 |
| --- | --- |
| 平面方块、常规方块、建材 | 16×16 |
| 立体小件（锅、笼、灯、盘、缸、灶） | 32×32 |
| 大型家具（桌、凳、蒸笼） | 64×64 |
| 巨件（石磨） | 128×128 |
| 染色变体 | 32×32，走 MC 16 色，背景 alpha=0 |

一张贴图可以被多个模型复用：本体 `chair/oak.png` 与 `cook_stool/oak.png` 字节相同，
靠 UV 取不同区域。新模型优先复用同组材质的已有贴图，需要新纹理才新建。

## 3. 绘制规则

1. **色阶数量**：单张 16×16 用 4-8 阶（本体实测单图 11-29 色，含反锯齿过渡，不必照抄）。简单建材 4-5 阶即可。
2. **轮廓**：朝外的边缘用共享最暗色 `#5E3723` 勾 1 像素；不要用纯黑。
3. **顶面高光**：方块顶面或家具顶沿加 1 行最亮色，模拟受光。
4. **透明**：非满方块贴图的外侧留 `alpha=0`，不要用白色填充。
5. **木纹**：沿纹理方向画 2-3 条比主体深一阶的细纹，不要拉通整张。
6. **砖石**：错缝排列，勾缝用最暗阶，砖面用中间阶，单块砖顶部 1 行亮阶。
7. **瓦片**：竖向瓦垄，垄脊亮、垄沟暗；屋面从上到下逐行加深一阶表现纵深。
8. **禁止**：渐变工具生成的高光带、圆角描边、超过 8 阶的细腻过渡 —— 与本系列 16px 的糙感不搭。

## 4. 模型规则

- Blockbench 导出，顶层键：`elements`、`textures`、`display`，必要时加 `texture_size`、`render_type`、`gui_light`。
- `elements` 的 `from`/`to` 允许两位小数（本体实例有 `1.8`、`11.75`），坐标单位是 1/16 格。
- 每个面显式写 `uv`，避免自动推 UV 造成的贴图错位。
- 镂空件（竹帘、窗棂、挂串）写 `"render_type": "minecraft:cutout"`。
- 需要正面受光的写 `"gui_light": "front"`。
- `display` 覆盖全部 8 个姿态：gui、ground、fixed、thirdperson_righthand/lefthand、firstperson_righthand/lefthand、head。
- 纯立方体走 `parent: minecraft:block/cube` 只写六面纹理与 `particle`。
- 结构件（桌、凳、台面）做成 `single` / `left` / `middle` / `right` 四种模型，按 `block/<topic>/<variant>` 分目录。

## 5. blockstate 规则

- 朝向 `facing`，轴向 `axis`，点亮 `lit`，含水 `waterlogged`，结构状态 `has_*` / `half` / `part` / `level`。
- 状态组合多时用 `variants`；部件独立变化时用 `multipart`。
- 可拼接平面结构用 `position` 属性（本体餐桌 32 个变体即由此产生）。
- 楼梯、台阶、墙直接用原版 `StairBlock` / `SlabBlock` / `WallBlock` 的方块状态，不要自定义。

## 6. 命名规范

- 注册名：小写下划线，语义直白 —— `firewood_stove`、`water_vat`、`cupboard`、`spice_rack`、`kitchen_counter`、`blue_brick`、`roof_tile_stairs`。
- 中文名走口语化中式：柴火灶、水缸、碗柜、调料架、料理台、青砖、青瓦、屋脊瓦、竹帘；不要用"XX方块""XX号"。
- 贴图路径与注册名对齐：`textures/block/<name>.png`；多态放 `textures/block/<topic>/<variant>.png`。

## 7. 生成脚本

`scripts/new_texture.py` 按纹理类型确定性生成贴图，避免手绘 16×16 的重复劳动：

```bash
python scripts/new_texture.py out.png --kind brick --ramp "#4E5E60,#3C4A4C,#2F3A3C" --seed 7
python scripts/new_texture.py out.png --kind roof_tile --ramp "#55636C,#38424A,#2A3138"
python scripts/new_texture.py out.png --kind plank --ramp "#C1A266,#AE8E52,#937544,#82563F" --species oak
python scripts/new_texture.py out.png --kind plaster --ramp "#E7E3D8,#D8D3C6"
python scripts/new_texture.py out.png --kind tile_floor --ramp "#617274,#4E5E60,#2F3A3C"
python scripts/new_texture.py out.png --kind lattice --ramp "#937544,#82563F" --size 16
```

`--seed` 决定噪点分布；同一组参数重复执行结果完全一致（幂等）。

## 8. 美术理念

前面几节讲的是做法，这一节讲判断依据。遇到没画过的家具时按这六条推，出来的东西就不会跑偏。

### 8.1 器物先立骨，再铺面

森罗的家具几乎都是"框架 + 面板"两层：木构（柱、梁、边框）用深木色描边，面材（板、席、布、石）填中间。
先画骨，面自然就位；反过来先填面，边框会显得像事后补上去的。

### 8.2 一种材质只讲一件事

木头讲纹理与年轮，陶讲釉色与斑驳，金属讲高光与转折，布讲经纬与垂坠。
不要把两种材质的画法混进同一格 —— 人靠质感认物，不靠颜色。

### 8.3 顶部亮、底部沉

16×16 里光从上来：顶面最亮、侧面中间调、底面最暗。
这是让方块"立起来"的主要手段，比描边更关键。

### 8.4 一切取中式器物的形

蹲下来看要是中式：直口、束腰、鼓腹、回纹、竹节、榫接、青瓷、陶土、夯土。
不要用西式的圆润把手、玻璃门、金属包角 —— 一个部件就能把整个系列拉出戏。

### 8.5 复用色板，少开新色

所有木种、所有家具共用同一组描边三色。系列感来自"少而稳"，不是"多而准"。
新家具优先从 `references/palette.json` 取色，确实缺色再补，补完回写 palette。

### 8.6 留白比填满高级

器物的"空"要敢留：盘心、缸口、格心、席面。
填满了像杂物堆；留出空，器物才有呼吸感，也才看得出本身是个"容器"。

## 9. 自检清单

生成贴图或模型后逐条核对：

1. 轮廓用的是共享暗色 `#5E3723`，不是纯黑。
2. 色阶数量在 4-8 之间，没有平滑渐变带。
3. 顶面或顶沿有 1 行高光。
4. 非满方块外侧为透明，不是白色。
5. 同组材质复用了公共色阶，木材颜色与本文表格一致。
6. 模型 8 个 display 姿态齐全，每个面有显式 uv。
7. 可拼接结构的四种模型都在，blockstate 的 `position` 变体齐全。
8. 中文名符合口语化中式习惯。
9. 与本体贴图并排看不跳色（拿本体 `chair/oak.png` 当基准比对）。

## 10. 参考

- 调色板机器可读版本：`references/palette.json`
- 目标工程：`kaleidoscope_kitchenware`（森罗物语：家什）
- 设计文档：该工程 `docs/superpowers/specs/`
