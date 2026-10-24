package com.kushtarbek.shogun.magic.command;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.item.SpellBookItem;
import com.kushtarbek.shogun.magic.item.SpellSlot;
import com.kushtarbek.shogun.magic.registry.MagicSpells;
import com.kushtarbek.shogun.magic.spell.Spell;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Debug command that edits the spell book in the main hand. Stand-in until there
 * is an in-world way to inscribe spells.
 * <pre>
 * /spellbook add &lt;spell&gt; [level]
 * /spellbook remove &lt;slot&gt;     (1-based)
 * /spellbook clear
 * </pre>
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellBookCommand {

    private static final String KEY = "commands." + SamuraiMod.MOD_ID + ".spellbook.";

    private static final SimpleCommandExceptionType NO_BOOK =
            new SimpleCommandExceptionType(Component.translatable(KEY + "no_book"));
    private static final SimpleCommandExceptionType FULL =
            new SimpleCommandExceptionType(Component.translatable(KEY + "full"));
    private static final DynamicCommandExceptionType UNKNOWN_SPELL = new DynamicCommandExceptionType(
            id -> Component.translatable("commands." + SamuraiMod.MOD_ID + ".spell.unknown", id));

    private static final SuggestionProvider<CommandSourceStack> SPELL_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(
                    MagicSpells.registry().getValues().stream()
                            .filter(spell -> MagicSpells.byId(spell.getId()) != null && !spell.isDevOnly())
                            .map(Spell::getId), builder);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spellbook")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("spell", ResourceLocationArgument.id())
                                .suggests(SPELL_SUGGESTIONS)
                                .executes(ctx -> add(ctx, -1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                                .executes(SpellBookCommand::remove)))
                .then(Commands.literal("clear")
                        .executes(SpellBookCommand::clear)));
    }

    private static ItemStack heldBook(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof SpellBookItem)) {
            throw NO_BOOK.create();
        }
        return stack;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ItemStack book = heldBook(ctx);
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "spell");
        Spell spell = MagicSpells.byId(id);
        if (spell == null) {
            throw UNKNOWN_SPELL.create(id);
        }
        SpellSlot slot = new SpellSlot(spell, spell.clampLevel(level < 0 ? spell.getMinLevel() : level));
        int index = SpellBookItem.addSpell(book, slot);
        if (index < 0) {
            throw FULL.create();
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "added", slot.getDisplayName(), index + 1),
                false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack book = heldBook(ctx);
        int slot = IntegerArgumentType.getInteger(ctx, "slot");
        SpellBookItem.setSlot(book, slot - 1, null);
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "removed", slot), false);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ItemStack book = heldBook(ctx);
        SpellBookItem.setSlots(book, new ArrayList<>(Collections.nCopies(SpellBookItem.getSlotCount(book), null)));
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "cleared"), false);
        return 1;
    }

    private SpellBookCommand() {
    }
}
