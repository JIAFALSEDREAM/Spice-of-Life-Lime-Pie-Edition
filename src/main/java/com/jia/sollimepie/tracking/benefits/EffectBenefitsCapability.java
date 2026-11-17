package com.jia.sollimepie.tracking.benefits;

import com.jia.sollimepie.tracking.CapabilityHandler;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

public class EffectBenefitsCapability implements INBTSerializable<CompoundTag>, Iterable<EffectBenefit> {
    private static final int WATCHDOG_TICKS = 20, DURATION = 300, REFRESH_AT = 200;
    private final Map<Holder<MobEffect>, Entry> effectBenefits = new LinkedHashMap<>();
    private final Map<Holder<MobEffect>, EffectBenefit> pending = new LinkedHashMap<>();
    private Entry[] scheduled = new Entry[0];
    private boolean layoutChanged, dirty, updating;
    private int generation, nextCheckTick;
    private Holder<MobEffect> applyingEffect;

    private static final class Entry {
        EffectBenefit benefit;
        int seen, retryAt;
        boolean backingOff;
        Entry(EffectBenefit benefit) { this.benefit = benefit; }
    }

    private static final String NBT_KEY_EFFECT_BENEFITS = "effect_benefits";

    public static EffectBenefitsCapability get(Player player) {
        return player.getData(CapabilityHandler.EFFECT_BENEFITS);
    }

    public void addEffectBenefit(EffectBenefit b) {
        addEffectBenefitUnique(b);
    }

    public void addEffectBenefitUnique(EffectBenefit b) {
        Holder<MobEffect> effect = b.effect();
        if (effect == null) return;
        if (updating) {
            pending.put(effect, b);
            return;
        }
        Entry entry = effectBenefits.get(effect);
        if (entry == null) {
            entry = new Entry(b);
            effectBenefits.put(effect, entry);
            layoutChanged = dirty = true;
        } else if (!entry.benefit.equals(b)) {
            entry.benefit = b;
            entry.backingOff = false;
            dirty = true;
        }
        entry.seen = generation;
    }

    public void removeEffectBenefit(EffectBenefit b) {
        if (updating) {
            pending.remove(b.effect(), b);
            return;
        }
        Entry entry = effectBenefits.get(b.effect());
        if (entry != null && entry.benefit.equals(b)) {
            effectBenefits.remove(b.effect());
            layoutChanged = dirty = true;
            if (effectBenefits.isEmpty()) clear();
        }
    }

    public void beginUpdate() {
        pending.clear();
        updating = true;
        generation++;
    }

    public void endUpdate() {
        updating = false;
        for (EffectBenefit benefit : pending.values()) addEffectBenefitUnique(benefit);
        pending.clear();
        if (effectBenefits.values().removeIf(entry -> entry.seen != generation)) layoutChanged = dirty = true;
        if (effectBenefits.isEmpty()) clear();
    }

    // Events are notifications before vanilla mutates its map; reconcile on the player tick.
    public void effectChanged(Holder<MobEffect> effect) {
        if (effect != applyingEffect && effectBenefits.containsKey(effect)) dirty = true;
    }

    public void tick(Player player) {
        int now = player.tickCount;
        if (updating || effectBenefits.isEmpty() || (!dirty && now - nextCheckTick < 0)) return;
        dirty = false;
        if (layoutChanged) {
            scheduled = effectBenefits.values().toArray(Entry[]::new);
            layoutChanged = false;
        }
        // Stagger both validation and renewals across players; int subtraction tolerates tick wraparound.
        int delay = WATCHDOG_TICKS - Math.floorMod(now + player.getUUID().hashCode(), WATCHDOG_TICKS);
        for (Entry entry : scheduled) delay = Math.min(delay, reconcile(player, entry, now));
        nextCheckTick = now + Math.max(1, delay);
    }

    private int reconcile(Player player, Entry entry, int now) {
        Holder<MobEffect> effect = entry.benefit.effect();
        MobEffectInstance current = player.getEffect(effect);
        int amplifier = (int) entry.benefit.getValue();
        int refreshAt = REFRESH_AT - Math.floorMod(player.getUUID().hashCode(), WATCHDOG_TICKS);
        if (current != null && current.getAmplifier() >= amplifier) {
            if (current.isInfiniteDuration()) return WATCHDOG_TICKS;
            if (current.getAmplifier() > amplifier) return Math.max(1, current.getDuration());
            if (current.getDuration() > refreshAt) return current.getDuration() - refreshAt;
        }
        if (entry.backingOff && now - entry.retryAt < 0) return entry.retryAt - now;
        applyingEffect = effect;
        try {
            entry.backingOff = !player.addEffect(new MobEffectInstance(effect, DURATION, amplifier, false, false));
        } finally {
            applyingEffect = null;
        }
        entry.retryAt = now + WATCHDOG_TICKS;
        return entry.backingOff ? WATCHDOG_TICKS : DURATION - refreshAt;
    }

    public void clear() {
        effectBenefits.clear();
        pending.clear();
        updating = false;
        scheduled = new Entry[0];
        layoutChanged = dirty = false;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag list = new ListTag();
        for (Entry entry : effectBenefits.values()) list.add(entry.benefit.serializeNBT());
        tag.put(NBT_KEY_EFFECT_BENEFITS, list);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
       ListTag list = tag.getList(NBT_KEY_EFFECT_BENEFITS, Tag.TAG_COMPOUND);

        clear();
        list.stream()
                .map(nbt-> (CompoundTag) nbt)
                .map(EffectBenefit::fromNBT)
                .forEach(this::addEffectBenefitUnique);
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) { return serializeNBT(); }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) { deserializeNBT(tag); }

    @Override
    public Iterator<EffectBenefit> iterator() {
        return effectBenefits.values().stream().map(entry -> entry.benefit).iterator();
    }

    public static class EffectsBenefitsNotFoundException extends RuntimeException {
        public EffectsBenefitsNotFoundException() {
            super("Player must have effect benefits capability attached, but none was found.");
        }
    }
}
