## 中文

灶台重做，并补上配套的大铁锅。

### 重做：柴火灶 → 柴火灶台

- 由单格改为**两格整体**：左右各一个锅眼，前侧每格一个烧火口，中间一道砖墙隔开
- 原版**红砖**砌体新贴图，锅眼内壁做煤灰暗部，点燃时烧火口透亮
- 两格**各自烧各自的**：独立燃料槽、独立燃烧时间、独立火力档，互不影响
- 破坏任一格，两格一起消失，只掉 1 个；爆炸会一并处理，孤儿格自动清理
- **锅眼只坐大铁锅**：本体的炒锅 / 汤锅 / 蒸笼 / 茶壶不能再放到锅眼上，会提示

### 新增：大铁锅

- 只能坐在柴火灶台的锅眼上，锅身沉进灶膛，锅口与灶面齐平
- **一口锅两种功能**，由第一次投入决定：
  - 先放油（本体油脂 / 带油的锅铲 / 油壶）→ **炒菜**，走本体的炒锅配方
  - 先放汤底（水桶等本体认可的汤底）→ **烧汤**，走本体的汤锅配方
  - 空锅随时可以改主意；锅里还有东西时不能改
- 时间、状态、容器（碗 / 盘）、"翻炒不够出迷之炒菜"、糊锅掉木炭、在点燃的灶上翻锅被烫 —— 全部照本体炒锅的规矩
- 锅里能看到食材与成品；油面与汤面随状态换模型
- **破坏大铁锅会连锅带内容一起掉**（本体炒锅是吞掉的，这里刻意不学）
- 与锅铲 / 调味盘联动免费生效：大铁锅实现了本体的 `IPot` 接口

### 已知限制

- 没有专门的 HUD 与 Jade 信息面板（本体那两个按它自己的方块硬编码），状态提示走动作栏
- 依赖 `instanceof PotBlockEntity` 的机械臂类附属不会识别大铁锅
- 烧汤不需要锅盖：本体汤锅不盖盖不推进，大铁锅直接开烧，这是刻意的差异

### 从 1.0.0 升级

旧存档里已放置的柴火灶只保留一格，看起来像"半截灶台"（1.0.0 的灶没有左右格属性）。
打掉重摆一次即可，其余方块不受影响。

### 环境要求

| 项 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 及以上 |
| **森罗物语：厨房** | **1.6.0 及以上（必需前置）** |

---

## English

The range is rebuilt, and the wok that goes with it is new.

### Rebuilt: the Firewood Stove

- One block became **two**: a burner each, a fire mouth each, a shared brick wall between them
- Plain **red brick** textures, soot inside the burners, and a fire mouth that glows when lit
- **Each half burns on its own**: its own fuel slot, its own burn time, its own heat grade
- Breaking either half takes both, and drops exactly one stove; blasts are handled, and an
  orphaned half cleans itself up
- **The burners only take the iron wok now**: the base mod's wok, stockpot, steamer and teapot can
  no longer be placed on top, and the action bar says so

### New: the Iron Wok

- It only sits on a stove burner, its bowl sunk into the firebox so the rim ends up flush with the
  brick counter
- **One pan, two jobs**, decided by what goes in first:
  - oil first (the oil item, an oiled shovel, an oil pot) → **stir frying**, using the base mod's
    pot recipes
  - a soup base first (a water bucket, or anything else the base mod accepts) → **soup**, using its
    stockpot recipes
  - an empty wok can still change its mind; a loaded one cannot
- The timings, the states, the carrier rules, the "not stirred enough" suspicious stir fry, the
  charcoal a burnt pan leaves and the burns you get for fumbling a dish on a lit stove are all the
  base mod's own rules
- Ingredients and the finished dish are visible in the pan; the oil and soup surfaces are their own
  models
- **Breaking the wok hands back the wok and everything in it**, which the base mod's pot does not
- The kitchen shovel and the seasoning tray work on it for free: the wok implements the base mod's
  `IPot`

### Known limits

- No dedicated HUD and no Jade panel: both are hard-wired to the base mod's own blocks, so feedback
  goes to the action bar instead
- Automation addons that check `instanceof PotBlockEntity` will not recognise the wok
- Soup needs no lid here. The base mod's stockpot does not cook without one; this wok does, on
  purpose

### Upgrading from 1.0.0

A stove placed by 1.0.0 keeps one half and reads as half a range (that version had no left/right
state). Break it and place it again; nothing else changes.
