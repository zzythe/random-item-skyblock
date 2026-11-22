package dev.farskies;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.farskies.core.Deck;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class Campaign {
    private static String disabledReason;
    static boolean enabled() { return Settings.INSTANCE.campaignEnabled && disabledReason == null; }
    static final Map<UUID, Presence> PRESENCE = new HashMap<>();
    static final String[] TEAM_IDS = {"farskies_west", "farskies_east"};
    static String teamName(int side) { return Settings.INSTANCE.factionNames[side]; }
    static final class Presence {
        Vec3 pos; float yaw,pitch; long active;
        Presence(ServerPlayer p, long now) { pos = p.position(); yaw=p.getYRot();pitch=p.getXRot(); active = now; }
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        PRESENCE.clear();
        ItemDrops.initialize(event.getServer());
        disabledReason = null;
        if (!Settings.INSTANCE.campaignEnabled) return;
        if (!WorldSetup.supports(event.getServer().overworld())) {
            disabledReason = "This world uses ordinary terrain. Create a new world with World Type: Random Item Skyblock to play the faction campaign.";
            FarSkies.LOG.warn(disabledReason);
            return;
        }
        MinecraftServer server = event.getServer();
        if(!server.getWorldData().worldGenOptions().generateStructures()){
            disabledReason="Random Item Skyblock needs Generate Structures enabled for its islands. Create a new world with that option enabled.";return;
        }
        for (List<Settings.Drop> pool : Settings.INSTANCE.pools) for (Settings.Drop d : pool) item(d.item());
        CampaignData data = CampaignData.get(server);
        if(data.initialized && data.layoutVersion!=4){disabledReason="This save has the previous island layout. Create a new Random Item Skyblock world for this update.";return;}
        if (!data.initialized) WorldSetup.initialize(server, data);
        for (int i = 0; i < 2; i++) {
            var board = server.getScoreboard(); var team = board.getPlayerTeam(TEAM_IDS[i]);
            if (team == null) team = board.addPlayerTeam(TEAM_IDS[i]);
            team.setDisplayName(Component.literal(teamName(i)));
            team.setColor(i == 0 ? ChatFormatting.RED : ChatFormatting.BLUE);
            team.setAllowFriendlyFire(true);
        }
        Market.claim(server);
        Shop.ensureTrader(server);
        if(data.contentVersion<6){Shop.refreshStock(server);data.contentVersion=6;data.setDirty();}
        LuckyEvents.install();
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { LuckyEvents.uninstall(); PRESENCE.clear(); Market.clear(); Network.clear(); Factions.clear(); disabledReason = null; }
    @SubscribeEvent public static void chat(ServerChatEvent event) { if (Campaign.enabled()) Market.touch(event.getPlayer()); }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!Campaign.enabled()) {
            if (disabledReason != null) player.sendSystemMessage(Component.literal(disabledReason).withStyle(ChatFormatting.YELLOW));
            return;
        }
        CampaignData data = CampaignData.get(player.server);
        var a = data.account(player.getUUID());
        a.supply.anchor=data.clock;a.supply.bank=0;
        PRESENCE.put(player.getUUID(), new Presence(player, data.clock));
        if (a.faction < 0) {
            int side=randomSide(data,player.server.overworld().random);
            join(player,side==0?"west":"east");

        } else syncTeam(player, a.faction);
        if(a.clearBedOnLogin){player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD,null,0,false,false);a.clearBedOnLogin=false;var rally=WorldSetup.recoverRally(player.server.overworld(),data,a.faction);teleport(player,rally==null?WorldSetup.pos(data,2):rally);}
        Market.allowPlayer(player);
        Network.update(player.server,data);
        awardEras(player, Progression.tier(data));
        data.setDirty();
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Campaign.enabled()) return;
        MinecraftServer server = event.getServer(); CampaignData data = CampaignData.get(server);
        data.clock++;
        if (data.clock % 20 == 0) {
            Settings c = Settings.INSTANCE;int oldTier=Progression.tier(data);
            var activePlayers=new java.util.ArrayList<ServerPlayer>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                var a = data.account(player.getUUID()); if (a.faction < 0 || player.isSpectator() || player.isCreative() || !player.isAlive()) continue;
                Presence p = PRESENCE.computeIfAbsent(player.getUUID(), id -> new Presence(player, data.clock));
                boolean walking=p.pos.distanceToSqr(player.position())>.04&&player.onGround()&&!player.isPassenger()&&!player.isInWater();
                if(walking || Math.abs(p.yaw-player.getYRot())>.5f || Math.abs(p.pitch-player.getXRot())>.5f)p.active=data.clock;
                p.pos=player.position();p.yaw=player.getYRot();p.pitch=player.getXRot();
                if (data.clock-p.active <= c.activeSeconds*20L) { activePlayers.add(player); a.activeTicks+=20; a.lastActiveMillis=System.currentTimeMillis(); }
            }
            boolean truce=OpeningTruce.remaining(data)>0;
            if(advanceCycle(data,!activePlayers.isEmpty()))for(var player:activePlayers)deliver(player,data);
            if(truce&&OpeningTruce.remaining(data)==0)server.getPlayerList().broadcastSystemMessage(Component.literal("The opening truce has ended. PvP and raids are now enabled."),false);
            int tier=Progression.tier(data);data.era=tier;
            if(tier>oldTier){for(var player:server.getPlayerList().getPlayers())awardEras(player,tier);server.getPlayerList().broadcastSystemMessage(Component.literal("Tier "+(tier+1)+" drops are now available to everyone."),false);}
            data.setDirty();
            Network.update(server,data);
        }
        Market.tick(server, data);
        Shop.tickTrader(server);
        SkyEvents.tick(server,data);
    }

    static List<ItemStack> nextDrop(CampaignData.Account a, int era) {
        return List.of(ItemDrops.roll(a.seed,a.received,a.pendingTier>=0?a.pendingTier:era));
    }

    /** Called once per server second, never once per recipient. */
    static boolean advanceCycle(CampaignData data,boolean active){
        long before=Progression.drops(data);if(active)data.progressTicks+=20;
        return Progression.drops(data)>before;
    }

    static boolean deliver(ServerPlayer player,CampaignData data){
        var a=data.account(player.getUUID());if(a.pendingTier<0)a.pendingTier=Progression.tier(data);
        var drops=nextDrop(a,Progression.tier(data));
        if(!fits(player,drops))return false;
        int rolledTier=a.pendingTier;
        for(var stack:drops){player.getInventory().add(stack.copy());PickupNotice.send(player,stack);}
        a.received++;data.receipts++;a.pendingTier=-1;a.supply.bank=0;a.supply.anchor=data.clock;
        FarSkies.LOG.info("SUPPLY {} #{} tier={} total={} item={} count={} tick={}",player.getGameProfile().getName(),a.received,rolledTier,data.receipts,BuiltInRegistries.ITEM.getKey(drops.getFirst().getItem()),drops.getFirst().getCount(),data.clock);
        return true;
    }

    public static Item item(String id) {
        ResourceLocation key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) throw new IllegalStateException("Unknown campaign item " + id);
        return BuiltInRegistries.ITEM.get(key);
    }

    /** Simulate all stacks before inserting any, including multiple stacks competing for a slot. */
    static boolean fits(ServerPlayer player, List<ItemStack> stacks) {
        var slots = player.getInventory().items.stream().map(ItemStack::copy).toList();
        for (ItemStack incoming : stacks) {
            int left = incoming.getCount();
            for (ItemStack slot : slots) if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, incoming)) {
                int n = Math.min(left, slot.getMaxStackSize() - slot.getCount()); slot.grow(n); left -= n;
            }
            for (int i = 0; i < slots.size() && left > 0; i++) if (slots.get(i).isEmpty()) {
                int n = Math.min(left, incoming.getMaxStackSize());
                // Empty ItemStack instances cannot be mutated into a different item.
                var mutable = new java.util.ArrayList<>(slots);
                mutable.set(i, incoming.copyWithCount(n)); slots = mutable; left -= n;
            }
            if (left > 0) return false;
        }
        return true;
    }

    static void awardEras(ServerPlayer p, int era) {
        var root=p.server.getAdvancements().get(ResourceLocation.parse("farskies:journey"));if(root!=null)p.getAdvancements().award(root,"supply");
        for (int i = 0; i <= era; i++) {
            var advancement = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(FarSkies.ID, "era_" + i));
            if (advancement != null) p.getAdvancements().award(advancement, "supply");
        }
    }

    static void syncTeam(ServerPlayer p, int side) { p.server.getScoreboard().addPlayerToTeam(p.getScoreboardName(), p.server.getScoreboard().getPlayerTeam(TEAM_IDS[side])); }
    static void teleport(ServerPlayer p, BlockPos pos) { p.teleportTo(p.server.overworld(), pos.getX() + .5, pos.getY(), pos.getZ() + .5, p.getYRot(), 0); }

    static int randomSide(CampaignData data, net.minecraft.util.RandomSource random) {
        long red=data.players.values().stream().filter(v->v.faction==0).count();
        long blue=data.players.values().stream().filter(v->v.faction==1).count();
        return red==blue?random.nextInt(2):red<blue?0:1;
    }

    static int join(ServerPlayer p, String side) {
        CampaignData data = CampaignData.get(p.server); var a = data.account(p.getUUID());
        if (a.faction >= 0) { p.sendSystemMessage(Component.literal("Your side is already set. An operator can correct a mistaken assignment.")); return 0; }
        int team = side.equals("west") ? 0 : side.equals("east") ? 1 : -1;
        if (team < 0) return 0;
        long own=data.players.values().stream().filter(v->v.faction==team).count(), rival=data.players.values().stream().filter(v->v.faction==1-team).count();
        if(own-rival>=2)return Market.fail(p,teamName(team)+" has more players. An operator can correct the roster.");
        a.faction = team; a.lastActiveMillis=System.currentTimeMillis(); a.supply.anchor = data.clock; a.supply.bank = 0; syncTeam(p, team);
        p.setYRot(team == 0 ? -90 : 90); p.setYHeadRot(p.getYRot());
        teleport(p, WorldSetup.pos(data, team));
        if(!a.starterGranted){
            for(var item:List.of(Items.OAK_SAPLING,Items.WATER_BUCKET,Items.LAVA_BUCKET))p.getInventory().add(new ItemStack(item));
            a.starterGranted=true;p.inventoryMenu.broadcastChanges();
        }
        p.displayClientMessage(Component.empty(),true);
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(8,30,12));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(teamName(team)).withStyle(team==0?ChatFormatting.RED:ChatFormatting.BLUE)));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.empty()));
        Network.update(p.server,data);
        data.setDirty(); p.server.getPlayerList().broadcastSystemMessage(Component.literal(p.getGameProfile().getName() + " joined " + teamName(team) + "."), false);
        FarSkies.LOG.info("FACTION {} {}", p.getUUID(), side); return 1;
    }

    static int switchTeam(MinecraftServer server,com.mojang.authlib.GameProfile profile,int side){
        var data=CampaignData.get(server);var account=data.players.get(profile.getId());if(account==null||account.faction<0)return 0;
        account.faction=side;var player=server.getPlayerList().getPlayer(profile.getId());
        if(player!=null){syncTeam(player,side);player.setRespawnPosition(net.minecraft.world.level.Level.OVERWORLD,null,0,false,false);var rally=WorldSetup.recoverRally(server.overworld(),data,side);teleport(player,rally==null?WorldSetup.pos(data,2):rally);}
        else{server.getScoreboard().addPlayerToTeam(profile.getName(),server.getScoreboard().getPlayerTeam(TEAM_IDS[side]));account.clearBedOnLogin=true;}
        FarSkies.LOG.info("TEAM_SWITCH {} {}",profile.getId(),teamName(side));data.setDirty();return 1;
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("teamswitch").requires(s->Campaign.enabled()&&s.hasPermission(2))
            .then(Commands.argument("player",net.minecraft.commands.arguments.GameProfileArgument.gameProfile())
                .then(Commands.argument("team",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"Red","Blue"},b))
                    .executes(c->{String name=StringArgumentType.getString(c,"team");int side=name.equalsIgnoreCase("red")?0:name.equalsIgnoreCase("blue")?1:-1;
                        if(side<0){c.getSource().sendFailure(Component.literal("Choose Red or Blue."));return 0;}
                        var s=c.getSource().getServer();var d=CampaignData.get(s);int changed=0;
                        for(var profile:net.minecraft.commands.arguments.GameProfileArgument.getGameProfiles(c,"player")){
                            var account=d.players.get(profile.getId());if(account==null||account.faction<0){c.getSource().sendFailure(Component.literal(profile.getName()+" has not joined this world."));continue;}
                            switchTeam(s,profile,side);
                            c.getSource().sendSuccess(()->Component.literal(profile.getName()+" moved to "+teamName(side)+"."),true);changed++;
                        }d.setDirty();Network.update(s,d);return changed;
                    }))));
        event.getDispatcher().register(Commands.literal("skies").requires(s -> Campaign.enabled())
            .then(Commands.literal("join").requires(s->s.hasPermission(2)).then(Commands.argument("side", StringArgumentType.word()).suggests((c,b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(new String[]{"west","east"},b)).executes(c -> join(c.getSource().getPlayerOrException(), StringArgumentType.getString(c,"side")))))
            .then(Commands.literal("islands").requires(s->s.hasPermission(2)).executes(c->{CampaignData.get(c.getSource().getServer()).islands.forEach(s->c.getSource().sendSuccess(()->Component.literal(s),false));return 1;}))
            .then(Commands.literal("status").executes(c -> { var status=Progression.status(CampaignData.get(c.getSource().getServer())); c.getSource().sendSuccess(() -> status, false); return 1; }))
            .then(Commands.literal("event").requires(s->s.hasPermission(2)).then(Commands.literal("start").executes(c->{int result=SkyEvents.start(c.getSource().getServer());if(result==0)c.getSource().sendFailure(Component.literal("No eligible loaded island, or a cache is already active."));return result;})).then(Commands.literal("cancel").executes(c->{var s=c.getSource().getServer();SkyEvents.end(s,CampaignData.get(s),true);return 1;})))
            .then(Commands.literal("shop").executes(c -> Shop.show(c.getSource().getPlayerOrException())))
            .then(Commands.literal("corrupt").executes(c -> Shop.corrupt(c.getSource().getPlayerOrException())))
            .then(Commands.literal("roster").requires(s -> s.hasPermission(2)).then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player()).then(Commands.argument("side", StringArgumentType.word()).executes(c -> {
                ServerPlayer p=net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"); String side=StringArgumentType.getString(c,"side"); if (!side.equals("west")&&!side.equals("east")) return 0;
                return switchTeam(p.server,p.getGameProfile(),side.equals("west")?0:1);
            })))));
        event.getDispatcher().register(Commands.literal("market").requires(s -> Campaign.enabled())
            .executes(c -> Market.request(c.getSource().getPlayerOrException(), false))
            .then(Commands.literal("home").executes(c -> Market.request(c.getSource().getPlayerOrException(), true))));
    }

    @SubscribeEvent public static void audit(CommandEvent event) {
        if (!Campaign.enabled()) return;
        var source = event.getParseResults().getContext().getSource();
        String command = event.getParseResults().getReader().getString();
        String root = command.stripLeading().split(" ", 2)[0].replaceFirst("^/", "");
        root = root.substring(root.lastIndexOf(':') + 1);
        if (root.equals("chancecubes") && command.contains("reload")) { event.setCanceled(true); source.sendFailure(Component.literal("Restart the server after editing farskies-luck.json so the campaign reward pool remains consistent.")); }
        if (List.of("give","gamemode","tp","teleport","fill","setblock","kill","effect","summon","item","data","execute","team","op","deop","function","reload","gamerule","difficulty","loot","enchant","xp","experience","schedule","worldborder","spawnpoint","setworldspawn","attribute","chancecubes","teamswitch","openpac","opac").contains(root) || command.startsWith("skies roster")) {
            FarSkies.LOG.info("ADMIN_ATTEMPT {} /{}", source.getTextName(), command);
        }
    }
}
