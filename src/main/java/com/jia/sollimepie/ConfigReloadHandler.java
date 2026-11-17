package com.jia.sollimepie;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.concurrent.ConcurrentCommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** All gameplay reload entry points converge here; application and feedback run on the server thread. */
@EventBusSubscriber(modid = SOLLimePie.MOD_ID)
public final class ConfigReloadHandler {
    private static ModConfig config;
    private static volatile MinecraftServer server;
    private static Map<String, Object> appliedValues;
    private record Result(Path path, String revision, int status) {}
    private static Result lastResult;

    @SubscribeEvent
    public static void onLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SOLLimePieConfig.SERVER_SPEC) config = event.getConfig();
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        server = event.getServer();
        appliedValues = values(config.getLoadedConfig().config());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        server = null;
        config = null;
        appliedValues = null;
        lastResult = null;
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SOLLimePieConfig.SERVER_SPEC) return;
        MinecraftServer current = server;
        if (current != null) current.execute(() -> {
            if (server == current) reload(current, null, config.getFullPath());
        });
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // A null player identifies the post-reload broadcast, rather than an individual login.
        if (event.getPlayer() == null && event.getPlayerList().getServer() == server) {
            reload(server, null, config.getFullPath(), true);
        }
    }

    public static int reload(CommandSourceStack source) {
        if (source.getServer() != server || config == null) {
            source.sendFailure(message("not_loaded"));
            return 0;
        }
        return reload(server, source, config.getFullPath());
    }

    // Explicit path keeps disk I/O testable without editing a live watched file.
    static int reload(MinecraftServer current, CommandSourceStack source, Path path) {
        return reload(current, source, path, source != null);
    }

    private static int reload(MinecraftServer current, CommandSourceStack source, Path path, boolean force) {
        Component scope = message(path.getParent().getFileName().toString().equals("serverconfig") ? "scope.world" : "scope.global");
        String revision = null;
        try {
            revision = Files.readString(path, StandardCharsets.UTF_8);
            if (!force && lastResult != null && lastResult.path().equals(path) && lastResult.revision().equals(revision)) {
                return lastResult.status();
            }
            CommentedConfig candidate = new TomlParser().parse(revision);
            List<String> warnings = new ArrayList<>();
            SOLLimePieConfig.SERVER_SPEC.correct(candidate, (action, key, oldValue, newValue) ->
                warnings.add("Corrected configuration key: " + String.join(".", key)));
            Map<String, Object> nextValues = values(candidate);
            boolean changed = !nextValues.equals(appliedValues);
            ConfigHandler.ParsedConfig parsed = ConfigHandler.parseServerConfig(candidate, warnings::add);
            if (!changed && !force && warnings.isEmpty()) {
                lastResult = new Result(path, revision, 1);
                return 1;
            }
            warnings.forEach(warning -> SOLLimePie.LOGGER.warn("{}: {}", path, warning));
            replaceLoadedValues(candidate);
            ConfigHandler.applyServerConfig(parsed, current.getPlayerList().getPlayers());
            appliedValues = nextValues;
            Component feedback = warnings.isEmpty()
                ? message(force ? "success" : "changed", scope).withStyle(ChatFormatting.GREEN)
                : message("warnings", scope, warnings.size()).withStyle(ChatFormatting.YELLOW);
            lastResult = new Result(path, revision, 1);
            notify(current, source, feedback, force || changed || !warnings.isEmpty(), false);
            SOLLimePie.LOGGER.info("Reloaded gameplay config {} ({} warnings)", path, warnings.size());
            return 1;
        } catch (Exception exception) {
            if (revision == null) revision = exception.getClass().getName() + ": " + exception.getMessage();
            if (!force && lastResult != null && lastResult.path().equals(path) && lastResult.revision().equals(revision)) return 0;
            lastResult = new Result(path, revision, 0);
            SOLLimePie.LOGGER.error("Could not reload gameplay config {}", path, exception);
            notify(current, source, message("failed", scope).withStyle(ChatFormatting.RED), true, true);
            return 0;
        }
    }

    static CommentedConfig read(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new TomlParser().parse(reader);
        }
    }

    private static void replaceLoadedValues(CommentedConfig candidate) {
        CommentedConfig loaded = config.getLoadedConfig().config();
        java.util.function.Consumer<CommentedConfig> replace = target -> {
            target.clear();
            target.putAll(candidate);
            target.putAllComments(candidate);
        };
        if (loaded instanceof ConcurrentCommentedConfig concurrent) concurrent.bulkCommentedUpdate(replace);
        else replace.accept(loaded);
        SOLLimePieConfig.SERVER_SPEC.afterReload();
    }

    private static Map<String, Object> values(UnmodifiableConfig data) {
        Map<String, Object> result = new LinkedHashMap<>();
        data.valueMap().forEach((key, value) -> result.put(key,
            value instanceof UnmodifiableConfig section ? values(section) : value instanceof List<?> list ? List.copyOf(list) : value));
        return result;
    }

    private static void notify(MinecraftServer current, CommandSourceStack source, Component feedback, boolean broadcast, boolean failed) {
        if (source != null) {
            if (failed) source.sendFailure(feedback);
            else source.sendSuccess(() -> feedback, false);
        }
        if (!broadcast) return;
        current.getPlayerList().getPlayers().stream()
            .filter(player -> player.createCommandSourceStack().hasPermission(2))
            .filter(player -> source == null || player != source.getEntity())
            .forEach(player -> player.sendSystemMessage(feedback));
        if (source == null || source.getEntity() != null) current.sendSystemMessage(feedback);
    }

    private static net.minecraft.network.chat.MutableComponent message(String key, Object... args) {
        return Component.translatable("reload.sollimepie." + key, args);
    }
}
