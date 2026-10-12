package com.zhugey.betteritemstack.mixin;

import com.zhugey.betteritemstack.Config;
import com.zhugey.betteritemstack.ContainerPolicy;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * <p>针对 {@link Container} 的 Mixin，用于把"每格容量"按容器类型区分对待。
 *
 * <p>1.21.1 的 {@code Container} 有<b>两个</b>容量入口，两个都必须接管：
 * <ul>
 *   <li>{@code getMaxStackSize()}（无参）—— 原版 default 返回 {@code 99}。
 *       提升容器返回 {@link Config#GLOBAL_MAX}，非提升容器返回原版值 99；</li>
 *   <li>{@code getMaxStackSize(ItemStack)} —— <b>1.20.5 才加入的重载</b>，原版 default 是
 *       {@code Math.min(this.getMaxStackSize(), stack.getMaxStackSize())}。
 *       这一条是 1.21.1 相对 1.20.1 的<b>关键差异</b>，见下。</li>
 * </ul>
 *
 * <h2>为什么必须接管带参重载</h2>
 *
 * <p>1.20.5 起原版在插入路径上多了一句"插入即截断"：{@code setItem} 之后会调用
 * {@code stack.limitSize(this.getMaxStackSize(stack))}（共 6 处：{@code BaseContainerBlockEntity}、
 * {@code HopperBlockEntity}、{@code SimpleContainer}、{@code AbstractFurnaceBlockEntity}、
 * {@code MerchantContainer}、{@code ContainerEntity}）。
 *
 * <p>原版之所以安全，是因为 `min(容器默认 99, 物品上限 64) = 64`；但本 Mod 已把
 * {@code ItemStack#getMaxStackSize()} <b>全局放大</b>到 {@code global_max}，于是
 * {@code min(99, 9999) = 99} —— 漏斗这类"非提升容器"会被悄悄放宽到每格 99，原版语义失守。
 *
 * <p>因此这里覆写带参重载，把容量交回 {@link ContainerPolicy#itemCapFor}：
 * 提升容器给 {@code global_max}，非提升容器给该物品的<b>原版上限</b>（普通 64 / 鸡蛋 16 / 不可堆叠 1）。
 *
 * <p>返回值再夹一层下界 {@code >= stack.getCount()}：{@code limitSize} 是"超出即销毁且不返还"
 * 的破坏性操作，一旦容器里已存在超过新上限的堆叠（例如玩家调低了 {@code global_max}，
 * 或从提升容器搬进漏斗），不夹下界就会直接销毁物品。
 *
 * <h2>为什么是 {@code @Overwrite} 而不是 {@code @Inject}</h2>
 *
 * <p>目标是 interface，而 Mixin 有一条硬性规则——目标是 interface 时，mixin 自己也必须是 interface
 * （写成 {@code abstract class} 会在运行期被拒：{@code @Mixin target type mismatch: ... is an interface}）。
 * 但 Forge 侧的 Mixin 注解处理器又拒绝 interface 里的 injector
 * （{@code Injector in interface is unsupported}）。两者无法同时满足，
 * 而 {@code @Overwrite} 不属于 injector，是唯一两侧都能通过的形式。
 */
@Mixin(Container.class)
public interface InventoryMixin {

    /**
     * 覆写无参版本：提升容器给 {@code global_max}，其余保持原版默认 99。
     *
     * <p>注意这里<b>不能</b>按 {@code itemCapFor} 返回物品级上限——本方法拿不到物品，
     * 而它既是 {@code Slot} 的默认容量来源，也是下面带参重载在其它实现里的兜底。
     * 按物品收窄的职责在带参重载里。
     *
     * @author zhugey
     * @reason 把提升容器（箱子 / 木桶 / 玩家背包等）的每格上限由原版 99 改为 {@link Config#GLOBAL_MAX}
     */
    @Overwrite
    default int getMaxStackSize() {
        return ContainerPolicy.isBoosted((Container) (Object) this) ? Config.GLOBAL_MAX : 99;
    }

    /**
     * 覆写带参版本：真正的容量闸门，按"容器分类 + 物品原版上限"给出每格容量。
     *
     * <p>夹下界的理由见类文档：{@code limitSize} 会销毁超出部分。
     *
     * @param stack 待放入该容器的堆叠
     * @return 该物品在此容器中每格的上限，且不小于 {@code stack} 当前数量
     * @author zhugey
     * @reason 用容器感知的上限取代 {@code min(容器默认, 被放大的物品上限)}，
     *         并在插入截断前夹下界，避免销毁既有物品
     */
    @Overwrite
    default int getMaxStackSize(ItemStack stack) {
        int cap = ContainerPolicy.itemCapFor((Container) (Object) this, stack);
        return Math.max(cap, stack.getCount());
    }
}
