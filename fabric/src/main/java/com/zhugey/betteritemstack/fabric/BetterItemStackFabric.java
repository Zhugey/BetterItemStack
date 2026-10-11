package com.zhugey.betteritemstack.fabric;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.Config;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * Fabric 平台入口。
 *
 * <p>只做三件事：加载配置、把命令树挂到 {@code CommandRegistrationCallback} 上、打日志。
 * 命令树本身在 {@link BetterItemStack#registerCommands} 里，三个平台共用。
 */
public class BetterItemStackFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        // 读取配置（文件不存在时会生成默认配置）
        Config.load();

        // 注册命令回调；实际命令树由平台无关的 BetterItemStack 提供
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BetterItemStack.registerCommands(dispatcher));

        BetterItemStack.LOGGER.info("BetterItemStack ({}) initialized (version {})",
                "Fabric", BetterItemStack.version());
    }
}
