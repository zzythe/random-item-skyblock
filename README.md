# Random Item Skyblock 0.6.1

Two skyblock teams, random supplies every thirty seconds, resource projects, useful islands to explore, and a neutral market. PvP and raids are optional ways to interact. Minecraft Java 1.21.1, NeoForge 21.1.265, Java 21.

## Start playing

Install Random Item Skyblock from Modrinth, or import `Random-Item-Skyblock-0.6.1.mrpack` into the Modrinth App. The pack downloads its own rules mod, Random Item Skyblock Core, from Modrinth like every other mod. For a new world, use the automatically selected **Random Item Skyblock** preset with Generate Structures enabled. Existing 0.4.0 worlds can be updated without clearing their builds or inventories.

Red and Blue start on bare 3 by 3 grass pads at x=-80 and x=80, y=150. New players join the smaller total roster, with a random choice on a tie. Membership is saved by UUID and does not change on rejoining, going offline or being inactive. Friendly fire is on. The host can use `/teamswitch Player Red` or `/teamswitch Player Blue`, including for previously joined offline players. This needs operator permission and clears the old bed when the switch takes effect.

Your one-time startup supplies are a sapling, water bucket and lava bucket. Make a generator and a tree farm. Open Minecraft's Advancements screen for practical branches for manual resources, cooking, and optional Create power and machinery. Crafting milestones explain the next usable setup rather than awarding items for waiting. These are optional milestones, with no quest claims or locked recipes.

## Random supplies and shared tiers

Each active player gets one random roll every thirty seconds. Early building materials arrive in useful stacks, usually 8-16 basic blocks, 2-4 logs or ingots, or 8-16 arrows. Basic blocks scale up to 18-36 and logs/ingots to 4-9 at tier 6, so familiar materials remain useful. Tools and machines arrive individually. Ore chunks and pebbles arrive in usable batches. A full inventory holds one roll unchanged for a later shared cycle; it never builds a backlog or causes a burst. Offline time earns nothing. Looking around, walking and interacting keep you active; after three idle minutes, personal drops and unattended tier progress pause.

The full cumulative catalog stays available. A roll first chooses a category: 45% raw resources, 20% functional items, 20% equipment and food, 10% unusual finds, and 5% decorative variants. Every eligible item still has a chance. Higher-tier items gain more weight, so opening a tier changes the quality of the pool. There is no fixed opening sequence or guaranteed tier-up item.

Tiers are shared by the whole server. One shared drop cycle advances for each thirty seconds of active play, regardless of how many players are online or how many items they receive. The HUD shows the shared drops remaining and the shared next-drop countdown. It moves above armour, extra hearts and air bubbles. Tab shows shared progression and your own delivery count.

| Tier | Shared cycles | Cumulative active play |
|---|---:|---:|
| 1 | 0 | Start |
| 2 | 90 | 45 minutes |
| 3 | 240 | 2 hours |
| 4 | 480 | 4 hours |
| 5 | 900 | 7.5 hours |
| 6 | 1,440 | 12 hours |

The saved shared clock resumes after a restart. Late joiners immediately use the server's current tier and countdown. Each player rolls independently on that same cycle; missed offline or idle cycles are not stored. Upgrading a 0.4.0 save starts from its highest personal delivery count, preserving progress. The generated `config/random-item-pool.csv` lists the catalog's earliest tiers, effective item weights and categories.

## Make resources and choose your goals

Ex Deorum provides a route that needs no Create machinery: hammer cobble into gravel, sift it with a sieve and mesh, and turn the ore chunks into metal. Barrels, crucibles and watering cans support farming and resource production. EMI shows the recipes and uses. Its energy-only machines are excluded because this pack has no FE power system. Optional wood variants whose source mods are absent are excluded from drops and EMI; the supported vanilla-wood versions remain available. Unsupported metal pieces and ore chunks are excluded too.

Create and Create Sifter remain the automation route. Hold W over Create components for Ponder. Work toward dependable resource production, storage, cooking, enchanting, stronger equipment, a Wither fight and a working beacon. These remain useful beyond the final random-item tier.

Kaleidoscope Cookery is the dome pack's straw-hat mod. Wear the hat and break grass to find its crop seeds. The market sells the hat, basic meshes and some starter crops. Kaleidoscope Cookery and Farmer's Delight offer farming and cooking; the market also has a public kitchen and workshop. Surplus crops and prepared meals can be sold for emeralds and spent on useful supplies or Chance Cubes. The villager buys carrots, potatoes, Farmer's Delight crops, vegetable soup and egg fried rice as well as the original resources.

## Chance Cubes

The native normal and Giant Chance Cubes reward pools are retained, including their extreme outcomes. The 32 custom outcomes are additions, not replacements. Their nine luck dispatchers are registered once alongside the native rewards. Chance Cubes enter random drops at tier 3, but the market sells normal and lucky cubes from the start. Native rewards can give items ahead of the normal drop tiers. Corrupt one cube with a spider eye and gunpowder for -75 luck.

## Explore and choose your projects

