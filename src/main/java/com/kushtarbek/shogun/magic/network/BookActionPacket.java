package com.kushtarbek.shogun.magic.network;
import com.kushtarbek.shogun.magic.casting.*;
import com.kushtarbek.shogun.magic.data.MagicData;
import com.kushtarbek.shogun.magic.item.*;
import com.kushtarbek.shogun.magic.menu.BookEquipmentMenu;
import com.kushtarbek.shogun.magic.spell.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Server decides the equipped spell, level, mana and cooldown. No client item data is trusted. */
public record BookActionPacket(Action action) {
    public enum Action { OPEN, START, RELEASE, HEARTBEAT }
    public void encode(FriendlyByteBuf b) { b.writeEnum(action); }
    public static BookActionPacket decode(FriendlyByteBuf b) { return new BookActionPacket(b.readEnum(Action.class)); }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var p = context.get().getSender();
        if (p != null) MagicData.get(p).ifPresent(d -> {
            if (action == Action.RELEASE) {
                if (d.getCastSource() == CastSource.EQUIPPED_BOOK) SpellCasting.endCast(p, CastEndReason.RELEASED);
                return;
            }
            if (!p.isAlive() || p.isSpectator()) return;
            if (action == Action.OPEN) {
                if (p.containerMenu != p.inventoryMenu || SpellCasting.isCommitted(d)) return;
                SpellCasting.endCast(p, CastEndReason.CANCELLED);
                p.openMenu(new SimpleMenuProvider((id, inventory, player) -> new BookEquipmentMenu(id, inventory),
                        Component.translatable("screen.samuraiboss.equipment")));
            } else if (p.containerMenu == p.inventoryMenu) {
                if (action == Action.HEARTBEAT) {
                    if (d.getCastSource() == CastSource.EQUIPPED_BOOK) d.refreshBookHold();
                    return;
                }
                if (p.isUsingItem()) return;
                SpellSlot slot = SpellBookItem.getActive(d.getBookInventory().getItem(0));
                if (slot == null) {
                    p.displayClientMessage(Component.translatable("message.samuraiboss.no_spell"), true);
                    return;
                }
                CastResult result = SpellCasting.tryCast(p, slot.spell(), slot.level(), CastSource.EQUIPPED_BOOK, null);
                if (!result.isSuccess()) p.displayClientMessage(result.getMessage(), true);
            }
        });
        context.get().setPacketHandled(true);
    }
}
