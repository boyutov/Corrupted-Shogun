package com.kushtarbek.shogun.magic.client;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.item.GuideItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** The vanilla book screen over the guide's translated pages. */
public final class GuideScreen {

    public static void open() {
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess() {
            @Override
            public int getPageCount() {
                return GuideItem.PAGES;
            }

            @Override
            public FormattedText getPageRaw(int page) {
                return Component.translatable("guide." + SamuraiMod.MOD_ID + ".page." + (page + 1));
            }
        }));
    }

    private GuideScreen() {
    }
}
