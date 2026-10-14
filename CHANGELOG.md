# 更新日志

---

## 1.3.0 — 2026-10-14

**新增对 Minecraft 26.2 的支持，同时提供 Fabric / Forge / NeoForge 三个平台。**

功能与 1.2.0 完全一致——全局堆叠上限、按容器类型分别控制、`/bis` 指令、10 种语言、
数量提示、单件槽位保留原版语义，容器键名表也不变（仍为 13 个，含合成器 `crafter`）。

> 本分支只支持 **26.2**。1.21.11 请使用 `1.21.11` 分支的 1.2.0 产物，
> 1.21.1 请使用 `1.21.1` 分支的 1.1.0 产物，1.20.1 请使用 `1.20.1` 分支的 1.0.0 产物。

### 堆叠上限现在要在**两个**地方接管

上一代 `getMaxStackSize()` 是 `ItemStack` 自己声明的方法，覆写一处即可。26.2 把它上提成了
接口 `ItemInstance` 的默认方法（`ItemStack` 与新增的 `ItemStackTemplate` 都只是继承它），
于是同一个调用在三个平台命中的宿主并不相同：

| 平台 | `stack.getMaxStackSize()` 实际走到哪 |
|---|---|
| Fabric（原版） | `ItemInstance` 的接口默认方法 —— 原版的 `ItemStack` **没有**自己声明它 |
| Forge | 同上（Forge 没有额外补丁） |
| **NeoForge** | **`ItemStack` 自己的实现**（NeoForge 补的 `getItem().getMaxStackSize(this)`），**绕开接口** |

因此本版本在公共代码里接管 `ItemInstance`，**另外**只为 NeoForge 生成一份接管 `ItemStack` 的
补丁（写进共享配置会让 Fabric / Forge 直接起不来——那边根本没有这个方法）。
两处共用同一个上限计算入口，所以三个平台的行为一致。

这个坑的症状很隐蔽：**混入其实全都生效了、`/bis get` 也报新上限**，但往箱子/背包里放
超过 64 的物品会被拒绝（`can only stack up to 64`），界面上也仍按 64 / 16 收窄。

### 平台门槛的变化

| 项目 | 变化 |
|---|---|
| **Java** | 需要 **25**（Mojang 自 26.1 起向玩家分发的就是 Java 25） |
| **Fabric Loader** | 需要 **≥ 0.19.5**。0.19.4 内嵌的 MixinExtras 0.5.4 在扫描 `@Redirect` 时有一个类型强转缺陷，会让游戏在启动阶段直接崩（`ClassCastException`），**与本模组无关但会影响所有用到 `@Redirect` 的模组**；0.19.5 起自带修好的 0.5.5 |
| **Forge / NeoForge** | 见下方兼容性表 |

### 兼容性

| 项目 | 要求 |
|---|---|
| Minecraft | **26.2** |
| Java | **25** |
| Fabric | Loader ≥ 0.19.5 + Fabric API |
| Forge | 65.1.x |
| NeoForge | 26.2.x |

- **数量超过 99 的物品需要联机两端都装本模组**：未装的一端无法解析该字段。
  数量不超过 99 时仍与原版完全兼容。
- 本代的 NeoForge 与 Forge 是**两套独立实现**，两份产物互不通用。
- 原版 `/item replace … <数量>` 指令的数量参数**本身就限定在 99 以内**（Brigadier 的约束），
  想验证更大的堆叠请用漏斗或创造模式堆叠——这不是本模组的限制。

---

## 1.2.0 — 2026-10-13

**新增对 Minecraft 1.21.11 的支持，同时提供 Fabric / Forge / NeoForge 三个平台。**

功能与 1.1.0 完全一致——全局堆叠上限、按容器类型分别控制、`/bis` 指令、10 种语言、
数量提示、单件槽位保留原版语义，容器键名表也不变（仍为 13 个，含合成器 `crafter`）。
区别在于：这一代原版又动了两处与本模组直接相关的地方。

> 本分支只支持 **1.21.11**。1.21.1 请使用 `1.21.1` 分支的 1.1.0 产物，
> 1.20.1 请使用 `1.20.1` 分支的 1.0.0 产物。

### 数量上限的替换点变了：真正的核心是 `MAP_CODEC`

1.21.9 起，`ItemStack` 的编解码多了一层"根"：

```java
MAP_CODEC = MapCodec.recursive("ItemStack", … intRange(1, 99) …)   // 源头，数量在这里校验
CODEC     = Codec.lazyInitialized(MAP_CODEC::codec)                // 由它派生，且按值捕获
```

