package com.zhugey.betteritemstack.neoforge;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.Config;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * NeoForge 1.20.1 平台入口。
 *
 * <p>1.20.1 的 NeoForge 是 Forge 47.x 的镜像（包名仍是 {@code net.minecraftforge}、
 * loader 的 modid 仍是 {@code forge}），因此本类与 {@code forge} 子项目里的入口在结构上完全相同；
 * 差异只在构建脚本解析的坐标（{@code net.neoforged:forge}）与产物命名。
 */
@Mod(BetterItemStack.MOD_ID)
public class BetterItemStackNeoForge {

    public BetterItemStackNeoForge() {
        // 读取配置（文件不存在时会生成默认配置）
        Config.load();

        // 注册到事件总线，接收 RegisterCommandsEvent
        MinecraftForge.EVENT_BUS.register(this);

        BetterItemStack.LOGGER.info("BetterItemStack ({}) initialized (version {})",
                "NeoForge", BetterItemStack.version());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BetterItemStack.registerCommands(event.getDispatcher());
    }
}
