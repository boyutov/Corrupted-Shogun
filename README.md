# Corrupted Shogun

A boss-fight mod for **Minecraft 1.20.1 (Forge)**. A three-block-tall
samurai, consumed by purple corruption, waits for anyone foolish enough to
summon him. Beat him and take his katana. The mod also adds a spellbook
with two devastating spells.

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-62B47A)
![Forge](https://img.shields.io/badge/Forge-47.x-1F6FEB)
![GeckoLib](https://img.shields.io/badge/GeckoLib-4.8.x-orange)

## The Corrupted Shogun

A fully animated boss with 600 HP, heavy armour and a boss bar.

- **Nodachi combat.** He switches between a fast diagonal slash and a slow
  overhead slam that throws you into the air. The hit lands on the exact
  frame the blade connects, so you can read his swings and dodge them.
- **Dash phase.** At 75%, 50% and 25% HP he winds up and charges 20 blocks
  through everything in his way. Then he turns, raises his guard and
  regenerates until you land a hit.
- **Skyfall.** He leaps high into the air and crashes down on his target,
  sending out shockwaves. Jump over them.
- **Rage.** Below 30% HP he roars, and his attacks get faster and hit harder.
- **Reward.** He always drops his katana.

### How to summon him

Use the **Corrupted Shogun spawn egg** from the Spawn Eggs creative tab, or
run this command (requires cheats or operator):

```
/summon samuraiboss:samurai_shogun ~ ~ ~5
```

He does not spawn naturally. You pick the arena, so give him room to
move. He is a three-block-tall boss with a 20-block dash.

## Blade of the Corrupted Shogun

A Netherite-tier katana dropped by the boss.

- **Shadow Dash** (Shift + right-click). You dash 10 blocks forward and
  damage everything along the way, then get Regeneration II for 30 seconds.
  1-minute cooldown.

## Magic

- **Spell Book.** Holds up to 5 spells. Put it in the new **book slot** in
  your inventory (to the right of the armour, above the offhand) to cast
  hands-free, or hold it in your hand.
- **Spell Scrolls.** Cast a scroll once directly, or right-click it onto a
  book to write the spell into the book permanently.
- **Mana and cooldowns.** Your mana bar and spell cooldowns show on the HUD.

### Spells

| Spell | What it does |
|---|---|
| **Meteor** | You rise into the air and call down a meteor that blasts a crater where it lands. Once the cast starts, it cannot be interrupted. |
| **Black Hole** | Opens a singularity where you look for seven seconds. It pulls in and damages everything nearby except you. |

### Controls

| Action | Default key |
|---|---|
| Spell wheel (hold, then release on a spell) | `R` |
| Previous / next spell | `Z` / `X` |
| Cast from the equipped book (hold) | `V` |

You can rebind all keys in Options → Controls → Key Binds.

### Getting the spell book and scrolls

The book and a scroll of each spell are in the **Shogun Magic** creative tab.
Server operators can also hand them out with commands:

- `/spell scroll <spell> [level]` gives a scroll
- `/spellbook add <spell> [level]` writes a spell into the held book
- `/mana` shows your mana; `/mana fill`, `/mana set` and `/mana add` change it

These commands need operator rights (permission level 2).

### Server config

`serverconfig/samuraiboss-server.toml` in the world folder:

- `meteor.destroyTerrain` controls whether the meteor digs a crater and starts
  fires. Damage is dealt either way.
- `blackHole.affectPlayers` controls whether black holes pull and hurt other players.
  They never affect their caster.

## Requirements

- **Minecraft 1.20.1**
- **Forge 47.x** for 1.20.1
- **[GeckoLib](https://modrinth.com/mod/geckolib) 4.8.x for Forge 1.20.1.**
  This is a **required** dependency, and the game will not start without it.
- Java 17. The official launcher already includes it.

## Installation

1. Install Forge for Minecraft 1.20.1.
2. Put `samuraiboss-1.20.1-1.1.0.jar` and the GeckoLib jar into your `mods`
   folder.
3. Start the game with the Forge profile.

**Multiplayer:** install the mod and GeckoLib on the server **and** on
every player's client.

## Modpacks

You may include this mod in modpacks. See [LICENSE](LICENSE) for the terms.

## Bugs and feedback

Report bugs on the [issue tracker](https://github.com/boyutov/Corrupted-Shogun/issues).

## License

All Rights Reserved © 2026 Kushtarbek. See [LICENSE](LICENSE) for details.