而**容器侧**（箱子、木桶、玩家背包）的序列化直接引用 `MAP_CODEC`。因此只替换 `CODEC`
对这两条最常用的路径**完全无效**：数量超过 99 的堆叠在存盘时报
`Value must be within range [1;99]`，症状是退出世界时那一格**没能写进存档**。

本版本以 `MAP_CODEC` 为根重建，并一并重建由它派生的 `CODEC` / `STRICT_CODEC` /
`OPTIONAL_CODEC` 三个静态实例，其余字段与原版逐字一致。
**1.21.1 及更早没有这一层结构，所以这一个坑只属于本分支。**

### 模组包的格式声明改用数据格式

`pack.mcmeta` 在本代由 `pack_format` 改为 `min_format` / `max_format`。需要注意，
**模组包会被校验两次**（一次按资源、一次按数据），两者的"旧格式上限"分别是 64 与 81；
声明值不大于对应上限时，原版会转而要求补 `supported_formats` 字段。本版本按 Forge 与
NeoForge 自身资源的写法声明为 `[94, 1]`（= 本代的数据格式）。**这不是可选项**：声明不当会让
Forge / NeoForge 在日志里刷 `Couldn't load mod:… pack metadata`，并使本模组在资源包列表中显示异常。

### 1.1.0 里的一处写法在本代必须换掉

`ItemStack.ITEM_NON_AIR_CODEC`（1.1.0 用它描述物品 `id` 字段）自 1.21.9 起已被移除，
本版本改用等价的 `Item.CODEC`——同样是"按注册名解析成物品、并校验非空气"。
此外原版的权限判定从 `hasPermission(int)` 换成 `PermissionSet`；
对使用者没有影响，`/bis` 的权限要求不变（仍为 OP 等级 1）。

### 兼容性

| 项目 | 要求 |
|---|---|
| Minecraft | **1.21.11** |
| Java | 21 |
| Fabric | Loader ≥ 0.18.2 + Fabric API |
| Forge | 61.2.x |
| NeoForge | 21.11.x |

- **数量超过 99 的物品需要联机两端都装本模组**：未装的一端无法解析该字段。
  数量不超过 99 时仍与原版完全兼容。
- 本代的 NeoForge 与 Forge 是**两套独立实现**，两份产物互不通用
  （1.20.1 那一代两者等价，正好相反）。

---

## 1.1.0 — 2026-10-12

**新增对 Minecraft 1.21.1 的支持，同时提供 Fabric / Forge / NeoForge 三个平台。**

功能与 1.0.0 完全一致——全局堆叠上限、按容器类型分别控制、`/bis` 指令、10 种语言、
数量提示、单件槽位保留原版语义；但**1.20.5 换代后，需要处理的问题换了一整套**：
1.20 一代那三处"数量只有 8 位"的修补在本代全部作废，取而代之的是下面这个更隐蔽的约束。

> 本分支只支持 **1.21.1**，1.20.1 请使用 `1.20.1` 分支的 1.0.0 产物。

### 本代的关键改动：数量上限被写死在校验型 Codec 里

1.20.5 起，物品的存档与网络都改由 **Codec** 描述，而原版把数量字段写成：

```java
ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1)
```

`intRange` 是**校验型**编解码器——越界返回错误，**不是截断**。所以"只放大
`getMaxStackSize()` 而不动 Codec"的后果是：**数量超过 99 的物品直接无法保存**，
而不是像 1.20 一代那样悄悄变成另一个数量。

本模组在 `ItemStack` 静态初始化块末尾整体替换 `ItemStack.CODEC`，把数量区间放开到
`[1, Integer.MAX_VALUE]`，其余字段（`id` / `components`）与原版逐字一致；同时把由它
派生、按值捕获了旧实例的 `OPTIONAL_CODEC` 一并重建，否则创造模式保存的物品栏在编码时会失败，
表现为该格被静默替换为空。

### 1.20 一代的三处修补在本代全部删除

| 1.0.0 的处理 | 本代为何不需要 |
|---|---|
| 网络包数量由 `writeByte` 改为 VarInt | 本代数量在网络里**本来就是 VarInt**，不存在 8 位截断 |
| 存档里补写 `putInt("Count")` | `ItemStack#save` / `#of` 在本代**已不存在**，存档改走 Codec |
| 创造模式取物的 `getCount() <= 64` 硬编码校验 | 本代服务端校验已改为与真实上限比较 |

