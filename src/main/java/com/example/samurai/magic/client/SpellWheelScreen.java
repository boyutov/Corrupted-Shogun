package com.example.samurai.magic.client;

import com.example.samurai.SamuraiMod;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.BookLocation;
import com.example.samurai.magic.item.SpellSlot;
import com.example.samurai.magic.network.MagicNetwork;
import com.example.samurai.magic.network.SelectSpellPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Radial spell picker. Opens while the menu key is held; the mouse direction picks a
 * sector; releasing the key (or clicking) makes that spell active. Releasing with the
 * cursor in the centre, or Esc, changes nothing.
 */
public class SpellWheelScreen extends Screen {

    private static final int INNER = 36;
    private static final int OUTER = 100;
    /** Cursor closer than this to the centre selects nothing. */
    private static final int DEADZONE = 22;
    private static final float GAP_RADIANS = 0.04f;
    private static final int ARC_STEPS = 24;

    private final BookLocation location;
    /** Book slot index for each sector, filled slots only, in book order clockwise from the top. */
    private final List<Integer> sectors = new ArrayList<>();
    private int hovered = -1;

    public SpellWheelScreen(InteractionHand hand) { this(BookLocation.of(hand)); }

    public SpellWheelScreen(BookLocation location) {
        super(Component.translatable("screen." + SamuraiMod.MOD_ID + ".spell_book"));
        this.location = location;
    }

    private ItemStack book() {
        return minecraft == null || minecraft.player == null ? ItemStack.EMPTY : location.get(minecraft.player);
    }

