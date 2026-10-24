package com.kushtarbek.shogun;

import com.kushtarbek.shogun.entity.SamuraiBossEntity;
import com.kushtarbek.shogun.item.SamuraiBladeItem;
import com.kushtarbek.shogun.magic.MagicSetup;
import com.kushtarbek.shogun.registry.ModEntities;
import com.kushtarbek.shogun.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import software.bernie.geckolib.GeckoLib;

@Mod(SamuraiMod.MOD_ID)
public class SamuraiMod {

    /** Must match the asset folder name and mod_id in gradle.properties. */
    public static final String MOD_ID = "samuraiboss";

    public SamuraiMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModEntities.ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        MagicSetup.init(modBus);
        modBus.addListener(this::registerAttributes);
        modBus.addListener(this::buildCreativeModeTab);

        SamuraiBladeItem.register();
        GeckoLib.initialize();
    }

    private void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SAMURAI_SHOGUN.get(), SamuraiBossEntity.createAttributes().build());
    }

    private void buildCreativeModeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.SAMURAI_BLADE);
        } else if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ModItems.SAMURAI_SHOGUN_SPAWN_EGG);
        }
    }
}
