package com.zhugey.betteritemstack.forge;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.Config;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge 平台入口。
 *
 * <p>与 Fabric 版的差别只在外壳：入口是 {@code @Mod} 构造器，注册时机由
 * {@code RegisterCommandsEvent} 事件给出，配置目录来自 {@code FMLPaths}。
 * 命令树与全部文案都由平台无关代码提供，因此 {@code assets/betteritemstack/lang/} 可原样复用。
 */
@Mod(BetterItemStack.MOD_ID)
public class BetterItemStackForge {

    public BetterItemStackForge() {
        // 读取配置（文件不存在时会生成默认配置）
        Config.load();

        // 注册到 Forge 事件总线，接收 RegisterCommandsEvent
        MinecraftForge.EVENT_BUS.register(this);

        BetterItemStack.LOGGER.info("BetterItemStack ({}) initialized (version {})",
                "Forge", BetterItemStack.version());
    }

    /**
     * 指令注册时机由 Forge 的事件系统决定（对应 Fabric 的 {@code CommandRegistrationCallback}）。
     */
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BetterItemStack.registerCommands(event.getDispatcher());
    }
}
