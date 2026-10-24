package com.example.samurai.magic.data;

import com.example.samurai.magic.spell.Spell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Per-spell cooldowns in game ticks. Keyed by id so unknown (removed) spells survive a load untouched.
 * Used on both sides: the server owns it, the client keeps a copy that counts down on its own.
 */
public class CooldownTracker {

    public static final class Entry {
        private int remaining;
        private final int total;

        Entry(int remaining, int total) {
            this.remaining = remaining;
            this.total = total;
        }

        public int getRemaining() {
            return remaining;
        }

        public int getTotal() {
            return total;
        }
    }

    private final Map<ResourceLocation, Entry> cooldowns = new HashMap<>();
    /** Set when a cooldown is added or removed by hand. Natural expiry does not set it. */
    private boolean dirty;

    public boolean isOnCooldown(Spell spell) {
        return cooldowns.containsKey(spell.getId());
    }

    public int getRemaining(ResourceLocation id) {
        Entry entry = cooldowns.get(id);
        return entry == null ? 0 : entry.remaining;
    }

    public int getRemaining(Spell spell) {
        return getRemaining(spell.getId());
    }

    /** 1.0 just started → 0.0 ready. */
    public float getPercent(ResourceLocation id) {
        Entry entry = cooldowns.get(id);
        return entry == null || entry.total <= 0 ? 0f : (float) entry.remaining / entry.total;
    }

    public float getPercent(Spell spell) {
        return getPercent(spell.getId());
    }

    public void start(Spell spell, int ticks) {
        if (ticks > 0) {
            cooldowns.put(spell.getId(), new Entry(ticks, ticks));
            dirty = true;
        }
    }

    public void put(ResourceLocation id, int remaining, int total) {
        if (remaining > 0) {
            cooldowns.put(id, new Entry(remaining, Math.max(total, remaining)));
        } else {
            cooldowns.remove(id);
        }
        dirty = true;
    }

    public void clear() {
        cooldowns.clear();
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    /** Returns whether anything changed since the last call, and resets the flag. */
    public boolean consumeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public Map<ResourceLocation, Entry> view() {
        return Collections.unmodifiableMap(cooldowns);
    }

    public boolean isEmpty() {
        return cooldowns.isEmpty();
    }

    /** Returns true if any cooldown expired this tick. */
    public boolean tick() {
        boolean expired = false;
        for (Iterator<Entry> it = cooldowns.values().iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (--entry.remaining <= 0) {
                it.remove();
                expired = true;
            }
        }
        return expired;
    }

    public void copyFrom(CooldownTracker other) {
        cooldowns.clear();
        other.cooldowns.forEach((id, e) -> cooldowns.put(id, new Entry(e.remaining, e.total)));
        dirty = true;
    }

    public ListTag save() {
        ListTag list = new ListTag();
        cooldowns.forEach((id, entry) -> {
            CompoundTag tag = new CompoundTag();
            tag.putString("Spell", id.toString());
            tag.putInt("Remaining", entry.remaining);
            tag.putInt("Total", entry.total);
            list.add(tag);
        });
        return list;
    }

    public void load(ListTag list) {
        cooldowns.clear();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Spell"));
            if (id != null) {
                put(id, tag.getInt("Remaining"), tag.getInt("Total"));
            }
        }
    }
}
