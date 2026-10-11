package com.zhugey.betteritemstack.neoforge;

import com.zhugey.betteritemstack.BetterItemStack;
import com.zhugey.betteritemstack.platform.Platform;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * NeoForge 1.20.1 侧的 {@link Platform} 实现。
 *
 * <p>1.20.1 的 NeoForge 沿用 {@code net.minecraftforge} 包名，API 与 Forge 相同。
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
