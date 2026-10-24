package com.example.samurai.magic.data;

import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.network.SyncManaPacket;
import com.example.samurai.magic.registry.MagicAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Per-player magic state. Stage 1 holds only mana; casting state and cooldowns
 * will live here too. Max mana is not stored — it is read from the
 * {@link MagicAttributes#MAX_MANA} attribute every time.
 */
public class MagicData implements INBTSerializable<CompoundTag> {

    public static final Capability<MagicData> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {
    });

    /** Fraction of max mana restored per second at mana_regen = 1.0. */
    public static final float BASE_REGEN_FRACTION_PER_SECOND = 0.01f;
    /** Minimum ticks between two regen-driven sync packets. */
    private static final int SYNC_INTERVAL = 5;

    private final Player player;
    private float mana;
    private boolean dirty = true;
    private int ticksSinceSync;

    public MagicData(Player player) {
        this.player = player;
        // Start full; overwritten by NBT for existing players.
        this.mana = -1f;
    }

    public static LazyOptional<MagicData> get(Player player) {
        return player.getCapability(CAPABILITY);
    }

    public float getMaxMana() {
        return (float) player.getAttributeValue(MagicAttributes.MAX_MANA.get());
    }

    public float getMana() {
        return mana;
    }

    public void setMana(float value) {
        float clamped = Mth.clamp(value, 0f, getMaxMana());
        if (clamped != mana) {
            mana = clamped;
            dirty = true;
        }
    }

    public void addMana(float amount) {
        setMana(mana + amount);
    }

    public boolean hasMana(float amount) {
        return mana >= amount;
    }

    /** Removes mana if there is enough of it. Returns false and changes nothing otherwise. */
    public boolean consumeMana(float amount) {
        if (!hasMana(amount)) {
            return false;
        }
        setMana(mana - amount);
        return true;
    }

    /** Server-side, once per player tick. */
    public void tick() {
        float max = getMaxMana();
        if (mana < 0f || mana > max) {
            // Fresh player starts full; otherwise max mana dropped below current.
            setMana(max);
        } else if (mana < max) {
            float regen = (float) player.getAttributeValue(MagicAttributes.MANA_REGEN.get());
            addMana(max * BASE_REGEN_FRACTION_PER_SECOND * regen / 20f);
        }

        ticksSinceSync++;
        if (dirty && ticksSinceSync >= SYNC_INTERVAL) {
            sync();
        }
    }

    /** Sends current state to the owning client immediately. */
    public void sync() {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            MagicNetwork.sendToPlayer(serverPlayer, new SyncManaPacket(mana));
        }
        dirty = false;
        ticksSinceSync = 0;
    }

    /** Called from PlayerEvent.Clone (death or End-portal return). */
    public void copyFrom(MagicData other) {
        this.mana = other.mana;
        this.dirty = true;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Mana", mana);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        // No clamping here: attributes may not be loaded yet. tick() clamps.
        mana = tag.contains("Mana") ? tag.getFloat("Mana") : -1f;
        dirty = true;
    }
}