前两条若照搬，会因宿主方法不存在而在**启动时**抛 `InjectionError`（构建期完全不报错）。

### 新增：合成器（Crafter）

1.21 新增的合成器方块纳入提升范围，容器键名为 `crafter`，键名表增至 **13** 个，
10 种语言都补上了官方译名。

### 其它

- 物品上限改读组件 `minecraft:max_stack_size`（本代 `Item#getMaxStackSize()` 已被移除）。
- "可损坏物品"的判定改看 `MAX_DAMAGE` 组件（`Item#isDamageable()` 已被移除），语义与 1.0.0 一致。
- 单件槽位语义、GUI 侧的两处非负夹取、客户端镜像容器登记——这三项与 1.0.0 相同，仍然保留。
- 1.0.0 里针对"中键拖拽分堆每格只落 64"的修正**不再需要**：本代原版那句读的就是堆叠级上限，
  会自动跟随 `global_max`。

### 兼容性

- ✅ **Minecraft 1.21.1**；需要 **Java 21**。
- Fabric：Fabric Loader ≥ 0.16.1 + Fabric API；Forge：52.1.x；NeoForge：21.1.x。
- ⚠ **本代的 NeoForge 已是独立实现**（模组 ID 为 `neoforge`、包名为 `net.neoforged`），
  **与 Forge 产物互不通用**——这与 1.20.1 时代"两者互为镜像、可以互换"正好相反。
- ⚠ **数量超过 99 时，联机的两端都需要安装本模组**：网络侧的数量字段本身是 VarInt、不受限制，
  但原版会在**创造模式取物包**里用上面那个 Codec 复校一次，未装模组的一端在**保存**含这类堆叠的
  容器时也会直接报错。数量 ≤ 99 时与未装模组的一端完全兼容。

### 许可

