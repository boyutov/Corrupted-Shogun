package com.example.samurai.registry;

import com.example.samurai.SamuraiMod;
import com.example.samurai.item.SamuraiBladeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SamuraiMod.MOD_ID);

    public static final RegistryObject<Item> SAMURAI_BLADE =
            ITEMS.register("samurai_blade", () -> new SamuraiBladeItem(new Item.Properties()));

    public static final RegistryObject<Item> SAMURAI_SHOGUN_SPAWN_EGG =
            ITEMS.register("samurai_shogun_spawn_egg", () -> new ForgeSpawnEggItem(
                    ModEntities.SAMURAI_SHOGUN, 0x1C1A22, 0x944DDB, new Item.Properties()));

    private ModItems() {
    }
}
