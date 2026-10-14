package com.zhugey.betteritemstack.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
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
 * <p>对 {@link ItemStack} 的 Mixin：放宽数量在序列化上的取值上限，并在工具提示里追加堆叠数量。</p>
 *
 * <p><b>"每叠能放多少"不在这里</b>：26.2 把 {@code getMaxStackSize()} 上提到了接口
 * {@link net.minecraft.world.item.ItemInstance} 的默认方法，{@code ItemStack} 自己不再声明它
 * ⇒ 放大上限的职责已挪到 {@code ItemInstanceMixin}（接口目标只能用 {@code @Overwrite}）。
 * 本类只负责"让放大的数量真的存得下来、并与原版逐字兼容"。</p>
 *
 * <p><b>必须改写 {@link ItemStack#CODEC} 家族。</b>原版把数量写死成
 * {@code ExtraCodecs.intRange(1, 99)}：</p>
 *
 * <pre>
 * MAP_CODEC = MapCodec.recursive("ItemStack", subCodec -&gt; RecordCodecBuilder.mapCodec(
 *         i -&gt; i.group(Item.CODEC_WITH_BOUND_COMPONENTS.fieldOf("id").forGetter(ItemStack::typeHolder),
 *                      ExtraCodecs.optionalAlwaysPresentFieldOf(ExtraCodecs.intRange(1, 99), "count", 1)
 *                              .forGetter(ItemStack::getCount),
 *                      DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
 *                              .forGetter(s -&gt; s.components.asPatch()))
 *              .apply(i, ItemStack::new)));
 * </pre>
 *
 * <p>也就是说，<b>只把 {@code getMaxStackSize()} 放大而不动 CODEC，数量超过 99 的物品
 * 会在编码阶段直接报错</b>——{@code intRange} 是"校验型"编解码器，越界返回
 * {@code DataResult.error} 而不是截断。落盘（箱子、玩家背包、区块里的物品实体）与网络
 * （{@code ItemStack#validatedStreamCodec} 在解码后会拿 CODEC 再编一遍做校验）都会走到它，
 * 后果是物品无法保存、同步被踢。</p>
 *
 * <p>因此这里在 {@code <clinit>} 末尾<b>整体替换</b> {@code MAP_CODEC} 及其派生，
 * 除把数量区间放开到 {@link Integer#MAX_VALUE} 外，其余字段与原版逐字一致。</p>
 *
 * <p><b>26.2 相对 1.21.11 的两处字段变化</b>（都已按新版原版逐字对齐）：</p>
 * <ol>
 *   <li>id 字段的 codec 由 {@code Item.CODEC} 换成 {@code Item.CODEC_WITH_BOUND_COMPONENTS}
 *       （它在 {@code CODEC} 之外多校验"该物品的组件已绑定"），
 *       取值方法由 {@code ItemStack::getItemHolder} 改名为 {@code ItemStack::typeHolder}。</li>
 *   <li>count 字段由 {@code ExtraCodecs.intRange(1, 99).fieldOf("count").orElse(1)}
 *       换成 {@code ExtraCodecs.optionalAlwaysPresentFieldOf(ExtraCodecs.intRange(1, 99), "count", 1)}
 *       ——编码时会<b>始终写出</b> count 字段（不再省略默认值），语义由 {@code orElse} 变成
 *       {@code xmap(o -&gt; o.orElse(default), Optional::of)}。</li>
 * </ol>
 *
 * <p>另外，本代 {@code ItemStack#getTooltipLines} 的签名是
 * {@code (Item.TooltipContext, Player, TooltipFlag)}，
 * 少写或错写一个参数只在<b>启动时</b>才会被 Mixin 以回调签名不符拒收，构建期无提示。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    /** 堆叠数量达到该值时才在工具提示中显示数量。 */
    private static final int BIS_TOOLTIP_THRESHOLD = 1000;

    /**
     * 原版 {@link ItemStack#MAP_CODEC} —— 本代（1.21.9 起）{@code ItemStack} 编解码结构的<b>根</b>。
     *
     * <p><b>这是本代与 1.21.1 最重要的结构差别</b>：1.21.1 里 {@code CODEC} 是直接由
     * {@code RecordCodecBuilder.create(...)} 构造的源头；本代改成
     * {@code MAP_CODEC = MapCodec.recursive("ItemStack", ...)} 作根，{@code CODEC} 反过来由它派生
     * （{@code Codec.lazyInitialized(MAP_CODEC::codec)}）。</p>
     *
     * <p><b>所以数量上限必须从这个字段下手</b>：容器侧（{@code ItemStackWithSlot.CODEC} ——
     * 箱子的 {@code "Items"} 列表就是它）<b>直接引用 {@code MAP_CODEC}</b>，
     * 只替换 {@code CODEC} 对它完全无效。症状是箱子/背包里的堆叠<b>落盘</b>时报
     * {@code Value must be within range [1;99]}，物品根本保存不下来。</p>
     */
    @Mutable
    @Shadow
    @Final
    public static MapCodec<ItemStack> MAP_CODEC;

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

    /*
     * ⚠ 这里曾经还有第三项 {@code STRICT_CODEC}（= {@code CODEC.validate(ItemStack::validateStrict)}）。
     *
     * 26.2 把该字段<b>整个删掉了</b>（1.21.11 上它还在）。现在 {@code validateStrict} 改为在解码
     * 之后由调用方直接调用（{@code ItemInput#parse}、{@code ItemStackTemplate#fromStack}、
     * {@code ItemStack#validateComponents} 路径），不再有"严格版 codec"这一层。
     *
     * 保留 {@code @Shadow} 的后果**只在启动时**才暴露，而且极凶：
     *   InvalidMixinException: @Shadow field STRICT_CODEC was not located in the target class …
     *   ⇒ 混入整体应用失败 ⇒ 游戏进不去。构建期完全看不出来。
     * 因此 26.2 的 codec 家族只有三项：MAP_CODEC（根）、CODEC、OPTIONAL_CODEC。
     */

    /**
     * <p>重写 {@link ItemStack#CODEC}，把数量字段的取值上限由原版的 {@code [1, 99]}
     * 放开到 {@code [1, Integer.MAX_VALUE]}。</p>
     *
     * <p>注入点是静态初始化块的末尾：此时原版的 {@code CODEC} 与由它派生的
     * {@code OPTIONAL_CODEC} 都已初始化完毕，直接整体替换即可，
     * 不必去改原版那段 {@code RecordCodecBuilder} 的源码。</p>
     *
     * <p><b>本代 {@code ItemStack} 的编解码结构比 1.21.11 多了一层</b>：{@link #MAP_CODEC}
     * 才是根（{@code MapCodec.recursive}），{@link #CODEC} 反过来由它派生。
     * 因此这里以 {@code MAP_CODEC} 为主改，并把由它派生的 {@link #CODEC}、
     * {@link #OPTIONAL_CODEC} 一并重建（26.2 已无 {@code STRICT_CODEC}，见上方注释）。</p>
     *
     * <p>除数量字段外，id 与 components 两项与 26.2 原版逐字一致：
     * id 用 {@code Item.CODEC_WITH_BOUND_COMPONENTS}（= {@code Item.CODEC} 之上再校验
     * "该物品的组件已绑定"）、取值走 {@code ItemStack::typeHolder}；
     * components 用 {@code getComponentsPatch()}（公开方法，等价于原版内联的
     * {@code components.asPatch()}）。</p>
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
        // ① 先换"根"：MAP_CODEC 是本代数量上限的唯一源头，其余 codec 都从它派生。
        //    三个字段除数量区间外与原版逐字一致（含 id 用 CODEC_WITH_BOUND_COMPONENTS）。
        MAP_CODEC = MapCodec.recursive(
                "ItemStack",
                self -> RecordCodecBuilder.mapCodec(
                        instance -> instance.group(
                                        Item.CODEC_WITH_BOUND_COMPONENTS.fieldOf("id").forGetter(ItemStack::typeHolder),
                                        ExtraCodecs.optionalAlwaysPresentFieldOf(
                                                        ExtraCodecs.intRange(1, Integer.MAX_VALUE), "count", 1)
                                                .forGetter(ItemStack::getCount),
                                        DataComponentPatch.CODEC
                                                .optionalFieldOf("components", DataComponentPatch.EMPTY)
                                                .forGetter(ItemStack::getComponentsPatch)
                                )
                                .apply(instance, ItemStack::new)
                )
        );

        // ② 以下两个都必须用新根重建。原版它们是"按值捕获"派生出来的
        //    （CODEC 由 Codec.lazyInitialized(MAP_CODEC::codec) 捕获了当时的实例），
        //    只换字段不会让它们跟着变：
        //        CODEC          = MAP_CODEC::codec
        //        OPTIONAL_CODEC = optionalEmptyMap(CODEC).xmap(...)
        //    ⚠ 漏掉任何一个，对应那条落盘/存档路径上超过 99 的堆叠都会报 [1;99] 存不下来。
        //    ⚠ 26.2 没有 STRICT_CODEC（1.21.11 有），不要再加回来。
        CODEC = Codec.lazyInitialized(MAP_CODEC::codec);
        OPTIONAL_CODEC = ExtraCodecs.optionalEmptyMap(CODEC)
                .xmap(optional -> optional.orElse(ItemStack.EMPTY),
                        stack -> stack.isEmpty() ? Optional.empty() : Optional.of(stack));
    }

    /*
     * ⚠ 这里曾经有 {@code @Inject(method = "getMaxStackSize()I", at = @At("HEAD"), cancellable = true)}。
     *
     * 26.2 把 {@code getMaxStackSize()} 上提到接口 {@link net.minecraft.world.item.ItemInstance} 的
     * <b>默认方法</b>，{@code ItemStack} 自己不再声明它 ⇒ 注入到本类的注解在<b>运行期</b>会以
     * "找不到目标方法"被拒（构建期不报错）。该逻辑已整体迁到 {@code ItemInstanceMixin}：
     * 接口目标必须用接口 mixin，而接口 mixin 里又只能用 {@code @Overwrite}（Forge 的
     * Mixin 注解处理器拒绝接口里的 injector）。
     *
     * 本 Mod 依然是"唯一一处全局放大"：原版有若干逻辑（尤其漏斗的容量判定）直接依赖
     * {@code ItemStack#getMaxStackSize()}，因此凡是需要"物品原本能堆多少"的地方都必须改用
     * {@link com.zhugey.betteritemstack.VanillaMax}。
     */

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
