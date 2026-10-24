package com.kushtarbek.shogun.magic.registry;
import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.menu.BookEquipmentMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.registries.*;
public final class MagicMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, SamuraiMod.MOD_ID);
    public static final RegistryObject<MenuType<BookEquipmentMenu>> BOOK_EQUIPMENT = MENUS.register("book_equipment",
            () -> new MenuType<>(BookEquipmentMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private MagicMenus() { }
}
