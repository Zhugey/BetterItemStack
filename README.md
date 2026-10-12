<img src=".github/logo.png" width="150" alt="BetterItemStack">

# BetterItemStack

> 一个可以**按容器类型分别控制**物品堆叠上限的 Minecraft 模组。
> 箱子、木桶、潜影盒、玩家背包可以堆到极大值；**漏斗与漏斗矿车保持原版上限**，
> 红石计时器与计数器因此不受影响。

- 支持 Minecraft **1.21.1**，Fabric / Forge / NeoForge 三平台
- 需要 **Java 21**
- 基于 [ItemStackProMax](https://github.com/develk-coder/ItemStackProMax) 二次开发
- 仓库：<https://github.com/Zhugey/BetterItemStack>

---

## 目录

- [它解决什么问题](#它解决什么问题)
- [工作原理](#工作原理)
- [安装](#安装)
- [配置](#配置)
- [指令](#指令)
- [版本兼容性](#版本兼容性)
- [兼容性与已知限制](#兼容性与已知限制)
- [与其他模组的冲突风险](#与其他模组的冲突风险)
- [从源码构建](#从源码构建)
- [项目结构](#项目结构)
- [许可与致谢](#许可与致谢)

---

## 它解决什么问题

原版每格能放多少，由"容器自身的上限"与"物品上限"两者取小决定（容器默认 99、普通物品 64，
实际就是 64），这个值写死在多个层面里。常见的堆叠模组会把上限全局拉高，但这会带来一个
副作用：**漏斗也是靠同一套数值判定"装满了没有"**。漏斗一旦不再"装满即停"，以 16 / 64
计数的红石计时器、计数器就全部失效——因为漏斗会一直往外搬东西。

本模组把这两件事拆开：

| 容器 | 默认行为 |
|---|---|
| 玩家背包、箱子、陷阱箱、大箱子、木桶、潜影盒、末影箱、运输矿车 | **堆到 `global_max`（默认 9999）** |
| 漏斗、漏斗矿车 | **保持原版上限**（普通物品 64、鸡蛋/雪球等 16、不可堆叠物品 1） |
| 其它未被识别的容器 | 默认不提升（可配置） |

并且：

- 哪些容器提升、哪些不提升，**完全由配置文件决定**；
- **漏斗向箱子等提升容器推送时，可以把每一格填满到 `global_max`**，而不是停在 64；
- 不需要维护任何"物品原版上限"数据表，对模组物品同样有效。

---

## 工作原理

### 一、原版的容量公式有两个来源

原版判定某格能放多少件，用的是：

```
min(容器自身每格上限,  物品上限)
```

而"物品上限"就是 `ItemStack#getMaxStackSize()`。关键在于**同一个 `getMaxStackSize()` 会被两类
完全不同的逻辑使用**：

| 使用方 | 典型调用点 | 期望值 |
|---|---|---|
| 玩家 / GUI 侧 | `Slot#getMaxStackSize(ItemStack)`、`Container#getMaxStackSize` | 提升后的上限 |
| 机器逻辑侧 | `HopperBlockEntity` 的 `isFullContainer` / `inventoryFull` / `tryMoveInItem` / `canMergeItems` | **原版上限** |

`ItemStack` 本身不知道自己在哪个容器里，所以单纯改写 `getMaxStackSize()` 必然同时影响两边。
本模组只改写右边那一项，并**在左边那一项上保留原版语义**：

```
min(容器自身的 getMaxStackSize() / 槽位自身的 getMaxStackSize(),  itemCapFor(容器, 物品))
```

- `itemCapFor(容器, 物品)`：提升容器返回 `global_max`，其它容器返回该物品的**原版上限**；
- 左边那一项**一个字都不能丢**（原因见下文「单件槽位」）。

### 二、漏斗单独处理

漏斗的容量判定**完全不经过** `Container#getMaxStackSize()`，而是直接读
`ItemStack#getMaxStackSize()`。所以只改容器接口对漏斗无效，必须逐个接回容器感知逻辑：

| 方法 | 判定对象 | 采用的上限 |
|---|---|---|
| `isFullContainer(Container, Direction)` | **目标容器** | 该容器的分类 → 箱子为 `global_max` |
| `tryMoveInItem(...)` | **目标容器** | 同上，这是真正决定"一次能搬多少"的闸门 |
| `inventoryFull(Container)` | 漏斗**自己** | 漏斗的分类（默认不提升）→ 原版 64 / 16 / 1 |
| `canMergeItems(a, b)` | — | 只判"是否同种物品"，计数交给 `tryMoveInItem` |

由此，**漏斗自己**装满原版上限后就会停下（计时器正常），而**它往箱子里推**的时候可以把
箱子填到 `global_max`。

### 三、不需要物品上限数据表

改写 `getMaxStackSize()` 之后，"这件物品原本能堆多少"在游戏内就不可考了——这也是很多同类模组
不得不维护一张上千条 JSON 表的原因。

本模组换了一条路：本代（1.20.5 起）堆叠上限是一件**物品组件** `minecraft:max_stack_size`
（声明入口仍是 `Item.Properties#stacksTo()`），所以直接读组件即可：

```java
int limit = stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
```

绕开了 `ItemStack` 上被改写的方法，因此不会受影响；而且**对模组新增物品同样有效**，
无需任何维护。

> 为什么不按"物品"取上限：本代原版的 `Item` 上**已经没有**取上限的方法——1.20.5 起
> `Item#getMaxStackSize()` 被 `Item#getDefaultMaxStackSize()` 取代，而 NeoForge 又把
> "按堆叠取值"的版本移进了扩展接口。**直接读组件是三个平台唯一都成立的写法。**

### 四、为什么必须保留"槽位自身声明的上限"

有些槽位天生只接受 1 件，它们通过覆写 `Slot#getMaxStackSize()` 来声明：

```java
// EnchantmentMenu：附魔台的输入槽
this.addSlot(new Slot(this.enchantSlots, 0, 15, 47) {
    @Override public int getMaxStackSize() { return 1; }
});
```

附魔台与附魔灌注台的判定都要求槽内**恰好 1 件**（`Item#isEnchantable(ItemStack)` 检查
`getMaxStackSize() == 1`、`BookItem` 的同名重载检查 `getCount() == 1`）。一旦绕过这个 1，
整摞物品就能被 shift-click 塞进单件槽位，判定随即失败——表现为**附魔台不显示附魔、
附魔灌注台无法使用**。

因此本模组在这一项上完全保留原版语义，并额外保证：若某槽位子类**覆写了带参数的重载**，
本模组的注入不会执行，其自定义上限天然生效。

### 五、堆叠数量提示

数量 ≥ 1000 时，鼠标悬停会在物品名下方追加一行 `堆叠: N`。

该提示注入在 `ItemStack#getTooltipLines`（所有物品提示的唯一汇总入口），而**不是**
`Item#appendHoverText`。原因：原版有 14 个物品覆写了 `appendHoverText` 却**没有调用
`super.appendHoverText(...)`**——烟花、药水箭、旗帜、旗帜图案、盾牌、弩、收纳袋、药水、
滞留药水、成书、已探索地图、鱼桶、烟花之星、唱片碎片。注入在被覆写的父类方法上，
对这些物品不会执行。

格子**角标数字**走的是另一条路径（`GuiGraphics#renderItemDecorations`），与物品类型无关。

### 六、注入点一览

| Mixin | 目标（官方映射名） | 作用 |
|---|---|---|
| `ItemStackMixin` | `ItemStack` 的静态初始化块（`<clinit>`） | 整体替换 `CODEC`，把数量区间放开到 `[1, Integer.MAX_VALUE]`（见第八节） |
| | `ItemStack#getMaxStackSize` | 把可堆叠物品的上限提升为 `global_max` |
| | `ItemStack#getTooltipLines` | 追加堆叠数量提示 |
| `InventoryMixin` | `Container#getMaxStackSize` 与 `#getMaxStackSize(ItemStack)`（`@Overwrite`） | 按容器类型区分每格容量 |
| `SlotMixin` | `Slot#getMaxStackSize(ItemStack)` | GUI 侧容量，保留槽位自身声明的上限 |
| `ScreenHandlerMixin` | `AbstractContainerMenu#addSlot` | 客户端 GUI 的镜像容器按界面类型登记，与服务端判定对齐 |
| `HopperBlockEntityMixin` | `HopperBlockEntity#isFullContainer` / `#tryMoveInItem` / `#inventoryFull` / `#canMergeItems` | 漏斗四处判定接回容器感知逻辑 |
| `ItemEntityMixin` | `ItemEntity#merge` | 掉落物合并上限 |
| `DrawContextMixin`（客户端） | `GuiGraphics#renderItemDecorations` | 角标数字缩写为 K / M / B |

> 表中用 **Mojang 官方映射名**——全平台共用一份源码，必须统一映射。Fabric 侧的产物在构建时
> 会被重映射到 intermediary；Forge / NeoForge 自 1.20.6 起在**运行期**就是官方命名，
> 那两份产物因此不需要 refmap。

### 七、三处安全夹取（防止调低上限时丢物品 / 出现负数增量）

`global_max` 可以在游戏内用 `/bis set` 调低，而本代原版有 6 处会拿"容器上限"当参数去做
**破坏性截断**：

> **1.20.5 起原版多了"插入即截断"**：`setItem` 之后会调用
> `stack.limitSize(this.getMaxStackSize(stack))`（`BaseContainerBlockEntity`、`HopperBlockEntity`、
> `SimpleContainer`、`AbstractFurnaceBlockEntity`、`MerchantContainer`、`ContainerEntity` 共 6 处）。
> `ItemStack#limitSize` 的实现就是 `setCount(maxCount)`——**超出的部分被销毁且不返还**。
> 于是把上限从 9999 调到 1000 之后，箱子里任何一次 `setItem` 都会把已有的 9999 截成 1000，
> 一次性丢掉 8999 个。

因此本模组把"容器每格上限"的返回值**夹到不小于堆叠当前数量**，让那 6 处截断自然变成 no-op；
同时另有两处要防止算出**负数增量**：

1. `ContainerPolicy#capacityFor`（由 `InventoryMixin` 的两个覆写引用）：返回值永不小于
   `stack.getCount()` —— 一处覆盖上面那 6 个截断点；
2. `SlotMixin#getMaxStackSize(ItemStack)`：GUI 侧一次最多放入上限以内的数量，
   返回值同样夹到不小于槽内当前数量（原版 `Slot#tryRemove` 会据此算 `上限 − 当前数量`）；
3. `HopperBlockEntityMixin#tryMoveInItem`：保证漏斗传输的增量恒为非负
   （`ItemStack#split(负数)` / `grow(负数)` 会让数量朝**反方向**变化，可被用于复制物品）。

正常情形下（当前数量不超过上限）这些夹取不产生任何影响。效果是**已有堆叠不会被回溯截断，
容量只在"插入时"生效**——玩家背包本来就没有截断这一步，现在箱子与它行为一致。

副作用是良性的：那些拿该返回值判断"还能不能合并 / 还剩多少空间"的原版逻辑
（`Inventory`、`DispenserBlockEntity`、`SimpleContainer`、`AbstractContainerMenu` 等），
会一致地把超量堆叠视为"已满"，增量也恒为非负。

### 八、数量本身也要装得下：本代的瓶颈是**校验型 Codec**

把上限抬到几千之后还有一件容易忽略的事：**数量在数据模型里也是有范围的**。本代原版把
`ItemStack.CODEC` 的数量字段写成：

```java
ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1)
```

`intRange` 是**校验型**编解码器——越界返回**错误**，而不是截断。所以"只放大
`getMaxStackSize()` 而不动 Codec"的后果是：**数量超过 99 的物品根本存不下来**，
而且**不会**像 1.20 一代那样悄悄变成另一个数量。

| 路径 | 用到的编解码器 | 不处理会怎样 |
|---|---|---|
| 存档（区块、玩家背包、方块容器、掉落物实体） | `ItemStack.CODEC` / `OPTIONAL_CODEC` | 数量 > 99 时**编码失败**，物品保存不下去 |
| 创造模式取物包 | `ItemStack.CODEC`（经 `validatedStreamCodec` 复校） | 未装本模组的一端会在解码时直接报错 |
| 其它网络包 | `OPTIONAL_STREAM_CODEC` | **不受影响**——数量字段本来就是 `VarInt` |

本模组的做法是在 `ItemStack` 静态初始化块末尾整体替换 `CODEC`，除把数量区间放开到
`[1, Integer.MAX_VALUE]` 外，`id` 与 `components` 两项与原版**逐字一致**；同时把由它派生、
**按值捕获**了旧实例的 `OPTIONAL_CODEC` 一并重建——不重建的话，创造模式保存的物品栏（热键栏）
在编码时会失败，表现为该格被静默替换为空。

> **为什么不改传输层**：1.20.5 起网络里的数量已经是 `VarInt`，没有 8 位限制。所以 1.0.0
> （1.20.1）里那两条"把 `writeByte` 改成 VarInt""给存档补写 `putInt`"的补丁在本代**全部作废**：
> 它们的宿主方法在本代已经不存在，照搬会因找不到注入目标而在**启动时**报错——构建期不报错。

**兼容性**：数量 ≤ 99 时与未装本模组的一端完全兼容（网络格式一致、Codec 区间也够用）。
数量超过 99 时，**联机的两端都需要安装本模组**：未装的一端要么在解码创造模式取物包时报错，
要么在保存含这类堆叠的容器时报错。

### 九、与 Stackable 的对比：瓶颈从"有符号字节"换成了"校验型 Codec"

[Stackable](https://github.com/Colin12345678910/Stackable) 的 README 里有一段很坦诚的解释：

> Minecraft stores all items with a single signed byte which for our purposes only can be
> between -128 to 127, since negative values are invalid that leaves a maximum stack size of 127.
>
> As of Stackable 1.1.4 and Minecraft 1.20.5, this mod now can work with stack sizes above 127,
> however, **for versions below 1.20.5, the following is still accurate.**

这段话点出了分水岭——**Minecraft 1.20.5**，也正好是本分支所在的世代。

#### 1.20.5 之前：上限是"有符号字节"

原版把数量直接塞进一个字节（网络 `writeByte`、NBT `putByte("Count")`），单字节只能表达
−128 ~ 127；而 `ItemStack#isEmpty()` 的判定是 `count <= 0`，所以数量越过 127 不是"变少"，
而是**整格消失**。把上限设在 127，等于在不触碰编码格式的前提下，取编码能安全承载的最后一格。

#### 1.20.5 起：序列化换成 Codec，但多了一个 99

这一代起，物品的存档与网络改由 **Codec** 描述，数量字段是 `ExtraCodecs.intRange(1, 99)`。
于是瓶颈换了个形式：不再有"整格消失"这种粗暴故障，但**数量 > 99 的物品直接存不下来**
（`intRange` 是校验型的，越界返回错误而非截断），而且这个限制藏在数据模型里，
不像单字节那样一眼看得出来。

#### 本模组在 1.21.1 上做的事

| 层 | 原版 | 不处理会怎样 | 本模组 |
|---|---|---|---|
| 物品上限 | `ItemStack#getMaxStackSize()` → 64 / 16 / 1 | 堆不过原版值 | 返回 `global_max` |
| 容器每格上限 | `Container#getMaxStackSize(ItemStack)` = `min(99, 物品上限)` | 界面与插入逻辑仍按 64 | 按容器类型分别给值，**漏斗显式保持原版** |
| **存档 / 校验** | `ItemStack.CODEC` 的数量区间 `[1, 99]` | 数量 > 99 **保存失败** | 在 `<clinit>` 末尾整体替换 `CODEC`，区间放开到 `[1, Integer.MAX_VALUE]` |
| 插入即截断 | `limitSize(this.getMaxStackSize(stack))`（共 6 处） | 调低上限时一次性销毁超量堆叠 | 容量返回值夹到不小于当前数量 |
| 客户端 GUI | 镜像容器不被识别 | 显示与拖拽预览都按 64 | 登记镜像容器 |

关键只有**存档与校验**这一条——它一旦解开，数量的存储就与物品上限彻底无关了。

#### 那到底能堆到多少

物品组件与 Codec 都是 32 位，理论上限约 **21.4 亿**。实际可用值由配置文件的 `global_max`
决定（默认 9999），可随时在游戏内用指令调整。

#### 顺带一个定位上的差异

Stackable 是"把**所有**容器的堆叠上限一起抬高"；本模组默认只提升玩家背包与箱子、木桶、
潜影盒、末影箱等存储容器，**漏斗与漏斗矿车保持原版上限**——因为漏斗一旦不再"装满即停"，
以 16 / 64 计数的红石计时器、计数器就会全部失效。这一行为可由配置调整（见 `containers`）。

#### 前提

数量 > 99 时，**联机的两端都需要安装本模组**（见[第八节](#八数量本身也要装得下本代的瓶颈是校验型-codec)）。

---

## 安装

三个平台各有一份 jar，**按你使用的加载器选其中一个**：

| 加载器 | 前置 | 装哪个 jar |
|---|---|---|
| **Fabric** | [Fabric Loader](https://fabricmc.net/use/installer/) ≥ 0.16.1 + [Fabric API](https://modrinth.com/mod/fabric-api) | `BetterItemStack-1.21.1-fabric-<版本>.jar` |
| **Forge** | [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/) 52.1.x | `BetterItemStack-1.21.1-forge-<版本>.jar` |
| **NeoForge** | NeoForge 21.1.x | `BetterItemStack-1.21.1-neoforge-<版本>.jar` |

把对应的 jar 放进 `.minecraft/mods/` 即可，运行时需要 **Java 21**。

> **关于 1.21.1 的 NeoForge**：这一代 NeoForge 已经是**独立实现**（包名是 `net.neoforged`、
> 模组 ID 是 `neoforge`），与 Forge 产物**互不通用**——这与 1.20.1 时代"两者互为镜像、
> 可以互换"正好相反。仍然**不要同时装两个。**

---

## 配置

配置文件：`.minecraft/config/betteritemstack.json`

```json
{
  "configVersion": 2,
  "global_max": 9999,
  "nonStackableItems": [],
  "containers": {
    "mode": "blacklist",
    "list": ["hopper", "hopper_minecart"],
    "unknown": false
  }
}
```

### `containers` 容器提升策略

| 字段 | 说明 |
|---|---|
| `mode` | `"blacklist"`（默认）：除 `list` 中列出的容器外，全部提升；`"whitelist"`：只提升 `list` 中的容器 |
| `list` | 容器键名列表，见下表 |
| `unknown` | 仅 `blacklist` 模式有效：未识别的容器（含其它模组新增的容器）是否也提升，**默认 `false`** |

> **为什么 `unknown` 默认是 `false`**：未识别的容器往往带有自己的容量语义——很多 GUI 模组
> 会给输入槽单独声明"只接受 1 件"。把它们一起提升会绕过这类保护，导致模组功能失效。
> 需要为其它模组的存储容器也开启提升时，再改为 `true`。

### 容器键名

| 键名 | 对应容器 |
|---|---|
| `player_inventory` | 玩家背包 |
| `chest` | 箱子 · 陷阱箱 · 大箱子 |
| `barrel` | 木桶 |
| `shulker_box` | 潜影盒 |
| `ender_chest` | 末影箱 |
| `chest_minecart` | 运输矿车 |
| `hopper` | 漏斗 |
| `hopper_minecart` | 漏斗矿车 |
| `dropper` | 投掷器 |
| `dispenser` | 发射器 |
| `furnace` | 熔炉 · 高炉 · 烟熏炉 |
| `brewing_stand` | 酿造台 |
| `crafter` | 合成器 |

### 其它字段

| 字段 | 说明 |
|---|---|
| `global_max` | 提升后的每格上限，必须 ≥ 1，默认 9999 |
| `nonStackableItems` | 不参与提升的物品 ID 列表（如 `minecraft:bed`），保持各自的原版上限 |

### 配置示例

**让木桶也保持原版上限**（`blacklist` 模式）：

```json
"containers": {
  "mode": "blacklist",
  "list": ["hopper", "hopper_minecart", "barrel"]
}
```

**只提升指定的几个容器**（白名单模式）：

```json
"containers": {
  "mode": "whitelist",
  "list": ["player_inventory", "chest", "barrel", "shulker_box"]
}
```

**让床、鞍等不可堆叠物品保持原样**：

```json
"nonStackableItems": ["minecraft:bed", "minecraft:saddle", "minecraft:cod_bucket"]
```

修改配置后可在游戏内执行 `/bis reload` 立即生效。

---

## 指令

所有输出都已国际化：文案从语言文件读取，游戏语言为中文时显示中文，其它语言显示对应译文，
未收录的语言自动回退到英文。详见 [语言](#语言)。

| 指令 | 权限 | 说明 |
|---|---|---|
| `/bis` | 所有人 | 显示 Mod 版本与可用指令 |
| `/bis get` | 所有人 | 显示当前堆叠上限 |
| `/bis info` | 所有人 | 显示提升规则、可用容器键名与配置示例 |
| `/bis set <数量>` | OP | 设置堆叠上限并写回配置文件 |
| `/bis reload` | OP | 从磁盘重新读取配置 |

`/bis info` 输出示例（游戏语言为简体中文）：

```
=== BetterItemStack 设置 ===
当前堆叠上限: 9999
提升规则: 黑名单模式 - 下列容器保持原版，其余全部提升
保持原版的容器: hopper（漏斗） · hopper_minecart（漏斗矿车）
未识别的容器（含其它模组）: 保持原版
可用容器键名:
player_inventory（物品栏） · chest（箱子 · 陷阱箱） · barrel（木桶） · shulker_box（潜影盒） ·
ender_chest（末影箱） · chest_minecart（运输矿车） · hopper（漏斗） · hopper_minecart（漏斗矿车） ·
dropper（投掷器） · dispenser（发射器） · furnace（熔炉 · 高炉 · 烟熏炉） · brewing_stand（酿造台） ·
crafter（合成器）
--- 示例: 如何让更多容器保持原版 ---
黑名单模式下，想让木桶也保持原版上限，就在 config/betteritemstack.json 的 containers.list 里
加入 "barrel"，改完执行 /bis reload。
  "containers": { "mode": "blacklist", "list": ["hopper", "hopper_minecart", "barrel"] }
```

同一份输出在英文环境下：

```
=== BetterItemStack settings ===
Current stack limit: 9999
Boost rule: blacklist - the containers listed below keep vanilla limits, all others are boosted
Containers kept vanilla: hopper(Hopper) · hopper_minecart(Minecart with Hopper)
Unrecognized containers (including other mods): kept vanilla
Available container keys:
player_inventory(Inventory) · chest(Chest / Trapped Chest) · barrel(Barrel) · ...
```

---

## 版本兼容性

| Minecraft | 结论 |
|---|---|
| **1.21.1** | ✅ 三平台各一份 |
| 1.20.1 | ✅ 由 `1.20.1` 分支的 1.0.x 提供（那一代数量走手写字节流，机制与本分支不同） |
| 其它版本 | ❌ 不适用。1.21 一代的其它小版本未做验证；1.20.4 及更早没有物品组件，本构建会因符号缺失而无法启动 |

三平台产物的映射命名空间不同：Fabric 的产物处于 **intermediary**（构建时重映射），
Forge / NeoForge 自 1.20.6 起**运行期就是官方命名**，产物无需再重混淆。
**产物与编译目标始终是绑定的**——Mixin 的注入目标、`@Shadow` 成员这些符号一旦在目标版本里
被改名或移除，就会以 `NoSuchMethodError` 或 `Mixin apply failed` 直接失败。

### `gradle.properties` 里的版本号分别管什么

这些**都只影响构建环境**，但其中一部分会进入产物元数据，成为**玩家可见的硬门槛**。

| 配置 | 玩家可见 | 作用 |
|---|---|---|
| `minecraft_version` | ✅ 进 `depends.minecraft` / `mods.toml` | 编译目标（1.21.1） |
| `minecraft_version_range` | ✅ 同上 | 声明支持的区间 |
| `fabric_loader_requires` | ✅ 进 `fabric.mod.json` 的 `depends.fabricloader` | Fabric 玩家门槛，取**能工作的最低版本** |
| `forge_version_range` / `neoforge_version_range` | ✅ 进 `mods.toml` 的依赖段 | Forge / NeoForge 玩家门槛 |
| `fabric_api_version` | ❌ | 编译期 Fabric API，决定"代码最多能用多新的 API" |
| `fabric_loader_version` | ❌ | 编译期 Fabric Loader |
| `forge_version` / `neoforge_version` | ❌ | 编译期的 Forge / NeoForge 版本 |
| `loom_version` / `moddev_version` / `forge_gradle_version` / `mixin_gradle_version` | ❌ | 构建插件的版本 |
| `neo_form_version` | ❌ | 供 `common` 子项目独立编译用的 NeoForm 版本 |
| `java_version` | ✅ 进元数据 | 编译目标（本代 = 21） |

Fabric Loader 与 Fabric API 都向后兼容（新版能跑旧模组），所以门槛设低只会放宽、不会收紧。

构建插件相反——Loom 要按**打算编译的最高 MC 版本**选，且硬性耦合 Gradle 与 JDK：

| Loom | 要求的 Gradle | 要求的 JDK |
|---|---|---|
| 1.11.8 / 1.12.7 / **1.13.6** | **8.14** | **21** |
| 1.14.10 / 1.15.5 | 9.2.0 | 21 |
| 1.16.3 | 9.4.0 | 21 |
| 1.17.21 | 9.5.0 | 21 |
| 1.18.3 | 9.7.0 | 25 |

本项目用 **Gradle 8.14.2 + JDK 21**，对应当前取值 **Loom 1.13.6**。
**不要用 `-SNAPSHOT`**——浮动版本会让构建不可复现。

### 在哪里查版本

- <https://fabricmc.net/develop/> —— 官方推荐页，选定 MC 版本后直接给出 loader / Fabric API / Loom
- 机器可读的 meta API：
  - `https://meta.fabricmc.net/v2/versions/loader/<MC版本>`
- Maven 目录（可浏览全部历史版本与发布日期）：
  - Fabric API `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/`
  - Loom `https://maven.fabricmc.net/net/fabricmc/fabric-loom/`
  - Loader `https://maven.fabricmc.net/net/fabricmc/fabric-loader/`
  - Forge `https://maven.minecraftforge.net/net/minecraftforge/forge/`
  - NeoForge `https://maven.neoforged.net/releases/net/neoforged/neoforge/`（1.20.5 起；
    1.20.1 一代的坐标是 `net/neoforged/forge/`）

---

## 兼容性与已知限制

1. **漏斗自己保持原版**：只能装到原版上限，因此以 16 / 64 计数的红石计时器与计数器不受影响。
   漏斗向提升容器推送时会把每格填到 `global_max`。
2. **单件槽位不受影响**：附魔台输入槽、铁砧、砂轮等通过覆写 `Slot#getMaxStackSize()` 声明的
   上限被完整保留，附魔台与附魔灌注台可正常工作。
3. **未识别的容器默认不提升**。其它模组的存储容器若不在键名表内会保持原版上限；
   需要提升时把 `containers.unknown` 改为 `true`。
4. 原版上限为 1 且不可损坏的物品（床、鞍、鱼桶、唱片等）会变为可堆叠。
   如不需要，请加入 `nonStackableItems`。
5. 堆叠数量提示在数量 ≥ 1000 时显示；角标数字在 ≥ 1000 时缩写为 K / M / B。
6. 本模组不提供物品上限数据表——原版上限直接读自 `minecraft:max_stack_size` 物品组件，
   对模组物品同样有效。
7. **调低 `global_max` 不会销毁已有物品**：已经超过新上限的堆叠会原样保留，
   只是此后无法再把更多物品放进该格（GUI 侧一次最多放入新上限以内的数量）。
   这与玩家背包的行为一致，详见第七节。
8. **数量不再受原版 99 的限制**：本代原版把数量区间写死在校验型 Codec 里（`intRange(1, 99)`），
   数量超过 99 的物品**保存不下来**。本模组替换了该 Codec，详见第八节。
9. **数量超过 99 时，联机的两端都要安装本模组**（见第八节）。

---

## 与其他模组的冲突风险

本模组直接改写原版行为，**凡是触及同一批原版目标的模组都可能互相干扰**。
下面把本模组实际改动的原版位置全部列出，方便出问题时对号入座。

### 本模组触及的原版目标

| Mixin | 改动的原版位置 |
|---|---|
| `ItemStackMixin` | `ItemStack` 的静态字段 `CODEC` / `OPTIONAL_CODEC`（`<clinit>`）、`getMaxStackSize`、`getTooltipLines` |
| `InventoryMixin` | `Container#getMaxStackSize` 与 `#getMaxStackSize(ItemStack)` |
| `SlotMixin` | `Slot#getMaxStackSize(ItemStack)` |
| `ScreenHandlerMixin` | `AbstractContainerMenu#addSlot` |
| `HopperBlockEntityMixin` | `HopperBlockEntity` 的 `isFullContainer`、`tryMoveInItem`、`inventoryFull`、`canMergeItems` 里的 `ItemStack#getMaxStackSize()` |
| `ItemEntityMixin` | `ItemEntity#merge` |
| `client.DrawContextMixin` | `GuiGraphics#renderItemDecorations` |

### 几类最容易冲突的模组

1. **同类堆叠模组**——风险最高。它们与本模组抢的是同一批目标，尤其 `ItemStack#getMaxStackSize()`。
   两个模组都注入这个方法时，**先执行的那个生效**，后一个的返回值被直接丢弃，而且**不会有任何报错**
   ——表现为"其中一个完全没起作用"。**请勿与本模组同时安装其它堆叠模组。**
2. **改动物品序列化的模组**——见下方特别提示，本模组的风险最高。
3. **漏斗 / 物流 / 红石类模组**——它们常改写 `HopperBlockEntity`，与本模组的四处改动重合。
4. **容器界面类模组**——改动 `Slot#getMaxStackSize`、`AbstractContainerMenu` 或数量文字绘制的模组，
   可能与本模组在 GUI 侧的改动叠加。

### 冲突会以什么形式出现

- **启动即崩**：日志里出现 `Mixin apply for mod betteritemstack failed` 或
  `Critical injection failure`。这类多半是 `@Redirect` 找不到目标——目标已被另一个模组改掉。
  这是**最好的一种**，问题立刻可见。
- **静默失效**：两个模组都注入成功、语义互相抵消，游戏照常启动，只是行为不对。最难查。
- **运行期错乱**：数量显示或同步异常、整格物品消失、GUI 行为怪异。

### 排查步骤

1. 先看 `logs/latest.log` 里有没有 Mixin 相关的 `ERROR`；
2. 只留本模组（+ 原版）跑一次，确认单独工作正常；
3. 二分法把其它模组加回来，定位到具体哪一个。

### 特别提示：本模组替换了物品的 `ItemStack.CODEC`

本代原版把数量区间写死在校验型 Codec 里（`intRange(1, 99)`），本模组在 `ItemStack` 的
静态初始化块末尾整体替换了 `CODEC`（详见第八节）。由此带来两类额外风险：

- **任何同样替换或包装 `ItemStack.CODEC` 的模组都可能冲突**——两边各自持有自己的实例时，
  后执行的那个生效，另一方的改动静默失效；
- **数量 > 99 时联机两端都要装本模组**：只装一端的话，未装的一端会在创造模式取物包的解码
  或容器保存时报错（**数量 ≤ 99 时仍然完全兼容**）。

---

## 从源码构建

`common/` 存放全部逻辑与资源，`fabric/`、`forge/`、`neoforge/` 是三个平台子项目。
`common` 不产出 jar，而是把源码与资源目录导出给平台子项目，由每个平台各自编译一遍。

```bash
./gradlew build               # 构建三个平台的产物
./gradlew :fabric:build       # 只构建某一个平台
./gradlew :fabric:runClient   # Fabric / NeoForge 的开发环境客户端
./gradlew :neoforge:runServer # Fabric / NeoForge 的开发环境服务端
./gradlew :forge:Client       # Forge 走 ForgeGradle，任务名没有 run 前缀
./gradlew :forge:Server
```

产物在各平台的 `build/libs/` 下：

- `fabric/build/libs/BetterItemStack-1.21.1-fabric-<版本>.jar`
- `forge/build/libs/BetterItemStack-1.21.1-forge-<版本>.jar`
- `neoforge/build/libs/BetterItemStack-1.21.1-neoforge-<版本>.jar`

> Fabric 下还会有一份 `-dev.jar` 落在 `build/devlibs/`，那是**未重映射**的中间产物，
> 只用于本地调试，不要分发。Forge 侧设了 `reobf = false`（本代运行期就是官方命名），
> 因此只有 `build/libs` 一份。

构建环境：Gradle 8.14.2 + JDK 21（`java_version = 21` 是编译目标，与运行 Gradle 的 JDK 无关）。
不生成 `-sources.jar`。push / PR 会触发 GitHub Actions 构建，产物作为 Artifacts 上传（保留 90 天）。

### 发布新版本

推 tag 会自动创建 GitHub Release：正文取自 `CHANGELOG.md` 中 `## <版本号>` 的小节，
附件是三个平台的 jar。

1. `CHANGELOG.md` 新增 `## <版本号> — <日期>` 一节
2. `gradle.properties` 的 `mod_version` 改成同一版本号
3. `git tag v1.0.0 && git push origin v1.0.0`

tag 带不带 `v` 前缀都可以，但**只用版本号，不要写 MC 区间**——CI 是拿 tag 名去
`CHANGELOG.md` 里找小节，匹配不上会静默回退成把整份日志当成 Release 正文。

在 IDEA 里：`Git` → `New Tag...` 打 tag，再 `Git` → `Push...` 并勾选底部的 `Push tags`
（tag 只在本地不会触发 CI）。

### 推了 tag 却没有 Release

1. `git ls-remote --tags origin` 确认 tag 真的到了远程；
2. 到 `Actions` 页看是否多了一次运行；没有就是 tag 没推送成功；
3. 有运行但失败：多半是仓库没开 Actions 写权限
   （`Settings` → `Actions` → `General` → `Workflow permissions` 选 `Read and write permissions`）。

普通 push / PR 不会创建 Release，只上传 Actions Artifact（需登录才能下载，90 天过期）。

---

## 项目结构

```
common/                                  平台无关的全部逻辑与资源
├── src/main/java/com/zhugey/betteritemstack/
│   ├── BetterItemStack.java             核心入口：配置加载、/bis 指令（输出走翻译键）
│   ├── Config.java                      配置读写与静态 global_max
│   ├── ContainerPolicy.java             容器分类：是否提升、物品层上限、格子层上限
│   ├── VanillaMax.java                  读取"原版上限"（读 max_stack_size 组件，不受本模组改写影响）
│   ├── platform/
│   │   ├── Platform.java                平台抽象（配置目录 / 模组版本号）
│   │   └── Platforms.java               经 ServiceLoader 惰性解析实现
│   └── mixin/
│       ├── ItemStackMixin.java          替换 CODEC、物品上限、堆叠数量提示
│       ├── InventoryMixin.java          容器每格上限按类型区分（并抵消原版的"插入即截断"）
│       ├── SlotMixin.java               GUI 槽位容量（保留槽位自身声明的上限）
│       ├── ScreenHandlerMixin.java      客户端镜像容器登记
│       ├── HopperBlockEntityMixin.java  漏斗容量判定接回容器感知逻辑
│       ├── ItemEntityMixin.java         掉落物合并上限
│       └── client/DrawContextMixin.java 角标数字缩写
└── src/main/resources/
    ├── icon.png                         模组图标（三平台共用，也是仓库 README 的图）
    ├── betteritemstack.mixins.json      6 个公共 mixin + 1 个客户端专属
    ├── pack.mcmeta
    └── assets/betteritemstack/
        └── lang/                        10 种语言

fabric/                                  Fabric 入口 + fabric.mod.json + Platform 实现
forge/                                   Forge 入口（ForgeGradle）+ META-INF/mods.toml + Platform 实现
neoforge/                                NeoForge 入口（ModDevGradle）+ META-INF/neoforge.mods.toml + Platform 实现

buildSrc/                                两个约定插件
├── multiloader-common.gradle            公共配置：产物名 / 工具链 / jar manifest / 资源占位符替换
└── multiloader-loader.gradle            把 common 的源码与资源并进当前平台

gradle.properties                        所有版本号与元数据的唯一来源
```

容器名取自 Minecraft 官方语言文件里 `block.minecraft.*` 的权威译名，生成时校验各语言键集合一致。

---

## 许可与致谢

- **License**：MIT
- 基于 [develk-coder/ItemStackProMax](https://github.com/develk-coder/ItemStackProMax) 二次开发，
  原作者 develk。上游项目同样采用 MIT 授权，本项目一并保留其版权与许可声明。

### 相关模组

- [Staaaaaaaaaaaack](https://modrinth.com/mod/staaaaaaaaaaaack) — 在物品实体生成前完成合并，
  并优化了背包爆炸运算，与本模组搭配效果良好
- [Stackable](https://github.com/Colin12345678910/Stackable) — 同类堆叠模组。它自述在 1.20.5
  之前受"有符号字节"的物理限制只能堆到 127，1.20.5 起才突破；本模组的两个分支各自解决了
  其中一代的问题。两者的技术路线对比见上文第九节。

### 语言

已内置 10 种语言，未收录的语言自动回退到 `en_us`：

| 语言 | 代码 | 语言 | 代码 |
|---|---|---|---|
| English (US) | `en_us` | Русский | `ru_ru` |
| 简体中文 | `zh_cn` | Deutsch | `de_de` |
| 繁體中文 | `zh_tw` | Français | `fr_fr` |
| 日本語 | `ja_jp` | Español | `es_es` |
| 한국어 | `ko_kr` | Português (Brasil) | `pt_br` |

添加/修正语言：直接编辑 `common/src/main/resources/assets/betteritemstack/lang/<locale>.json`。
注意两点——各语言的**键集合要保持一致**（缺键会显示原始键名），以及每个键的 `%s` 个数要与
代码中的调用匹配。非中英文的译文欢迎在 Issues 里提出修正。
