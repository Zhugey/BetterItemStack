package com.zhugey.betteritemstack.mixin;

import com.zhugey.betteritemstack.Config;
import com.zhugey.betteritemstack.ContainerPolicy;
import net.minecraft.world.Container;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * <p>针对 {@link Container} 的 Mixin，用于把"每格容量"按容器类型区分对待。
 *
 * <p>本代（1.20 – 1.20.4）的 {@code Container} <b>只有</b> {@code getMaxStackSize()}，
 * 还没有 1.20.5 才加入的 {@code getMaxStackSize(ItemStack)}。所以这里只拦截前者：
 * <ul>
 *   <li><b>提升容器</b>（默认：玩家背包、箱子、木桶、潜影盒等，见配置 {@code containers}）：
 *       返回 {@link Config#GLOBAL_MAX}；</li>
 *   <li><b>非提升容器</b>（默认：漏斗、漏斗矿车）：不拦截，保持原版默认 99。</li>
 * </ul>
 *
 * <p>注意：这里刻意<b>不</b>拦截非提升分支。原版默认值 99 本身没有意义——有效的格子容量
 * 总会被"物品层上限"收窄（1.20.5+ 是 {@code getMaxStackSize(ItemStack)}，本代则是插入逻辑里
 * 直接读的 {@code ItemStack#getMaxStackSize()}）。一旦在这里改成 64，反而会让 16 上限的物品
 * （鸡蛋、雪球等）被错误地允许堆到 64。
 *
 * <p>物品层的上限由被放大的 {@code ItemStack#getMaxStackSize()} 提供；漏斗那条路径不收窄，
 * 因为漏斗的容量判定<b>完全不经过本接口</b>，而是由 {@code HopperBlockEntityMixin} 分别在
 * {@code inventoryFull} / {@code isFullContainer}（目标容器是否已满）/ {@code tryMoveInItem} /
 * {@code canMergeItems} 四处接回容器感知逻辑。
 *
 * <p><b>与 1.20.5+ 分支的差异</b>：那边还需要拦截 {@code getMaxStackSize(ItemStack)}，并用
 * {@link ContainerPolicy#capacityFor} 把返回值夹到"不小于该堆叠当前数量"，
 * 以抵消原版 6 处 {@code ItemStack#capCount} 的"插入即截断"行为。
 * <b>本代没有 {@code ItemStack#capCount}</b>，那条代码路径不存在，因此不需要这个夹取
 * （{@code ContainerPolicy#capacityFor} 在本分支保留但无人调用，仅作参考）。
 */
@Mixin(Container.class)
public interface InventoryMixin {

    /**
     * 重写 {@code Container#getMaxStackSize()} 的默认实现。
     *
     * <p><b>为什么是 {@code @Overwrite} 而不是 {@code @Inject}</b>：目标是 interface，
     * 而 Mixin 有一条硬性规则——目标是 interface 时，mixin 自己也必须是 interface
     * （写成 {@code abstract class} 会在运行期被拒：
     * {@code @Mixin target type mismatch: ... is an interface}）。
     * 但 Forge 侧的 Mixin 注解处理器又拒绝 interface 里的 injector
     * （{@code Injector in interface is unsupported}）。两者无法同时满足，
     * 而 {@code @Overwrite} 不属于 injector，是唯一两侧都能通过的形式。
     *
     * <p>语义与原 {@code @Inject(HEAD, cancellable)} <b>完全一致</b>：原版默认实现就是一句
     * {@code return 64;}，这里把"提升容器"分支换成 {@link Config#GLOBAL_MAX}，其余仍返回 64。
     * 非提升分支<b>刻意保持原样</b>（理由见类文档）。
     *
     * @author zhugey
     * @reason 把提升容器（箱子 / 木桶 / 玩家背包等）的每格上限由原版 64 改为
     *         {@link Config#GLOBAL_MAX}；非提升容器（漏斗等）仍返回原版 64。
     */
    @Overwrite
    default int getMaxStackSize() {
        return ContainerPolicy.isBoosted((Container) (Object) this) ? Config.GLOBAL_MAX : 64;
    }
}
