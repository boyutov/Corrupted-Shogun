package com.kushtarbek.shogun.magic.item;

import com.kushtarbek.shogun.SamuraiMod;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Anvil: two scrolls of the same spell and level make one scroll a level higher.
 *
 * <p>The left slot must hold a single scroll: the anvil empties the whole left slot when
 * the result is taken, so a stack there would lose the rest. The right slot gives one.
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ScrollUpgrade {

    /** Experience levels for going from level n to n + 1. */
    public static int cost(int fromLevel) {
        return 3 * fromLevel;
    }

    @SubscribeEvent
    public static void anvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        SpellSlot a = SpellScrollItem.getSpell(left);
        SpellSlot b = SpellScrollItem.getSpell(right);
        if (a == null || b == null) {
            return;
        }
        // Scrolls never combine any other way: stop the vanilla repair/rename result too.
        if (left.getCount() != 1 || a.spell() != b.spell() || a.level() != b.level()
                || a.level() >= a.spell().getMaxLevel()) {
            event.setCanceled(true);
            return;
        }
        event.setOutput(SpellScrollItem.create(a.spell(), a.level() + 1));
        event.setCost(cost(a.level()));
        event.setMaterialCost(1);
    }

    private ScrollUpgrade() {
    }
}