    @Override
    protected void init() {
        sectors.clear();
        List<SpellSlot> slots = SpellBookItem.getSlots(book());
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) != null) {
                sectors.add(i);
            }
        }
    }

    @Override
    public void tick() {
        if (!(book().getItem() instanceof SpellBookItem)) {
            onClose();
        }
    }

    /** Book slot under the cursor, or -1. */
    public int hoveredSlot() {
        return hovered < 0 ? -1 : sectors.get(hovered);
    }

    private int sectorAt(double mouseX, double mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        if (sectors.isEmpty() || dx * dx + dy * dy < DEADZONE * DEADZONE) {
            return -1;
        }
        double span = Mth.TWO_PI / sectors.size();
        // 0 at the top, growing clockwise (screen y points down).
        double angle = Mth.positiveModulo(Math.atan2(dy, dx) + Math.PI / 2 + span / 2, Mth.TWO_PI);
        return (int) (angle / span) % sectors.size();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Equipment can change through a server sync while the wheel is open.
        init();
        hovered = sectorAt(mouseX, mouseY);
        int cx = width / 2;
        int cy = height / 2;
        ItemStack stack = book();
        List<SpellSlot> slots = SpellBookItem.getSlots(stack);
        int active = SpellBookItem.getEffectiveActiveIndex(stack);

        if (sectors.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen." + SamuraiMod.MOD_ID + ".spell_book.no_spells"),
                    cx, cy - 4, 0xFFFFFF);
            return;
        }

        // Shapes first, through their own buffer; GuiGraphics' batched text goes on top.
        graphics.flush();
        Matrix4f pose = graphics.pose().last().pose();
        double span = Mth.TWO_PI / sectors.size();
        for (int i = 0; i < sectors.size(); i++) {
            SpellSlot slot = slots.get(sectors.get(i));
            double mid = -Math.PI / 2 + i * span;
            double from = mid - span / 2 + GAP_RADIANS;
            double to = mid + span / 2 - GAP_RADIANS;
            int color = slot.spell().getSchool().getColor();
            boolean isHovered = i == hovered;
            int outer = isHovered ? OUTER + 6 : OUTER;
            ring(pose, cx, cy, INNER, outer, from, to, darken(color, isHovered ? 0.85f : 0.45f), isHovered ? 0xE0 : 0xB0);
            if (sectors.get(i) == active) {
                ring(pose, cx, cy, outer + 2, outer + 5, from, to, 0xFCE296, 0xFF); // gold rim on the active one
            }
        }
        ring(pose, cx, cy, 0, INNER - 3, 0, Mth.TWO_PI, 0x0B0B1A, 0xC0);

        for (int i = 0; i < sectors.size(); i++) {
            SpellSlot slot = slots.get(sectors.get(i));
            double mid = -Math.PI / 2 + i * span;
            int lx = cx + (int) (Math.cos(mid) * (INNER + OUTER) / 2);
            int ly = cy + (int) (Math.sin(mid) * (INNER + OUTER) / 2);
            Component name = slot.spell().getDisplayName();
            Component level = Component.translatable("enchantment.level." + slot.level());
            int cooldown = ClientMagicData.getCooldowns().getRemaining(slot.spell());
            int lines = cooldown > 0 ? 3 : 2;
            int y = ly - lines * font.lineHeight / 2;
            graphics.drawCenteredString(font, name, lx, y, 0xFFFFFF);
            graphics.drawCenteredString(font, level, lx, y + font.lineHeight, 0xFCE296);
            if (cooldown > 0) {
                graphics.drawCenteredString(font, Component.literal(ClientSpellInfo.formatSeconds(cooldown))
                        .withStyle(ChatFormatting.RED), lx, y + 2 * font.lineHeight, 0xFFFFFF);
            }
        }

        // Centre: what releasing now would select.
        if (hovered >= 0) {
            SpellSlot slot = slots.get(sectors.get(hovered));
            graphics.drawCenteredString(font, slot.getDisplayName(), cx, cy - font.lineHeight, 0xFFFFFF);
            graphics.drawCenteredString(font, Component.translatable("tooltip." + SamuraiMod.MOD_ID + ".mana",
                    String.format("%.0f", slot.spell().getManaCost(slot.level()))), cx, cy + 1, 0x9CC0FF);
        } else {
            graphics.drawCenteredString(font, title, cx, cy - font.lineHeight / 2, 0xAAAAAA);
        }
    }

    /** Filled annulus sector between two angles (radians, 0 = +x, clockwise on screen). */
    private static void ring(Matrix4f pose, int cx, int cy, float inner, float outer, double from, double to,
                             int rgb, int alpha) {
        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int k = 0; k <= ARC_STEPS; k++) {
            double a = from + (to - from) * k / ARC_STEPS;
            float cos = (float) Math.cos(a);
            float sin = (float) Math.sin(a);
            buffer.vertex(pose, cx + cos * outer, cy + sin * outer, 0).color(r, g, b, alpha).endVertex();
            buffer.vertex(pose, cx + cos * inner, cy + sin * inner, 0).color(r, g, b, alpha).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static int darken(int rgb, float factor) {
        int r = (int) ((rgb >> 16 & 0xFF) * factor);
        int g = (int) ((rgb >> 8 & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    /** Makes the hovered spell active (if any) and closes. */
    private void confirm() {
        int slot = hoveredSlot();
        if (slot >= 0 && SpellBookItem.setActiveIndex(book(), slot)) {
            // Applied locally for instant feedback; the server's copy syncs back anyway.
            MagicNetwork.CHANNEL.sendToServer(new SelectSpellPacket(location, slot));
        }
        onClose();
    }

    /** For MagicAutoTest: select a book slot as if hovered and released. */
    public void selectSlotForTest(int slot) {
        hovered = sectors.indexOf(slot);
        confirm();
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (MagicKeys.OPEN_SPELL_MENU.matches(keyCode, scanCode)) {
            hovered = sectorAt(currentMouseX(), currentMouseY());
            confirm();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // The menu key may be bound to a mouse button.
        if (MagicKeys.OPEN_SPELL_MENU.matchesMouse(button)) {
            hovered = sectorAt(mouseX, mouseY);
            confirm();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            hovered = sectorAt(mouseX, mouseY);
            confirm();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private double currentMouseX() {
        return minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
    }

    private double currentMouseY() {
        return minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
