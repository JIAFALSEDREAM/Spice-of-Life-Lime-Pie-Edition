package com.jia.sollimepie.communication;

import com.jia.sollimepie.ConfigHandler;
import com.jia.sollimepie.SOLLimePie;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Objects;

public record ConfigMessage(CompoundTag tag) implements CustomPacketPayload {
    public static final Type<ConfigMessage> TYPE = new Type<>(SOLLimePie.resourceLocation("config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigMessage> STREAM_CODEC = StreamCodec.of(
        (buffer, message) -> buffer.writeNbt(message.tag),
        buffer -> new ConfigMessage(Objects.requireNonNull(buffer.readNbt(), "Missing config payload"))
    );

    public ConfigMessage() { this(ConfigHandler.serializeConfig()); }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ConfigMessage message, IPayloadContext context) {
        // The integrated server shares these static values; a delayed local packet must not overwrite them.
        if (context.connection().isMemoryConnection()) return;
        context.enqueueWork(() -> ConfigHandler.deserializeConfig(message.tag));
    }
}
