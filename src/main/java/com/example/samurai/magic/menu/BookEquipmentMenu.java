package com.example.samurai.magic.menu;

import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.registry.MagicMenus;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Real server-owned equipment slot. Vanilla menu transactions handle cursor stacks and shift-clicks. */
public class BookEquipmentMenu extends AbstractContainerMenu {
    public BookEquipmentMenu(int id, Inventory inventory) {
        this(id, inventory, inventory.player.level().isClientSide ? new SimpleContainer(1)
                : MagicData.get(inventory.player).orElseThrow(IllegalStateException::new).getBookInventory());
    }
    private BookEquipmentMenu(int id, Inventory inventory, SimpleContainer book) {
        super(MagicMenus.BOOK_EQUIPMENT.get(), id);
        addSlot(new Slot(book, 0, 80, 28) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof SpellBookItem; }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 76 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 134));
    }
    @Override public boolean stillValid(Player player) { return player.isAlive() && !player.isSpectator(); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof SpellBookItem && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (index < 28) {
            if (!moveItemStackTo(stack, 28, 37, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 1, 28, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }
}
