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
        // ⚠ 26.2（Forge 65）起 {@code ModList} 由"单例 + 实例方法"改成了**纯静态工具类**：
        //   {@code ModList.get()} 已被删除，查容器一律走静态的 {@code getModContainerById(String)}。
        //   照旧写 {@code ModList.get().getModContainerById(...)} 会在编译期报
        //   "找不到符号: 方法 get()"。（NeoForge 26.2 仍保留 {@code ModList.get()}，两边不同步。）
        return ModList.getModContainerById(BetterItemStack.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
