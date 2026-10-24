package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.item.BookLocation;
import com.kushtarbek.shogun.magic.item.SpellBookItem;
import com.kushtarbek.shogun.magic.item.SpellSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Compact, always-visible book slots. Cooldowns belong to spells, never to the whole book. */
public final class SpellRingOverlay implements IGuiOverlay {
    public static final SpellRingOverlay INSTANCE = new SpellRingOverlay();
    private static final ResourceLocation METEOR = new ResourceLocation(SamuraiMod.MOD_ID, "textures/gui/spells/meteor.png");
    private static final ResourceLocation BLACK_HOLE = new ResourceLocation(SamuraiMod.MOD_ID, "textures/gui/spells/black_hole.png");

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
        var mc = gui.getMinecraft();
        if (mc.player == null || mc.player.isSpectator() || mc.options.hideGui) return;
        BookLocation location = BookLocation.preferred(mc.player);
        if (location == null) return;
        var book = location.get(mc.player);
        var slots = SpellBookItem.getSlots(book);
        if (slots.isEmpty()) return;
        int active = SpellBookItem.getEffectiveActiveIndex(book);
        // Keep clear of the hotbar even on Minecraft's minimum logical width.
        int cx = 55, cy = height - 94;
        g.flush();
        circle(g, cx, cy, 0, 48, 0, Mth.TWO_PI, 0x0B0B1A, 160);
        circle(g, cx, cy, 29, 30, 0, Mth.TWO_PI, 0x63527B, 180);
        for (int i = 0; i < slots.size(); i++) {
            double angle = -Math.PI / 2 + Math.PI * 2 * i / slots.size();
            int x = cx + (int) (Math.cos(angle) * 31), y = cy + (int) (Math.sin(angle) * 31);
            SpellSlot slot = slots.get(i);
            int color = slot == null ? 0x40384B : slot.spell().getSchool().getColor();
            circle(g,x,y,0,12,0,Mth.TWO_PI,0x171221,245);
            circle(g,x,y,11,13,0,Mth.TWO_PI,i == active ? 0xFCE296 : color,slot == null ? 100 : 245);
            if (slot != null) {
                float cooldown = ClientMagicData.getCooldowns().getPercent(slot.spell());
                if (cooldown > 0) circle(g,x,y,13,15,-Math.PI/2,-Math.PI/2 + Mth.TWO_PI*cooldown,0xEE778D,255);
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            SpellSlot slot = slots.get(i);
            if (slot == null) continue;
            double angle = -Math.PI / 2 + Math.PI * 2 * i / slots.size();
            int x = cx + (int)(Math.cos(angle)*31), y = cy + (int)(Math.sin(angle)*31);
            icon(g,slot,x-8,y-8,16);
            int ticks = ClientMagicData.getCooldowns().getRemaining(slot.spell());
            if (ticks > 0) g.drawCenteredString(mc.font,Integer.toString((ticks+19)/20),x,y+5,0xFFABB8);
        }
        if (active >= 0) {
            SpellSlot slot = slots.get(active);
            icon(g,slot,cx-10,cy-10,20);
            String name = mc.font.plainSubstrByWidth(slot.spell().getDisplayName().getString(),100);
            g.drawCenteredString(mc.font,name,cx,cy+50,0xFCE296);
        }
    }

    private static void icon(GuiGraphics g, SpellSlot slot, int x, int y, int size) {
        ResourceLocation texture = switch (slot.spell().getId().getPath()) {
            case "meteor" -> METEOR;
            case "black_hole" -> BLACK_HOLE;
            default -> null;
        };
        if (texture != null) g.blit(texture,x,y,size,size,0,0,32,32,32,32);
        else g.fill(x+3,y+3,x+size-3,y+size-3,0xFF000000 | slot.spell().getSchool().getColor());
    }

    private static void circle(GuiGraphics g,int x,int y,float inner,float outer,double from,double to,int color,int alpha) {
        SpellWheelScreen.ring(g.pose().last().pose(),x,y,inner,outer,from,to,color,alpha);
    }
    private SpellRingOverlay() { }
}
