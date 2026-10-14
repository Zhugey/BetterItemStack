package com.zhugey.betteritemstack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * <p><b>「这一叠最多能放多少」的唯一计算入口。</b></p>
 *
 * <p>为什么必须集中在一处：26.2 里这个方法有<b>三个</b>可能的宿主，而不同平台命中的不是同一个，
 * 任何一处漏掉都会表现为"改了上限却还是 64 / 16"：</p>
 *
 * <ol>
 *   <li>{@link ItemInstance#getMaxStackSize()} —— 接口默认方法。<b>原版</b>（Fabric）与
 *       <b>Forge 26.2</b> 的 {@code ItemStack} 都不自带 {@code getMaxStackSize()}，
 *       所有 {@code invokevirtual ItemStack.getMaxStackSize()} 都解析到这个默认方法；</li>
 *   <li>{@code ItemStack#getMaxStackSize()} —— <b>只有 NeoForge 26.2 有</b>。它的补丁给
 *       {@code ItemStack} 补了一个自己的实现（{@code return getItem().getMaxStackSize(this)}），
 *       于是所有调用都命中它、<b>绕开</b>接口默认方法 ⇒ 这里必须单独再接管一次
 *       （见 {@code ItemStackNeoForgeMixin}）；</li>
 *   <li>{@code Item.getMaxStackSize(ItemStack)} —— NeoForge 为物品留的扩展点，被 (2) 调用。
 *       本 Mod 不接管它：把 (2) 接管掉即可覆盖 NeoForge，同时不干扰"物品级自定义上限"这一语义。</li>
 * </ol>
 *
 * <p>本类只做计算，不含任何平台判断；两个混入都调它，因此三平台行为必然一致。</p>
 */
public final class MaxStackLimit {

    private MaxStackLimit() {
    }

    /**
     * 该实例的<b>原版上限</b>：读它自己的 {@code MAX_STACK_SIZE} 组件，<b>绕过</b>被本 Mod 改写的
     * {@code getMaxStackSize()}。与 {@link VanillaMax#of(ItemStack)} 同源，只是对
     * {@link ItemInstance}（含 {@code ItemStackTemplate}）都成立。
     *
     * @param instance 物品实例
     * @return 原版单叠上限（普通物品 64 / 鸡蛋等 16 / 不可堆叠 1）
     */
    public static int vanillaOf(ItemInstance instance) {
        return instance.getOrDefault(DataComponents.MAX_STACK_SIZE, 1);
    }

    /**
     * 本 Mod 生效后的单叠上限。
     *
     * <p>三条"不放大"的例外，返回的都是原版值：</p>
     * <ol>
     *   <li><b>非 {@link ItemStack} 的实现者</b>（{@code ItemStackTemplate}，配方产物模板）；</li>
     *   <li><b>带耐久的物品</b>（工具、护甲）：原版上限即 1。判定用 {@code has(MAX_DAMAGE)}——
     *       它只看物品类型、<b>不受 {@code UNBREAKABLE} 组件影响</b>。若改用
     *       {@link ItemStack#isDamageableItem()}，一个附了 Unbreakable 的工具会被放大，
     *       进而破坏依赖"本叠只有 1 件"的原版逻辑；</li>
     *   <li><b>黑名单物品</b>（{@link Config#nonStackableItems}）。仅在黑名单非空时才查 ID——
     *       本方法是热路径，不做无谓的查找。</li>
     * </ol>
     *
     * @param instance 物品实例
     * @return 生效后的上限
     */
    public static int of(ItemInstance instance) {
        if (!(instance instanceof ItemStack stack)) {
            return vanillaOf(instance);
        }
        if (stack.has(DataComponents.MAX_DAMAGE)) {
            return vanillaOf(stack);
        }
        List<String> blacklist = Config.get().nonStackableItems;
        if (!blacklist.isEmpty()
                && blacklist.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
            return vanillaOf(stack);
        }
        return Config.GLOBAL_MAX;
    }
}
