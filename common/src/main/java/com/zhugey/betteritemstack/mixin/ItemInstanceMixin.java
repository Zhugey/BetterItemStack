package com.zhugey.betteritemstack.mixin;

import com.zhugey.betteritemstack.MaxStackLimit;
import net.minecraft.world.item.ItemInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * <p>针对 {@link ItemInstance} 的 Mixin：把"每叠最多能放多少"全局放大到
 * {@link com.zhugey.betteritemstack.Config#GLOBAL_MAX}。
 * 这是本 Mod 唯一一处"全局放大"物品上限的地方，也是其余所有修正的根源。</p>
 *
 * <h2>为什么目标从 {@code ItemStack} 变成了 {@code ItemInstance}（26.2 的关键差异）</h2>
 *
 * <p>1.21.11 及以前，{@code getMaxStackSize()} 是 {@code ItemStack} <b>自己声明</b>的方法，
 * 直接 {@code @Inject(method = "getMaxStackSize()I", at = @At("HEAD"))} 即可。</p>
 *
 * <p>26.2 把它上提到了接口 {@code ItemInstance}，写成<b>默认方法</b>；{@code ItemStack} 与本代
 * 新增的 {@code ItemStackTemplate} 都只是继承它，<b>原版与 Forge 的 {@code ItemStack} 自己不再
 * 声明</b>。于是：</p>
 *
 * <ol>
 *   <li>在 {@code @Mixin(ItemStack.class)} 上写 {@code @Inject(method = "getMaxStackSize()I")}
 *       已经<b>注入不到</b>——方法不在宿主类里。</li>
 *   <li>目标换成接口之后，Mixin 有一条硬性规则：<b>目标是接口时，mixin 自己也必须是接口</b>
 *       （写成 {@code abstract class} 会在运行期被拒：
 *       {@code @Mixin target type mismatch: ... is an interface}）。
 *       而 Forge 侧的 Mixin 注解处理器又拒绝接口里的 injector
 *       （{@code Injector in interface is unsupported}）。两者无法同时满足，
 *       因此只能走 {@link Overwrite}（它不属于 injector，是两侧都能通过的唯一形式）。
 *       这与 {@link InventoryMixin} 处理 {@code Container} 的做法完全同构。</li>
 * </ol>
 *
 * <h2>★ 只改这里远远不够：NeoForge 会绕开本接口</h2>
 *
 * <p><b>NeoForge 26.2 的补丁给 {@code ItemStack} 补了自己的 {@code getMaxStackSize()}</b>
 * （实现是 {@code return getItem().getMaxStackSize(this)}，走它自己的物品级扩展点）。
 * 于是 NeoForge 上所有 {@code invokevirtual ItemStack.getMaxStackSize()} 都命中
 * <b>ItemStack 自己的方法</b>，本接口默认方法<b>根本不会被调用</b>——
 * 症状是"改了上限却还是 64 / 16"，而且<b>只在 NeoForge 上出现</b>（Fabric 与 Forge 的
 * {@code ItemStack} 都没有这个方法）。NeoForge 侧由 {@code ItemStackNeoForgeMixin} 接管。</p>
 *
 * <p>其余两个平台之所以命中本接口：原版字节码里 {@code stack.getMaxStackSize()} 被编译成
 * {@code invokevirtual ItemStack.getMaxStackSize()I}（即便 {@code ItemStack} 自己没声明它），
 * 而按 JVMS，{@code invokevirtual} 解析到接口默认方法时按 {@code invokeinterface} 的分派规则走。</p>
 *
 * <h2>为什么可以安全地覆盖整个接口</h2>
 *
 * <p>{@code ItemInstance} 的实现者只有两个，行为都对得上：</p>
 * <ul>
 *   <li>{@code ItemStack} —— 本 Mod 要放大的目标；</li>
 *   <li>{@code ItemStackTemplate}（配方产物用的不可变模板）—— 这里对非 {@code ItemStack} 的情形
 *       返回它自己的 {@code MAX_STACK_SIZE} 组件值，与原版默认实现逐字一致，行为不变。</li>
 * </ul>
 *
 * <p>计算本身集中在 {@link MaxStackLimit#of(ItemInstance)}：本接口与 NeoForge 侧那份
 * {@code @Overwrite} 都调它，因此三平台结果必然一致，不会出现"某平台少写一条例外"的偏差。</p>
 */
@Mixin(ItemInstance.class)
public interface ItemInstanceMixin {

    /**
     * 覆写接口默认的"每叠上限"。
     *
     * <p>计算规则、以及三条"不放大"的例外（非 {@code ItemStack} 的实现者 / 带耐久的物品 /
     * 黑名单物品）全部在 {@link MaxStackLimit#of(ItemInstance)} 里，这里只做转发——
     * 保证与 NeoForge 侧的 {@code ItemStackNeoForgeMixin} 结果完全一致。</p>
     *
     * @return 该实例每叠能容纳的最大数量
     * @author zhugey
     * @reason {@code ItemStack} 在原版与 Forge 上都不再自己声明该方法（26.2 上提到
     *         {@code ItemInstance}），而接口目标只能用 {@code @Overwrite}；
     *         把物品级上限从原版 64 放大到 {@link com.zhugey.betteritemstack.Config#GLOBAL_MAX}
     */
    @Overwrite
    default int getMaxStackSize() {
        return MaxStackLimit.of((ItemInstance) (Object) this);
    }
}
