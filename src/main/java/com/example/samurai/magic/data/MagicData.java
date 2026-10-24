package com.example.samurai.magic.data;

import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.network.SyncManaPacket;
import com.example.samurai.magic.registry.MagicAttributes;
import com.example.samurai.magic.registry.MagicSpells;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * Per-player magic state: mana, the spell currently being cast, cooldowns.
 * Max mana is not stored — it is read from the {@link MagicAttributes#MAX_MANA}
 * attribute every time. Cast transitions are driven by
 * {@link com.example.samurai.magic.casting.SpellCasting}; this class only holds state.
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

    // Current cast. castingSpell == null means idle.
    @Nullable
    private ResourceLocation castingSpell;
    private int castingLevel;
    private int castTicksElapsed;
    private int castTicksRemaining;

    private final CooldownTracker cooldowns = new CooldownTracker();

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

    public Player getPlayer() {
        return player;
    }

    public CooldownTracker getCooldowns() {
        return cooldowns;
    }

    public boolean isCasting() {
        return castingSpell != null;
    }

    /** Null when idle, or when the saved spell no longer exists. */
    @Nullable
    public Spell getCastingSpell() {
        return MagicSpells.byId(castingSpell);
    }

    @Nullable
    public ResourceLocation getCastingSpellId() {
        return castingSpell;
    }

    public int getCastingLevel() {
        return castingLevel;
    }

    public int getCastTicksElapsed() {
        return castTicksElapsed;
    }

    public int getCastTicksRemaining() {
        return castTicksRemaining;
    }

    public void startCasting(Spell spell, int level, int duration) {
        castingSpell = spell.getId();
        castingLevel = level;
        castTicksElapsed = 0;
        castTicksRemaining = duration;
    }

    /** Advances the cast by one tick and returns the new elapsed count. */
    public int advanceCast() {
        castTicksRemaining--;
        return ++castTicksElapsed;
    }

    public void clearCasting() {
        castingSpell = null;
        castingLevel = 0;
        castTicksElapsed = 0;
        castTicksRemaining = 0;
    }

    /** Server-side, once per player tick. Mana regen only; casting ticks in SpellCasting. */
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
        this.castingSpell = other.castingSpell;
        this.castingLevel = other.castingLevel;
        this.castTicksElapsed = other.castTicksElapsed;
        this.castTicksRemaining = other.castTicksRemaining;
        this.cooldowns.copyFrom(other.cooldowns);
        this.dirty = true;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Mana", mana);
        if (castingSpell != null) {
            CompoundTag cast = new CompoundTag();
            cast.putString("Spell", castingSpell.toString());
            cast.putInt("Level", castingLevel);
            cast.putInt("Elapsed", castTicksElapsed);
            cast.putInt("Remaining", castTicksRemaining);
            tag.put("Cast", cast);
        }
        tag.put("Cooldowns", cooldowns.save());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        // No clamping here: attributes may not be loaded yet. tick() clamps.
        mana = tag.contains("Mana") ? tag.getFloat("Mana") : -1f;
        clearCasting();
        if (tag.contains("Cast", Tag.TAG_COMPOUND)) {
            CompoundTag cast = tag.getCompound("Cast");
            castingSpell = ResourceLocation.tryParse(cast.getString("Spell"));
            castingLevel = cast.getInt("Level");
            castTicksElapsed = cast.getInt("Elapsed");
            castTicksRemaining = cast.getInt("Remaining");
        }
        cooldowns.load(tag.getList("Cooldowns", Tag.TAG_COMPOUND));
        dirty = true;
    }
}
