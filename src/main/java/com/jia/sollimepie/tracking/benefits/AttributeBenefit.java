package com.jia.sollimepie.tracking.benefits;

import com.jia.sollimepie.ConfigHandler;
import com.jia.sollimepie.SOLLimePie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;


import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Each instance represents a specific attribute and modifier. Handles logic for application to player.
 */
public final class AttributeBenefit extends Benefit {
    private final AttributeModifier modifier;
    private final UUID id;
    private Holder<Attribute> attribute;
    private final boolean isMaxHealth;

    public AttributeBenefit(String name, double value, double threshold, boolean detriment) {
        super("attribute", name, value, threshold, detriment);

        id = UUID.nameUUIDFromBytes((name + ":" + value + ":" + threshold + ":" + detriment).getBytes(StandardCharsets.UTF_8));

        modifier = new AttributeModifier(SOLLimePie.resourceLocation("benefit/" + id), value, AttributeModifier.Operation.ADD_VALUE);

        isMaxHealth = "generic.max_health".equals(name);
    }

    public AttributeBenefit(String name, double value, double threshold, String uuid, boolean detriment) {
        super("attribute", name, value, threshold, detriment);

        id = UUID.fromString(uuid);

        modifier = new AttributeModifier(SOLLimePie.resourceLocation("benefit/" + id), value, AttributeModifier.Operation.ADD_VALUE);

        isMaxHealth = "generic.max_health".equals(name);
    }

    @Override
    public void applyTo(Player player) {
        if (!checkUsage() || player.level().isClientSide) {
            return;
        }

        float oldMax = player.getMaxHealth();

        AttributeInstance attr;
        try {
            attr = Objects.requireNonNull(player.getAttribute(attribute));
        }
        catch (NullPointerException e) {
            SOLLimePie.LOGGER.warn("ERROR: player does not have attribute: {}", attribute.value().getDescriptionId());
            return;
        }

        if (modifier.equals(attr.getModifier(modifier.id()))) {
            return;
        }
        attr.removeModifier(modifier);
        attr.addPermanentModifier(modifier);

        if (isMaxHealth && !ConfigHandler.isFirstAid) {
            // increase current health proportionally
            float newHealth = player.getHealth() * player.getMaxHealth() / oldMax;
            player.setHealth(newHealth);
        }
    }

    // A fresh index per update avoids N linear vanilla modifier lookups for N unchanged rewards.
    void update(Player player, boolean active, Map<AttributeInstance, Map<ResourceLocation, AttributeModifier>> indexes) {
        if (!checkUsage() || player.level().isClientSide) return;
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) {
            if (active) applyTo(player); else removeFrom(player);
            return;
        }
        var applied = indexes.computeIfAbsent(attr, instance -> {
            Map<ResourceLocation, AttributeModifier> index = new HashMap<>();
            for (var existing : instance.getModifiers()) index.put(existing.id(), existing);
            return index;
        });
        if (active && !modifier.equals(applied.get(modifier.id()))) {
            applyTo(player);
            applied.put(modifier.id(), modifier);
        } else if (!active && applied.remove(modifier.id()) != null) {
            removeFrom(player);
        }
    }

    @Override
    public void removeFrom(Player player) {
        if (!checkUsage() || player.level().isClientSide) {
            return;
        }

        AttributeInstance attr;
        try {
            attr = Objects.requireNonNull(player.getAttribute(attribute));
        }
        catch (NullPointerException e) {
            SOLLimePie.LOGGER.warn("ERROR: player does not have attribute: {}", attribute.value().getDescriptionId());
            return;
        }
        attr.removeModifier(modifier);
    }

    private boolean checkUsage() {
        if (invalid){
            return false;
        }

        if (attribute == null) {
            createAttribute();
            return !invalid;
        }

        return true;
    }

    private void createAttribute() {
        try {
            attribute = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse("generic.speed".equals(name) ? "generic.movement_speed" : name)).orElse(null);
        }
        catch (ResourceLocationException e) {
            markInvalid();
            return;
        }

        if (attribute == null || value == 0) {
            markInvalid();
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        StringTag type = StringTag.valueOf(benefitType);
        StringTag n = StringTag.valueOf(name);
        DoubleTag v = DoubleTag.valueOf(value);
        StringTag uuid = StringTag.valueOf(id.toString());
        DoubleTag thresh = DoubleTag.valueOf(threshold);
        ByteTag detr = ByteTag.valueOf((byte) (detriment ? 1 : 0));

        tag.put("type", type);
        tag.put("name", n);
        tag.put("value", v);
        tag.put("id", uuid);
        tag.put("threshold", thresh);
        tag.put("detriment", detr);

        return tag;
    }

    public static AttributeBenefit fromNBT(CompoundTag tag) {
        String type = tag.getString("type");
        if (!"attribute".equals(type)) {
            throw new RuntimeException("Mismatching benefit type");
        }
        String n = tag.getString("name");
        double v = tag.getDouble("value");
        String uuid = tag.getString("id");
        double thresh = tag.getDouble("threshold");
        boolean detr = tag.getByte("detriment") == 1;

        return new AttributeBenefit(n, v, thresh, uuid, detr);
    }
}
