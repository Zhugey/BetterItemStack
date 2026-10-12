package com.zhugey.betteritemstack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * 「原版上限」查询工具。
 *
 * <p>背景：{@code ItemStack#getMaxStackSize()} 被 {@code ItemStackMixin} 全局改写为
 * {@link Config#GLOBAL_MAX}，因此任何调用它的原版逻辑（尤其是漏斗的容量判定）都会
 * 拿到被放大的值。凡是需要"这件物品原本能堆多少"的地方，都必须走本类。
 *
 * <p>实现方式：1.20.5 起堆叠上限是一件<b>物品组件</b>（{@code minecraft:max_stack_size}），
 * 所以这里直接读组件、<b>绕过</b>被改写的 {@code ItemStack#getMaxStackSize()}：
 * <ul>
 *   <li>不需要维护任何物品上限数据表；</li>
 *   <li>对模组新增物品同样有效（它们也必须通过组件声明上限）；</li>
 *   <li>与物品自己的 {@code Item.Properties#stacksTo()} 声明完全一致（64 / 16 / 1 等）。</li>
 * </ul>
 *
 * <p>（1.20 一代没有组件，那边读的是 {@code Item#getMaxStackSize()}；
 * 1.20.5 起 {@code Item} 上的同名方法已被移除，只能读组件——这是本分支与 1.20.1 的差异。）
 */
public final class VanillaMax {

    /** 取不到上限时的兜底值，与原版未声明组件时的默认一致。 */
    private static final int FALLBACK = 1;

    private VanillaMax() {
    }

    /**
     * @param stack 物品堆叠，允许为 null
     * @return 该堆叠的原版上限；空堆叠或数据缺失时返回 1
     */
    public static int of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return FALLBACK;
        }
        int limit = stack.getOrDefault(DataComponents.MAX_STACK_SIZE, FALLBACK);
        return limit < 1 ? FALLBACK : limit;
    }
}

