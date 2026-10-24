package com.example.samurai.magic.client;

import com.example.samurai.magic.data.CooldownTracker;
import com.example.samurai.magic.network.SyncCooldownsPacket;
import com.example.samurai.magic.registry.MagicAttributes;
import com.example.samurai.magic.registry.MagicSpells;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Client-side mirror of the local player's magic state. Kept static rather than
 * in the client player's capability because the LocalPlayer object is replaced
 * on respawn and dimension change, and sync packets can race that swap.
 * Cast progress and cooldowns are counted down locally between packets.
 */
public final class ClientMagicData {

    private static float mana;

    @Nullable
    private static ResourceLocation castingSpell;
    private static int castingLevel;
    private static int castElapsed;
    private static int castRemaining;

    private static final CooldownTracker COOLDOWNS = new CooldownTracker();

    public static float getMana() {
        return mana;
    }

    public static void setMana(float value) {
        mana = value;
    }

    public static float getMaxMana() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0f : (float) player.getAttributeValue(MagicAttributes.MAX_MANA.get());
    }

    public static void setCast(@Nullable ResourceLocation spell, int level, int elapsed, int remaining) {
        castingSpell = spell;
        castingLevel = level;
        castElapsed = elapsed;
        castRemaining = remaining;
    }

    public static boolean isCasting() {
        return castingSpell != null;
    }

    @Nullable
    public static Spell getCastingSpell() {
        return MagicSpells.byId(castingSpell);
    }

    public static int getCastingLevel() {
        return castingLevel;
    }

    /** 0 → 1 over the cast. Partial tick smooths the bar between client ticks. */
    public static float getCastProgress(float partialTick) {
        int total = castElapsed + castRemaining;
        if (total <= 0) {
            return 0f;
        }
        return Math.min(1f, (castElapsed + partialTick) / total);
    }

    public static int getCastRemaining() {
        return castRemaining;
    }

    public static CooldownTracker getCooldowns() {
        return COOLDOWNS;
    }

    public static void setCooldowns(List<SyncCooldownsPacket.Entry> entries) {
        COOLDOWNS.clear();
        for (SyncCooldownsPacket.Entry e : entries) {
            COOLDOWNS.put(e.spell(), e.remaining(), e.total());
        }
    }

    /** Client tick: the server only sends transitions, so count down here. */
    public static void tick() {
        COOLDOWNS.tick();
        if (castingSpell != null && castRemaining > 0) {
            castElapsed++;
            castRemaining--;
        }
    }

    public static void reset() {
        mana = 0f;
        setCast(null, 0, 0, 0);
        COOLDOWNS.clear();
    }

    private ClientMagicData() {
    }
}
