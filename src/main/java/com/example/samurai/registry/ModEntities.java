package com.example.samurai.registry;

import com.example.samurai.SamuraiMod;
import com.example.samurai.entity.SamuraiBossEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SamuraiMod.MOD_ID);

    public static final RegistryObject<EntityType<SamuraiBossEntity>> SAMURAI_SHOGUN =
            ENTITIES.register("samurai_shogun", () -> EntityType.Builder
                    .of(SamuraiBossEntity::new, MobCategory.MONSTER)
                    // 3 blocks tall, 1.6 wide — matches the 48-unit geometry.
                    .sized(1.6F, 3.0F)
                    .clientTrackingRange(16)
                    .updateInterval(2)
                    .fireImmune()
                    .build(SamuraiMod.MOD_ID + ":samurai_shogun"));

    private ModEntities() {
    }
}
