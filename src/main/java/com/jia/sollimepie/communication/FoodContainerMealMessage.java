package com.jia.sollimepie.communication;

import com.jia.sollimepie.SOLLimePie;
import com.jia.sollimepie.item.foodcontainer.FoodContainerUse;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record FoodContainerMealMessage(int playerId, int sequence, InteractionHand hand, ItemStack food)
    implements CustomPacketPayload {
    public static final Type<FoodContainerMealMessage> TYPE = new Type<>(SOLLimePie.resourceLocation("food_container_meal"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FoodContainerMealMessage> STREAM_CODEC = StreamCodec.of(
        (buffer, message) -> {
            buffer.writeVarInt(message.playerId);
            buffer.writeVarInt(message.sequence);
            buffer.writeEnum(message.hand);
            ItemStack.STREAM_CODEC.encode(buffer, message.food);
        },
        buffer -> new FoodContainerMealMessage(buffer.readVarInt(), buffer.readVarInt(), buffer.readEnum(InteractionHand.class),
            ItemStack.STREAM_CODEC.decode(buffer))
    );

    @Override
    public Type<FoodContainerMealMessage> type() { return TYPE; }

    public static void handle(FoodContainerMealMessage message, IPayloadContext context) {
        context.enqueueWork(() -> FoodContainerUse.acceptAppearance(context.player(), message));
    }
}
