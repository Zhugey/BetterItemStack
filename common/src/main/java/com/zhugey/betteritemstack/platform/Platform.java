package com.zhugey.betteritemstack.platform;

import java.nio.file.Path;

/**
 * 平台差异抽象。
 *
 * <p>本 Mod 三个平台（Fabric / Forge / NeoForge）共用同一份 {@code common} 源码，
 * 真正需要分平台的只有三件事：
 *
 * <table border="1">
 *   <caption>平台差异点</caption>
 *   <tr><th>能力</th><th>Fabric</th><th>Forge / NeoForge</th></tr>
 *   <tr><td>配置目录</td><td>{@code FabricLoader#getConfigDir}</td><td>{@code FMLPaths.CONFIGDIR}</td></tr>
 *   <tr><td>自身版本号</td><td>{@code FabricLoader#getModContainer}</td><td>{@code ModList#getModContainerById}</td></tr>
 *   <tr><td>入口与注册时机</td><td>{@code ModInitializer} + {@code CommandRegistrationCallback}</td>
 *       <td>{@code @Mod} 构造器 + {@code RegisterCommandsEvent}</td></tr>
 * </table>
 *
 * <p>注意<b>不是</b>差异点的东西（因此这里刻意不抽象）：命令树本身是纯 Brigadier，
 * 权限判定在官方映射下两边都叫 {@code CommandSourceStack#hasPermission(int)}，
 * 面向玩家的文案都走 {@code Component.translatable} 读同一套语言文件。
 *
 * <p>实现由各平台子项目提供，并通过 {@code META-INF/services} 注册（见 {@link Platforms}）。
 */
public interface Platform {

    /** 平台名，仅用于日志与 {@code /bis} 输出，如 {@code "Fabric"} / {@code "Forge"} / {@code "NeoForge"}。 */
    String name();

    /** 游戏实例的 {@code config} 目录。 */
    Path configDir();

    /**
     * 从 Mod 元数据读取版本号。
     *
     * <p>刻意从元数据读而不是在代码里写常量：版本号只在 {@code gradle.properties} 里维护一处，
     * 写进源码后一旦忘记同步就会出现"日志里的版本和实际 jar 不一致"。
     *
     * @return 版本字符串；取不到时返回 {@code "unknown"}
     */
    String modVersion();
}
