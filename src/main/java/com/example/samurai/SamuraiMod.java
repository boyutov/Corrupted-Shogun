package com.example.samurai;

import com.example.samurai.entity.SamuraiBossEntity;
import com.example.samurai.item.SamuraiBladeItem;
import com.example.samurai.magic.MagicSetup;
import com.example.samurai.registry.ModEntities;
import com.example.samurai.registry.ModItems;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import software.bernie.geckolib.GeckoLib;

@Mod(SamuraiMod.MOD_ID)
public class SamuraiMod {

    /** Replace with your own mod id — it must match the asset folder name. */
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
