package com.example.samurai.magic.data;

import com.example.samurai.magic.casting.CastSource;
import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.network.SyncCastPacket;
import com.example.samurai.magic.network.SyncCooldownsPacket;
import com.example.samurai.magic.network.SyncManaPacket;
import com.example.samurai.magic.registry.MagicAttributes;
import com.example.samurai.magic.registry.MagicSpells;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
    /** Minimum ticks between two regen-driven mana packets. Cast and cooldown changes go out at once. */
    private static final int MANA_SYNC_INTERVAL = 5;
    /** Vanilla players take no fall damage from this height or less. */
    public static final float SAFE_FALL_DISTANCE = 3f;
    /** Ticks after granting during which standing on the ground does not end fall immunity (takeoff). */
    private static final int FALL_IMMUNITY_GRACE = 10;

    private final Player player;
    private float mana;

    // Current cast. castingSpell == null means idle.
    @Nullable
    private ResourceLocation castingSpell;
    private int castingLevel;
    private int castTicksElapsed;
    private int castTicksRemaining;
    private CastSource castSource = CastSource.NONE;
    /** Hand holding the item that started the cast; null for item-less sources. */
    @Nullable
    private InteractionHand castHand;
    /**
     * Per-cast scratch space for the spell (target point, start height...). Reset when a cast
     * starts; deliberately kept through {@link #clearCasting} so the end hooks can still read it.
     */
    private CompoundTag castData = new CompoundTag();

    /** Cancels the next landing's fall damage. Cleared on landing, not by a timer. */
    private boolean fallImmune;
    private int fallImmuneTicks;

    private final CooldownTracker cooldowns = new CooldownTracker();

    private boolean manaDirty = true;
    private boolean castDirty = true;
    private int ticksSinceManaSync;

    public MagicData(Player player) {
        this.player = player;
        // Start full; overwritten by NBT for existing players.
        this.mana = -1f;
    }

    public static LazyOptional<MagicData> get(Player player) {
        return player.getCapability(CAPABILITY);
    }

    public Player getPlayer() {
        return player;
    }

    // ---- mana ----

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
            manaDirty = true;
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

    // ---- cooldowns ----

    public CooldownTracker getCooldowns() {
        return cooldowns;
    }

    // ---- casting ----

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

    public CastSource getCastSource() {
        return castSource;
    }

    @Nullable
    public InteractionHand getCastHand() {
        return castHand;
    }

    /** Spell-owned data for the current (or just-ended) cast. Saved with the player while casting. */
    public CompoundTag getCastData() {
        return castData;
    }

    public void startCasting(Spell spell, int level, int duration, CastSource source, @Nullable InteractionHand hand) {
        castingSpell = spell.getId();
        castingLevel = level;
        castTicksElapsed = 0;
        castTicksRemaining = duration;
        castSource = source;
        castHand = hand;
        castData = new CompoundTag();
        castDirty = true;
    }

    /** Advances the cast by one tick and returns the new elapsed count. Not synced: the client counts too. */
    public int advanceCast() {
        castTicksRemaining--;
        return ++castTicksElapsed;
    }

    public void clearCasting() {
        castingSpell = null;
        castingLevel = 0;
        castTicksElapsed = 0;
        castTicksRemaining = 0;
        castSource = CastSource.NONE;
        castHand = null;
        castDirty = true;
    }

    // ---- fall immunity ----

    public void grantFallImmunity() {
        fallImmune = true;
        fallImmuneTicks = 0;
    }

    public boolean isFallImmune() {
        return fallImmune;
    }

    public void clearFallImmunity() {
        fallImmune = false;
    }

    /**
     * Fall ended some way that fires no fall event. A dangerous fallDistance means the
     * fall event for this landing is still to come (player tick and movement packet
     * order is not fixed), so leave the immunity for that event to use.
     */
    private boolean isSafelyLanded() {
        if (player.fallDistance > SAFE_FALL_DISTANCE) {
            return false;
        }
        return player.onGround() || player.isInWater() || player.isInLava() || player.onClimbable()
                || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying;
    }

    // ---- tick & sync ----

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

        if (fallImmune && ++fallImmuneTicks > FALL_IMMUNITY_GRACE && isSafelyLanded()) {
            // Ended a fall without a fall event (water, ladder, elytra, flight...).
            fallImmune = false;
        }

        ticksSinceManaSync++;
        if (manaDirty && ticksSinceManaSync >= MANA_SYNC_INTERVAL) {
            sendMana();
        }
        // Picks up cast/cooldown changes made outside SpellCasting, e.g. by commands.
        syncStateChanges();
    }

    /** Sends everything that changed, mana included, right now. */
    public void sync() {
        if (manaDirty) {
            sendMana();
        }
        syncStateChanges();
    }

    /** Sends the full state regardless of what changed. Login, respawn, dimension change. */
    public void syncAll() {
        manaDirty = true;
        castDirty = true;
        cooldowns.markDirty();
        sync();
    }

    private void sendMana() {
        if (player instanceof ServerPlayer sp && sp.connection != null) {
            MagicNetwork.sendToPlayer(sp, new SyncManaPacket(mana));
        }
        manaDirty = false;
        ticksSinceManaSync = 0;
    }

    private void syncStateChanges() {
        if (!(player instanceof ServerPlayer sp) || sp.connection == null) {
            return;
        }
        if (castDirty) {
            MagicNetwork.sendToPlayer(sp, SyncCastPacket.of(this));
            castDirty = false;
        }
        if (cooldowns.consumeDirty()) {
            MagicNetwork.sendToPlayer(sp, SyncCooldownsPacket.of(cooldowns));
        }
    }

    // ---- persistence ----

    /** Called from PlayerEvent.Clone (death or End-portal return). */
    public void copyFrom(MagicData other) {
        this.mana = other.mana;
        this.castingSpell = other.castingSpell;
        this.castingLevel = other.castingLevel;
        this.castTicksElapsed = other.castTicksElapsed;
        this.castTicksRemaining = other.castTicksRemaining;
        this.castSource = other.castSource;
        this.castHand = other.castHand;
        this.castData = other.castData.copy();
        this.fallImmune = other.fallImmune;
        this.fallImmuneTicks = other.fallImmuneTicks;
        this.cooldowns.copyFrom(other.cooldowns);
        this.manaDirty = true;
        this.castDirty = true;
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
            cast.putString("Source", castSource.name());
            if (castHand != null) {
                cast.putString("Hand", castHand.name());
            }
            cast.put("Data", castData.copy());
            tag.put("Cast", cast);
        }
        tag.put("Cooldowns", cooldowns.save());
        if (fallImmune) {
            tag.putBoolean("FallImmune", true);
        }
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
            castSource = CastSource.byName(cast.getString("Source"));
            castHand = switch (cast.getString("Hand")) {
                case "MAIN_HAND" -> InteractionHand.MAIN_HAND;
                case "OFF_HAND" -> InteractionHand.OFF_HAND;
                default -> null;
            };
            castData = cast.getCompound("Data");
        }
        cooldowns.load(tag.getList("Cooldowns", Tag.TAG_COMPOUND));
        fallImmune = tag.getBoolean("FallImmune");
        fallImmuneTicks = 0;
        manaDirty = true;
        castDirty = true;
    }
}
