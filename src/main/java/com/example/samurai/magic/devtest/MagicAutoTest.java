package com.example.samurai.magic.devtest;

import com.example.samurai.SamuraiMod;
import com.example.samurai.entity.SamuraiBossEntity;
import com.example.samurai.magic.entity.BlackHoleEntity;
import com.example.samurai.registry.ModEntities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import com.example.samurai.magic.casting.SpellCasting;
import com.example.samurai.magic.client.ClientMagicData;
import com.example.samurai.magic.client.ClientSpellInfo;
import com.example.samurai.magic.client.MagicKeys;
import com.example.samurai.magic.client.SpellWheelScreen;
import com.example.samurai.magic.data.MagicData;
import com.example.samurai.magic.entity.MeteorEntity;
import com.example.samurai.magic.entity.MeteorImpact;
import com.example.samurai.magic.item.SpellBookItem;
import com.example.samurai.magic.item.SpellScrollItem;
import com.example.samurai.magic.item.SpellSlot;
import com.example.samurai.magic.registry.MagicAttributes;
import com.example.samurai.magic.registry.MagicItems;
import com.example.samurai.magic.registry.MagicSpells;
import com.example.samurai.magic.spell.Spell;
import com.mojang.logging.LogUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Dev-only end-to-end check of the magic framework, driven through the real
 * client: key mappings for right click / R / Z / X, the book screen's buttons,
 * a save-and-quit plus reload, and screenshots of the HUD.
 *
 * <p>Inert unless the JVM runs with {@code -Dsamuraiboss.autotest=true}. Creates
 * a fresh creative world in run/saves, writes run/autotest-report.txt and
 * screenshots under run/screenshots, then closes the game.
 */
