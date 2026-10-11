package com.zhugey.betteritemstack.platform;

import java.util.ServiceLoader;

/**
 * {@link Platform} 实现的解析入口。
 *
 * <p>用 {@link ServiceLoader} 而不是"入口类里给静态字段赋值"，是为了消除<b>初始化顺序</b>问题：
 * {@code Config} 的配置文件路径是 {@code static final}，一旦有混入代码在平台入口跑起来之前
 * 触碰了 {@code Config}，静态字段就会被过早固化。ServiceLoader 是惰性的，谁先要谁先解析。
 *
 * <p>实现由各平台子项目通过 {@code META-INF/services/com.zhugey.betteritemstack.platform.Platform}
 * 提供。三种平台各自只打包一个实现，因此这里取第一个即可。
 */
public final class Platforms {

    private static volatile Platform instance;

    private Platforms() {
    }

    /**
     * @return 当前平台的实现（进程内只解析一次）
     * @throws IllegalStateException 找不到实现时 —— 说明打包漏了 services 文件
     */
    public static Platform get() {
        Platform local = instance;
        if (local == null) {
            synchronized (Platforms.class) {
                local = instance;
                if (local == null) {
                    local = ServiceLoader.load(Platform.class).findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "未找到 " + Platform.class.getName() + " 的实现，"
                                            + "请检查 META-INF/services 是否被打进 jar"));
                    instance = local;
                }
            }
        }
        return local;
    }
}