**MIT**。基于 [develk-coder/ItemStackProMax](https://github.com/develk-coder/ItemStackProMax) 二次开发，
原作者 develk；上游同样采用 MIT 授权，本项目一并保留其版权与许可声明。

---

## 1.0.0 — 2026-10-11

**首个发布版本，支持 Minecraft 1.20.1，同时提供 Fabric / Forge / NeoForge 三个平台。**

基于 [ItemStackProMax](https://github.com/develk-coder/ItemStackProMax) 二次开发：上游是 1.20.5+ 的
Fabric 单平台模组，本版把逻辑整体移植到 **1.20 一代**（无物品组件、数量走手写字节流、运行环境为
Java 17），并重构为"一份 `common` 源码 + 三个平台子项目"的结构。

### 功能

- **全局堆叠上限** `global_max`（默认 9999），可在游戏内用指令随时调整，改动写回配置文件。
- **按容器类型分别控制**：玩家背包、箱子 / 陷阱箱 / 大箱子、木桶、潜影盒、末影箱、运输矿车、
  投掷器、发射器、熔炉 / 高炉 / 烟熏炉、酿造台、合成器提升到上限；**漏斗与漏斗矿车保持原版上限**
  （普通物品 64、鸡蛋 / 雪球等 16、不可堆叠物品 1），以 16 / 64 计数的红石计时器与计数器因此不受影响。
  漏斗向提升容器推送时，仍可把目标容器每一格填满到 `global_max`。
- **配置项 `containers`**：`mode`（`blacklist` / `whitelist`）、`list`、`unknown`，共 12 个容器键名；
  未识别的容器（含其它模组新增的）默认保持原版上限。`/bis info` 可直接查看当前规则、键名与配置示例。
- **不需要物品上限数据表**：原版上限直接读自 `Item#getMaxStackSize()`（本代还没有物品组件），
  绕开了被本模组改写的方法，对模组新增物品同样有效。
- **指令**：`/bis`、`/bis get`、`/bis info`、`/bis set <数量>`、`/bis reload`；输出全面国际化，
  内置 10 种语言（`en_us` / `zh_cn` / `zh_tw` / `ja_jp` / `ko_kr` / `ru_ru` / `de_de` / `fr_fr` /
  `es_es` / `pt_br`），未收录的语言自动回退英文。容器显示名取自 Minecraft 官方译名。
- **数量提示**：数量 ≥ 1000 时在物品名下方追加一行；格子角标数字在 ≥ 1000 时缩写为 K / M / B。
- **单件槽位保留原版语义**：附魔台输入槽、铁砧、砂轮等通过覆写 `Slot#getMaxStackSize()` 声明的
  上限被完整保留，附魔台与附魔灌注台可正常工作。

### 三平台

`common` 不产出被依赖的 jar，而是把源码与资源目录导出给平台子项目，由每个平台各自编译一遍，
因此不需要跨映射重映射。相应地，全平台统一使用 **Mojang 官方映射**（Fabric 侧用
`loom.officialMojangMappings()`）。三个平台的产物均已实机启动验证，无注入失败。

> **1.20.1 的 NeoForge** 是 Forge 47.x 的镜像（包名仍为 `net.minecraftforge`、模组 ID 仍为
> `forge`），两份 jar 在运行期基本等价。**不要同时安装。**

### 本代特有的五处缺陷

这一代原版把数量存成 **8 位字节**、把容器容量判定**写死 64**，而且网络与存档两条路径都不经过
Codec，所以下列每一处都必须单独处理，漏一个就会出现"数量错乱甚至整格消失"：

1. **网络包数量按字节读写**：`FriendlyByteBuf#writeItem` 里 `writeByte(count)`、`readItem` 里
   `readByte()`，超过 127 会读成负数、256 的整数倍读成 0，而 `ItemStack#isEmpty()` 判定 `count <= 0`
   → **整格消失**。改为 `writeVarInt` / `readVarInt`。
2. **存档里的数量也是字节**：`ItemStack#save` 的 `putByte("Count")`，重进世界后数量错乱。改为
   在 `save` 的 TAIL 补一个 `putInt("Count")` 覆盖，并在 `of` 的 RETURN 用 `getInt` 还原。
3. **创造模式取物被服务端静默丢弃**：`ServerGamePacketListenerImpl#handleSetCreativeModeSlot` 的
   防作弊校验把数量与**字面量 64** 比较（字节码里是一次 `bipush 64`）。客户端因为上限被放大，
   点一下就是整整一摞、看起来成功，服务端却根本没接收。改为把那次 `getCount()` 替换成
   「是否超限」的哨兵值，等价地让校验变成 `count > 真实上限`。
4. **客户端 GUI 把容器显示成 64**：打开方块容器时客户端走的是界面构造器，容器只是一个临时的
   `SimpleInventory` 镜像，落不进容器分类表，被判为"未识别容器"而退回 64——服务端同步下来的
   大堆叠会被它截断成 64 显示。改为在 `ScreenHandler#addSlot` 时按界面类型登记镜像容器。
5. **中键拖拽分堆每格只落 64**：`ScreenHandler#calculateStackSize` 的 `case 2` 读的是
   `Item#getMaxStackSize()`（物品级上限，恒为 64）而不是被放大的 `ItemStack#getMaxStackSize()`；
   该分支客户端与服务端都会执行。改为读 `ItemStack` 的那一项。

### 一并处理的安全问题

- **容量只在插入时生效，绝不回溯截断**：调低 `global_max` 不会销毁已有物品，超量堆叠原样保留，
  只是此后无法再放入更多（本代原版还没有 1.20.5+ 的 `ItemStack#capCount` 那条路径）。
- **两处夹取防负数增量**：GUI 侧一次放入量与漏斗传输增量都夹到非负，避免 `split(负数)` /
  `grow(负数)` 让数量朝反方向变化。
- **`compatibilityLevel` 定为 `JAVA_17`**：与本代编译目标一致，也兼容随附 Mixin 0.8.5 的低版本
  Loader（写成 `JAVA_21` 会被拒收整份 mixin 配置，导致全部 Mixin 一起失效）。
- **保留"槽位自身声明的上限"**：槽位子类若覆写了带参数的重载，本模组的注入不执行，其自定义上限
  天然生效。

### 兼容性

- ✅ **Minecraft 1.20.1**；需要 **Java 17**。
- Fabric：Fabric Loader ≥ 0.14.22 + Fabric API；Forge：47.x；NeoForge：47.1.x。
- ❌ **1.20.5 及以上不适用**：那一代起原版引入物品组件、数量改由 Codec 描述，符号画像完全不同。
- **数量 > 127 时，联机两端都需要安装本模组**：未装的一端仍按原版单字节解析，网络包会从数量字段
  之后开始错位（数量 ≤ 127 时逐位与原版一致，完全兼容）。

### 许可

**MIT**。基于 [develk-coder/ItemStackProMax](https://github.com/develk-coder/ItemStackProMax) 二次开发，
原作者 develk；上游同样采用 MIT 授权，本项目一并保留其版权与许可声明。
