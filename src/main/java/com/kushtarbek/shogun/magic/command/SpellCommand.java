package com.kushtarbek.shogun.magic.command;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.casting.CastSource;
import com.kushtarbek.shogun.magic.casting.SpellCasting;
import com.kushtarbek.shogun.magic.data.MagicData;
import com.kushtarbek.shogun.magic.item.SpellScrollItem;
import com.kushtarbek.shogun.magic.registry.MagicSpells;
import com.kushtarbek.shogun.magic.spell.CastEndReason;
import com.kushtarbek.shogun.magic.spell.CastResult;
import com.kushtarbek.shogun.magic.spell.Spell;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
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

/**
 * Debug command for the cast pipeline.
 * <pre>
 * /spell cast &lt;spell&gt; [level]   LONG casts run to completion on their own here
 * /spell cancel
 * /spell status
 * /spell cooldowns clear
 * /spell scroll &lt;spell&gt; [level]  gives a scroll (max level by default)
 * </pre>
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SpellCommand {

    private static final String KEY = "commands." + SamuraiMod.MOD_ID + ".spell.";

    private static final DynamicCommandExceptionType UNKNOWN_SPELL = new DynamicCommandExceptionType(
            id -> Component.translatable(KEY + "unknown", id));

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
        dispatcher.register(Commands.literal("spell")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("cast")
                        .then(Commands.argument("spell", ResourceLocationArgument.id())
                                .suggests(SPELL_SUGGESTIONS)
                                .executes(ctx -> cast(ctx, -1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> cast(ctx, IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("cancel")
                        .executes(SpellCommand::cancel))
                .then(Commands.literal("status")
                        .executes(SpellCommand::status))
                .then(Commands.literal("scroll")
                        .then(Commands.argument("spell", ResourceLocationArgument.id())
                                .suggests(SPELL_SUGGESTIONS)
                                .executes(ctx -> giveScroll(ctx, -1))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> giveScroll(ctx, IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("cooldowns")
                        .then(Commands.literal("clear")
                                .executes(SpellCommand::clearCooldowns))));
    }

    private static int cast(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "spell");
        Spell spell = MagicSpells.byId(id);
        if (spell == null) {
            throw UNKNOWN_SPELL.create(id);
        }
        int spellLevel = level < 0 ? spell.getMinLevel() : level;
        CastResult result = SpellCasting.tryCast(player, spell, spellLevel, CastSource.COMMAND, null);
        if (result.isSuccess()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "cast", spell.getDisplayName(), spellLevel),
                    false);
            return 1;
        }
        ctx.getSource().sendFailure(result.getMessage());
        return 0;
    }

    private static int giveScroll(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "spell");
        Spell spell = MagicSpells.byId(id);
        if (spell == null) {
            throw UNKNOWN_SPELL.create(id);
        }
        int spellLevel = spell.clampLevel(level < 0 ? spell.getMaxLevel() : level);
        ItemStack scroll = SpellScrollItem.create(spell, spellLevel);
        if (!player.getInventory().add(scroll)) {
            player.drop(scroll, false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "scroll_given", spell.getDisplayName(), spellLevel),
                false);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SpellCasting.endCast(player, CastEndReason.CANCELLED);
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "cancelled"), false);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicData data = MagicData.get(player).orElseThrow(IllegalStateException::new);
        Spell casting = data.getCastingSpell();
        if (casting == null) {
            ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "status.idle"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "status.casting",
                    casting.getDisplayName(), data.getCastingLevel(),
                    data.getCastTicksElapsed(), data.getCastTicksRemaining()), false);
        }
        data.getCooldowns().view().forEach((id, entry) -> {
            Spell spell = MagicSpells.byId(id);
            Component name = spell != null ? spell.getDisplayName() : Component.literal(id.toString());
            ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "status.cooldown",
                    name, entry.getRemaining(), entry.getTotal()), false);
        });
        return 1;
    }

    private static int clearCooldowns(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicData.get(player).ifPresent(data -> data.getCooldowns().clear());
        ctx.getSource().sendSuccess(() -> Component.translatable(KEY + "cooldowns_cleared"), false);
        return 1;
    }

    private SpellCommand() {
    }
}