Exploration islands generate randomly from the world seed. The nearby small biome islands now have guaranteed themed supply caches: breeding animals, crop seeds, brewing materials, metals, useful equipment or other local finds, alongside building supplies and food. No theme is deliberately barren.

Rarer Ember's Floating Islands landmarks add camps, towers, a cemetery and a large cherry shrine complex. Some templates are about 48 blocks across and include connected structures, native hazards and their original loot. They use the installed mod's authored structures and processors. Only the two starting pads and market have fixed locations; tier-ups do not create new islands.

Previously generated islands with no container can receive one cache when loaded. An existing container, including an empty looted chest or a player's chest, is never refilled by that migration. Larger landmarks appear in newly explored chunks after updating an existing world. Moog's Soaring Structures adds 34 rarer multi-piece island variants with its own loot and hazards. The small-island grid avoids these larger sites.

Every 45 active minutes, while both teams are active, a sky cache can appear on an already generated island in the contested middle. Chat announces its coordinates and Tab keeps the details. Participation is optional. It stays sealed for two minutes, then the first team to open or break it claims its useful, tier-scaled loot without a score or match objective. Eight bonus kits rotate in a shuffled order without repeating within a cycle. The last three sites are avoided. An unclaimed cache expires after ten server-running minutes. If there is no suitable loaded island, the event waits rather than generating land or replacing a build. Operators can test it with `/skies event start` and cancel with `/skies event cancel`.

Each campaign begins with a 30-minute opening truce, counted by the shared active-play clock. It pauses when nobody is active and does not restart on login, team switching or server restart. Player-sourced damage is blocked during the truce. Direct enemy-home mining, placement and block interactions are blocked within 48 blocks of the original pad, and owner-attributed blasts leave that area intact. A small top-left countdown disappears when the truce ends. Set `truceMinutes` to 0 in `config/farskies.json` to disable it. Existing campaigns already beyond 30 active minutes do not get another truce.

After the truce, homes are raidable and death drops your inventory. Dying within 48 blocks of the enemy base marks a returning raider as worn out for three server-running minutes. Another death there while worn out raises the timer to six minutes. Locally, mining takes four times as long and the `farskies:raid_restricted` tag blocks direct use/placement of fire tools, lava, TNT, end crystals and Chance Cubes. Opening an existing cube there is restricted too. Ordinary blocks and water remain available for bridging and landings. Fighting, theft and defending your own home remain available. These are limited deterrents, not full base protection: indirect automation, unowned hazards and extreme native cube events are not all attributed to an attacker.

Respawning has no added delay. Six seconds of damage grace ends immediately when you attack, start mining or place a block. There are no raid standards, home claims, compulsory flags or mandatory victory conditions.

Simple Planes offers fuel-powered travel to distant islands. Basic planes enter the random pool at tier 3, with larger aircraft and combat equipment at tier 4. Electrical engines and charging equipment are excluded because this pack has no power system. EMI shows the recipes.

## Market and travel

The market is south of the midpoint at z=24. PvP safety follows its original island land at island height. Player-built bridges beside it remain PvP ground. A faint green screen-edge tint fades in while you are on safe market land and fades out when you leave. The market villager walks normally and cannot be killed or pushed away. His shop uses Minecraft's normal scrollable trading screen. The public community chest is a voluntary exchange; anyone can take its contents.

Original market foundations and buildings are protected from players, explosions, Create drills and contraptions, and pistons. Foliage can be cleared and bridges can attach beside the island. Building above its original footprint is silently refused. Safe players cannot attack people outside the market. A separate hazard buffer keeps buckets, fire and Chance Cubes away from the island. Open Chance Cubes away from the market.

`/market` instantly travels from near your own base or bed while grounded and outside the thirty-second combat lock. `/market home` returns to your team. Destroyed rallies find existing safe terrain without restoring blocks. Other dimensions remain disabled.

## Install and host

Everyone needs the matching 0.6.1 pack. EMI with JEI recipe compatibility, Jade, AppleSkin, Zume, Pick Up Notifier, the performance stack, Iris and Complementary Reimagined are included. Mouse Tweaks, 3D Skin Layers, AmbientSounds, Seamless Sleep and Subtle Effects are also included with their required libraries. Ambient volume defaults to 35%; running dust, rare-item sparkles, heartbeat/hunger sounds, player/villager snoring and the sleep camera tilt are disabled or reduced for readability. Existing local controls, shaders and saves are preserved by the local updater. The shared pack contains no account data or personal options.

For a dedicated server, extract `Random-Item-Skyblock-0.6.1-server.zip` into an empty folder and run `START-SERVER.cmd` (Windows) or `sh start.sh` (Linux and macOS). Nothing else needs to be installed. The first start asks you to accept Minecraft's EULA; type yes only if you agree. It then downloads NeoForge and the server mods and checks each against a fixed hash, and if Java 21 is missing it downloads a portable Eclipse Temurin 21 into the server folder. A private whitelist helps prevent extra accounts earning extra personal supplies.

See `MOD-CREDITS.md` for third-party sources and licenses and `docs/verification.md` for the checks performed. Internal IDs retain `farskies` for save compatibility.
