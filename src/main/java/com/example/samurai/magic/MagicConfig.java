package com.example.samurai.magic;

import net.minecraftforge.common.ForgeConfigSpec;

/** Server config: {@code <world>/serverconfig/samuraiboss-server.toml}. */
public final class MagicConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue METEOR_DESTROYS_TERRAIN;
    public static final ForgeConfigSpec.IntValue METEOR_BLOCKS_PER_TICK;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("meteor");
        METEOR_DESTROYS_TERRAIN = builder
                .comment("Whether the meteor digs a crater and lights fires. Damage is dealt either way.")
                .define("destroyTerrain", true);
        METEOR_BLOCKS_PER_TICK = builder
                .comment("Most crater positions processed per server tick. A crater with more is spread over",
                        "several ticks. Crater sizes by spell level: 439, 700, 912, 1490, 2067 positions.",
                        "The default carves every crater in a single tick.")
                .defineInRange("blocksPerTick", 2500, 64, 100_000);
        builder.pop();
        SPEC = builder.build();
    }

    private MagicConfig() {
    }
}
