# Corrupted Shogun

A self-contained Forge 1.20.1 mod built around a single boss fight: a
three-block-tall undead samurai with full GeckoLib animations, a multi-phase
dash mechanic, a boss-drop katana with its own active ability, and a spawn
egg to summon him anywhere.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A)
![Forge](https://img.shields.io/badge/Forge-47.4.8-1F6FEB)
![GeckoLib](https://img.shields.io/badge/GeckoLib-4.8.4-orange)
![License](https://img.shields.io/badge/license-MIT-blue)

## Features

### The boss — `samuraiboss:samurai_shogun`

- 300 HP, GeckoLib-animated (idle, walk, slash, heavy attack, defend stance,
  death), with a boss bar and darkened screen while fought.
- **Phase mechanic** — fires once at 75%, 50% and 25% HP:
  1. **Windup** (1s) — the boss squares up and locks onto its target.
  2. **Dash** — charges 20 blocks in a straight line, damaging (1.5× attack
     damage) and knocking back everything in its path.
  3. **Turn & guard** — spins 180° and regenerates 10 HP/s until it's hit
     again, capped at 15 seconds so it can't stall forever.
- Drops its katana on death.

### The katana — `samuraiboss:samurai_blade`

- Netherite-tier sword, 12 attack damage.
- **Active ability — Shadow Dash**: hold the blade, hold Shift, right-click.
  The wielder dashes 10 blocks forward with a real run (not a teleport — the
  player's own movement physics drive it, both server- and client-side, so it
  plays the normal running animation and respects collision), damaging
  everything along the way and granting Regeneration II for 30 seconds.
  1-minute cooldown.
- The tooltip documents the ability in-game.

### Spawn egg — `samuraiboss:samurai_shogun_spawn_egg`

Standard spawn egg, available in the Spawn Eggs creative tab, colored to
match the boss's corrupted purple palette.

## Requirements

- **JDK 17** (Forge 1.20.1 does not support Java 21). If you don't have it,
  Gradle will fetch one automatically via the toolchain resolver configured
  in `settings.gradle`.

## Running it

```bash
git clone https://github.com/boyutov/Corrupted-Shogun.git
cd Corrupted-Shogun
./gradlew runClient          # Windows: gradlew.bat runClient
```

The first run takes a while — Gradle has to download Forge, Minecraft, and
GeckoLib (several gigabytes). Subsequent runs start in seconds.

In-game, either:

```
/summon samuraiboss:samurai_shogun ~ ~ ~2
```

or grab the spawn egg from the creative inventory (Spawn Eggs tab) or via

```
/give @s samuraiboss:samurai_shogun_spawn_egg
```

## Building a distributable jar

```bash
./gradlew build
```

Output: `build/libs/samuraiboss-1.0.0.jar`. To run it outside the dev
environment you also need **GeckoLib 4.8.4 for Forge 1.20.1** installed
alongside it — it's declared as a mandatory dependency in `mods.toml`.

## Project structure

```
build.gradle, settings.gradle, gradle.properties   — build configuration
gradlew / gradlew.bat / gradle/wrapper/             — Gradle 8.1.1 wrapper

src/main/java/com/example/samurai/
├── SamuraiMod.java                   — mod entry point, registry wiring, creative tab
├── registry/
│   ├── ModEntities.java              — boss EntityType registration
│   └── ModItems.java                 — katana + spawn egg registration
├── item/
│   └── SamuraiBladeItem.java         — the katana's Shadow Dash ability
├── entity/
│   └── SamuraiBossEntity.java        — AI, attacks, phase/dash state machine, animation controllers
└── client/
    ├── ClientSetup.java              — renderer binding
    ├── SamuraiBossModel.java         — resource paths, head rotation
    ├── SamuraiBossRenderer.java      — renderer, emissive layer, sword trail hookup
    └── vfx/
        ├── SamuraiVfx.java           — particle/sound keyframe handlers
        ├── SwordTrail.java           — blade trail ribbon
        └── CameraShake.java          — hit-reaction camera shake

src/main/resources/
├── META-INF/mods.toml
├── pack.mcmeta
└── assets/samuraiboss/
    ├── geo/samurai_shogun.geo.json
    ├── animations/samurai_shogun.animation.json
    ├── textures/entity/samurai_shogun.png            (256×256)
    ├── textures/entity/samurai_shogun_glowmask.png
    ├── textures/item/samurai_blade.png
    ├── models/item/samurai_blade.json, samurai_shogun_spawn_egg.json
    └── lang/en_us.json, ru_ru.json
```

See `MODEL_NOTES.md` for details on the model, bones, rotation conventions,
and VFX keyframes.

## Changing the mod ID

If you fork this to build your own mod, update all of these together or
resources won't resolve:

1. `gradle.properties` → `mod_id=...`
2. `SamuraiMod.MOD_ID` in `src/main/java/com/example/samurai/SamuraiMod.java`
3. Rename `src/main/resources/assets/samuraiboss/` to match
4. The lang keys in both `lang/*.json` files (`entity.<mod_id>.samurai_shogun`,
   `item.<mod_id>.samurai_blade`, etc.)

## License

MIT — see [LICENSE](LICENSE).
