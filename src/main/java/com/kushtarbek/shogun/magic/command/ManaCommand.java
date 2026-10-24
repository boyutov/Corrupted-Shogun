package com.kushtarbek.shogun.magic.command;

import com.kushtarbek.shogun.SamuraiMod;
import com.kushtarbek.shogun.magic.data.MagicData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Debug command.
 * <pre>
 * /mana                          show own mana
 * /mana get [targets]
 * /mana set &lt;targets&gt; &lt;amount&gt;
 * /mana add &lt;targets&gt; &lt;amount&gt;   (amount may be negative)
 * /mana fill [targets]
 * </pre>
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ManaCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mana")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> get(ctx, List.of(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("get")
                        .executes(ctx -> get(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> get(ctx, EntityArgument.getPlayers(ctx, "targets")))))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                                        .executes(ctx -> modify(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                (data, amount) -> data.setMana(amount))))))
                .then(Commands.literal("add")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg())
                                        .executes(ctx -> modify(ctx, EntityArgument.getPlayers(ctx, "targets"),
                                                MagicData::addMana)))))
                .then(Commands.literal("fill")
                        .executes(ctx -> fill(ctx, List.of(ctx.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(ctx -> fill(ctx, EntityArgument.getPlayers(ctx, "targets"))))));
    }

    private static int get(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            MagicData.get(player).ifPresent(data -> ctx.getSource().sendSuccess(() -> describe(player, data), false));
        }
        return targets.size();
    }

    private static int modify(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets,
                              BiConsumer<MagicData, Float> action) {
        float amount = FloatArgumentType.getFloat(ctx, "amount");
        for (ServerPlayer player : targets) {
            MagicData.get(player).ifPresent(data -> {
                action.accept(data, amount);
                data.sync();
                ctx.getSource().sendSuccess(() -> describe(player, data), true);
            });
        }
        return targets.size();
    }

    private static int fill(CommandContext<CommandSourceStack> ctx, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            MagicData.get(player).ifPresent(data -> {
                data.setMana(data.getMaxMana());
                data.sync();
                ctx.getSource().sendSuccess(() -> describe(player, data), true);
            });
        }
        return targets.size();
    }

    private static Component describe(ServerPlayer player, MagicData data) {
        return Component.translatable("commands." + SamuraiMod.MOD_ID + ".mana.info",
                player.getDisplayName(),
                String.format("%.1f", data.getMana()),
                String.format("%.1f", data.getMaxMana()));
    }

    private ManaCommand() {
    }
}
