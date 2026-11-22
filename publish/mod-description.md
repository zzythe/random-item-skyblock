**The game rules behind the [Random Item Skyblock](PACK_URL) modpack: two teams, a random item every 30 seconds, shared tiers, a neutral market and optional raids.**

> **Made for the Random Item Skyblock modpack.** This mod requires about a dozen of the pack's mods (listed below), and it builds its world around them. The easiest way to play is to install the [modpack](PACK_URL), which sets everything up for you.

![Random Item Skyblock](gallery:random-drop)

## What it adds

- **A void world type, "Random Item Skyblock".** Red and Blue each start on a bare 3x3 grass pad, with a protected market island between them. Exploration islands, landmark islands and Moog's Soaring Structures islands are generated from the world seed.
- **Teams.** New players join the smaller team. Membership is saved by player UUID. Friendly fire is on.
- **Random supplies.** Every active player gets one random item every 30 seconds from the full item list. Items are grouped into raw resources (45%), functional items (20%), equipment and food (20%), unusual finds (10%) and decorative variants (5%). Stacks are sized to be useful. A full inventory holds the roll for later, and offline or idle time earns nothing.
- **Six shared tiers** unlocked by the server's active play time. Higher tiers make better items more likely. A HUD line shows your next drop and the drops left until the next tier.
- **The market.** A villager shop using the normal trading screen, a public kitchen, a workshop and a community chest. Its original buildings are protected from players, explosions, Create contraptions and pistons. `/market` and `/market home` teleport you between your base and the market.
- **Sky caches.** Every 45 minutes of active play, while both teams are active, a sealed loot cache can appear on an island in the middle. The first team to open it after two minutes claims it.
- **Opening truce and raids.** A 30-minute truce, counted in active play time, blocks player damage and enemy-base griefing. After that, bases can be raided. Raiders who die near the enemy base are worn out for a few minutes (slower mining there, and no fire, lava, TNT or Chance Cubes there).
- **32 extra Chance Cubes outcomes**, added alongside the normal ones. Island supply caches, advancements with build tips, EMI integration, and pick-up notifications for every drop.

## Requirements

Minecraft 1.21.1, NeoForge 21.1.265 or newer, Java 21. Required on both the client and the server:

Create, Create Sifter, Open Parties and Claims, Chance Cubes, Pick Up Notifier, Skyblock Biome Islands, Rob's Floating Islands, Ember's Floating Islands, Moog's Soaring Structures, Moog's Structure Lib, Farmer's Delight, Kaleidoscope Cookery, Simple Planes, Ex Deorum.

The exact versions are listed as dependencies of each version of this mod.

## Settings and commands

- `config/farskies.json`: drop interval, tier thresholds, truce length (`truceMinutes`, `0` turns the truce off) and more.
- `/teamswitch <player> Red|Blue`: move a player to another team (operators).
- `/market`, `/market home`: travel to the market and back.
- `/skies event start`, `/skies event cancel`: test sky caches (operators).

Internal IDs use `farskies` so that older worlds stay compatible.

## License

MIT. Made by zzythe.
