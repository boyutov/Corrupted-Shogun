package com.example.samurai.magic.registry;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.SpellScrollItem;
import com.example.samurai.magic.item.SpellSlot;
import com.example.samurai.magic.spell.Spell;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public final class MagicItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SamuraiMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SamuraiMod.MOD_ID);

    public static final RegistryObject<Item> SPELL_BOOK = ITEMS.register("spell_book",
            () -> new SpellBookItem(5, new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> SPELL_SCROLL = ITEMS.register("spell_scroll",
            () -> new SpellScrollItem(new Item.Properties().stacksTo(16)));

    public static final RegistryObject<CreativeModeTab> MAGIC_TAB = TABS.register("magic",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + SamuraiMod.MOD_ID + ".magic"))
                    .icon(() -> new ItemStack(SPELL_BOOK.get()))
                    .displayItems((params, output) -> {
                        output.accept(SPELL_BOOK.get());
                        output.accept(createTestBook());
                        for (Spell spell : castableSpells()) {
                            output.accept(SpellScrollItem.create(spell, spell.getMinLevel()));
                        }
                    })
                    .build());

    /** A book pre-filled with every registered spell, for testing. */
    public static ItemStack createTestBook() {
        ItemStack stack = new ItemStack(SPELL_BOOK.get());
        for (Spell spell : castableSpells()) {
            SpellBookItem.addSpell(stack, new SpellSlot(spell, spell.getMinLevel()));
        }
        return stack;
    }

    private static List<Spell> castableSpells() {
        return MagicSpells.registry().getValues().stream()
                .filter(spell -> MagicSpells.byId(spell.getId()) != null)
                .toList();
    }

    private MagicItems() {
    }
}
