Random Item Skyblock - dedicated server
=======================================

You do not need to install anything first.

Windows
  1. Extract this zip into an empty folder.
  2. Double-click START-SERVER.cmd.

Linux or macOS
  1. Extract this zip into an empty folder.
  2. In a terminal in that folder, run:  sh start.sh
     (needs curl or wget, and tar - almost every system has them)

The first start asks you to accept the Minecraft EULA (https://aka.ms/MinecraftEULA).
Type yes only if you have read it and agree. Then it downloads NeoForge and the server
mods from their official sources and checks every file against a fixed hash. If Java 21
is not installed, it also downloads a portable Eclipse Temurin 21 runtime into the
"runtime" folder. Nothing is installed system-wide. Later starts skip the downloads.

When the console says "Done", players join with the Random Item Skyblock modpack
(same version) at this computer's address. Type stop and press Enter to save and
shut down.

Settings
  server.properties    port, max players, view distance, etc.
  user_jvm_args.txt    memory (-Xmx). Created on first start.
  config/farskies.json pack settings, e.g. truceMinutes.

For a public server, turn on the whitelist: it stops extra accounts from collecting
extra random items. Port forward TCP 25565 if friends join from outside your network.
