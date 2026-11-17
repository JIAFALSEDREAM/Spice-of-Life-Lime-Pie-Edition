package com.jia.sollimepie;

import com.electronwill.nightconfig.toml.TomlWriter;
import com.jia.sollimepie.utils.BenefitsParser;
import com.jia.sollimepie.utils.ComplexityParser;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
public final class ConfigReloadGameTests {
    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void diskReloadReportsWarningsAndPreservesStateOnFailure(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var output = new Output();
        var source = server.createCommandSourceStack().withSource(output).withPermission(2);
        Path directory = Files.createTempDirectory("sollimepie-reload-test");
        Path file = directory.resolve("server.toml"), backup = directory.resolve("original.toml");
        Files.copy(activePath(helper), backup);
        try {
            var candidate = ConfigReloadHandler.read(backup);
            candidate.set("Miscellaneous.queueSize", 17);
            candidate.set("Benefits.thresholds", List.of(0.0));
            candidate.set("Benefits.benefitsUnparsed", List.of("attribute,generic.max_health,4"));
            candidate.set("Complexity.complexityUnparsed", List.of("minecraft:bread,7"));
            Files.writeString(file, new TomlWriter().writeToString(candidate));
            helper.assertTrue(ConfigReloadHandler.reload(server, source, file) == 1 && SOLLimePieConfig.size() == 17
                && ConfigHandler.complexityMap.values().contains(7.0), "Disk values were not applied");
            helper.assertTrue(output.last().getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GREEN)),
                "Successful reload did not produce green feedback");
            var applied = ConfigHandler.benefitsList;
            ConfigReloadHandler.reload(server, null, file);
            helper.assertTrue(ConfigHandler.benefitsList == applied, "Duplicate automatic event reapplied benefits");
            // Correction to the already-applied default must still surface the first warning.
            candidate.set("Miscellaneous.queueSize", 32);
            Files.writeString(file, new TomlWriter().writeToString(candidate));
            ConfigReloadHandler.reload(server, source, file);
            applied = ConfigHandler.benefitsList;
            candidate.set("Miscellaneous.queueSize", -1);
            Files.writeString(file, new TomlWriter().writeToString(candidate));
            ConfigReloadHandler.reload(server, null, file);
            helper.assertTrue(ConfigHandler.benefitsList != applied, "Correction warning was skipped because effective values were unchanged");
            candidate.set("Miscellaneous.queueSize", -1);
            candidate.set("Complexity.complexityUnparsed", List.of("minecraft:bread,NaN"));
            Files.writeString(file, new TomlWriter().writeToString(candidate));
            helper.assertTrue(ConfigReloadHandler.reload(server, source, file) == 1, "Recoverable errors failed the reload");
            helper.assertTrue(output.last().getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.YELLOW))
                && ConfigHandler.complexityMap.isEmpty(), "Invalid entries did not produce yellow feedback");
            int size = SOLLimePieConfig.size();
            applied = ConfigHandler.benefitsList;
            Files.writeString(file, "[broken");
            helper.assertTrue(ConfigReloadHandler.reload(server, source, file) == 0 && SOLLimePieConfig.size() == size
                && ConfigHandler.benefitsList == applied, "Malformed TOML changed the active configuration");
            helper.assertTrue(output.last().getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED)),
                "Failed reload did not produce red feedback");
            helper.assertTrue(ConfigReloadHandler.reload(server, null, file) == 0
                && ConfigReloadHandler.reload(server, null, file) == 0, "Repeated automatic failures lost their failure status");
        } finally {
            try { ConfigReloadHandler.reload(server, source, backup); }
            finally { Files.deleteIfExists(file); Files.deleteIfExists(backup); Files.deleteIfExists(directory); }
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty", timeoutTicks = 400)
    public static void reloadCommandsReadDiskAndRequirePermission(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withSource(new Output());
        var dispatcher = server.getCommands().getDispatcher();
        boolean denied = false;
        try { dispatcher.execute("sollimepie reload", source.withPermission(0)); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { denied = true; }
        helper.assertTrue(denied, "Unprivileged source could reload gameplay settings");
        helper.assertTrue(dispatcher.execute("sollimepie reload", source.withPermission(2)) == 1, "Admin reload command failed");
        int original = SOLLimePieConfig.size();
        SOLLimePieConfig.SERVER.queueSize.set(original == 17 ? 18 : 17);
        try {
            dispatcher.execute("reload", source.withPermission(2));
            helper.assertTrue(SOLLimePieConfig.size() == original, "Vanilla /reload did not reread the active file");
        } finally {
            SOLLimePieConfig.SERVER.queueSize.set(original);
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = SOLLimePie.MOD_ID, template = "tests/empty")
    public static void parserWarningsKeepValidAliasesAndThresholdAlignment(GameTestHelper helper) {
        List<String> warnings = new ArrayList<>();
        var parsed = BenefitsParser.parse(List.of("attribute,generic.speed,1;effect,speed", "effect,missing,0", "", "effect,speed,NaN"), warnings::add);
        helper.assertTrue(warnings.size() == 2 && parsed.size() == 4 && parsed.getFirst().size() == 2
            && parsed.get(1).isEmpty() && parsed.get(2).isEmpty(), "Warning collection changed valid benefits or tier alignment");
        warnings.clear();
        var weights = ComplexityParser.parse(List.of("minecraft:bread,Infinity", "minecraft:bread,-1", "minecraft:bread,2.5"), warnings::add);
        helper.assertTrue(warnings.size() == 2 && weights.size() == 1 && weights.values().contains(2.5), "Food weight validation failed");
        helper.succeed();
    }

    private static Path activePath(GameTestHelper helper) {
        Path world = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig/sollimepie-server.toml");
        return Files.exists(world) ? world : FMLPaths.CONFIGDIR.get().resolve("sollimepie-server.toml");
    }

    private static final class Output implements CommandSource {
        private final List<Component> messages = new ArrayList<>();
        @Override public void sendSystemMessage(Component message) { messages.add(message); }
        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
        Component last() { return messages.getLast(); }
    }
}
