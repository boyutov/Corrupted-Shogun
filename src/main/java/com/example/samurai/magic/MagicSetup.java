package com.example.samurai.magic;

import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.registry.MagicAttributes;
import com.example.samurai.magic.registry.MagicItems;
import com.example.samurai.magic.registry.MagicSpells;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Entry point of the magic framework. Everything mod-bus related for magic is
 * wired here so the main mod class only needs a single call.
 */
public final class MagicSetup {

    public static void init(IEventBus modBus) {
        MagicAttributes.ATTRIBUTES.register(modBus);
        MagicSpells.SPELLS.register(modBus);
        MagicItems.ITEMS.register(modBus);
        MagicItems.TABS.register(modBus);

        modBus.addListener(MagicSetup::registerCapabilities);
        modBus.addListener(MagicSetup::addPlayerAttributes);
        modBus.addListener(MagicSetup::commonSetup);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(MagicData.class);
    }

    private static void addPlayerAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, MagicAttributes.MAX_MANA.get());
        event.add(EntityType.PLAYER, MagicAttributes.MANA_REGEN.get());
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(MagicNetwork::register);
    }

    private MagicSetup() {
    }
}
