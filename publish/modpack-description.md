**Two teams. Two bare 3x3 pads. A random item every 30 seconds. Build, trade, explore, and raid if you want to.**

![Random Item Skyblock](gallery:plane)

## What makes it different

- **You start with almost nothing.** Each team gets a bare 3x3 grass pad floating in the void, plus a sapling, a water bucket and a lava bucket.
- **A random item every 30 seconds.** Every active player gets their own roll. Building blocks come in useful stacks, and tools and machines come one at a time. If your inventory is full, the item waits for a later cycle, so you never get a flood of items. Offline and idle time earn nothing.
- **Shared tiers.** The whole server unlocks better items together through six tiers, based on active play time (tier 2 after 45 minutes, tier 6 after 12 hours). The HUD shows your next drop and how many drops are left until the next tier.
- **Two ways to make resources.** Use Ex Deorum hammers, sieves, barrels and crucibles by hand, or automate with Create and Create Sifter. EMI shows every recipe, and Create's Ponder explains the machines.
- **Islands worth visiting.** Small biome islands come with themed supply caches. Rarer landmark islands have camps, towers, a cemetery and a large cherry shrine. Moog's Soaring Structures adds 34 more island types. Simple Planes (from tier 3) gets you to the far ones.
- **A neutral market.** A protected island between the bases with a villager shop, a public kitchen, a workshop and a community chest. Sell crops and cooked meals for emeralds, and buy supplies or Chance Cubes.
- **PvP is optional.** A 30-minute opening truce protects both homes. After it ends, bases can be raided and you drop your inventory when you die. A raider who dies near the enemy base is worn out for a few minutes: mining there is slower, and fire, lava, TNT and Chance Cubes can't be used there. You can turn the truce off in the config.

![The market](gallery:market)

## How to play (with friends)

1. Install the pack in the [Modrinth App](https://modrinth.com/app) (or any launcher that imports `.mrpack` files). Everyone needs the same pack version.
2. **Create a new world.** The **Random Item Skyblock** world type is selected for you. Keep **Generate Structures** on, because it creates the islands.
3. Play together:
   - **Same house or same network:** one player opens the world to LAN.
   - **Over the internet:** run a dedicated server (see below). Friends who join from outside your network need port forwarding, or you can rent a Minecraft host.

New players join the smaller team. Team membership is saved, so you stay on the same team after logging out. Friendly fire is on. An operator can move a player with `/teamswitch <player> Red` or `/teamswitch <player> Blue`.

Open the **Advancements** screen for tips on what to build next. They are optional milestones, not quests, and no recipes are locked.

You can play alone. Sky caches and raids need players on both teams.

## Hosting a server

Download the **server pack** for your version: [Random-Item-Skyblock-server.zip](SERVER_ZIP_URL). You don't need to install Python or Java first.

- **Windows:** extract the zip into an empty folder and double-click `START-SERVER.cmd`.
- **Linux / macOS:** extract it and run `sh start.sh`.

On the first start you're asked to accept the [Minecraft EULA](https://aka.ms/MinecraftEULA). After that, the starter downloads NeoForge and the server mods from their official sources and checks each file's hash. If Java 21 isn't installed, it also downloads a portable Eclipse Temurin 21 into the server folder. Later starts skip the downloads. Type `stop` to save and shut down.

For a public server, turn on the whitelist, because every extra account collects its own random items.

## Sky caches

Every 45 minutes of active play, while both teams are active, a sealed cache can appear on an island in the contested middle. Chat announces the coordinates. After two minutes it opens, and the first team to open or break it claims the loot. You don't have to take part.

![A sky cache](gallery:sky-cache)

## FAQ

**Which Minecraft version and loader?** Minecraft Java 1.21.1 with NeoForge 21.1.265, on Java 21.

**Can I update my world to a new pack version?** Yes. Worlds from 0.4.0 and later keep their builds, inventories and progress.

**Do I have to use shaders?** No. Iris and Complementary Reimagined are included and turned on by default. Turn them off in Video Settings > Shader Packs if your PC struggles.

**What do I get for being online but AFK?** Nothing. Your personal drops pause after three idle minutes, and the shared tier clock only counts time while someone is active.

**Can I change the rules?** Yes, in `config/farskies.json`. For example, set `truceMinutes` to `0` to remove the truce.

**Are Chance Cubes safe?** Not always! Some cube outcomes are extreme. Open them away from the market and away from your base.

**Where are the commands?** `/market` takes you to the market when you're near your own base or bed, on the ground and not in combat. `/market home` takes you back to your team.

## Included

Gameplay: Create, Create Sifter, Ex Deorum, Farmer's Delight, Kaleidoscope Cookery, Chance Cubes, Simple Planes, Open Parties and Claims, Skyblock Biome Islands, Ember's Floating Islands, Rob's Floating Islands, Moog's Soaring Structures.

Quality of life: EMI (with JEI recipe support), Jade, AppleSkin, Pick Up Notifier, Mouse Tweaks, Zume, 3D Skin Layers, AmbientSounds, Seamless Sleep, Subtle Effects.

Performance and visuals: Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, Entity Culling, Dynamic FPS, Iris, Complementary Reimagined.

The pack's own rules (teams, drops, tiers, market, sky caches, truce and raids) are in [Random Item Skyblock Core](MOD_URL).

## Credits

Every third-party mod and the shader pack are downloaded from their own Modrinth pages and belong to their authors. The full list with licenses is in `MOD-CREDITS.md` inside the pack. Many thanks to all of them.

Random Item Skyblock is made by zzythe. The pack's own content and the core mod are MIT licensed.
