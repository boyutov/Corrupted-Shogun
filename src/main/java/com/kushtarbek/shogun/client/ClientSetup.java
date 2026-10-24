package com.kushtarbek.shogun.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SAMURAI_SHOGUN.get(), SamuraiBossRenderer::new);
    }

    private ClientSetup() {
    }
}
