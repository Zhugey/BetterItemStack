package com.zhugey.betteritemstack.mixin;

import com.zhugey.betteritemstack.ContainerPolicy;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
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
 * <p>共三处读取上限的地方，判定对象各不相同，必须分别处理：
 * <table border="1">
 *   <caption>注入点与上限来源</caption>
 *   <tr><th>方法</th><th>判定对象</th><th>上限来源</th><th>注入方式</th></tr>
 *   <tr><td>{@code inventoryFull()}</td><td>漏斗自身</td>
 *       <td>{@code itemCapFor(this)}：漏斗默认不提升 → 原版 64 / 16 / 1</td><td>{@code @Redirect}</td></tr>
 *   <tr><td>{@code isFullContainer(目标, 面)}</td><td>目标容器</td>
 *       <td>{@code itemCapFor(目标)}：箱子等提升容器 → {@code global_max}</td><td>{@code @Redirect}</td></tr>
 *   <tr><td>{@code tryMoveInItem(…, int, Direction)}</td><td>目标容器</td>
 *       <td>{@code itemCapFor(目标)}，并夹下界保证增量非负</td><td>{@code @Redirect}</td></tr>
 *   <tr><td>{@code canMergeItems(a, b)}</td><td>—</td>
 *       <td>只判"是否同种物品"；计数上限交由 {@code tryMoveInItem} 负责</td>
 *       <td>{@code @Inject}</td></tr>
 * </table>
 *
 * <p>由此得到的行为：
 * <ul>
 *   <li>漏斗自己仍然只能装到原版上限——红石计时器 / 计数器不受影响；</li>
 *   <li>漏斗向箱子等<b>提升容器</b>推送时，可以把每一格填到 {@code global_max}，而不是 64。</li>
 * </ul>
 *
 * <p>漏斗矿车（{@code MinecartHopper}）的搬运逻辑完全委托给本类的 {@code ejectItems} /
 * {@code tryMoveInItem}，因此同样被覆盖。
 *
 * <h2>为什么本代可以放心用 {@code @Redirect}</h2>
 *
 * <p>在 1.20.1 上，"目标容器是否已满"的那段判定被 javac 编译成了
 * <b>没有官方名的合成 lambda</b>（Fabric 侧叫 {@code method_17769}、Forge 侧叫
 * {@code lambda$isFullContainer$1}），一份共享源码写不出同时适配两者的 {@code @At(target = …)}，
 * 只能退而注入宿主方法本体、并额外阴影引用 {@code getSlots}。
 *
 * <p>本代的 {@code isFullContainer} 已改写为<b>内联的数组循环</b>，
 * 上限调用就落在方法自己的字节码里，因此可以直接重定向该调用，
 * 既不必复刻方法体，也不必阴影 {@code getSlots}（它的返回类型在本代还由
 * {@code IntStream} 变成了 {@code int[]}，多依赖一个符号就多一个失配点）。
 *
 * <p>另需注意：{@code ejectItems} 依旧<b>不是</b>注入点。它的签名在 Forge / NeoForge 的 patch
 * 里被改过（第 4 个参数由 {@code Container} 换成 {@code HopperBlockEntity} 并插入能力钩子），
 * 描述符一变就与映射表对不上，refmap 生成不出条目，<b>正式环境会因找不到宿主方法而注入失败</b>
 * （1.20.1 上实际踩到过）。本代同样避开它，只注入未被 patch 触碰的 {@code isFullContainer}。
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    /** 三处注入共同的目标调用：{@code ItemStack#getMaxStackSize()}。 */
    private static final String GET_MAX_STACK_SIZE = "Lnet/minecraft/world/item/ItemStack;getMaxStackSize()I";

    private static final String IS_FULL_CONTAINER =
            "isFullContainer(Lnet/minecraft/world/Container;Lnet/minecraft/core/Direction;)Z";

    private static final String TRY_MOVE_IN_ITEM =
            "tryMoveInItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;"
                    + "Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/Direction;)"
                    + "Lnet/minecraft/world/item/ItemStack;";

    /**
     * {@code isFullContainer(Container, Direction)} 中的上限：由<b>目标容器</b>决定。
     *
     * <p>原方法为
     * <pre>
     * for (int i : getSlots(container, direction)) {
     *     ItemStack stack = container.getItem(i);
     *     if (stack.getCount() &lt; stack.getMaxStackSize()) return false;
     * }
     * return true;
     * </pre>
     * 若沿用被放大的 {@code getMaxStackSize()}，箱子每格一到 64 就会被判定为"已满"，
     * 漏斗随即停止推送。
     *
     * @param stack     原调用接收者（目标容器某格中的堆叠）
     * @param container 目标容器（追加的目标方法参数）
     * @param direction 抽取/推送方向（追加的目标方法参数）
     * @return 该物品在目标容器中的上限
     */
    @Redirect(method = IS_FULL_CONTAINER, at = @At(value = "INVOKE", target = GET_MAX_STACK_SIZE))
    private static int bis$targetContainerCap(ItemStack stack, Container container, Direction direction) {
        return ContainerPolicy.itemCapFor(container, stack);
    }

    /**
     * {@code inventoryFull()} 中的上限：判定对象是<b>漏斗自身</b>，因此必须用漏斗自己的分类
     * （默认不提升 → 原版上限），这样漏斗装满 64 / 16 / 1 后就会停止抽取。
     *
     * <p>原方法为
     * <pre>
     * for (ItemStack stack : this.items) {
     *     if (stack.isEmpty() || stack.getCount() != stack.getMaxStackSize()) return false;
     * }
     * return true;
     * </pre>
     * 换成 {@code itemCapFor(this, stack)} 后，"数量刚好等于该物品在漏斗里的上限"仍被算作装满，
     * 与原版以 64 / 16 / 1 计数的行为一致。
     *
     * @param stack 原调用接收者（漏斗格中的堆叠）
     * @return 该物品在漏斗中的上限
     */
    @Redirect(method = "inventoryFull()Z", at = @At(value = "INVOKE", target = GET_MAX_STACK_SIZE))
    private int bis$hopperSelfCap(ItemStack stack) {
        return ContainerPolicy.itemCapFor((HopperBlockEntity) (Object) this, stack);
    }

    /**
     * {@code tryMoveInItem(…)} 中的上限：由目标容器 {@code to} 决定，
     * 这是真正决定"一次能搬多少"的闸门。
     *
     * <p>原方法为
     * <pre>
     * int i = incoming.getMaxStackSize() - target.getCount();
     * int j = Math.min(incoming.getCount(), i);
     * incoming.shrink(j); target.grow(j);
     * </pre>
     * 该返回值只参与一次减法，因此这里必须保证结果 {@code >= 0}：一旦目标格的数量已经超过上限
     * （例如玩家调低了 {@code global_max}，或物品是从提升容器搬过来的），负数增量会让目标格的
     * 数量<b>倒退</b>——也就是复制 / 销毁物品。夹一层下界即可消除该风险。
     *
     * <p>handler 的前六个形参依次是：被重定向调用的接收者，加目标方法自己的五个形参。
     * 注意本处接收者与第三个方法形参是同一个对象（原版就是拿刚传入的 {@code incoming} 来读上限的）。
     *
     * @param stack    原调用接收者（来料堆叠）
     * @param from     来源容器（目标方法第 1 参，可能为 null）
     * @param to       目标容器（目标方法第 2 参）
     * @param incoming 来料堆叠（目标方法第 3 参）
     * @param slot     目标格索引（目标方法第 4 参）
     * @param side     方向（目标方法第 5 参，可能为 null）
     * @return 参与"剩余可容纳量"计算的上限，保证结果非负
     */
    @Redirect(method = TRY_MOVE_IN_ITEM, at = @At(value = "INVOKE", target = GET_MAX_STACK_SIZE))
    private static int bis$transferCap(ItemStack stack, Container from, Container to, ItemStack incoming,
                                      int slot, Direction side) {
        int cap = ContainerPolicy.itemCapFor(to, incoming);
        int current = to.getItem(slot).getCount();
        return Math.max(cap, current);
    }

    /**
     * {@code canMergeItems(ItemStack first, ItemStack second)}：原版实现为
     * {@code first.getCount() <= first.getMaxStackSize() && ItemStack.isSameItemSameComponents(first, second)}。
     *
     * <p>前半段的计数检查在这里既无意义也有害：{@code first} 是目标格里的堆叠，而"能装多少"
     * 必须由目标容器决定，这个方法拿不到目标容器。若沿用原版值，一个已经堆到 5000 的提升容器
     * 格子会因为 {@code 5000 > 64} 而被判定为"不可合并"，导致漏斗无法把箱子补满到
     * {@code global_max} —— 与本 Mod 的目标冲突。
     *
     * <p>因此这里只保留真正有意义的"是否同种物品"判定。<b>计数上限并没有被放松</b>：
     * 权威闸门是 {@code tryMoveInItem} 里的 {@code bis$transferCap}（已夹下界保证安全），
     * 以及 {@code setItem} 路径上的容量收窄。
     *
     * <p>本代的"同种"判定是 {@code ItemStack#isSameItemSameComponents}（1.20 一代的
     * {@code isSameItemSameTags} / {@code canCombine} 已被移除）：物品相同 <b>且物品组件相同</b>，
     * 也就是 1.20 一代"物品 + NBT 相同"在组件体系下的等价物。
     *
     * @param first  目标格中的堆叠
     * @param second 来料堆叠
     * @param cir    回调对象，用于设置返回值
     */
    @Inject(method = "canMergeItems(Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private static void bis$canMerge(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(ItemStack.isSameItemSameComponents(first, second));
    }
}
