package com.zhugey.betteritemstack.forge;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.Config;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 平台入口。
 *
 * <p>与 Fabric 版的差别只在外壳：入口是 {@code @Mod} 构造器，注册时机由
 * {@code RegisterCommandsEvent} 事件给出，配置目录来自 {@code FMLPaths}。
 * 命令树与全部文案都由平台无关代码提供，因此 {@code assets/betteritemstack/lang/} 可原样复用。
 *
 * <p><b>Forge 61（eventbus 7.x）换了事件模型</b>：每个事件自带一条 {@code EventBus}，
 * 注册方式是 {@code RegisterCommandsEvent.BUS.addListener(Consumer&lt;T&gt;)}。
 * 旧的 {@code MinecraftForge.EVENT_BUS.register(this)} + {@code @SubscribeEvent} 已不是主路径
 * —— 前者现在的类型是 {@code net.minecraftforge.common.EventBusMigrationHelper}（迁移辅助），
 * {@code SubscribeEvent} 注解也从 {@code eventbus.api} 挪到了 {@code eventbus.api.listener}。
 * 这里直接用新 API，不再需要注解与实例注册。
 */
@Mod(BetterItemStack.MOD_ID)
public class BetterItemStackForge {

    public BetterItemStackForge() {
        // 读取配置（文件不存在时会生成默认配置）
        Config.load();

        // 指令注册时机由 Forge 的事件系统决定（对应 Fabric 的 CommandRegistrationCallback）。
        RegisterCommandsEvent.BUS.addListener(
                event -> BetterItemStack.registerCommands(event.getDispatcher()));

        BetterItemStack.LOGGER.info("BetterItemStack ({}) initialized (version {})",
                "Forge", BetterItemStack.version());
    }
}
