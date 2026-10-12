package com.zhugey.betteritemstack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zhugey.betteritemstack.platform.Platforms;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Mod 的平台无关核心：常量、日志、以及整棵 {@code /bis} 命令树。
 *
 * <p>三个平台各自的入口类只负责"在正确的时机调用 {@link #registerCommands} 并加载配置"，
 * 命令树本身只写一遍：
 *
 * <table border="1">
 *   <caption>入口分工</caption>
 *   <tr><th>职责</th><th>Fabric</th><th>Forge / NeoForge</th></tr>
 *   <tr><td>入口</td><td>{@code ModInitializer#onInitialize}</td><td>{@code @Mod} 构造器</td></tr>
 *   <tr><td>注册时机</td><td>{@code CommandRegistrationCallback.EVENT}</td><td>{@code RegisterCommandsEvent}</td></tr>
 * </table>
 *
 * <p><b>国际化</b>：所有面向玩家的文案都通过 {@link Component#translatable} 读语言文件，
 * 代码内不硬编码任何自然语言。语言文件位于 {@code assets/betteritemstack/lang/}，
 * 缺失语言由 Minecraft 自动回退到 {@code en_us}。
 */
public final class BetterItemStack {

    public static final String MOD_ID = "betteritemstack";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** 翻译键前缀。 */
    private static final String KEY = "betteritemstack.";

    /** 容器键名列表在输出中的分隔符（纯符号，无需翻译）。 */
    private static final String SEP = " · ";

    /** 配置文件在游戏内的相对路径，作为参数传给翻译键中的示例文案。 */
    private static final String CONFIG_HINT = "config/betteritemstack.json";

    private BetterItemStack() {
    }

    /** 配置目录，转发到当前平台实现。 */
    public static Path configDir() {
        return Platforms.get().configDir();
    }

    /**
     * 当前 Mod 版本号，取自平台实现。
     *
     * @return 版本字符串，取不到时为 {@code "unknown"}
     */
    public static String version() {
        return Platforms.get().modVersion();
    }

    /**
     * 向命令执行者发送一行文本。
     *
     * @param context 命令上下文
     * @param text    文本内容
     */
    private static void send(CommandContext<CommandSourceStack> context, Component text) {
        context.getSource().sendSuccess(() -> text, false);
    }

    /**
     * 构造一条"容器键名 + 其中文显示名"的输出项。
     *
     * @param configKey 配置里使用的键名，如 {@code hopper}
     * @return 形如 {@code hopper（漏斗）} 的组件
     */
    private static MutableComponent containerEntry(String configKey) {
        return Component.translatable(KEY + "container.entry",
                Component.literal(configKey),
                Component.translatable(ContainerPolicy.translationKey(configKey)));
    }

    /**
     * 把一组容器键名拼成一行输出。
     *
     * @param keys 键名列表；为 null 或空时输出"（无）"对应的翻译键
     * @return 拼接后的组件
     */
    private static MutableComponent joinKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Component.translatable(KEY + "command.info.empty");
        }
        MutableComponent result = Component.empty();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                result.append(Component.literal(SEP));
            }
            result.append(containerEntry(keys.get(i)));
        }
        return result;
    }

    /** {@code /bis get} —— 反馈当前全局最大堆叠值。 */
    private static int executeGet(CommandContext<CommandSourceStack> context) {
        send(context, Component.translatable(KEY + "command.current",
                Component.literal(String.valueOf(Config.GLOBAL_MAX)).withStyle(ChatFormatting.GOLD)));
        return 1;
    }

    /** {@code /bis info} —— 展示容器提升规则、可用键名与配置示例。 */
    private static int executeInfo(CommandContext<CommandSourceStack> context) {
        Config.Containers containers = Config.get().containers;
        boolean whitelist = containers.isWhitelist();

        send(context, Component.translatable(KEY + "command.info.header").withStyle(ChatFormatting.GOLD));
        send(context, Component.translatable(KEY + "command.current",
                Component.literal(String.valueOf(Config.GLOBAL_MAX)).withStyle(ChatFormatting.GOLD)));

        send(context, Component.translatable(KEY + "command.info.mode",
                Component.translatable(KEY + (whitelist ? "command.info.mode.whitelist" : "command.info.mode.blacklist"))
                        .withStyle(ChatFormatting.AQUA)));
        send(context, Component.translatable(KEY + (whitelist ? "command.info.boosted" : "command.info.kept"),
                joinKeys(containers.list).withStyle(ChatFormatting.YELLOW)));

        if (!whitelist) {
            send(context, Component.translatable(KEY + "command.info.unknown",
                    Component.translatable(KEY + (containers.unknown ? "command.info.unknown.yes" : "command.info.unknown.no"))
                            .withStyle(ChatFormatting.AQUA)));
        }

        send(context, Component.translatable(KEY + "command.info.keys").withStyle(ChatFormatting.GRAY));
        send(context, joinKeys(ContainerPolicy.SUPPORTED_KEYS).withStyle(ChatFormatting.GRAY));

        send(context, Component.translatable(KEY + "command.info.example.header").withStyle(ChatFormatting.GOLD));
        send(context, Component.translatable(
                        KEY + (whitelist ? "command.info.example.whitelist" : "command.info.example.blacklist"),
                        Component.literal(CONFIG_HINT).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.WHITE));
        if (!whitelist) {
            send(context, Component.literal("  \"containers\": { \"mode\": \"blacklist\", "
                    + "\"list\": [\"hopper\", \"hopper_minecart\", \"barrel\"] }")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        return 1;
    }

    /** {@code /bis set <global_max>} —— 修改全局最大堆叠值并写回配置文件。 */
    private static int executeSet(CommandContext<CommandSourceStack> context) {
        int value = IntegerArgumentType.getInteger(context, "global_max");

        if (value < 1) {
            context.getSource().sendFailure(Component.translatable(KEY + "command.set.invalid")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        int old = Config.GLOBAL_MAX;
        // 由 Config#setGlobalMax 负责"更新内存 -> 写盘 -> 刷新 GLOBAL_MAX"
        int now = Config.get().setGlobalMax(value);

        send(context, Component.translatable(KEY + "command.set.done",
                Component.literal(String.valueOf(old)).withStyle(ChatFormatting.GRAY),
                Component.literal(String.valueOf(now)).withStyle(ChatFormatting.GOLD)));
        return 1;
    }

    /** {@code /bis reload} —— 从磁盘重新读取配置。 */
    private static int executeReload(CommandContext<CommandSourceStack> context) {
        Config.reload();
        send(context, Component.translatable(KEY + "command.reload.done",
                Component.literal(String.valueOf(Config.GLOBAL_MAX)).withStyle(ChatFormatting.GOLD)));
        return 1;
    }

    /**
     * 注册整棵 {@code /bis} 命令树。
     *
     * <p>由各平台入口在合适的时机调用（Fabric 是 {@code CommandRegistrationCallback}，
     * Forge / NeoForge 是 {@code RegisterCommandsEvent}）。
     *
     * <p>权限判定统一用 {@code Commands#hasPermission(Commands#LEVEL_MODERATORS)}
     * （= 旧 API 的 OP 等级 1）。1.21.9 起原版把 {@code CommandSourceStack#hasPermission(int)}
     * 换成了 {@code PermissionSet} / {@code PermissionLevel} 体系，等级 1 对应
     * {@code PermissionLevel.MODERATORS}；新 API 三个平台同名同义，因此不需要平台分支。
     *
     * @param dispatcher 游戏的命令分发器
     */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bis")
                .executes(context -> {
                    // 输出 Mod 版本信息与可用子命令
                    send(context, Component.translatable(KEY + "command.version",
                            Component.literal(version()).withStyle(ChatFormatting.GOLD)));
                    send(context, Component.translatable(KEY + "command.help").withStyle(ChatFormatting.GRAY));
                    return 1;
                })
                // /bis get
                .then(Commands.literal("get")
                        .executes(BetterItemStack::executeGet))
                // /bis info
                .then(Commands.literal("info")
                        .executes(BetterItemStack::executeInfo))
                // /bis set <global_max>
                .then(Commands.literal("set")
                        .requires(Commands.hasPermission(Commands.LEVEL_MODERATORS))
                        .then(Commands.argument("global_max", IntegerArgumentType.integer())
                                .executes(BetterItemStack::executeSet)))
                // /bis reload
                .then(Commands.literal("reload")
                        .requires(Commands.hasPermission(Commands.LEVEL_MODERATORS))
                        .executes(BetterItemStack::executeReload))
        );
    }
}
