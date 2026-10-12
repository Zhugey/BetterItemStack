package com.zhugey.betteritemstack.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zhugey.betteritemstack.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

/**
 * <p>对 {@link ItemStack} 的 Mixin：改写全局最大堆叠数量、放宽数量在序列化上的取值上限，
 * 并在工具提示里追加堆叠数量。</p>
 *
 * <p><b>1.21.1 相对 1.20 一代的三处关键差异</b>——本代已经没有"数量只有 8 位"的历史包袱，
 * 但换成了一条更隐蔽的约束：</p>
 *
 * <ol>
 *   <li><b>必须改写 {@link ItemStack#CODEC}。</b>本代原版把数量写死成
 *       {@code ExtraCodecs.intRange(1, 99)}：
 *       <pre>
 *       ItemStack.CODEC = Codec.lazyInitialized(() -&gt; RecordCodecBuilder.create(
 *               i -&gt; i.group(ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
 *                            ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1).forGetter(ItemStack::getCount),
 *                            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
 *                                    .forGetter(s -&gt; s.components.asPatch()))
 *                    .apply(i, ItemStack::new)));
 *       </pre>
 *       也就是说，<b>只把 {@code getMaxStackSize()} 放大而不动 CODEC，数量超过 99 的物品
 *       会在编码阶段直接报错</b>——{@code intRange} 是"校验型"编解码器，
 *       越界返回 {@code DataResult.error} 而不是截断。落盘（箱子、玩家背包、区块里的物品实体）
 *       与网络（{@code ItemStack#validatedStreamCodec} 在解码后会拿 CODEC 再编一遍做校验）
 *       都会走到它，后果是物品无法保存、同步被踢。
 *       因此这里在 {@code <clinit>} 末尾<b>整体替换</b> {@code CODEC}，
 *       除把数量区间放开到 {@link Integer#MAX_VALUE} 外，其余字段与原版逐字一致。</li>
 *
 *   <li><b>1.20 一代那两条"字节改 int"的补丁必须删除。</b>那时 {@code ItemStack#save(CompoundTag)}
 *       写的是 {@code putByte("Count", (byte) count)}、{@code ItemStack#of(CompoundTag)} 读的是
 *       {@code getByte("Count")}，所以要在 TAIL 补一个 int 覆盖、在 RETURN 用 int 还原。
 *       本代这两个方法<b>已经不存在</b>（存档改走 CODEC），继续写它们的 {@code @Inject}
 *       会因找不到宿主方法而在启动时抛 {@code InjectionError}——
 *       <b>构建期完全不报错</b>，正是最需要警惕的那一类。</li>
 *
 *   <li><b>"可损坏物品"改由组件表达。</b>{@code Item#isDamageable()} 已被移除，
 *       判定改用 {@link DataComponents#MAX_DAMAGE}。它只看物品类型、<b>不受 Unbreakable 组件影响</b>，
 *       与 1.20 一代 {@code getItem().isDamageable()} 的语义完全一致。</li>
 * </ol>
 *
 * <p>另外，本代 {@code ItemStack#getTooltipLines} 多了一个
 * {@link Item.TooltipContext} 形参（{@code (Item.TooltipContext, Player, TooltipFlag)}），
 * 少写一个参数同样只在<b>启动时</b>才会被 Mixin 以回调签名不符拒收。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    /** 堆叠数量达到该值时才在工具提示中显示数量。 */
    private static final int BIS_TOOLTIP_THRESHOLD = 1000;

    /** 原版 {@link ItemStack#CODEC} 的静态实例，待 {@link #bis$allowLargeCounts} 整体替换。 */
    @Mutable
    @Shadow
    @Final
    public static Codec<ItemStack> CODEC;

    /**
     * 原版由 {@link #CODEC} 派生而来的"可为空"版本（{@code optionalEmptyMap(CODEC)}）。
     *
     * <p>它必须在 {@code <clinit>} 里<b>一并重建</b>：派生是<b>按值捕获</b>的，
     * 静态初始化块按声明顺序执行，{@code OPTIONAL_CODEC} 在 {@link #CODEC} 之后立刻用旧实例
     * 包装完成，之后再替换 {@code CODEC} 并不会影响它。若不重建，
     * 创造模式保存的物品栏（{@code HotbarManager} → {@code Hotbar}）在编码超过 99 的堆叠时会失败，
     * 表现为该格被静默替换为空。
     */
    @Mutable
    @Shadow
    @Final
    public static Codec<ItemStack> OPTIONAL_CODEC;

    /**
     * <p>重写 {@link ItemStack#CODEC}，把数量字段的取值上限由原版的 {@code [1, 99]}
     * 放开到 {@code [1, Integer.MAX_VALUE]}。</p>
     *
     * <p>注入点是静态初始化块的末尾：此时原版的 {@code CODEC} 与由它派生的
     * {@code OPTIONAL_CODEC} 都已初始化完毕，直接整体替换即可，
     * 不必去改原版那段 {@code RecordCodecBuilder} 的源码。</p>
     *
     * <p>除数量字段外，id 与 components 两项都沿用原版的写法：
     * {@code ITEM_NON_AIR_CODEC}（公开静态字段，原版 {@code CODEC} 用的就是它）
     * 与 {@code getComponentsPatch()}（公开方法，等价于原版内联的 {@code components.asPatch()}）。</p>
     *
     * <p><b>方法名写 {@code "<clinit>"}，不要补 {@code ()V}</b>：补了描述符之后 Mixin 注解处理器
     * 会去类的方法表里找 {@code "<clinit>()V"}，静态初始化块不在其中，于是报
     * {@code Cannot find target method "<clinit>()V"} 告警；不补则按名字匹配，静默通过。
     * 两种写法运行期行为一致（{@code <clinit>} 不需要任何名字映射），但前者会在构建日志里留噪声。</p>
     *
     * @param ci Mixin 注入所需的 CallbackInfo
     */
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void bis$allowLargeCounts(CallbackInfo ci) {
        CODEC = Codec.lazyInitialized(
                () -> RecordCodecBuilder.create(
                        instance -> instance.group(
                                        ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(ItemStack::getItemHolder),
                                        ExtraCodecs.intRange(1, Integer.MAX_VALUE).fieldOf("count").orElse(1)
                                                .forGetter(ItemStack::getCount),
                                        DataComponentPatch.CODEC
                                                .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                                .forGetter(ItemStack::getComponentsPatch)
                                )
                                .apply(instance, ItemStack::new)
                )
        );

        // 重建派生版本，否则它仍按值持有上面那个"数量最多 99"的旧实例。
        OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
                .xmap(optional -> optional.orElse(ItemStack.EMPTY),
                        stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));
    }

    /**
     * <p>重写 {@link ItemStack#getMaxStackSize()} 方法，返回全局最大堆叠数量。</p>
     * <p>非堆叠物品列表中的物品或可损坏物品将保持原始最大堆叠数量。</p>
     *
     * <p>注意：这是本 Mod 唯一一处"全局放大"物品上限的地方，也是问题的根源——
     * 原版有若干逻辑（尤其漏斗的容量判定）直接依赖该方法，因此凡是需要"物品原本能堆多少"
     * 的地方都必须改用 {@link com.zhugey.betteritemstack.VanillaMax}。</p>
     *
     * @param cir Mixin 注入所需的 CallbackInfoReturnable，用于设置返回值
     */
    @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)
    private void overrideMaxCount(CallbackInfoReturnable<Integer> cir) {
        ItemStack stack = (ItemStack) (Object) this;

        // 带耐久度的物品（工具、护甲）原版上限即为 1，必须保持原版值。
        //
        // 1.20.5 起"是否有耐久"由一个物品组件表达（Item 上的 isDamageable() 已移除），
        // 判定改用 has(MAX_DAMAGE) —— 它只看物品类型，**不**受 Unbreakable 组件影响，
        // 与 1.20 一代的 getItem().isDamageable() 语义完全一致。
        // 若改用 isDamageableItem()，一个附了 Unbreakable 的工具会被当成普通物品放大到 GLOBAL_MAX，
        // 进而导致 Item#isEnchantable（判断式 getMaxStackSize() == 1）失败——该装备将无法在附魔台附魔。
        if (stack.has(DataComponents.MAX_DAMAGE)) {
            return;
        }

        // 黑名单物品保持原版上限。仅在黑名单非空时才做 ID 查询：
        // getMaxStackSize() 是热路径，避免每次调用都分配一个 ResourceLocation。
        List<String> blacklist = Config.get().nonStackableItems;
        if (!blacklist.isEmpty() && blacklist.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
            return;
        }

        cir.setReturnValue(Config.GLOBAL_MAX);
    }

    /**
     * <p>在物品的工具提示中追加一行堆叠数量（数量达到 {@link #BIS_TOOLTIP_THRESHOLD} 时）。</p>
     *
     * <p><b>为什么注入 {@link ItemStack#getTooltipLines} 而不是 {@code Item#appendHoverText}</b>：
     * 原版有若干物品覆写了 {@code Item#appendHoverText} 且<b>没有调用 {@code super}</b>，
     * 例如烟花（{@code FireworkRocketItem}）、药水箭（{@code TippedArrowItem}），以及旗帜、盾牌、弩、
     * 收纳袋、药水、滞留药水、成书、已探索地图、鱼桶、烟花之星、唱片碎片、盔甲纹饰。
     * 注入在 {@code Item#appendHoverText} 上的代码对这些物品<b>完全不会执行</b>，
     * 表现为"这些物品堆叠后看不到数量提示"。</p>
     *
     * <p>{@code ItemStack#getTooltipLines} 是所有物品提示的唯一汇总入口，且不存在绕过它的旁路调用
     * （弩调用 {@code Items.FIREWORK_ROCKET.appendHoverText} 之类的是在同一入口内部发生的），
     * 因此改注入这里可以覆盖全部物品。</p>
     *
     * <p><b>形参必须与目标方法逐一对应</b>：本代签名是
     * {@code getTooltipLines(Item.TooltipContext, Player, TooltipFlag)}，比 1.20 一代多了开头的
     * {@code TooltipContext}。少写或多写一个都会在启动时被 Mixin 以回调签名不符拒收，构建期无提示。</p>
     *
     * @param context 提示上下文（1.20.5 起新增的形参，本注入不使用）
     * @param player  玩家（可能为 null，本注入不使用）
     * @param flag    提示类型（本注入不使用）
     * @param cir     回调对象，用于取回并修改已构建好的提示行列表
     */
    @Inject(method = "getTooltipLines(Lnet/minecraft/world/item/Item$TooltipContext;"
            + "Lnet/minecraft/world/entity/player/Player;"
            + "Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;", at = @At("RETURN"))
    private void bis$addStackCountTooltip(Item.TooltipContext context, Player player, TooltipFlag flag,
                                          CallbackInfoReturnable<List<Component>> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getCount() < BIS_TOOLTIP_THRESHOLD) {
            return;
        }

        List<Component> tooltip = cir.getReturnValue();
        // 原版在 HIDE_TOOLTIP 分支直接返回 List.of()（不可变空列表），正常分支则至少含物品名一项。
        // 因此用 isEmpty() 既跳过"整条提示被隐藏"的情形，也避免对不可变列表调用 add。
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }

        // 插在物品名之后（索引 1），与旧实现的显示位置保持一致。
        // 文案走语言文件；颜色用 ChatFormatting 而非在翻译值里嵌 § 代码，便于各语言复用。
        tooltip.add(Math.min(1, tooltip.size()),
                Component.translatable("tooltip.betteritemstack.stackcount", stack.getCount())
                        .withStyle(ChatFormatting.GREEN));
    }
}
