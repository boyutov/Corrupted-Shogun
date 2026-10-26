package com.kushtarbek.shogun.magic.item;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.client.GuideScreen;
import com.kushtarbek.shogun.magic.registry.MagicItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/**
 * "Notes on the Shogun's Magic": opens a book screen whose pages are lang keys
 * (guide.samuraiboss.page.1 ..), so the text follows the player's language.
 * Every player gets one the first time they join a world.
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GuideItem extends Item {

    public static final int PAGES = 13;
    private static final String TAG_GIVEN = SamuraiMod.MOD_ID + ".guide_given";

    public GuideItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> GuideScreen::open);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @SubscribeEvent
    public static void firstJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        // The persisted tag survives death, so the guide comes once per player per world.
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(TAG_GIVEN)) {
            return;
        }
        persisted.putBoolean(TAG_GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        ItemStack guide = new ItemStack(MagicItems.SHOGUN_GUIDE.get());
        if (!player.getInventory().add(guide)) {
            player.drop(guide, false);
        }
    }
}
