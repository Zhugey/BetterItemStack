package com.zhugey.betteritemstack.neoforge;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.platform.Platform;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * NeoForge 1.21.1 侧的 {@link Platform} 实现。
 *
 * <p>1.20.5 起 NeoForge 独立实现，API 位于 {@code net.neoforged.*}（不再是 1.20.1 的
 * {@code net.minecraftforge.*} 镜像）。
 */
public final class NeoForgePlatform implements Platform {

    @Override
    public String name() {
        return "NeoForge";
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
