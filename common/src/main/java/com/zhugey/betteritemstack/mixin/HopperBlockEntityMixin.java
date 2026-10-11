package com.zhugey.betteritemstack.mixin;

import com.zhugey.betteritemstack.ContainerPolicy;
import java.util.stream.IntStream;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <p>针对 {@link HopperBlockEntity} 的 Mixin，负责把漏斗的容量判定接回<b>容器感知</b>的逻辑。
 *
 * <p>为什么必须单独处理漏斗：漏斗的容量判定<b>完全不经过</b>
 * {@code Container#getMaxStackSize()}，而是直接使用 {@code ItemStack#getMaxStackSize()}，
 * 而后者已被 {@code ItemStackMixin} 全局放大到 {@link com.zhugey.betteritemstack.Config#GLOBAL_MAX}。
 * 若不处理，漏斗永远认为自己"没装满"，会不停抽取与合并，以 16 / 64 计数的红石计时器、
 * 计数器随之失效。
 *
 * <p>处理原则（两件事分开）：
 * <table border="1">
 *   <caption>判定点与上限来源</caption>
 *   <tr><th>方法</th><th>判定对象</th><th>上限来源</th></tr>
 *   <tr><td>{@code inventoryFull()}</td><td>漏斗自身</td><td>{@code itemCapFor(this)}：漏斗默认不提升 → 原版 64 / 16 / 1</td></tr>
 *   <tr><td>{@code isFullContainer(目标, 面)}</td><td>目标容器</td><td>{@code itemCapFor(目标)}：箱子等提升容器 → {@code global_max}</td></tr>
 *   <tr><td>{@code tryMoveInItem(…, int, Direction)}</td><td>目标容器</td><td>{@code itemCapFor(目标)}，并夹下界保证增量非负</td></tr>
 *   <tr><td>{@code canMergeItems(ItemStack, ItemStack)}</td><td>—</td><td>只判"是否同种物品"；计数上限交由 {@code tryMoveInItem} 负责</td></tr>
 * </table>
 *
 * <p>由此得到的行为：
 * <ul>
 *   <li>漏斗自己仍然只能装到原版上限——红石计时器 / 计数器不受影响；</li>
 *   <li>漏斗向箱子等<b>提升容器</b>推送时，可以把每一格填到 {@code global_max}，而不是 64。</li>
 * </ul>
 *
 * <p>漏斗矿车（{@code MinecartHopper}）的搬运逻辑完全委托给本类的
 * {@code extract} / {@code transfer}，因此同样被覆盖。
 *
 * <p>4 参数的重载 {@code transfer(Container, Container, ItemStack, Direction)} 只负责遍历格子，
 * 自身不直接读取上限，故不在重定向范围内。
 *
 * <p><b>为什么注入 {@code isFullContainer} 本体</b>：
 * 本代"目标容器是否已满"的判定被编译进 {@code isFullContainer} 内部<b>没有官方名的合成 lambda</b>
 * （Fabric 只能写中介名 {@code method_17769}，Forge 侧叫 {@code lambda$isFullContainer$1}），
 * 一份共享源码写不出同时适配两者的注解，故不注入 lambda，而是直接注入其宿主方法本体，
 * 完整复刻那段判定（只把上限来源换成 {@code itemCapFor}）。
 *
 * <p><b>为什么不走 {@code ejectItems}</b>：Forge 的 patch 改过它的签名（第 4 个参数
 * {@code Container} → {@code HopperBlockEntity}），描述符与映射表对不上，Mixin AP
 * 生成不出 refmap 条目，正式环境会注入失败 —— 详见 {@code IS_FULL_CONTAINER} 常量的注释。
 *
 * <p>其余三处（{@code inventoryFull}、{@code tryMoveInItem}、{@code canMergeItems}）
 * 都是未被 patch 改动的具名方法，结构不变。
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    private static final String GET_MAX_COUNT = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I";

    /**
     * {@code isFullContainer(Container, Direction)} —— 本混入的注入宿主。
     *
     * <p><b>为什么写「裸名 + 描述符」，且不去注入 {@code ejectItems}</b>：
     * Forge 的 patch 改过 {@code ejectItems} 的签名 —— 第 4 个参数由 {@code Container}
     * 换成了 {@code HopperBlockEntity}（{@code ejectItems(Level, BlockPos, BlockState,
     * HopperBlockEntity)}），并插入了一处 {@code VanillaInventoryCodeHooks.insertHook}。
     * 描述符一变，它在 Forge 的映射表里就对不上，Mixin AP <b>无法为它生成 refmap 条目</b>
     * （实测：Fabric 侧 refmap 有 {@code ejectItems → method_11246}，Forge 侧则完全没有，
     * 且是 AP 原始输出就缺、并非 reobf 丢失）。dev 环境因方法名恰好就是官方名而侥幸能跑，
     * <b>正式环境（SRG）会因找不到宿主方法而注入失败</b>。
     *
     * <p>而 {@code isFullContainer} 未被 patch 触碰，两侧签名与映射都干净
     * （Fabric {@code method_11258}、Forge {@code m_59385_}，refmap 两侧都有条目）。
     * 原版只有 {@code ejectItems} 调用它，因此直接注入它本体与在调用点重定向完全等价。
     */
    private static final String IS_FULL_CONTAINER =
            "isFullContainer(Lnet/minecraft/world/Container;Lnet/minecraft/core/Direction;)Z";

    private static final String TRANSFER_WITH_SLOT =
            "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;"
                    + "Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)"
                    + "Lnet/minecraft/world/item/ItemStack;";

    /**
     * 遍历目标容器的可用格。
     *
     * <p>原版为 {@code private static}，这里只为在 handler 中复刻
     * {@code isFullContainer} 的判定逻辑而阴影引用；两侧均为具名方法
     * （Fabric {@code method_17767}、Forge {@code m_59339_}），故可直接写官方名。
     */
    @Shadow
    private static IntStream getSlots(Container container, Direction direction) {
        throw new AssertionError("shadow");
    }

    /**
     * 目标容器"是否已满"的判定：上限由目标容器决定。
     *
     * <p>完整复刻原版 {@code isFullContainer(Container, Direction)}：
     * {@code getSlots(container, direction).allMatch(slot -> getItem(slot).getCount() >= 上限)}，
     * <b>唯一区别</b>是把 {@code ItemStack#getMaxStackSize()}（已被 {@code ItemStackMixin}
     * 全局放大）换成 {@code ContainerPolicy#itemCapFor(目标容器, 物品)}。
     *
     * <p>若沿用原版值，箱子每格一到 64 就会被判定为"已满"，漏斗停止推送。
     *
     * <p>注入方式为 {@code @Inject(HEAD, cancellable)}：直接在方法入口给出结果，跳过原方法体，
     * 语义上与"重定向其内部的 {@code ItemStack#getMaxStackSize()} 调用"完全一致，
     * 但不需要写 {@code @At(target = …)}，少一个可能失配的点。
     *
     * @param container 目标容器（原方法参数）
     * @param direction 抽取/推送方向（原方法参数）
     * @param cir       回调对象，用于设置返回值
     */
    @Inject(method = IS_FULL_CONTAINER, at = @At("HEAD"), cancellable = true)
    private static void bis$isFullContainer(Container container, Direction direction,
                                           CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(getSlots(container, direction).allMatch(slot -> {
            ItemStack stack = container.getItem(slot);
            return stack.getCount() >= ContainerPolicy.itemCapFor(container, stack);
        }));
    }

    /**
     * {@code transfer(Container from, Container to, ItemStack stack, int slot, Direction side)}
     * 中的上限：由目标容器 {@code to} 决定，这是真正决定"一次能搬多少"的闸门。
     *
     * <p>该返回值在原方法中只参与一次减法：{@code i = 返回值 - 目标格当前数量}，
     * 随后 {@code j = min(来料数量, i)}，再对两堆分别做 {@code decrement(j)} / {@code increment(j)}。
     * 因此这里必须保证 {@code i >= 0}：一旦目标格的数量已经超过上限（例如玩家调低了
     * {@code global_max}），负数增量会让目标格的数量<b>倒退</b>。夹一层下界即可消除该风险。
     *
     * @param stack     原调用接收者（来料堆叠）
     * @param from      来源容器（追加的目标方法参数）
     * @param to        目标容器（追加的目标方法参数）
     * @param incoming  目标方法的第 3 个参数，即来料堆叠
     * @param slot      目标格索引（追加的目标方法参数）
     * @param side      方向（追加的目标方法参数）
     * @return 参与"剩余可容纳量"计算的上限，保证结果非负
     */
    @Redirect(method = TRANSFER_WITH_SLOT, at = @At(value = "INVOKE", target = GET_MAX_COUNT))
    private static int bis$transferCap(ItemStack stack, Container from, Container to, ItemStack incoming,
                                      int slot, Direction side) {
        int cap = ContainerPolicy.itemCapFor(to, incoming);
        int current = to.getItem(slot).getCount();
        return Math.max(cap, current);
    }

    /**
     * {@code canMergeItems(ItemStack first, ItemStack second)}：原版实现为
     * {@code first.getCount() < first.getMaxStackSize() && ItemStack.canCombine(first, second)}。
     *
     * <p>前半段的计数检查在这里既无意义也有害：{@code first} 是目标格里的堆叠，而"能装多少"
     * 必须由目标容器决定，这个方法拿不到目标容器。若沿用原版值，一个已经堆到 5000 的提升容器
     * 格子会因为 {@code 5000 > 64} 而被判定为"不可合并"，导致漏斗无法把箱子补满到
     * {@code global_max} —— 与本 Mod 的目标冲突。
     *
     * <p>因此这里只保留真正有意义的"是否同种物品"判定。<b>计数上限并没有被放松</b>：
     * 权威闸门是 {@code transfer} 里的 {@code bis$transferCap}（已夹下界保证安全），
     * 以及 {@code to.setStack} 路径上的容量收窄。
     *
     * <p>{@code ItemStack#canCombine} 在本代（1.20 – 1.20.4）的定义是
     * {@code areItemsEqual && areNbtEqual}，即"物品 + NBT 相同"，没有 1.20.5+ 才有的物品组件概念。
     *
     * @param first  目标格中的堆叠
     * @param second 来料堆叠
     * @param cir    回调对象，用于设置返回值
     */
    @Inject(method = "canMergeItems", at = @At("HEAD"), cancellable = true)
    private static void bis$canMerge(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ItemStack.isSameItemSameTags(first, second));
    }

    /**
     * {@code isFull()} 中的上限：判定对象是<b>漏斗自身</b>，因此必须用漏斗自己的分类
     * （默认不提升 → 原版上限），这样漏斗装满 64 / 16 / 1 后就会停止抽取。
     *
     * @param stack 原调用接收者
     * @return 该物品在漏斗中的上限
     */
    @Redirect(method = "inventoryFull", at = @At(value = "INVOKE", target = GET_MAX_COUNT))
    private int bis$isFullCap(ItemStack stack) {
        return ContainerPolicy.itemCapFor((HopperBlockEntity) (Object) this, stack);
    }
}
