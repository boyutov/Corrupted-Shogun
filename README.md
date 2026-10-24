# Corrupted Shogun

A Forge 1.20.1 mod built around a boss fight with a three-block-tall corrupted
samurai, plus a small spellbook magic system: mana, spell scrolls, a spell
wheel and two heavy-hitting spells.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A)
![Forge](https://img.shields.io/badge/Forge-47.4.8-1F6FEB)
![GeckoLib](https://img.shields.io/badge/GeckoLib-4.8.4-orange)
![License](https://img.shields.io/badge/license-MIT-blue)

## Features

### The boss — `samuraiboss:samurai_shogun`

- 600 HP, heavy armour, boss bar, fully animated with GeckoLib.
- Wields a nodachi one-handed: a fast diagonal slash and a slow overhead
  slam that knocks targets into the air. Damage lands exactly on the frame
  the blade connects.
- **Dash phase** at 75%, 50% and 25% HP: winds up, charges 20 blocks through
  everything in its path, then turns and guards while regenerating until hit.
- **Skyfall** — leaps into the air and crashes down on its target, sending
  out shockwaves you can jump over.
- **Rage** at 30% HP — a roar, then faster and harder-hitting attacks.
- Drops its katana on death.

### The katana — `samuraiboss:samurai_blade`

- Netherite-tier sword.
- **Shadow Dash** — Shift + right-click: a 10-block dash that damages
  everything along the way and grants Regeneration II for 30 seconds.
  1-minute cooldown.

### Magic

- **Spell book** with 5 spell slots. Put it in the book slot of your
  inventory (next to the armour, above the offhand) to cast hands-free, or
  hold it in your hand.
- **Spell scrolls** — cast once from the scroll, or right-click a scroll onto
  a book to write the spell into it.
- **Mana** bar and per-spell cooldowns on the HUD.
- **Meteor** — rise into the air and call down a meteor that leaves a crater.
  Once started, the cast cannot be interrupted.
- **Black Hole** — opens a seven-second singularity where you look, pulling
  in and damaging everything nearby except you.

| Action | Default key |
|---|---|
| Spell wheel (hold, release on a spell) | `R` |
| Previous / next spell | `Z` / `X` |
| Cast from the equipped book (hold) | `V` |

All keys can be rebound in Options → Controls.

Commands for testing: `/mana`, `/spell`, `/spellbook`.

## Installing

1. Install **Forge 1.20.1** (47.x).
2. Put **GeckoLib 4.4+ for Forge 1.20.1** and `samuraiboss-1.0.0.jar` into
   the `mods` folder.

Multiplayer works the same way: the server and every player need the same
jar.

## Building from source

Requires **JDK 17** (Gradle fetches it automatically if missing).

```bash
git clone https://github.com/boyutov/Corrupted-Shogun.git
cd Corrupted-Shogun
./gradlew runClient          # run the game in a dev environment
./gradlew build              # jar in build/libs/
```

In-game: `/summon samuraiboss:samurai_shogun ~ ~ ~2` or use the spawn egg
from the Spawn Eggs creative tab.

## License

MIT — see [LICENSE](LICENSE).
