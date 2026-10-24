package com.kushtarbek.shogun.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

/** Drops from the Corrupted Shogun. Shift + right click dashes the wielder forward. */
public class SamuraiBladeItem extends SwordItem {

    private static final double DASH_SPEED = 0.5D;
    private static final double DASH_DISTANCE = 10.0D;
    private static final int DASH_TICKS = (int) (DASH_DISTANCE / DASH_SPEED);
    private static final float DASH_DAMAGE_MULTIPLIER = 1.5F;
    private static final int REGEN_DURATION_TICKS = 600;
    private static final int REGEN_AMPLIFIER = 1;
    private static final int DASH_COOLDOWN_TICKS = 1200;

    // A real ServerPlayer's position isn't driven by its own physics (the client
    // owns movement and reports position back), so the dash velocity has to be
    // applied on whichever side actually simulates it. Keeping separate maps per
    // side avoids the two ticks fighting over one shared state in singleplayer,
    // where client and server run in the same JVM.
    private static final Map<UUID, DashState> SERVER_DASHES = new HashMap<>();
    private static final Map<UUID, DashState> CLIENT_DASHES = new HashMap<>();

    public SamuraiBladeItem(Properties properties) {
        // attackDamageModifier 7 + NETHERITE's +4 bonus + the base 1 = 12 attack damage.
        super(Tiers.NETHERITE, 7, -2.4F, properties);
    }

    /** Hooks the forge player tick so the dash plays out as real running, not a teleport. */
    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(SamuraiBladeItem::onPlayerTick);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return new InteractionResultHolder<>(tryDash(level, player), stack);
    }

    // Right click with the crosshair over a block goes through useOn(), not use() —
    // without this override the dash only ever fired while aiming at open air.
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        InteractionResult result = tryDash(context.getLevel(), player);
        return result != InteractionResult.PASS ? result : super.useOn(context);
    }

    // Right click with the crosshair over a mob goes through here, not use() either.
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        InteractionResult result = tryDash(player.level(), player);
        return result != InteractionResult.PASS ? result : super.interactLivingEntity(stack, player, target, hand);
    }

    private InteractionResult tryDash(Level level, Player player) {
        if (!player.isShiftKeyDown() || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        beginDash(level, player);
        if (!level.isClientSide()) {
            player.getCooldowns().addCooldown(this, DASH_COOLDOWN_TICKS);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.samuraiboss.samurai_blade.ability_title")
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.samuraiboss.samurai_blade.ability_desc")
                .withStyle(ChatFormatting.GRAY));
    }

    private void beginDash(Level level, Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0.0D, look.z);
        if (dir.lengthSqr() < 1.0E-4D) {
            return;
        }
        dir = dir.normalize();

        if (level.isClientSide()) {
            CLIENT_DASHES.put(player.getUUID(), new DashState(dir, 0.0F, DASH_TICKS));
        } else {
            float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * DASH_DAMAGE_MULTIPLIER;
            SERVER_DASHES.put(player.getUUID(), new DashState(dir, damage, DASH_TICKS));
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_DURATION_TICKS, REGEN_AMPLIFIER));
        }
    }

    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        boolean clientSide = player.level().isClientSide();
        Map<UUID, DashState> dashes = clientSide ? CLIENT_DASHES : SERVER_DASHES;
        DashState state = dashes.get(player.getUUID());
        if (state == null) {
            return;
        }

        if (event.phase == TickEvent.Phase.START) {
            // Driving real velocity (instead of teleporting) is what makes the
            // player's own run animation play during the dash.
            player.setDeltaMovement(state.direction.x * DASH_SPEED,
                    player.getDeltaMovement().y,
                    state.direction.z * DASH_SPEED);
            return;
        }

        if (!clientSide) {
            hurtEntitiesInPath(player, state);
        }
        state.ticksRemaining--;
        if (state.ticksRemaining <= 0 || player.horizontalCollision) {
            dashes.remove(player.getUUID());
        }
    }

    /** Everything the dash runs through takes the hit once. */
    private static void hurtEntitiesInPath(Player player, DashState state) {
        for (LivingEntity victim : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(0.5D),
                e -> e != player && e.isAlive() && e.isAttackable())) {

            if (!state.hitVictims.add(victim.getId())) {
                continue;
            }
            victim.hurt(player.damageSources().playerAttack(player), state.damage);
            victim.knockback(1.1D, -state.direction.x, -state.direction.z);
        }
    }

    private static final class DashState {
        final Vec3 direction;
        final float damage;
        final Set<Integer> hitVictims = new HashSet<>();
        int ticksRemaining;

        DashState(Vec3 direction, float damage, int ticksRemaining) {
            this.direction = direction;
            this.damage = damage;
            this.ticksRemaining = ticksRemaining;
        }
    }
}
