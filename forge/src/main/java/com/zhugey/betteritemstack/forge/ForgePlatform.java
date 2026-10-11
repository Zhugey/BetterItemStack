package com.zhugey.betteritemstack.forge;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Forge 侧的 {@link Platform} 实现。
 *
 * <p>由 {@code META-INF/services/com.zhugey.betteritemstack.platform.Platform} 注册。
 */
public final class ForgePlatform implements Platform {

    @Override
    public String name() {
        return "Forge";
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modVersion() {
        return ModList.get().getModContainerById(BetterItemStack.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
