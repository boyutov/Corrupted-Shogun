package com.example.samurai.magic.registry;

import com.example.samurai.SamuraiMod;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MagicAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, SamuraiMod.MOD_ID);

    /** Mana pool size. Syncable so the client can draw the bar without an extra packet. */
    public static final RegistryObject<Attribute> MAX_MANA = ATTRIBUTES.register("max_mana",
            () -> new RangedAttribute("attribute." + SamuraiMod.MOD_ID + ".max_mana", 100.0, 0.0, 1_000_000.0)
                    .setSyncable(true));

    /** Multiplier on the base regeneration rate; 1.0 = normal, 0 = no regen. */
    public static final RegistryObject<Attribute> MANA_REGEN = ATTRIBUTES.register("mana_regen",
            () -> new RangedAttribute("attribute." + SamuraiMod.MOD_ID + ".mana_regen", 1.0, 0.0, 100.0)
                    .setSyncable(true));

    private MagicAttributes() {
    }
}
