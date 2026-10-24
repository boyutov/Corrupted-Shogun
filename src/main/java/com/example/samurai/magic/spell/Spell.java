package com.example.samurai.magic.spell;

import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.registry.MagicSpells;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;

/**
 * Base of every spell. Instances are registry singletons — all per-cast state
 * (level, elapsed time) lives in {@link MagicData} and is passed into the hooks.
 *
 * <p>Hook order by cast type:
 * <ul>
 *   <li>INSTANT: {@code onCastStart} → {@code onCast}</li>
 *   <li>LONG: {@code onCastStart} → {@code onCastTick}×N → {@code onCast} when the cast time runs out;
 *       ending early (release, damage, death) calls {@code onCastInterrupted} instead and costs nothing</li>
 *   <li>CONTINUOUS: {@code onCastStart} → {@code onCastTick}×N → {@code onCast} when released,
 *       out of time or out of mana; damage/death calls {@code onCastInterrupted} instead.
 *       Mana is paid at the end of each full {@link #getManaInterval()}; a trailing partial
 *       interval is free</li>
 * </ul>
 * Cooldown starts only after {@code onCast}.
 */
public abstract class Spell {

    private final SpellSchool school;
    private final CastType castType;
    private final int minLevel;
    private final int maxLevel;
    private final float baseManaCost;
    private final float manaCostPerLevel;
    private final int cooldown;
    private final int castTime;
    private final float interruptDamageThreshold;

    @Nullable
    private ResourceLocation id;
    @Nullable
    private String descriptionId;

    protected Spell(Properties properties) {
        this.school = properties.school;
        this.castType = properties.castType;
        this.minLevel = properties.minLevel;
        this.maxLevel = properties.maxLevel;
        this.baseManaCost = properties.baseManaCost;
        this.manaCostPerLevel = properties.manaCostPerLevel;
        this.cooldown = properties.cooldown;
        this.castTime = properties.castTime;
        this.interruptDamageThreshold = properties.interruptDamageThreshold;
    }

    public ResourceLocation getId() {
        if (id == null) {
            id = MagicSpells.registry().getKey(this);
        }
        return id;
    }

    public String getDescriptionId() {
        if (descriptionId == null) {
            descriptionId = Util.makeDescriptionId("spell", getId());
        }
        return descriptionId;
    }

    public Component getDisplayName() {
        return Component.translatable(getDescriptionId());
    }

    public SpellSchool getSchool() {
        return school;
    }

    public CastType getCastType() {
        return castType;
    }

    public int getMinLevel() {
        return minLevel;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public boolean isValidLevel(int level) {
        return level >= minLevel && level <= maxLevel;
    }

    public int clampLevel(int level) {
        return Mth.clamp(level, minLevel, maxLevel);
    }

    /** INSTANT: paid on cast. LONG: paid when charging completes. CONTINUOUS: paid at the end of every interval. */
    public float getManaCost(int level) {
        return baseManaCost + manaCostPerLevel * (level - minLevel);
    }

    /** Ticks. */
    public int getCooldown(int level) {
        return cooldown;
    }

    /** Ticks. LONG: charge time. CONTINUOUS: max channel time. Unused for INSTANT. */
    public int getCastTime(int level) {
        return castTime;
    }

    /** CONTINUOUS only: ticks between mana payments. Nothing is paid at start. */
    public int getManaInterval() {
        return 20;
    }

    /**
     * Minimum damage of a single hit that interrupts this spell while it is being
     * cast. 0 = never interrupted by damage.
     */
    public float getInterruptDamageThreshold() {
        return interruptDamageThreshold;
    }

    public boolean isInterruptedBy(float damage) {
        return interruptDamageThreshold > 0f && damage >= interruptDamageThreshold;
    }

    /** Spell-specific precondition (target in range, etc). Runs after the generic mana/cooldown checks. */
    public CastResult checkPreCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
        return CastResult.SUCCESS;
    }

    public void onCastStart(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data) {
    }

    /** @param ticksElapsed starts at 1 on the first tick after {@link #onCastStart} */
    public void onCastTick(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data, int ticksElapsed) {
    }

    /** The spell's actual effect. */
    public abstract void onCast(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data);

    public void onCastInterrupted(ServerLevel level, ServerPlayer caster, int spellLevel, MagicData data,
                                  CastEndReason reason) {
    }

    @Override
    public String toString() {
        return "Spell[" + getId() + "]";
    }

    public static class Properties {
        private SpellSchool school = SpellSchool.ARCANE;
        private CastType castType = CastType.INSTANT;
        private int minLevel = 1;
        private int maxLevel = 1;
        private float baseManaCost;
        private float manaCostPerLevel;
        private int cooldown;
        private int castTime;
        private float interruptDamageThreshold;

        public Properties school(SpellSchool school) {
            this.school = school;
            return this;
        }

        public Properties castType(CastType castType) {
            this.castType = castType;
            return this;
        }

        public Properties levels(int min, int max) {
            this.minLevel = min;
            this.maxLevel = max;
            return this;
        }

        public Properties manaCost(float base, float perLevel) {
            this.baseManaCost = base;
            this.manaCostPerLevel = perLevel;
            return this;
        }

        public Properties cooldown(int ticks) {
            this.cooldown = ticks;
            return this;
        }

        public Properties castTime(int ticks) {
            this.castTime = ticks;
            return this;
        }

        public Properties interruptDamageThreshold(float damage) {
            this.interruptDamageThreshold = damage;
            return this;
        }
    }
}
