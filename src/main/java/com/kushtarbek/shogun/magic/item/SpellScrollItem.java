package com.kushtarbek.shogun.magic.item;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.casting.CastSource;
import com.kushtarbek.shogun.magic.registry.MagicItems;
import com.kushtarbek.shogun.magic.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Single-use spell. Consumed only when the cast succeeds; a cancelled LONG cast
 * or a refused cast leaves the scroll intact. NBT: {@code Scroll: {Spell: id, Level: int}}.
 */
public class SpellScrollItem extends Item {

    private static final String TAG_ROOT = "Scroll";

    public SpellScrollItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Spell spell, int level) {
        ItemStack stack = new ItemStack(MagicItems.SPELL_SCROLL.get());
        stack.getOrCreateTag().put(TAG_ROOT, new SpellSlot(spell, spell.clampLevel(level)).save());
        return stack;
    }

    @Nullable
    public static SpellSlot getSpell(ItemStack stack) {
        if (!(stack.getItem() instanceof SpellScrollItem)) {
            return null;
        }
        CompoundTag tag = stack.getTagElement(TAG_ROOT);
        return tag == null ? null : SpellSlot.load(tag);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return SpellItemUse.use(level, player, hand, getSpell(player.getItemInHand(hand)), CastSource.SCROLL);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return SpellItemUse.USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        SpellItemUse.stopUsing(entity, CastSource.SCROLL);
    }

    @Override
    public Component getName(ItemStack stack) {
        SpellSlot slot = getSpell(stack);
        if (slot == null) {
            return super.getName(stack);
        }
        return Component.translatable("item." + SamuraiMod.MOD_ID + ".spell_scroll.named", slot.getDisplayName());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SpellSlot slot = getSpell(stack);
        if (slot != null) {
            SpellItemUse.appendSpellInfo(tooltip, slot, false);
            if (slot.level() < slot.spell().getMaxLevel()) {
                tooltip.add(Component.translatable("tooltip." + SamuraiMod.MOD_ID + ".scroll_upgrade")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
