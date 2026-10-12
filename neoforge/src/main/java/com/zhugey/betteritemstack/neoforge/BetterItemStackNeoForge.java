package com.zhugey.betteritemstack.neoforge;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.Config;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * NeoForge 1.21.1 平台入口。
 *
 * <p>与 1.20.1 不同：1.20.1 的 NeoForge 是 Forge 47.x 的镜像（包名 {@code net.minecraftforge}、
 * loader 的 modid 仍是 {@code forge}）；<b>1.20.5 起 NeoForge 是独立实现</b>，
 * 包名改为 {@code net.neoforged.*}、modid 为 {@code neoforge}，事件总线走
 * {@link NeoForge#EVENT_BUS}，元数据文件名也改为 {@code META-INF/neoforge.mods.toml}。
 */
@Mod(BetterItemStack.MOD_ID)
public class BetterItemStackNeoForge {

    public BetterItemStackNeoForge() {
        // 读取配置（文件不存在时会生成默认配置）
        Config.load();

        // 注册到游戏事件总线，接收 RegisterCommandsEvent
        NeoForge.EVENT_BUS.register(this);

        BetterItemStack.LOGGER.info("BetterItemStack ({}) initialized (version {})",
                "NeoForge", BetterItemStack.version());
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        BetterItemStack.registerCommands(event.getDispatcher());
    }
}
