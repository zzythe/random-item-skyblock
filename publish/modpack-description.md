Skyblock for two teams. Everyone gets a random item every 30 seconds.

![Random Item Skyblock: Teams](gallery:plane)

## Features

- Each team starts on a 3x3 grass pad with a sapling, a water bucket and a lava bucket
- Everyone gets a random item every 30 seconds
- Better items unlock in six tiers over 12 hours of play, for the whole server at once
- Ex Deorum sieves and hammers, or Create for automation
- Floating islands with loot and landmark builds, plus 34 island types from Moog's Soaring Structures
- Planes from tier 3 for the far islands
- A market island between the bases with a villager shop, a kitchen, a workshop and Chance Cubes
- Sky caches: loot shows up in the middle every 45 minutes and both teams can go for it
- A 30 minute truce at the start. After that, bases can be raided

![The market](gallery:market)

## Playing with friends

Install the pack in the [Modrinth App](https://modrinth.com/app) and create a new world. The Random Item Skyblock world type is already selected. Keep Generate Structures on or there won't be any islands.

On the same network, open the world to LAN. Otherwise, run a server.

New players join the smaller team. Operators can move people with `/teamswitch <player> Red` or `Blue`. You can play solo, but sky caches and raids need someone on each team.

## Server

Download the server pack from the [GitHub release](SERVER_ZIP_URL) for your version, extract it into an empty folder, and run `START-SERVER.cmd` on Windows or `sh start.sh` on Linux and macOS. The first start downloads NeoForge and the mods, plus Java 21 if you don't have it.

Use a whitelist on public servers, since every account gets its own items.

## FAQ

**What version is it?**
Minecraft 1.21.1 on NeoForge.

**Is there an end goal?**
No. Tiers stop at 12 hours of play and the game keeps going from there.

**Can I update my world?**
Yes. Worlds from 0.4.0 onward carry over.

**It runs badly.**
Turn off shaders under Video Settings > Shader Packs. The pack comes with Complementary Reimagined turned on.

**Can I change the rules?**
Yes, in `config/farskies.json`. Set `truceMinutes` to `0` to remove the truce.

## Included mods

Create, Create Sifter, Ex Deorum, Farmer's Delight, Kaleidoscope Cookery, Chance Cubes, Simple Planes, Open Parties and Claims, Skyblock Biome Islands, Ember's Floating Islands, Rob's Floating Islands, Moog's Soaring Structures, EMI, Jade, AppleSkin, Pick Up Notifier, Mouse Tweaks, Zume, 3D Skin Layers, AmbientSounds, Seamless Sleep, Subtle Effects, Sodium, Lithium, FerriteCore, ModernFix, ImmediatelyFast, Entity Culling, Dynamic FPS, Iris and Complementary Reimagined.

The rules (teams, drops, tiers, market, sky caches, raids) are in [Random Item Skyblock Core](MOD_URL). Licenses for every mod are in `MOD-CREDITS.md` inside the pack.
