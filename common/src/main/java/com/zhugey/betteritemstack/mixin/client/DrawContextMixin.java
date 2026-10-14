package com.zhugey.betteritemstack.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * <p>通过 Mixin 修改 {@code GuiGraphicsExtractor} 类，用于在物品栏中显示堆叠数时格式化大数字。
 * <p>将超过 1000 的数字格式化为 K、M、B 单位，并添加颜色标识。
 *
 * <p><b>26.2 的改名</b>：本代把旧 {@code net.minecraft.client.gui.GuiGraphics} 整类更名为
 * {@link GuiGraphicsExtractor}（新渲染管线里它只负责"抽取"绘制指令，不再即刻绘制），
 * 原 {@code renderItemDecorations} 也随之更名为 {@code itemDecorations}，签名逐字未变：
 * {@code (Font, ItemStack, int, int, @Nullable String)}。
 */
@Mixin(GuiGraphicsExtractor.class)
public class DrawContextMixin {

    /**
     * <p>修改 itemDecorations 方法中显示堆叠数量的字符串。
     * <p>当物品数量大于或等于 1000 时，按千、百万、十亿缩写显示，并添加颜色标识。
     *
     * @param original 原始显示字符串
     * @param textRenderer 文本渲染器对象
     * @param stack 物品堆栈对象
     * @param x x 坐标
     * @param y y 坐标
     * @param countOverride 可选的数量覆盖字符串
     * @return 格式化后的数量字符串
     */
    @ModifyVariable(
            method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    public String itemStackTextStringFormat(String original, Font textRenderer, ItemStack stack, int x, int y, @Nullable String countOverride) {
        int stackCount = stack.getCount();

        if (stackCount >= 1_000_000_000) { // 十亿以上
            double value = stackCount / 1_000_000_000.0;
            return formatNumber(value) + "§6B";
        } else if (stackCount >= 1_000_000) { // 百万以上
            double value = stackCount / 1_000_000.0;
            return formatNumber(value) + "§3M";
        } else if (stackCount >= 1_000) { // 千以上
            double value = stackCount / 1_000.0;
            return formatNumber(value) + "§9K";
        } else { // 小于千，保持原始显示
            return original;
        }
    }

    /**
     * <p>格式化大数字，保留一位小数，如果小数为 0 则去掉小数点。
     *
     * @param value 要格式化的数字
     * @return 格式化后的字符串
     */
    @Unique
    private String formatNumber(double value) {
        if (value == (int) value || value > 100D) {
            return String.valueOf((int) value);
        }
        String formatted = String.format("%.1f", value);
        if (formatted.charAt(formatted.indexOf('.') + 1) == '0') {
            return String.valueOf((int) value);
        }
        return formatted;
    }
}
