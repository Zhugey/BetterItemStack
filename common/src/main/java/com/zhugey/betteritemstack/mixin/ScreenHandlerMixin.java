package com.zhugey.betteritemstack.mixin;

import com.zhugey.betteritemstack.ContainerPolicy;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <p>针对 {@link AbstractContainerMenu} 的两处修正，都服务于同一个目标：
 * <b>让客户端那一侧的容量判定与服务端保持一致</b>。
 *
 * <h2>一、登记客户端 GUI 的镜像容器（{@code addSlot}）</h2>
 *
 * <p>原版客户端打开方块容器时走的是 <b>客户端专用</b>工厂，
 * 例如 {@code ChestMenu#createGeneric9x3(int, Inventory)}，
 * 其实现是 {@code new SimpleContainer(9 * rows)}。服务端走的则是
 * {@code createGeneric9x3(int, Inventory, Container)}，容器是真实的方块实体。
 *
 * <p>于是同一个箱子在两侧的类型并不相同：服务端是 {@code ChestBlockEntity}（被
 * {@code ContainerPolicy#keyOf} 识别为 {@code chest}，容量 = {@code global_max}），
 * 客户端却是一个临时的 {@code SimpleContainer}（不在 {@code keyOf} 的识别链里，
 * 在 {@code unknown=false} 下被判为"未识别容器不提升"，容量退回 64）。
 *
 * <p>这里在 {@code addSlot} 的时刻，按界面自身的 {@link MenuType} 反推它镜像的是哪类
 * 方块容器，交给 {@link ContainerPolicy#rememberMirrorContainer} 登记。之后
 * {@code ContainerPolicy#keyOf} 就能认出这些镜像容器，客户端与服务端的判定随之统一。
 *
 * <p>之所以选 {@code addSlot} 作为挂载点，是因为它是所有界面构建槽位的<b>唯一入口</b>：
 * 无论箱子、木桶、潜影盒、漏斗、发射器还是熔炉，客户端构造器最终都会走到这里。
 * 而服务端的真实方块容器都不是 {@code SimpleContainer}，会被
 * {@link ContainerPolicy#rememberMirrorContainer} 直接忽略。
 *
 * <h2>二、为什么<b>不需要</b>修正 {@code getQuickCraftPlaceCount}</h2>
 *
 * <p>本代原版的 case 2（中键拖拽：每格落下一"满堆"）读的是
 * {@code stack.getMaxStackSize()}——<b>堆叠级</b>上限；而它已被 {@code ItemStackMixin}
 * 放大到 {@code global_max}，所以拖拽分堆会自动跟随本 Mod 的上限，无需任何注入。
 *
 * <p>这里曾经有一条 {@code @Redirect}，想把 {@code Item#getMaxStackSize()}（<b>物品级</b>，
 * 恒为 64）换成堆叠级。那其实是<b>其它 MC 代</b>的形态（那些版本的 case 2 写作
 * {@code stack.getItem().getMaxStackSize()}）。在 1.20.1 上该调用<b>并不存在</b>，
 * 于是注入在运行期以
 * <pre>
 * InjectionError: Critical injection failure: Redirector … failed injection check,
 * (0/1) succeeded. Scanned 1 target(s).
 * </pre>
 * 直接崩掉整个 Mixin 应用流程。<b>构建期完全不报错</b>——{@code @At} 的 target 是一段字符串，
 * 注解处理器只核对符号是否存在，不会去目标方法体里确认那处调用真的在。
 */
@Mixin(AbstractContainerMenu.class)
public abstract class ScreenHandlerMixin {

    /** 界面类型，用于反推镜像的是哪类方块容器；部分界面（如马匹）注册为 {@code null}。 */
    @Shadow
    @Final
    private MenuType<?> menuType;

    /**
     * <p>注意 handler 的最后一个参数必须是 {@code CallbackInfoReturnable<Slot>} 而不是
     * {@code CallbackInfo}——{@code addSlot} <b>有返回值</b>（返回刚挂载的 {@link Slot}）。
     * 用错类型不会影响编译，但会在启动时被 Mixin 以
     * {@code InvalidInjectionException: CallbackInfoReturnable is required!} 拒收，
     * 进而连带 {@code AbstractContainerMenu} 类加载失败、整个游戏起不来。
     *
     * @param slot 正在挂载的槽位
     * @param cir  回调对象（本处不取消，仅旁路登记）
     */
    @Inject(method = "addSlot(Lnet/minecraft/world/inventory/Slot;)Lnet/minecraft/world/inventory/Slot;",
            at = @At("HEAD"))
    private void bis$rememberMirrorContainer(Slot slot, CallbackInfoReturnable<Slot> cir) {
        // 只登记客户端 GUI 的 SimpleContainer 镜像；服务端的方块容器不是 SimpleContainer，
        // 马匹/商人/信标等真正使用 SimpleContainer 的界面也会因其界面类型不在映射表内而被跳过。
        ContainerPolicy.rememberMirrorContainer(this.menuType, slot.container);
    }
}
