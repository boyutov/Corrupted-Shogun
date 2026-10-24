package com.example.samurai.magic.item;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.casting.CastSource;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A book with a fixed number of spell slots and one active slot. Spells are added the
 * way items go into a bundle: right-click the book with a scroll on the cursor (or the
 * scroll with the book on the cursor). The scroll is used up.
 *
 * NBT: {@code SpellBook: {Active: int, Slots: [{Slot: int, Spell: id, Level: int}, ...]}} — sparse.
 */
public class SpellBookItem extends Item {

    private static final String TAG_ROOT = "SpellBook";
    private static final String TAG_SLOTS = "Slots";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_ACTIVE = "Active";

    private final int slotCount;

    public SpellBookItem(int slotCount, Properties properties) {
        super(properties.stacksTo(1));
        this.slotCount = slotCount;
    }

    public int getSlotCount() {
        return slotCount;
    }

    public static int getSlotCount(ItemStack stack) {
        return stack.getItem() instanceof SpellBookItem book ? book.slotCount : 0;
    }

    /** Fixed-size list; empty slots are null. */
    public static List<SpellSlot> getSlots(ItemStack stack) {
        int count = getSlotCount(stack);
        List<SpellSlot> slots = new ArrayList<>(Collections.nCopies(count, null));
        CompoundTag root = stack.getTagElement(TAG_ROOT);
        if (root != null) {
            ListTag list = root.getList(TAG_SLOTS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                int index = tag.getInt(TAG_SLOT);
                if (index >= 0 && index < count) {
                    slots.set(index, SpellSlot.load(tag));
                }
            }
        }
        return slots;
    }

    public static void setSlots(ItemStack stack, List<SpellSlot> slots) {
        CompoundTag root = stack.getOrCreateTagElement(TAG_ROOT);
        ListTag list = new ListTag();
        for (int i = 0; i < slots.size(); i++) {
            SpellSlot slot = slots.get(i);
            if (slot != null) {
                CompoundTag tag = slot.save();
                tag.putInt(TAG_SLOT, i);
                list.add(tag);
            }
        }
        root.put(TAG_SLOTS, list);
    }

    public static void setSlot(ItemStack stack, int index, @Nullable SpellSlot slot) {
        List<SpellSlot> slots = getSlots(stack);
        if (index >= 0 && index < slots.size()) {
            slots.set(index, slot);
            setSlots(stack, slots);
        }
    }

    /** Puts the spell into the first empty slot. Returns that slot, or -1 if the book is full. */
    public static int addSpell(ItemStack stack, SpellSlot slot) {
        List<SpellSlot> slots = getSlots(stack);
        int index = slots.indexOf(null);
        if (index >= 0) {
            slots.set(index, slot);
            setSlots(stack, slots);
        }
        return index;
    }

    public static int getActiveIndex(ItemStack stack) {
        CompoundTag root = stack.getTagElement(TAG_ROOT);
        return root == null ? 0 : root.getInt(TAG_ACTIVE);
    }

    /** Only non-empty slots can be made active. */
    public static boolean setActiveIndex(ItemStack stack, int index) {
        List<SpellSlot> slots = getSlots(stack);
        if (index < 0 || index >= slots.size() || slots.get(index) == null) {
            return false;
        }
        stack.getOrCreateTagElement(TAG_ROOT).putInt(TAG_ACTIVE, index);
        return true;
    }

    /** Index of the slot that will be cast: the active one, or the first filled slot if that is empty. -1 if none. */
    public static int getEffectiveActiveIndex(ItemStack stack) {
        List<SpellSlot> slots = getSlots(stack);
        int active = getActiveIndex(stack);
        if (active >= 0 && active < slots.size() && slots.get(active) != null) {
            return active;
        }
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) != null) {
                return i;
            }
        }
        return -1;
    }

    @Nullable
    public static SpellSlot getActive(ItemStack stack) {
        int index = getEffectiveActiveIndex(stack);
        return index < 0 ? null : getSlots(stack).get(index);
    }

    /** Result of writing a scroll into a book. */
    public enum Inscribe {
        ADDED, UPGRADED, ALREADY_KNOWN, FULL;

        public boolean consumesScroll() {
            return this == ADDED || this == UPGRADED;
        }
    }

    /**
     * Writes the scroll's spell into the book: into the first empty slot, or over the same
     * spell if the scroll's level is higher. Does not touch the scroll.
     */
    public static Inscribe inscribe(ItemStack book, SpellSlot spell) {
        List<SpellSlot> slots = getSlots(book);
        for (int i = 0; i < slots.size(); i++) {
            SpellSlot existing = slots.get(i);
            if (existing != null && existing.spell() == spell.spell()) {
                if (existing.level() >= spell.level()) {
                    return Inscribe.ALREADY_KNOWN;
                }
                slots.set(i, spell);
                setSlots(book, slots);
                return Inscribe.UPGRADED;
            }
        }
        return addSpell(book, spell) >= 0 ? Inscribe.ADDED : Inscribe.FULL;
    }

    /** Scroll on the cursor, right-clicked onto this book in a slot. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack book, ItemStack carried, Slot slot, ClickAction action,
                                            Player player, SlotAccess carriedAccess) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        return tryInscribe(book, carried, player);
    }

    /** This book on the cursor, right-clicked onto a scroll in a slot. */
    @Override
    public boolean overrideStackedOnOther(ItemStack book, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        return tryInscribe(book, slot.getItem(), player);
    }

    /** Runs on both sides like the bundle does, so the client shows the result at once. */
    private static boolean tryInscribe(ItemStack book, ItemStack scroll, Player player) {
        SpellSlot spell = SpellScrollItem.getSpell(scroll);
        if (spell == null) {
            return false;
        }
        Inscribe result = inscribe(book, spell);
        if (result.consumesScroll()) {
            scroll.shrink(1);
            player.playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
            player.playSound(SoundEvents.ENCHANTMENT_TABLE_USE, 0.6f, 1.2f);
        } else {
            player.playSound(SoundEvents.VILLAGER_NO, 0.6f, 1f);
        }
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable("message." + SamuraiMod.MOD_ID + ".inscribe."
                    + result.name().toLowerCase(Locale.ROOT), spell.getDisplayName()), true);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return SpellItemUse.use(level, player, hand, getActive(player.getItemInHand(hand)), CastSource.SPELLBOOK);
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
        SpellItemUse.stopUsing(entity, CastSource.SPELLBOOK);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String key = "tooltip." + SamuraiMod.MOD_ID + ".";
        List<SpellSlot> slots = getSlots(stack);
        long filled = slots.stream().filter(s -> s != null).count();
        tooltip.add(Component.translatable(key + "book_slots", filled, slots.size()).withStyle(ChatFormatting.GRAY));

        int active = getEffectiveActiveIndex(stack);
        for (int i = 0; i < slots.size(); i++) {
            SpellSlot slot = slots.get(i);
            if (slot != null) {
                tooltip.add(Component.literal(i == active ? "▶ " : "   ").append(slot.getDisplayName()));
            }
        }
        if (active >= 0) {
            SpellItemUse.appendSpellInfo(tooltip, slots.get(active), true);
        }
    }
}
