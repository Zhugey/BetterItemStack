package com.zhugey.betteritemstack.neoforge.mixin;

import com.zhugey.betteritemstack.MaxStackLimit;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * <p><b>只在 NeoForge 生效</b>的补丁：接管 NeoForge 补丁给 {@code ItemStack} 加的那份
 * {@code getMaxStackSize()}。</p>
 *
 * <h2>为什么必须有这一个</h2>
 *
 * <p>26.2 把 {@code getMaxStackSize()} 上提成了 {@code ItemInstance} 的默认方法。
 * <b>原版（Fabric）与 Forge 26.2 的 {@code ItemStack} 都不自己声明它</b>，所以
 * {@code invokevirtual ItemStack.getMaxStackSize()} 会走到接口默认方法上，
 * 由 {@code ItemInstanceMixin} 的 {@code @Overwrite} 接管——这条链在 Fabric 与 Forge 上是通的。</p>
 *
 * <p><b>NeoForge 26.2 却给它补了一份自己的实现</b>（反编译自
 * {@code minecraft-patched-26.2.0.90.jar}）：</p>
 * <pre>
 * public int getMaxStackSize() {
 *     return this.getItem().getMaxStackSize(this);   // 走 NeoForge 的物品级扩展点
 * }
 * </pre>
 *
 * <p>于是 NeoForge 上所有 {@code invokevirtual ItemStack.getMaxStackSize()} 都命中
 * <b>ItemStack 自己的方法</b>，接口默认方法<b>根本不会被调用</b>。症状非常隐蔽：</p>
 * <ul>
 *   <li>混入全部正常应用（{@code .mixin.out} 里能看到 {@code ItemInstance} 被改写过）；</li>
 *   <li>{@code /bis get} 也正常打印新上限（配置与命令都没问题）；</li>
 *   <li>但 {@code /item replace … 90}、{@code /give} 都会报
 *       {@code can only stack up to 64}，箱子/背包的容量也仍按原版 64 / 16 收窄
 *       ——因为 {@code ContainerPolicy#itemCapFor} 对提升容器返回的正是
 *       {@code stack.getMaxStackSize()}。</li>
 * </ul>
 *
 * <p>本类只做转发，计算规则与 {@code ItemInstanceMixin} 共用
 * {@link MaxStackLimit#of(net.minecraft.world.item.ItemInstance)}，因此三平台结果一致。</p>
 *
 * <h2>为什么单独放一个配置文件</h2>
 *
 * <p>{@code @Overwrite} 要求目标方法<b>必须存在</b>；而在 Fabric / Forge 上
 * {@code ItemStack} 没有这个方法，写进共享配置会让那两个平台直接起不来。
 * 因此它只列在 {@code betteritemstack.neoforge.mixins.json} 里、由
 * {@code neoforge.mods.toml} 的第二个 {@code [[mixins]]} 条目加载。
 * 类本身放在 neoforge 子项目，Fabric / Forge 的产物里不含它。</p>
 */
@Mixin(ItemStack.class)
public abstract class ItemStackNeoForgeMixin {

    /**
     * @return 该堆叠每叠能容纳的最大数量（与其它平台同源）
     * @author zhugey
     * @reason NeoForge 给 {@code ItemStack} 补了自己的 {@code getMaxStackSize()}，
     *         它会绕开 {@code ItemInstance} 的默认方法 ⇒ 必须在这层再接管一次，
     *         否则 NeoForge 上"改了上限却还是 64 / 16"
     */
    @Overwrite
    public int getMaxStackSize() {
        return MaxStackLimit.of((ItemStack) (Object) this);
    }
}
