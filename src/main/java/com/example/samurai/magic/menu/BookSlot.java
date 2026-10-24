package com.example.samurai.magic.menu;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.item.SpellBookItem;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Method;

/**
 * The spellbook equipment slot, appended to every player's own inventory menu (index 46) so it
 * sits beside the armour in the vanilla inventory screen. Added on both sides when the player
 * joins a level, before the server's first full menu sync, so slot indices always agree.
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BookSlot extends Slot {
    /** Above the offhand slot, right of the player preview. */
    public static final int X = 77, Y = 44;

    private static final Method ADD_SLOT =
            ObfuscationReflectionHelper.findMethod(AbstractContainerMenu.class, "m_38897_", Slot.class);

    public BookSlot(Container book) {
        super(book, 0, X, Y);
    }

    @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof SpellBookItem; }
    @Override public int getMaxStackSize() { return 1; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void joined(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        // Dimension changes re-add the same server player; its menu already has the slot.
        if (player.inventoryMenu.slots.stream().anyMatch(s -> s instanceof BookSlot)) return;
        MagicData.get(player).ifPresent(data -> {
            try {
                ADD_SLOT.invoke(player.inventoryMenu, new BookSlot(data.getBookInventory()));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Could not add the spellbook slot", e);
            }
        });
    }
}