@Mod.EventBusSubscriber(modid = SamuraiMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MagicAutoTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** "all" (default), or "meteor" / "blackhole" for just that spell's part. */
    private static final String MODE = System.getProperty("samuraiboss.autotest", "");
    private static final boolean ENABLED = !MODE.isEmpty() && !MODE.equals("false");
    private static final boolean FULL = !MODE.equals("meteor") && !MODE.equals("blackhole");
    private static final boolean RUN_METEOR = FULL || MODE.equals("meteor");
    private static final boolean RUN_BLACK_HOLE = FULL || MODE.equals("blackhole");
    private static final String WORLD = "samuraiboss-autotest-" + System.currentTimeMillis();

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Step> steps = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private int index;
    private int stepTicks;
    private int fails;

    // Values carried between steps.
    private float manaBefore;
    private double riseStartY;
    private double riseMaxY;
    private double riseStartX;
    private double riseStartZ;
    private double apexDrift;
    private int expectedIndex;
    private boolean cloneResult;
    private final List<String> casterDamage = new ArrayList<>();
    private boolean watchCasterDamage;

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        if (ENABLED) {
            LOGGER.info("[AUTOTEST] enabled, world {}", WORLD);
            MinecraftForge.EVENT_BUS.register(new MagicAutoTest());
        }
    }

    private MagicAutoTest() {
        buildScript();
    }

    // ------------------------------------------------------------------ script

    private void buildScript() {
        until("main menu up", () -> mc.getOverlay() == null && mc.screen != null && mc.level == null, 2400);
        act("create creative world", this::createWorld);
        until("world loaded", () -> mc.player != null && mc.level != null && server() != null
                && serverPlayer() != null && mc.screen == null, 2400);
        act("setup", () -> {
            mc.options.pauseOnLostFocus = false;
            onServer(() -> {
                ServerPlayer p = serverPlayer();
                p.serverLevel().setDayTime(6000);
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server());
                p.serverLevel().getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server());
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server());
                buildArena(p.serverLevel());
                p.teleportTo(0.5, ARENA_TOP + 1, 0.5);
                p.getInventory().clearContent();
                p.getInventory().setItem(0, MagicItems.createTestBook());
                // Exact mana arithmetic below; regen is checked separately.
                p.getAttribute(MagicAttributes.MANA_REGEN.get()).setBaseValue(0);
                return null;
            });
            mc.player.getInventory().selected = 0;
            mc.player.setXRot(-30);
        });
        waitTicks(40);

        if (FULL) {
            frameworkSteps();
        }

        if (RUN_METEOR) {
            meteorSteps();
        }
        if (RUN_BLACK_HOLE) {
            blackHoleSteps();
        }

        act("finish", this::finish);
    }

    /** Book, keys, cast types, damage threshold, persistence. Skipped with -Psamuraiboss.autotest=meteor. */
    private void frameworkSteps() {
        // 1. Mana
        check("1 fresh player has full mana (server)", () -> serverData(d -> d.getMana() == d.getMaxMana() && d.getMaxMana() == 100f));
        check("1 client mirror shows the same mana", () -> Math.abs(ClientMagicData.getMana() - 100f) < 0.01f
                && ClientMagicData.getMaxMana() == 100f);
        screenshot("01_hud_idle");

        // 2. Book menu on R, select the LONG stub by clicking its button
        act("press R", () -> KeyMapping.click(MagicKeys.OPEN_SPELL_MENU.getKey()));
        until("2 R opens the spell wheel", () -> mc.screen instanceof SpellWheelScreen, 40);
        screenshot("02_book_menu");
        act("pick Test Stub (Long) on the wheel", () -> ((SpellWheelScreen) mc.screen).selectSlotForTest(
                bookSlotOf(MagicSpells.TEST_STUB_LONG.get())));
        waitTicks(5);
        check("2 server book copy has the LONG stub active", () -> serverActive() == MagicSpells.TEST_STUB_LONG.get());
        act("close menu", () -> mc.setScreen(null));
        waitTicks(5);

        // 3/4. Hold right click for the full LONG cast
        act("hold RMB", () -> {
            manaBefore = serverData(MagicData::getMana);
            holdUse(true);
        });
        waitTicks(15);
        check("3 casting on server and client while held", () -> serverData(MagicData::isCasting)
                && ClientMagicData.isCasting() && mc.player.isUsingItem());
        screenshot("03_cast_bar");
        until("3 cast completes", () -> !serverData(MagicData::isCasting), 60);
        waitTicks(3);
        check("3 mana spent exactly 25", () -> {
            float spent = manaBefore - serverData(MagicData::getMana);
            record("   mana " + manaBefore + " -> " + serverData(MagicData::getMana));
            return Math.abs(spent - 25f) < 0.01f;
        });
        check("3 cooldown started on server and client", () -> serverCooldown(MagicSpells.TEST_STUB_LONG.get()) > 80
                && ClientMagicData.getCooldowns().getRemaining(MagicSpells.TEST_STUB_LONG.get()) > 80);
        check("4 use pose released while RMB is still held", () -> !mc.player.isUsingItem()
                && !serverPlayerBool(ServerPlayer::isUsingItem));
        waitTicks(10);
        check("6 still held on cooldown: no new hold pose (prediction)", () -> !mc.player.isUsingItem());
        screenshot("04_hud_cooldown");
        act("release RMB", () -> holdUse(false));
        act("press R", () -> KeyMapping.click(MagicKeys.OPEN_SPELL_MENU.getKey()));
        until("wheel open", () -> mc.screen instanceof SpellWheelScreen, 40);
        check("6 the LONG stub's label shows its cooldown", () -> {
            String label = ClientSpellInfo.labelWithCooldown(new SpellSlot(MagicSpells.TEST_STUB_LONG.get(), 1)).getString();
            record("   label: " + label);
            return label.matches(".*\\d+[.,]\\ds.*");
        });
        screenshot("05_menu_cooldown");
        act("close menu", () -> mc.setScreen(null));

        // 5. Early release cancels for free
        act("clear cooldowns", () -> onServer(() -> {
            serverData(d -> {
                d.getCooldowns().clear();
                return null;
            });
            return null;
        }));
        waitTicks(5);
        act("hold RMB", () -> {
            manaBefore = serverData(MagicData::getMana);
            holdUse(true);
        });
        waitTicks(15);
        act("release RMB early", () -> holdUse(false));
        waitTicks(5);
        check("5 early release: no cast, no mana, no cooldown", () -> !serverData(MagicData::isCasting)
                && Math.abs(serverData(MagicData::getMana) - manaBefore) < 0.01f
                && serverCooldown(MagicSpells.TEST_STUB_LONG.get()) == 0);

        // 7. Hotkeys (slot order comes from registry iteration, so expectations are computed)
        act("press X", () -> {
            expectedIndex = nextFilled(serverActiveIndex(), 1);
            KeyMapping.click(MagicKeys.NEXT_SPELL.getKey());
        });
        waitTicks(5);
        check("7 X moves to the next filled slot", () -> serverActiveIndex() == expectedIndex);
        act("press Z twice", () -> {
            expectedIndex = nextFilled(nextFilled(serverActiveIndex(), -1), -1);
            KeyMapping.click(MagicKeys.PREVIOUS_SPELL.getKey());
            KeyMapping.click(MagicKeys.PREVIOUS_SPELL.getKey());
        });
        waitTicks(5);
        check("7 Z twice moves back two filled slots (wrapping)", () -> serverActiveIndex() == expectedIndex);
        selectWithHotkey(MagicSpells.TEST_STUB_INSTANT.get());

        // 8. INSTANT and CONTINUOUS
        act("click RMB once", () -> {
            manaBefore = serverData(MagicData::getMana);
            holdUse(true);
        });
        waitTicks(2);
        act("release", () -> holdUse(false));
        waitTicks(3);
        check("8 INSTANT: mana -10, cooldown 60", () -> {
            float spent = manaBefore - serverData(MagicData::getMana);
            return Math.abs(spent - 10f) < 0.01f && serverCooldown(MagicSpells.TEST_STUB_INSTANT.get()) > 50;
        });
        selectWithHotkey(MagicSpells.TEST_STUB_CONTINUOUS.get());
        act("hold RMB", () -> {
            manaBefore = serverData(MagicData::getMana);
            holdUse(true);
        });
        waitTicks(50);
        act("release", () -> holdUse(false));
        waitTicks(3);
        check("8 CONTINUOUS ~2.4 s: two full intervals paid (exactly -8), cooldown 80", () -> {
            float spent = manaBefore - serverData(MagicData::getMana);
            int cd = serverCooldown(MagicSpells.TEST_STUB_CONTINUOUS.get());
            record("   continuous spent " + spent + ", cooldown " + cd);
            return Math.abs(spent - 8f) < 0.01f && cd > 70;
        });

        // 9. Damage threshold (survival: creative players take no damage)
        act("survival, select LONG, clear cooldowns", () -> onServer(() -> {
            serverPlayer().setGameMode(GameType.SURVIVAL);
            serverData(d -> {
                d.getCooldowns().clear();
                d.setMana(100);
                return null;
            });
            return null;
        }));
        selectWithHotkey(MagicSpells.TEST_STUB_LONG.get());
        act("hold RMB", () -> {
            casterDamage.clear();
            watchCasterDamage = true;
            holdUse(true);
        });
        waitTicks(8);
        // Hit and read the cast state in one server task: with RMB still held a cast
        // interrupted by damage restarts on the client's next use repeat.
        check("9 a 3-damage hit does not interrupt (threshold 5)", () -> onServer(() -> {
            serverPlayer().hurt(serverPlayer().damageSources().generic(), 3f);
            return serverData(MagicData::isCasting);
        }));
        // Let vanilla's invulnerability frames (first 10 of 20 ticks) run out, or only 6-3 would land.
        waitTicks(12);
        check("9 a 6-damage hit interrupts at once, no cooldown", () -> onServer(() -> {
            boolean before = serverData(MagicData::isCasting);
            serverPlayer().hurt(serverPlayer().damageSources().generic(), 6f);
            record("   6-hit: casting before " + before + ", after " + serverData(MagicData::isCasting)
                    + ", damage seen " + casterDamage);
            return before && !serverData(MagicData::isCasting) && serverCooldown(MagicSpells.TEST_STUB_LONG.get()) == 0;
        }));
        act("release", () -> {
            holdUse(false);
            watchCasterDamage = false;
            casterDamage.clear();
        });
        act("heal, creative", () -> onServer(() -> {
            serverPlayer().setHealth(20);
            serverPlayer().setGameMode(GameType.CREATIVE);
            return null;
        }));

        // 10. Persistence: relog and death
        act("set mana 42, start a cooldown", () -> onServer(() -> {
            serverData(d -> {
                d.setMana(42);
                d.getCooldowns().start(MagicSpells.TEST_STUB_LONG.get(), 2400);
                return null;
            });
            return null;
        }));
        waitTicks(5);
        act("save and quit", () -> {
            mc.level.disconnect();
            mc.clearLevel(new GenericDirtMessageScreen(Component.literal("autotest: saving")));
            mc.setScreen(new TitleScreen());
        });
        until("back at title", () -> mc.level == null && server() == null, 1200);
        act("reload world", () -> mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD));
        until("world reloaded", () -> mc.player != null && server() != null && serverPlayer() != null
                && mc.screen == null, 2400);
        waitTicks(20);
        check("10 relog: mana kept (exactly 42, client too)", () -> {
            float m = serverData(MagicData::getMana);
            record("   mana after relog " + m + ", client " + ClientMagicData.getMana());
            return Math.abs(m - 42f) < 0.01f && Math.abs(ClientMagicData.getMana() - m) < 0.01f;
        });
        check("10 relog: cooldown kept on server and client", () -> {
            int s = serverCooldown(MagicSpells.TEST_STUB_LONG.get());
            int c = ClientMagicData.getCooldowns().getRemaining(MagicSpells.TEST_STUB_LONG.get());
            record("   cooldown after relog server " + s + " client " + c);
            return s > 2200 && Math.abs(s - c) < 10;
        });
        check("10 relog: active book slot kept", () -> serverActive() == MagicSpells.TEST_STUB_LONG.get());
        act("to the Nether", () -> onServer(() -> {
            serverData(d -> {
                d.setMana(33);
                return null;
            });
            ServerPlayer p = serverPlayer();
            p.teleportTo(server().getLevel(Level.NETHER), 0.5, 100, 0.5, 0, 0);
            return null;
        }));
        until("in the Nether", () -> mc.level != null && mc.level.dimension() == Level.NETHER && serverPlayer() != null, 400);
        waitTicks(20);
        check("10 dimension change: mana and cooldown kept, client synced", () -> {
            float m = serverData(MagicData::getMana);
            record("   nether mana " + m + " client " + ClientMagicData.getMana());
            return Math.abs(m - 33f) < 0.01f && Math.abs(ClientMagicData.getMana() - 33f) < 0.01f
                    && serverCooldown(MagicSpells.TEST_STUB_LONG.get()) > 2000;
        });
        act("back to the arena", () -> onServer(() -> {
            serverPlayer().teleportTo(server().getLevel(Level.OVERWORLD), 30.5, ARENA_TOP + 1, 30.5, 0, 0);
            return null;
        }));
        until("back in the overworld", () -> mc.level != null && mc.level.dimension() == Level.OVERWORLD, 400);
        waitTicks(20);
        act("clone without death (the End-exit path)", () -> onServer(() -> {
            serverData(d -> {
                d.setMana(27);
                d.grantFallImmunity();
                return null;
            });
            ServerPlayer old = serverPlayer();
            // What handleClientCommand does after the End credits: respawn keeping everything,
            // then point the connection at the new player object.
            old.connection.player = server().getPlayerList().respawn(old, true);
            // Read at once: standing on the ground, the immunity is (correctly) dropped after its grace period.
            MagicData d = MagicData.get(old.connection.player).orElseThrow(IllegalStateException::new);
            cloneResult = Math.abs(d.getMana() - 27f) < 0.01f && d.isFallImmune()
                    && d.getCooldowns().getRemaining(MagicSpells.TEST_STUB_LONG.get()) > 2000;
            record("   after clone mana " + d.getMana() + " immune " + d.isFallImmune());
            return null;
        }));
        check("10 clone without death: mana, cooldown and fall immunity kept", () -> cloneResult);
        waitTicks(20);
        act("survival, kill", () -> onServer(() -> {
            serverData(d -> {
                d.setMana(17);
                return null;
            });
            serverPlayer().setGameMode(GameType.SURVIVAL);
            serverPlayer().kill();
            return null;
        }));
        waitTicks(10);
        act("respawn", () -> {
            mc.player.respawn();
            mc.setScreen(null);
        });
        until("respawned", () -> serverPlayer() != null && serverPlayer().isAlive(), 200);
        waitTicks(10);
        check("10 death: mana kept (17, regen resumes on the fresh player)", () -> {
            float m = serverData(MagicData::getMana);
            record("   mana after death " + m);
            return m >= 17f && m < 19f;
        });
        check("10 death: cooldown kept", () -> serverCooldown(MagicSpells.TEST_STUB_LONG.get()) > 2000);
    }

    /** Meteor cast, landing, damage, crater, refusals, crater timing. */
    private void meteorSteps() {
        // Meteor
        act("meteor setup: survival, scroll lvl 4, chest, golem, tamed wolf", this::setupMeteor);
        waitTicks(10);
        act("hold RMB with meteor scroll", () -> {
            watchCasterDamage = true;
            riseStartY = serverPlayer().getY();
            riseStartX = serverPlayer().getX();
            riseStartZ = serverPlayer().getZ();
            riseMaxY = riseStartY;
            holdUse(true);
        });
        untilTracking("M meteor cast completes", () -> !serverData(MagicData::isCasting), 80);
        screenshot("06_meteor_top");
        act("release RMB", () -> holdUse(false));
        check("M arc: ≈32 up and ≈18 away from the impact", () -> {
            apexDrift = onServer(() -> Math.hypot(serverPlayer().getX() - riseStartX, serverPlayer().getZ() - riseStartZ));
            record("   rose " + String.format("%.2f", riseMaxY - riseStartY) + ", drifted " + String.format("%.2f", apexDrift));
            return riseMaxY - riseStartY > 29 && riseMaxY - riseStartY < 34 && apexDrift > 15 && apexDrift < 20;
        });
        check("M scroll consumed", () -> onServer(() -> SpellScrollItem.getSpell(serverPlayer().getMainHandItem()) == null));
        check("M meteor in flight, not straight down", () -> onServer(() -> {
            List<MeteorEntity> meteors = serverPlayer().serverLevel().getEntitiesOfClass(MeteorEntity.class,
                    new AABB(serverPlayer().blockPosition()).inflate(80));
            meteors.forEach(m -> record("   meteor at " + m.position() + " v " + m.getDeltaMovement()));
            return !meteors.isEmpty() && Math.abs(meteors.get(0).getDeltaMovement().horizontalDistance()) > 0.3;
        }));
        waitTicks(15);
        screenshot("07_meteor_flight");
        untilTracking("M caster landed", () -> serverPlayerBool(p -> p.onGround() && p.getY() < riseStartY + 2), 200);
        check("M fall immunity used up by that landing", () -> !serverData(MagicData::isFallImmune));
        check("M landed beyond the crater edge (radius 9)", () -> {
            double d = onServer(() -> Math.hypot(serverPlayer().getX() - 0.5, serverPlayer().getZ() - 0.5));
            record("   landed " + String.format("%.2f", d) + " blocks from the impact");
            return d > 10;
        });
        waitTicks(20);
        screenshot("08_crater");
        check("M caster took no damage of any kind", () -> {
            record("   caster damage events: " + casterDamage);
            return casterDamage.isEmpty();
        });
        check("M golem (10 blocks) damaged ~31", () -> onServer(() -> {
            List<IronGolem> golems = serverPlayer().serverLevel().getEntitiesOfClass(IronGolem.class,
                    new AABB(BlockPos.ZERO.above(ARENA_TOP)).inflate(30));
            golems.forEach(g -> record("   golem health " + g.getHealth()));
            return !golems.isEmpty() && golems.get(0).getHealth() < 75 && golems.get(0).getHealth() > 60;
        }));
        check("M tamed wolf untouched", () -> onServer(() -> {
            List<Wolf> wolves = serverPlayer().serverLevel().getEntitiesOfClass(Wolf.class,
                    new AABB(BlockPos.ZERO.above(ARENA_TOP)).inflate(30));
            wolves.forEach(w -> record("   wolf health " + w.getHealth() + "/" + w.getMaxHealth()));
            return !wolves.isEmpty() && wolves.get(0).getHealth() == wolves.get(0).getMaxHealth();
        }));
        check("M chest contents spilled (5 netherite ingots on the ground)", () -> onServer(() -> {
            int count = serverPlayer().serverLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(BlockPos.ZERO.above(ARENA_TOP)).inflate(30)).stream()
                    .filter(e -> e.getItem().is(Items.NETHERITE_INGOT)).mapToInt(e -> e.getItem().getCount()).sum();
            record("   netherite on ground " + count);
            return count == 5;
        }));
        check("M crater dug", () -> onServer(() -> serverPlayer().serverLevel()
                .getBlockState(new BlockPos(3, ARENA_TOP - 3, 3)).isAir()));
        act("stop watching damage", () -> watchCasterDamage = false);

        // Meteor refusals
        act("ceiling over caster", () -> onServer(() -> {
            ServerLevel level = serverPlayer().serverLevel();
            serverData(d -> {
                d.getCooldowns().clear();
                return null;
            });
            serverPlayer().setHealth(20);
            serverPlayer().teleportTo(60.5, ARENA_TOP + 1, 0.5);
            level.setBlock(new BlockPos(60, ARENA_TOP + 12, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            serverPlayer().getInventory().setItem(serverPlayer().getInventory().selected,
                    SpellScrollItem.create(MagicSpells.METEOR.get(), 1));
            return null;
        }));
        waitTicks(10);
        check("M ceiling 11 blocks up: refused (no_room)", () -> onServer(() -> {
            String msg = tryCastFromScroll().getMessage().getString();
            record("   message: " + msg);
            return msg.contains("взлёта") || msg.toLowerCase().contains("take off");
        }));
        act("one-block platform high in the air", () -> onServer(() -> {
            ServerLevel level = serverPlayer().serverLevel();
            level.setBlock(new BlockPos(120, 250, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            serverPlayer().teleportTo(120.5, 251, 0.5);
            return null;
        }));
        waitTicks(10);
        check("M one-block platform: refused (no_ground)", () -> onServer(() -> {
            String msg = tryCastFromScroll().getMessage().getString();
            record("   message: " + msg);
            return msg.contains("земли") || msg.toLowerCase().contains("ground");
        }));

        // Worst case crater timing: radius 10 entirely inside stone
        act("worst case: radius-10 crater inside solid stone", () -> onServer(() -> {
            ServerPlayer p = serverPlayer();
            ServerLevel level = p.serverLevel();
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.teleportTo(-15.5, ARENA_TOP + 10, 100.5);
            BlockPos c = new BlockPos(0, ARENA_TOP - 8, 60);
            fill(level, c.offset(-11, -8, -11), c.offset(11, 5, 11), Blocks.STONE);
            MeteorImpact.scheduleCrater(level, c, 10, null);
            return null;
        }));
        waitTicks(10);
        // Timing series: eight level-4 craters, each fully inside fresh stone, 2 s apart.
        for (int i = 0; FULL && i < 8; i++) {
            int n = i;
            act("bench crater " + n, () -> onServer(() -> {
                ServerLevel level = serverPlayer().serverLevel();
                BlockPos c = new BlockPos(-60 + (n % 4) * 30, ARENA_TOP - 8, 100 + (n / 4) * 30);
                fill(level, c.offset(-10, -7, -10), c.offset(10, 4, 10), Blocks.STONE);
                return null;
            }));
            waitTicks(20);
            act("bench carve " + n, () -> onServer(() -> {
                MeteorImpact.scheduleCrater(serverPlayer().serverLevel(),
                        new BlockPos(-60 + (n % 4) * 30, ARENA_TOP - 8, 100 + (n / 4) * 30), 9, null);
                return null;
            }));
            waitTicks(20);
        }
    }

    // ---- black hole scenarios ----

    private BlackHoleEntity hole;
    private Zombie pulled;
    private Zombie walled;
    private Zombie centred;
    private Zombie dummy;
    private SamuraiBossEntity boss;
    private ItemEntity loot;
    private Boat boat;
    /** {hole tick, damage in tenths} for every hit on the pulled zombie. */
    private final List<int[]> pulledHits = new ArrayList<>();
    private double pulledStartDistance;
    private double pulledMinDistance = Double.MAX_VALUE;
    private double centredMaxDistance;
    private double centredMaxStep;
    private Vec3 centredLast;
    private Vec3 lootStart;
    private Vec3 boatStart;
    private float walledStartHealth;
    private boolean walledEverInWall;
    private int dashStartTick = -1;
    private Vec3 dashStart;
    private double dashTravelled = -1;
    private int dashTicks = -1;
    /** Server-thread sampling of the scenario: client ticks can fall behind the server's. */
    private volatile boolean sampling;

    private static final Vec3 HOLE = new Vec3(0.5, MagicAutoTest.ARENA_TOP + 3.5, 0.5);

    /** The spec's four cases (plain mobs, Shogun mid-dash, mob behind a wall, mob at the centre) plus exclusions. */
    private void blackHoleSteps() {
        act("black hole setup: night, wall, zombies, Shogun, loot, boat", () -> onServer(() -> {
            ServerPlayer p = serverPlayer();
            ServerLevel level = p.serverLevel();
            level.setDayTime(18000); // zombies don't burn
            p.setGameMode(GameType.CREATIVE); // mobs ignore a creative player, so the Shogun keeps its target
            p.teleportTo(0.5, ARENA_TOP + 1, -16.5);
            // A closed stone booth around the walled zombie (x -10.5): no line of sight from any side.
            fill(level, new BlockPos(-12, ARENA_TOP + 1, -1), new BlockPos(-9, ARENA_TOP + 4, 2), Blocks.STONE);
            fill(level, new BlockPos(-11, ARENA_TOP + 1, 0), new BlockPos(-10, ARENA_TOP + 3, 1), Blocks.AIR);

            hole = new BlackHoleEntity(level, p);
            hole.setPos(HOLE);
            level.addFreshEntity(hole);

            pulled = spawn(level, EntityType.ZOMBIE, 12.5, ARENA_TOP + 1, 0.5);
            walled = spawn(level, EntityType.ZOMBIE, -10.0, ARENA_TOP + 1, 1.0);
            centred = spawn(level, EntityType.ZOMBIE, HOLE.x + 1, HOLE.y - 1, HOLE.z);
            dummy = spawn(level, EntityType.ZOMBIE, 15.5, ARENA_TOP + 1, 15.5);
            dummy.setNoAi(true);
            boss = spawn(level, ModEntities.SAMURAI_SHOGUN.get(), -15.5, ARENA_TOP + 1, 15.5);

            loot = new ItemEntity(level, 0.5, ARENA_TOP + 1, 6.5, new ItemStack(Items.DIAMOND));
            loot.setPickUpDelay(32767);
            loot.setDeltaMovement(Vec3.ZERO); // vanilla gives dropped items a random shove
            level.addFreshEntity(loot);
            boat = new Boat(level, 0.5, ARENA_TOP + 1, -6.5);
            level.addFreshEntity(boat);

            pulledStartDistance = pulled.getBoundingBox().getCenter().distanceTo(HOLE);
            walledStartHealth = walled.getHealth();
            lootStart = loot.position();
            boatStart = boat.position();
            centredLast = centred.position();
            // Shogun at 74%: winds up and dashes along z = 15.5 towards the dummy, 15 blocks from the hole.
            boss.setTarget(dummy);
            boss.setHealth(boss.getMaxHealth() * 0.74f);
            return null;
        }));
        act("start sampling on server ticks", () -> sampling = true);
        until("black hole ran its 7 s", () -> onServer(() -> !hole.isAlive()), BlackHoleEntity.DURATION + 200);
        act("stop sampling", () -> sampling = false);
        check("BH plain zombie pulled from 12 blocks to the centre", () -> {
            record(String.format("   zombie distance %.1f -> min %.2f", pulledStartDistance, pulledMinDistance));
            return pulledMinDistance < 3.0;
        });
        check("BH 2 magic damage once per second, not in fractions", () -> {
            boolean steady = pulledHits.size() >= 6;
            for (int i = 0; i < pulledHits.size(); i++) {
                int[] hit = pulledHits.get(i);
                steady &= hit[1] == 20; // tenths of a point: exactly 2.0
                if (i > 0) {
                    steady &= hit[0] - pulledHits.get(i - 1)[0] == 20;
                }
            }
            return steady;
        });
        check("BH zombie behind a wall: not pulled into it, not hurt", () -> {
            record(String.format("   walled health %.1f -> %.1f, ever in wall %s", walledStartHealth, walled.getHealth(), walledEverInWall));
            return !walledEverInWall && walled.getHealth() == walledStartHealth;
        });
        check("BH zombie at the centre held still, no jitter", () -> {
            record(String.format("   centred max distance %.2f, max step %.3f", centredMaxDistance, centredMaxStep));
            return centredMaxDistance < 2.6 && centredMaxStep < 0.3;
        });
        check("BH Shogun's dash still covers its 20 blocks (KB resistance 0.9)", () -> {
            record(String.format("   dash travelled %.2f in %d server ticks (limit %d)", dashTravelled, dashTicks, 36));
            return dashTravelled >= 19.5 && dashTicks > 0 && dashTicks < 36;
        });
        check("BH items and boats untouched", () -> {
            double lootMoved = onServer(() -> loot.position().distanceTo(lootStart));
            double boatMoved = onServer(() -> boat.position().distanceTo(boatStart));
            record(String.format("   loot moved %.2f, boat moved %.2f", lootMoved, boatMoved));
            return lootMoved < 0.5 && boatMoved < 0.5;
        });

        // Casting it: the caster is never pulled or hurt.
        act("survival caster 10 blocks from where they aim", () -> onServer(() -> {
            ServerPlayer p = serverPlayer();
            ServerLevel level = p.serverLevel();
            for (Entity e : level.getEntitiesOfClass(Zombie.class, new AABB(BlockPos.ZERO.above(ARENA_TOP)).inflate(40))) {
                e.discard();
            }
            boss.discard();
            p.setGameMode(GameType.SURVIVAL);
            p.setHealth(20);
            p.teleportTo(0.5, ARENA_TOP + 1, -9.5);
            p.getInventory().setItem(p.getInventory().selected, SpellScrollItem.create(MagicSpells.BLACK_HOLE.get(), 1));
            serverData(d -> {
                d.getCooldowns().clear();
                return null;
            });
            return null;
        }));
        act("look at the ground ahead", () -> {
            mc.player.setYRot(0);
            mc.player.setXRot(10);
        });
        waitTicks(10);
        act("hold RMB with the black hole scroll", () -> {
            casterDamage.clear();
            watchCasterDamage = true;
            riseStartX = serverPlayer().getX();
            riseStartZ = serverPlayer().getZ();
            holdUse(true);
        });
        until("BH cast spawns a black hole", () -> onServer(() -> !serverPlayer().serverLevel()
                .getEntitiesOfClass(BlackHoleEntity.class, new AABB(BlockPos.ZERO.above(ARENA_TOP)).inflate(60)).isEmpty()), 60);
        act("release RMB", () -> holdUse(false));
        check("BH scroll consumed", () -> onServer(() -> SpellScrollItem.getSpell(serverPlayer().getMainHandItem()) == null));
        waitTicks(25);
        screenshot("09_black_hole");
        waitTicks(60);
        check("BH caster neither pulled nor hurt", () -> {
            double moved = onServer(() -> Math.hypot(serverPlayer().getX() - riseStartX, serverPlayer().getZ() - riseStartZ));
            record(String.format("   caster moved %.2f, damage %s", moved, casterDamage));
            return moved < 0.5 && casterDamage.isEmpty();
        });
        act("stop watching damage", () -> watchCasterDamage = false);
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && sampling && hole != null) {
            sampleBlackHole();
        }
    }

    /** Server thread, once a tick while the scenario runs. */
    private void sampleBlackHole() {
        if (pulled.isAlive()) {
            pulledMinDistance = Math.min(pulledMinDistance, pulled.getBoundingBox().getCenter().distanceTo(HOLE));
        }
        walledEverInWall |= walled.isInWall();
        if (centred.isAlive() && hole.isAlive()) {
            centredMaxDistance = Math.max(centredMaxDistance, centred.getBoundingBox().getCenter().distanceTo(HOLE));
            centredMaxStep = Math.max(centredMaxStep, centred.position().distanceTo(centredLast));
            centredLast = centred.position();
        }
        // Server ticks (the boss's own counter): client ticks can fall behind under load.
        if (dashStartTick < 0 && boss.getPhase() == SamuraiBossEntity.PHASE_DASH) {
            dashStartTick = boss.tickCount;
            dashStart = boss.position();
        } else if (dashStartTick >= 0 && dashTicks < 0 && boss.getPhase() != SamuraiBossEntity.PHASE_DASH) {
            dashTicks = boss.tickCount - dashStartTick;
            dashTravelled = boss.position().subtract(dashStart).horizontalDistance();
        }
    }

    private static <T extends net.minecraft.world.entity.Mob> T spawn(ServerLevel level, EntityType<T> type,
                                                                      double x, double y, double z) {
        T mob = type.create(level);
        mob.moveTo(x, y, z, 0, 0);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    // ------------------------------------------------------------------ helpers

    private static final int ARENA_TOP = 150;

    /** 41×41 stone slab, 20 deep, top at ARENA_TOP: flat, solid, open sky. */
    private void buildArena(ServerLevel level) {
        fill(level, new BlockPos(-20, ARENA_TOP - 19, -20), new BlockPos(20, ARENA_TOP, 20), Blocks.STONE);
    }

    private static void fill(ServerLevel level, BlockPos a, BlockPos b, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(a, b)) {
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void setupMeteor() {
        onServer(() -> {
            ServerPlayer p = serverPlayer();
            ServerLevel level = p.serverLevel();
            p.setGameMode(GameType.SURVIVAL);
            p.setHealth(20);
            p.teleportTo(0.5, ARENA_TOP + 1, 0.5);
            p.setYRot(0);
            p.getInventory().setItem(p.getInventory().selected, SpellScrollItem.create(MagicSpells.METEOR.get(), 4));
            serverData(d -> {
                d.getCooldowns().clear();
                return null;
            });

            BlockPos chestPos = new BlockPos(3, ARENA_TOP, 0);
            level.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                chest.setItem(0, new ItemStack(Items.NETHERITE_INGOT, 5));
            }
            IronGolem golem = EntityType.IRON_GOLEM.create(level);
            golem.setNoAi(true);
            golem.setPos(10.5, ARENA_TOP + 1, 0.5);
            level.addFreshEntity(golem);
            Wolf wolf = EntityType.WOLF.create(level);
            wolf.setNoAi(true);
            wolf.tame(p);
            wolf.setPos(-11.0, ARENA_TOP + 1, 0.5);
            level.addFreshEntity(wolf);
            return null;
        });
        mc.player.setYRot(0);
        // Straight down: the meteor is aimed at the arena centre under the caster.
        mc.player.setXRot(90);
    }

    private com.example.samurai.magic.spell.CastResult tryCastFromScroll() {
        ServerPlayer p = serverPlayer();
        SpellSlot slot = SpellScrollItem.getSpell(p.getMainHandItem());
        com.example.samurai.magic.spell.CastResult r = SpellCasting.tryCast(p, slot.spell(), slot.level(),
                com.example.samurai.magic.casting.CastSource.SCROLL, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (r.isSuccess()) {
            SpellCasting.endCast(p, com.example.samurai.magic.spell.CastEndReason.CANCELLED);
        }
        return r;
    }

    private void createWorld() {
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261024L, false, false),
                WorldPresets::createNormalWorldDimensions);
    }

    private void holdUse(boolean down) {
        KeyMapping.set(mc.options.keyUse.getKey(), down);
        if (down) {
            KeyMapping.click(mc.options.keyUse.getKey());
        }
    }

    private int bookSlotOf(Spell spell) {
        List<SpellSlot> slots = SpellBookItem.getSlots(mc.player.getInventory().getItem(0));
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) != null && slots.get(i).spell() == spell) {
                return i;
            }
        }
        return -1;
    }

    private void clickButton(Spell spell) {
        String name = spell.getDisplayName().getString();
        for (var child : mc.screen.children()) {
            if (child instanceof Button b && b.getMessage().getString().contains(name)) {
                b.onPress();
                return;
            }
        }
        fail("no button for " + name);
    }

    private String buttonText(Spell spell) {
        String name = spell.getDisplayName().getString();
        for (var child : mc.screen.children()) {
            if (child instanceof Button b && b.getMessage().getString().contains(name)) {
                record("   button: " + b.getMessage().getString());
                return b.getMessage().getString();
            }
        }
        return "";
    }

    private void screenshot(String name) {
        act("screenshot " + name, () -> Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png",
                mc.getMainRenderTarget(), msg -> { }));
    }

    private IntegratedServer server() {
        return mc.getSingleplayerServer();
    }

    private ServerPlayer serverPlayer() {
        IntegratedServer s = server();
        return s == null || mc.player == null ? null : s.getPlayerList().getPlayer(mc.player.getUUID());
    }

    private <T> T onServer(Supplier<T> task) {
        IntegratedServer s = server();
        return s.isSameThread() ? task.get() : s.submit(task).join();
    }

    private <T> T serverData(java.util.function.Function<MagicData, T> read) {
        return onServer(() -> read.apply(MagicData.get(serverPlayer()).orElseThrow(IllegalStateException::new)));
    }

    private boolean serverPlayerBool(java.util.function.Predicate<ServerPlayer> test) {
        return onServer(() -> test.test(serverPlayer()));
    }

    private int serverCooldown(Spell spell) {
        return serverData(d -> d.getCooldowns().getRemaining(spell));
    }

    private int serverActiveIndex() {
        return onServer(() -> SpellBookItem.getEffectiveActiveIndex(serverPlayer().getInventory().getItem(0)));
    }

    /** Index of the next filled book slot from {@code from} in {@code direction}, wrapping. */
    private int nextFilled(int from, int direction) {
        List<SpellSlot> slots = onServer(() -> SpellBookItem.getSlots(serverPlayer().getInventory().getItem(0)));
        for (int step = 1; step <= slots.size(); step++) {
            int i = Math.floorMod(from + direction * step, slots.size());
            if (slots.get(i) != null) {
                return i;
            }
        }
        return from;
    }

    /** Presses X every few ticks until the spell is active, the way a player would. */
    private void selectWithHotkey(Spell spell) {
        until("select " + spell.getId().getPath() + " with X", new BooleanSupplier() {
            private int ticks;

            public boolean getAsBoolean() {
                if (serverActive() == spell) {
                    return true;
                }
                if (ticks++ % 4 == 0) {
                    KeyMapping.click(MagicKeys.NEXT_SPELL.getKey());
                }
                return false;
            }
        }, 60);
    }

    private Spell serverActive() {
        return onServer(() -> {
            ItemStack book = serverPlayer().getInventory().getItem(0);
            SpellSlot slot = book.getItem() instanceof SpellBookItem ? SpellBookItem.getActive(book) : null;
            return slot == null ? null : slot.spell();
        });
    }

    @SubscribeEvent
    public void casterDamaged(LivingDamageEvent event) {
        if (watchCasterDamage && event.getEntity() instanceof ServerPlayer) {
            casterDamage.add(event.getSource().getMsgId() + " " + event.getAmount());
        }
        if (pulled != null && event.getEntity() == pulled) {
            record(String.format("   zombie hurt at hole tick %d: %s %.1f", hole.tickCount,
                    event.getSource().getMsgId(), event.getAmount()));
            if ("indirectMagic".equals(event.getSource().getMsgId())) {
                pulledHits.add(new int[]{hole.tickCount, Math.round(event.getAmount() * 10)});
            }
        }
    }

    // ------------------------------------------------------------------ runner

    private interface Step {
        String name();

        /** Returns true when the step is finished. */
        boolean tick(int ticks);
    }

    private void act(String name, Runnable action) {
        steps.add(new Step() {
            public String name() {
                return name;
            }

            public boolean tick(int ticks) {
                action.run();
                return true;
            }
        });
    }

    private void waitTicks(int n) {
        steps.add(new Step() {
            public String name() {
                return "wait " + n;
            }

            public boolean tick(int ticks) {
                return ticks >= n;
            }
        });
    }

    private void until(String name, BooleanSupplier condition, int timeout) {
        steps.add(new Step() {
            public String name() {
                return name;
            }

            public boolean tick(int ticks) {
                if (condition.getAsBoolean()) {
                    record("PASS " + name + " (" + ticks + " ticks)");
                    return true;
                }
                if (ticks >= timeout) {
                    fail(name + " (timed out after " + timeout + " ticks)");
                    return true;
                }
                return false;
            }
        });
    }

    /** Like until, but samples the caster's height every tick. */
    private void untilTracking(String name, BooleanSupplier condition, int timeout) {
        steps.add(new Step() {
            public String name() {
                return name;
            }

            public boolean tick(int ticks) {
                riseMaxY = Math.max(riseMaxY, onServer(() -> serverPlayer().getY()));
                onServer(() -> {
                    ServerPlayer p = serverPlayer();
                    MagicData d = MagicData.get(p).orElseThrow(IllegalStateException::new);
                    LOGGER.info("[AUTOTEST]    trace {} t{} y={} side={} ground={} immune={} fall={} casting={} hp={}",
                            name, ticks, String.format("%.2f", p.getY() - riseStartY),
                            String.format("%.2f", Math.hypot(p.getX() - riseStartX, p.getZ() - riseStartZ)), p.onGround(),
                            d.isFallImmune(), String.format("%.2f", p.fallDistance), d.isCasting(), p.getHealth());
                    return null;
                });
                if (ticks > 2 && condition.getAsBoolean()) {
                    record("PASS " + name + " (" + ticks + " ticks)");
                    return true;
                }
                if (ticks >= timeout) {
                    fail(name + " (timed out)");
                    return true;
                }
                return false;
            }
        });
    }

    private void check(String name, BooleanSupplier condition) {
        act(name, () -> {
            boolean ok;
            try {
                ok = condition.getAsBoolean();
            } catch (RuntimeException e) {
                LOGGER.error("[AUTOTEST] check threw", e);
                ok = false;
            }
            if (ok) {
                record("PASS " + name);
            } else {
                fail(name);
            }
        });
    }

    private void record(String line) {
        LOGGER.info("[AUTOTEST] {}", line);
        report.add(line);
    }

    private void fail(String what) {
        fails++;
        record("FAIL " + what);
    }

    private void finish() {
        record(fails == 0 ? "ALL PASSED" : fails + " FAILED");
        try {
            Files.write(Path.of(mc.gameDirectory.getPath(), "autotest-report.txt"), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[AUTOTEST] could not write report", e);
        }
        mc.stop();
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || index >= steps.size()) {
            return;
        }
        Step step = steps.get(index);
        boolean done;
        try {
            done = step.tick(stepTicks++);
        } catch (RuntimeException e) {
            LOGGER.error("[AUTOTEST] step '{}' threw", step.name(), e);
            fail(step.name() + " threw " + e);
            done = true;
        }
        if (done) {
            index++;
            stepTicks = 0;
        }
    }
}
