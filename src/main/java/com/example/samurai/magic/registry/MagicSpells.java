package com.example.samurai.magic.registry;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.spell.CastType;
import com.example.samurai.magic.spell.EmptySpell;
import com.example.samurai.magic.spell.MeteorSpell;
import com.example.samurai.magic.spell.Spell;
import com.example.samurai.magic.spell.TestStubSpell;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryBuilder;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public final class MagicSpells {

    public static final ResourceLocation REGISTRY_NAME = new ResourceLocation(SamuraiMod.MOD_ID, "spells");
    public static final ResourceLocation NONE_ID = new ResourceLocation(SamuraiMod.MOD_ID, "none");

    public static final DeferredRegister<Spell> SPELLS = DeferredRegister.create(REGISTRY_NAME, SamuraiMod.MOD_ID);

    // Spells are referenced by ResourceLocation in NBT, so numeric ids need not be saved.
    private static final Supplier<IForgeRegistry<Spell>> REGISTRY = SPELLS.makeRegistry(
            () -> new RegistryBuilder<Spell>().setDefaultKey(NONE_ID).disableSaving());

    public static final RegistryObject<Spell> NONE = SPELLS.register("none", EmptySpell::new);

    // Pipeline probes, not real spells. See TestStubSpell.
    public static final RegistryObject<Spell> TEST_STUB_INSTANT = SPELLS.register("test_stub_instant",
            () -> new TestStubSpell(new Spell.Properties()
                    .castType(CastType.INSTANT)
                    .levels(1, 3)
                    .manaCost(10f, 5f)
                    .cooldown(60)));

    public static final RegistryObject<Spell> TEST_STUB_LONG = SPELLS.register("test_stub_long",
            () -> new TestStubSpell(new Spell.Properties()
                    .castType(CastType.LONG)
                    .levels(1, 3)
                    .manaCost(25f, 5f)
                    .castTime(40)
                    .cooldown(100)
                    // non-zero only so the damage-interrupt path has something to exercise
                    .interruptDamageThreshold(5f)));

    // 5 s channel, 4 mana per second, threshold left at 0 (never interrupted by damage).
    public static final RegistryObject<Spell> TEST_STUB_CONTINUOUS = SPELLS.register("test_stub_continuous",
            () -> new TestStubSpell(new Spell.Properties()
                    .castType(CastType.CONTINUOUS)
                    .levels(1, 3)
                    .manaCost(4f, 2f)
                    .castTime(100)
                    .cooldown(80)));

    public static final RegistryObject<Spell> METEOR = SPELLS.register("meteor", MeteorSpell::new);

    public static IForgeRegistry<Spell> registry() {
        return REGISTRY.get();
    }

    /** Null for unknown ids and for the empty spell. */
    @Nullable
    public static Spell byId(@Nullable ResourceLocation id) {
        if (id == null || id.equals(NONE_ID)) {
            return null;
        }
        return registry().containsKey(id) ? registry().getValue(id) : null;
    }

    private MagicSpells() {
    }
}
