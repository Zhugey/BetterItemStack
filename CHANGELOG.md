# 更新日志

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
