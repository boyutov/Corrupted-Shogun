package com.example.samurai.magic.registry;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.entity.MeteorEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class MagicEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SamuraiMod.MOD_ID);

    // Not saved: a meteor that outlives its chunk would land in a world state it was never cast into.
    public static final RegistryObject<EntityType<MeteorEntity>> METEOR = ENTITIES.register("meteor",
            () -> EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                    .sized(MeteorEntity.SIZE, MeteorEntity.SIZE)
                    .fireImmune()
                    .noSave()
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build(SamuraiMod.MOD_ID + ":meteor"));

    private MagicEntities() {
    }
}
