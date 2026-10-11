package com.zhugey.betteritemstack.fabric;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric 侧的 {@link Platform} 实现。
 *
 * <p>由 {@code META-INF/services/com.zhugey.betteritemstack.platform.Platform} 注册，
 * 供公共代码通过 {@code Platforms.get()} 取用。
 */
public final class FabricPlatform implements Platform {

    @Override
    public String name() {
        return "Fabric";
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modVersion() {
        return FabricLoader.getInstance().getModContainer(BetterItemStack.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
