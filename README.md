<img src=".github/logo.png" width="150" alt="BetterItemStack">

# BetterItemStack

> 一个可以**按容器类型分别控制**物品堆叠上限的 Minecraft 模组。
> 箱子、木桶、潜影盒、玩家背包可以堆到极大值；**漏斗与漏斗矿车保持原版上限**，
> 红石计时器与计数器因此不受影响。

- **支持的游戏版本**：**Minecraft 1.20.1 / 1.21.1**，Fabric / Forge / NeoForge 三平台
- **一个游戏版本一个分支**：源码请切到对应分支查看
  （[`1.20.1`](https://github.com/Zhugey/BetterItemStack/tree/1.20.1) /
  [`1.21.1`](https://github.com/Zhugey/BetterItemStack/tree/1.21.1)）；
  本 README 描述的是**当前分支**，即 Minecraft 1.20.1
- **构建产物从 [Releases](https://github.com/Zhugey/BetterItemStack/releases) 下载** ——
  Release 标题带 `(Minecraft x.y.z)`，按你的游戏版本对号即可
- 需要 **Java 17**（Minecraft 1.21.1 需要 Java 21）
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

原版把"每格最多 64 件"写死在多个层面里。常见的堆叠模组会把上限全局拉高，但这会带来一个
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

本模组换了一条路：1.20 一代还没有物品组件，原版上限就声明在 `Item.Properties#stacksTo()` 里、
由 `Item#getMaxStackSize()` 暴露，所以直接读**物品级**上限即可：

```java
int limit = stack.getItem().getMaxStackSize();
```

绕开了 `ItemStack` 上被改写的方法，因此不会受影响；而且**对模组新增物品同样有效**，
无需任何维护。

### 四、为什么必须保留"槽位自身声明的上限"

有些槽位天生只接受 1 件，它们通过覆写 `Slot#getMaxStackSize()` 来声明：

```java
// EnchantmentMenu：附魔台的输入槽
this.addSlot(new Slot(this.enchantSlots, 0, 15, 47) {
    @Override public int getMaxStackSize() { return 1; }
});
```

附魔台与附魔灌注台的判定都要求槽内**恰好 1 件**（`Item#isEnchantable` 检查
`getMaxStackSize() == 1`、`BookItem` 的同名方法检查 `getCount() == 1`）。一旦绕过这个 1，
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
| `ItemStackMixin` | `ItemStack#getMaxStackSize` | 把可堆叠物品的上限提升为 `global_max` |
| | `ItemStack#save` / `ItemStack#of` | **落盘**的数量由字节改为 int（见第八节） |
| | `ItemStack#getTooltipLines` | 追加堆叠数量提示 |
| `PacketByteBufMixin` | `FriendlyByteBuf#writeItem` / `#readItem` | **网络包**里的数量由字节改为 VarInt（见第八节） |
| `ServerPlayNetworkHandlerMixin` | `ServerGamePacketListenerImpl#handleSetCreativeModeSlot` 里的 `getCount()` | 创造模式取物的服务端校验由硬编码 64 改为真实上限（见第八节） |
| `InventoryMixin` | `Container#getMaxStackSize`（`@Overwrite`） | 按容器类型区分每格容量 |
| `SlotMixin` | `Slot#getMaxStackSize(ItemStack)` | GUI 侧容量，保留槽位自身声明的上限 |
| `ScreenHandlerMixin` | `AbstractContainerMenu#addSlot` | 客户端 GUI 的镜像容器按界面类型登记，与服务端判定对齐 |
| `HopperBlockEntityMixin` | `HopperBlockEntity#isFullContainer` / `#tryMoveInItem` / `#inventoryFull` / `#canMergeItems` | 漏斗四处判定接回容器感知逻辑 |
| `ItemEntityMixin` | `ItemEntity#merge` | 掉落物合并上限 |
| `DrawContextMixin`（客户端） | `GuiGraphics#renderItemDecorations` | 角标数字缩写为 K / M / B |

> 本代（1.20 一代）**没有** 1.20.5+ 的那个 `ItemStack` 静态初始化注入：那一版起原版把数量
> 收窄成 `rangedInt(1, 99)` 才需要重写 Codec；本代原版 Codec 里数量本来就是不限范围的
> `Codec.INT`，不需要动。
>
> 表中用 **Mojang 官方映射名**（1.20.1 上它与 Forge 的 MCP 名一致）——全平台共用一份源码，
> 必须统一映射。

### 七、两处安全夹取（防止调低上限时出现负数增量）

`global_max` 可以在游戏内用 `/bis set` 调低。此时若某个格子里的数量已经超过新上限，
原版有几处会据此算出**负数增量**：

> **本代暂无 `ItemStack#capCount`**：1.20.5 起原版多了 6 处
> `stack.limitSize(this.getMaxStackSize(stack))`，会把超量堆叠**静默截断**（一次丢掉几千个），
> 那边必须把上限返回值夹到不小于当前数量来抵消。本代还没有这条路径，
> 所以这里只剩"负数增量"这一类风险。

1. `SlotMixin#getMaxStackSize(ItemStack)`：保证 GUI 侧一次最多放入上限以内的数量，
   返回值同时夹到不小于槽内当前数量（原版 `Slot#tryRemove` 会据此算 `上限 - 当前数量`）；
2. `HopperBlockEntityMixin#tryMoveInItem`：保证漏斗传输的增量恒为非负
   （`ItemStack#split(负数)` / `grow(负数)` 会让数量朝**反方向**变化，可被用于复制物品）。

正常情形下（当前数量不超过上限）这两处夹取不产生任何影响。

### 八、数量本身也要装得下：两处 8 位上限

把上限抬到几千之后还有一件容易忽略的事：**原版存放数量的地方只有 8 位**，而且本代这两处
都不经过 Codec，所以必须逐个处理，漏一个就会出现"数量错乱甚至整格消失"。

| 路径 | 原版写法 | 截断后果 | 本模组的改法 |
|---|---|---|---|
| 网络包 | `FriendlyByteBuf#writeItem` 里 `writeByte(count)`；`readItem` 里 `readByte()` | 128 ~ 255 读成负数、256 的整数倍读成 0；`ItemStack#isEmpty()` 对 `count <= 0` 为 true，于是**整格物品消失**（打开箱子、拾取、GUI 点击都会触发整包同步） | `PacketByteBufMixin` 改成 `writeVarInt` / `readVarInt` |
| 存档（区块、玩家数据、掉落物实体） | `ItemStack#save` 里 `putByte("Count", (byte) count)` | 同上；重进世界后数量错乱 | `ItemStackMixin` 在 `save` 的 TAIL 补一个 `putInt("Count")` 覆盖，并在 `of` 的 RETURN 用 `getInt` 还原 |

**为什么读侧要用 `@ModifyVariable` 而不是等方法返回后再修**：原版拿到被截断的字节后会立刻
`ItemStack.of(...)`；若截断值恰好是 0，这个栈已经成了空栈、物品信息丢失，
返回后再改也救不回来。必须在"存进局部变量"那一步就把真实数量换回去。

**兼容性**：VarInt 对 0 ~ 127 就是单个字节，取值与原 `writeByte` 完全一致；NBT 侧同理
（`putInt(64)` 与 `putByte((byte) 64)` 读出来都是 64）。因此对端没装本模组时，只要数量没超过
127 就仍能正确解析，不会错位；只有超过 127 才会不一致——而那本来也只有装了本模组才会出现。

#### 附：还有一处不在字节层，但同样会让整摞物品消失——创造模式取物的 64 硬编码校验

`ServerGamePacketListenerImpl#handleSetCreativeModeSlot` 里有一段防作弊校验（本代**直接写死 64**，
字节码里是一次 `bipush 64`）：

```java
boolean valid = stack.isEmpty()
        || stack.getDamageValue() >= 0 && stack.getCount() <= 64 && !stack.isEmpty();
```

数量超过 64 的堆叠会被**静默丢弃**（既不 `setItem` 也不回包）。客户端因为本模组放大了
`getMaxStackSize()`，点一下就是整整一摞、看起来成功；服务端却没接收。一打开箱子触发整包同步，
客户端背包被服务端的真实状态覆盖，那一格就没了——**现象与上面两处字节截断完全一样，原因却完全不同**。

1.20.5 起原版才把这里改成 `getCount() <= getMaxStackSize()`；本代需要用
`ServerPlayNetworkHandlerMixin` 把那次 `getCount()` 替换成「是否超限」的哨兵值，
等价地把 `count > 64` 改写成 `count > getMaxStackSize()`。

> 为什么不直接把常量 64 改掉：`bipush` 只能承载 -128 ~ 127，而 `global_max` 可以在游戏内
> 随意调高，写死任何值都会在某个上限下重新失效。

### 九、与 Stackable 的对比：同类模组为何停在 127，本模组如何做到任意数量

[Stackable](https://github.com/Colin12345678910/Stackable) 的 README 里有一段很坦诚的解释：

> Minecraft stores all items with a single signed byte which for our purposes only can be
> between -128 to 127, since negative values are invalid that leaves a maximum stack size of 127.
> Moreover, this mod is intended to be compatible with other mods which may also use signed
> bytes to store items.
>
> As of Stackable 1.1.4 and Minecraft 1.20.5, this mod now can work with stack sizes above 127,
> however, **for versions below 1.20.5, the following is still accurate.**

这段话把问题说得很准：**127 不是设计选择，而是"有符号字节"的物理上限**；
它同时点出了分水岭——**Minecraft 1.20.5**。

#### 127 是怎么来的

原版有两处把数量直接塞进**一个有符号字节**：

| 路径 | 原版写法 | 单字节能表达的范围 |
|---|---|---|
| 网络包 | `FriendlyByteBuf#writeItem` 里 `writeByte(stack.getCount())` | −128 ~ 127 |
| 存档 NBT | `ItemStack#save` 里 `putByte("Count", (byte) count)` | 同上 |

后果分三档：

- **0 ~ 127**：正常；
- **128 ~ 255**：读回来是**负数**（128 → −128，200 → −56）；
- **256 的整数倍**：读回来是 **0**。

而 `ItemStack#isEmpty()` 的判定正是 `count <= 0`——所以数量一旦越过 127，物品不是"变少"，
而是**整格消失**。把上限设在 127，等于**在不触碰编码格式的前提下，取编码能安全承载的最后一格**。

#### 分水岭为什么是 1.20.5

1.20.5 起，原版把物品的网络与存档序列化**从手写字节流换成了 Codec**（`ItemStack.CODEC`），
数量在数据模型里由 `rangedInt(1, 99)` 描述。Codec 是**自描述**的、范围写在模型里，
因此只要重写那个范围就能放开上限——**完全不需要碰传输层的字节**。

这正好解释了 Stackable 的两个时间点：它能突破 127 是在 1.20.5 及以后（自 1.1.4 起），
而 1.20.5 之前不能——那一代原版还是手写字节流，数量就是一个 `writeByte`。

#### 本模组改的是编码本身

本模组的目标是 **1.20.1**——**没有 Codec 可改**的那一代。没有"等原版"这条路，
于是只能自己把两条编码路径替换掉：

| 层 | 原版 | 不处理会怎样 | 本模组 |
|---|---|---|---|
| 物品层上限 | `ItemStack#getMaxStackSize()` → 64 / 16 / 1 | 堆不过原版值 | 返回 `global_max`；带耐久物品与黑名单物品保持原版 |
| 容器与槽位容量 | `Container#getMaxStackSize()` = 64 | 界面与插入逻辑仍按 64 | 按容器类型分别给值，**漏斗显式保持原版** |
| **网络编码** | `writeByte` / `readByte`（8 位） | 数量 >127 即错乱 | `PacketByteBufMixin` 改为 **VarInt** |
| **存档编码** | `putByte("Count")` / `getByte` | 存档里的数量被截断 | `ItemStackMixin` 在 `save` 的 TAIL 用 `putInt` **覆盖**，`of` 用 `getInt` 还原 |
| 服务端校验 | 写死 `getCount() <= 64` | >64 静默丢弃 | 改写成"是否超过当前上限" |
| 客户端 GUI | 镜像容器不被识别 | 显示与拖拽预览都按 64 | 登记镜像容器 |
| 漏斗 | 64 停抽 | 红石计时器 / 计数器失效 | 显式保持原版 |

关键只有**网络**与**存档**两条。这两条一旦解开，数量的存储与传输就与物品上限彻底无关了。

#### 为什么用 VarInt，而不是直接换成 4 字节 int

`writeItem` 是一条**位置相关**的二进制流：数量字段后面紧跟 NBT。若把 1 字节换成固定
4 字节的 `writeInt`，包长度与字段偏移全部改变，**任何未装本模组的一端都会从 NBT 处开始错位**。

VarInt 的价值正在于此：

- 对 **0 ~ 127**，VarInt 恰好是一个字节，而且**逐位与原来的 `writeByte` 完全相同**；
- 更大的值才逐级扩展（最多 5 字节，覆盖 32 位有符号范围）。

也就是说，**数量在 127 以内时，本模组的包与原版逐字节一致**。没装本模组的一端读到小数量仍然完全
正常；只有真的出现 >127 的数量才会不一致——而那本来也只有装了本模组才会产生。

NBT 侧没有这个顾虑：NBT 是**自描述**格式（每个标签自带类型与长度），把 `Count` 由 ByteTag 换成
IntTag 不会破坏结构。未装本模组的一端会把数量按低 8 位读（`getByte` 遇到 IntTag 会走
`byteValue()`），数量偏小，但存档不会损坏。

这也回应了 Stackable 自述里的另一层顾虑——它停在 127，还因为它希望与**其它同样用有符号字节
存数量的模组**保持兼容。VarInt 恰好满足这一点：**小数量下逐位与原版相同**，按单字节解析的
模组照常工作；只有真正出现大数量时才要求两端都装本模组。

#### 那到底能堆到多少

编码这一层已经不再是瓶颈：VarInt 与 NBT `int` 都是 32 位，理论上限约 **21.4 亿**。
实际可用值由配置文件的 `global_max` 决定（默认 9999），可随时在游戏内用指令调整。

#### 顺带一个定位上的差异

Stackable 是"把**所有**容器的堆叠上限一起抬高"；本模组默认只提升玩家背包与箱子、木桶、
潜影盒、末影箱等存储容器，**漏斗与漏斗矿车保持原版上限**——因为漏斗一旦不再"装满即停"，
以 16 / 64 计数的红石计时器、计数器就会全部失效。这一行为可由配置调整（见 `containers`）。

#### 前提

数量 >127 时，**联机的两端都需要安装本模组**：只装一端的话，另一端仍按原版 8 位解析——
网络包会从数量字段之后开始错位，而 NBT 侧只是数量偏小、不会崩。

---

## 安装

三个平台各有一份 jar，**按你使用的加载器选其中一个**：

| 加载器 | 前置 | 装哪个 jar |
|---|---|---|
| **Fabric** | [Fabric Loader](https://fabricmc.net/use/installer/) ≥ 0.14.22 + [Fabric API](https://modrinth.com/mod/fabric-api) | `BetterItemStack-1.20.1-fabric-<版本>.jar` |
| **Forge** | [Forge](https://files.minecraftforge.net/net/minecraftforge/forge/) 47.x | `BetterItemStack-1.20.1-forge-<版本>.jar` |
| **NeoForge** | NeoForge 47.1.x | `BetterItemStack-1.20.1-neoforge-<版本>.jar` |

把对应的 jar 放进 `.minecraft/mods/` 即可，运行时需要 **Java 17**。

> **关于 1.20.1 的 NeoForge**：这一代 NeoForge 是 Forge 47.x 的镜像（包名仍是
> `net.minecraftforge`、模组 ID 仍是 `forge`），两份 jar 在运行期基本等价。**不要同时装两个。**

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
dropper（投掷器） · dispenser（发射器） · furnace（熔炉 · 高炉 · 烟熏炉） · brewing_stand（酿造台）
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
| **1.20.1** | ✅ 三平台各一份 |
| 1.21.1 | ✅ 由 `1.21.1` 分支的 1.1.x 提供（那一代数量走 Codec，机制与本分支不同） |
| 其它版本 | ❌ 不适用。1.20 一代的其它小版本未做验证；1.20.5 起原版结构换代（引入物品组件、数量走 Codec），本构建会因符号缺失而无法启动 |

三平台产物的映射命名空间不同：Fabric 处于 **intermediary**，Forge / NeoForge 处于 **SRG**，
运行期各自映射到当前版本的混淆名。**所以产物与编译目标是绑定的**——Mixin 的注入目标、
`@Shadow` 成员这些符号一旦在目标版本里被改名或移除，就会以 `NoSuchMethodError` 或
`Mixin apply failed` 直接失败。

### `gradle.properties` 里的版本号分别管什么

这些**都只影响构建环境**，但其中一部分会进入产物元数据，成为**玩家可见的硬门槛**。

| 配置 | 玩家可见 | 作用 |
|---|---|---|
| `minecraft_version` | ✅ 进 `depends.minecraft` / `mods.toml` | 编译目标（1.20.1） |
| `minecraft_version_range` | ✅ 同上 | 声明支持的区间 |
| `fabric_loader_requires` | ✅ 进 `fabric.mod.json` 的 `depends.fabricloader` | Fabric 玩家门槛，取**能工作的最低版本** |
| `forge_version_range` / `neoforge_version_range` | ✅ 进 `mods.toml` 的依赖段 | Forge / NeoForge 玩家门槛 |
| `fabric_api_version` | ❌ | 编译期 Fabric API，决定"代码最多能用多新的 API" |
| `fabric_loader_version` | ❌ | 编译期 Fabric Loader |
| `forge_version` / `neoforge_version` | ❌ | 编译期的 Forge / NeoForge 版本 |
| `loom_version` / `moddev_version` | ❌ | 构建插件的版本 |
| `java_version` | ✅ 进元数据 | 编译目标（本代 = 17） |

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
  - NeoForge `https://maven.neoforged.net/releases/net/neoforged/forge/`（1.20.1 一代）

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
6. 本模组不提供物品上限数据表——原版上限直接读自物品（`Item#getMaxStackSize()`），对模组物品同样有效。
7. **调低 `global_max` 不会销毁已有物品**：已经超过新上限的堆叠会原样保留，
   只是此后无法再把更多物品放进该格（GUI 侧一次最多放入新上限以内的数量）。
   这与玩家背包的行为一致，详见[第七节](#七两处安全夹取防止调低上限时出现负数增量)。
8. **数量在网络与存档里都不再受 8 位限制**：本代原版用字节存放数量，超过 127 会截断
   （打开容器时表现为数量错乱甚至整格消失）。本模组把网络包与 NBT 两条路都改成 VarInt / int，
   详见[第八节](#八数量本身也要装得下两处-8-位上限)。

---

## 与其他模组的冲突风险

本模组直接改写原版行为，**凡是触及同一批原版目标的模组都可能互相干扰**。
下面把本模组实际改动的原版位置全部列出，方便出问题时对号入座。

### 本模组触及的原版目标

| Mixin | 改动的原版位置 |
|---|---|
| `ItemStackMixin` | `ItemStack#getMaxStackSize`、`save`、`of`、`getTooltipLines` |
| `PacketByteBufMixin` | `FriendlyByteBuf#writeItem` 里的 `writeByte`（两个平台变体）、`readItem` 里的 `readByte` 与它存进的局部变量 |
| `ServerPlayNetworkHandlerMixin` | `ServerGamePacketListenerImpl#handleSetCreativeModeSlot` 里的 `ItemStack#getCount()` |
| `InventoryMixin` | `Container#getMaxStackSize` |
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

### 特别提示：本模组改动了网络与存档的编码

本代的原版把数量存成**有符号字节**，本模组必须替换两条编码路径
（网络 `writeByte` → VarInt，NBT `putByte` → `putInt`，详见第九节）。由此带来两类额外风险：

- **任何同样改动物品序列化的模组都必然冲突**——改的是同一段字节流的位置与长度，
  不像上面那些可以靠"先执行者生效"勉强共存；
- **联机时两端都要装本模组**：只装一端的话，数量一旦超过 127，未装的一端仍按原版单字节解析，
  网络包会从数量字段之后开始错位（**数量 ≤127 时仍然完全兼容**）。

---

## 从源码构建

`common/` 存放全部逻辑与资源，`fabric/`、`forge/`、`neoforge/` 是三个平台子项目。
`common` 不产出 jar，而是把源码与资源目录导出给平台子项目，由每个平台各自编译一遍。

```bash
./gradlew build               # 构建三个平台的产物
./gradlew :fabric:build       # 只构建某一个平台
./gradlew :forge:runClient    # 启动对应平台的开发环境客户端
./gradlew :neoforge:runServer # 启动开发环境服务端
```

产物在各平台的 `build/libs/` 下：

- `fabric/build/libs/BetterItemStack-1.20.1-fabric-<版本>.jar`
- `forge/build/libs/BetterItemStack-1.20.1-forge-<版本>.jar`
- `neoforge/build/libs/BetterItemStack-1.20.1-neoforge-<版本>.jar`

> Forge / NeoForge 的 `build/libs` 是**已重混淆**的发布产物，可以直接分发；
> 未重混淆的那份在 `build/devlibs/`，只用于本地调试，不要分发。

构建环境：Gradle 8.14.2 + JDK 21（`java_version = 17` 是编译目标，与运行 Gradle 的 JDK 无关）。
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
│   ├── VanillaMax.java                  读取"原版上限"（读 Item#getMaxStackSize()，不受本模组改写影响）
│   ├── platform/
│   │   ├── Platform.java                平台抽象（配置目录 / 模组版本号）
│   │   └── Platforms.java               经 ServiceLoader 惰性解析实现
│   └── mixin/
│       ├── ItemStackMixin.java          物品上限、落盘数量、堆叠数量提示
│       ├── PacketByteBufMixin.java      网络包里的数量改用 VarInt
│       ├── ServerPlayNetworkHandlerMixin.java  创造模式取物的上限校验（本代原版写死 64）
│       ├── InventoryMixin.java          容器每格上限按类型区分
│       ├── SlotMixin.java               GUI 槽位容量（保留槽位自身声明的上限）
│       ├── ScreenHandlerMixin.java      客户端镜像容器登记
│       ├── HopperBlockEntityMixin.java  漏斗容量判定接回容器感知逻辑
│       ├── ItemEntityMixin.java         掉落物合并上限
│       └── client/DrawContextMixin.java 角标数字缩写
└── src/main/resources/
    ├── icon.png                         模组图标（三平台共用，也是仓库 README 的图）
    ├── betteritemstack.mixins.json      8 个公共 mixin + 1 个客户端专属
    ├── pack.mcmeta
    └── assets/betteritemstack/
        └── lang/                        10 种语言

fabric/                                  Fabric 入口 + fabric.mod.json + Platform 实现
forge/                                   Forge 入口 + META-INF/mods.toml + Platform 实现
neoforge/                                NeoForge 入口 + META-INF/mods.toml + Platform 实现

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
  之前受"有符号字节"的物理限制，只能堆到 127；本模组则在同样的 1.20 一代上自己改写了
  编码层。两者的技术路线对比见上文第九节。

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
