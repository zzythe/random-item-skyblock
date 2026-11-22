package dev.farskies;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.config.ServerConfig;
import xaero.pac.common.server.player.config.PlayerConfig;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

/** OPAC protects machinery and world effects; the original block mask permits player bridges. */
public final class Market {
    private static final Map<UUID,Long> COMBAT=new HashMap<>();
    public static boolean protectedWorldEdit(Level level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        if(!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)||!Campaign.enabled()||level.dimension()!=Level.OVERWORLD||!serverLevel.getServer().isSameThread())return false;
        var data=CampaignData.get(serverLevel.getServer());
        if(!data.initialized)return false;
        var old=level.getBlockState(pos);
        if(data.marketBlocks.contains(pos.asLong())&&old.getBlock()!=state.getBlock())return true;
        return old.isAir()&&!state.isAir()&&pos.getY()>=data.marketBounds[1]&&data.marketColumns.contains(WorldSetup.column(pos));
    }
    public static boolean protectedOriginal(Level level,BlockPos pos){return level instanceof net.minecraft.server.level.ServerLevel serverLevel&&Campaign.enabled()&&level.dimension()==Level.OVERWORLD&&serverLevel.getServer().isSameThread()&&CampaignData.get(serverLevel.getServer()).marketBlocks.contains(pos.asLong());}
    @SubscribeEvent public static void piston(net.neoforged.neoforge.event.level.PistonEvent.Pre event){
        if(!Campaign.enabled()||!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)||level.dimension()!=Level.OVERWORLD)return;
        var resolver=event.getStructureHelper();if(resolver==null||!resolver.resolve())return;var data=CampaignData.get(level.getServer());
        if(resolver.getToDestroy().stream().anyMatch(p->data.marketBlocks.contains(p.asLong()))||resolver.getToPush().stream().anyMatch(p->data.marketBlocks.contains(p.asLong())||data.marketColumns.contains(WorldSetup.column(p.relative(event.getPistonMoveType().isExtend?event.getDirection():event.getDirection().getOpposite())))))event.setCanceled(true);
    }
    static void clear(){COMBAT.clear();}
    public static boolean inside(Level level,BlockPos pos){return inside(level,pos,0);}
    public static boolean inside(Level level,BlockPos pos,int margin) {
        if(!Campaign.enabled() || level.dimension()!=Level.OVERWORLD)return false;
        return inBounds(CampaignData.get(level.getServer()),pos,Settings.INSTANCE.marketSafeMargin+margin);
    }
    /** PvP peace covers only the market's own island land, never the surrounding void or player bridges. */
    public static boolean onLand(Level level,BlockPos pos) {
        if(!Campaign.enabled() || level.dimension()!=Level.OVERWORLD)return false;
        var data=CampaignData.get(level.getServer());int[] b=data.marketBounds;
        return pos.getY()>=b[1]-8 && pos.getY()<=b[4]+16 && data.marketColumns.contains(WorldSetup.column(pos));
    }
    static boolean inBounds(CampaignData data,BlockPos pos,int margin) {
        int[] b=data.marketBounds;
        return pos.getX()>=b[0]-margin && pos.getX()<=b[3]+margin && pos.getZ()>=b[2]-margin && pos.getZ()<=b[5]+margin;
    }
    static void allowPlayer(ServerPlayer player) {
        if(!(player instanceof net.neoforged.neoforge.common.util.FakePlayer))
            OpenPACServerAPI.get(player.server).getChunkProtection().giveFullPass(player.getUUID());
    }
    static void claim(MinecraftServer server) {
        ServerConfig.CONFIG.maxPlayerClaims.set(0);ServerConfig.CONFIG.maxPlayerClaimForceloads.set(0);
        ServerConfig.CONFIG.maxPlayerClaimsPermission.set("");ServerConfig.CONFIG.claimWelcomeMessages.set(false);
        var api=OpenPACServerAPI.get(server);var config=api.getPlayerConfigManager().getServerClaimConfig();
        config.tryToSet(PlayerConfigOptions.PROTECT_CLAIMED_CHUNKS,true);
        config.tryToSet(PlayerConfigOptions.CLAIM_EXCEPTION_PLAYERS_BY_PLAYERS,false);
        config.tryToSet(PlayerConfigOptions.CLAIM_EXCEPTION_PLAYERS_BY_MOBS,false);
        config.tryToSet(PlayerConfigOptions.CLAIM_EXCEPTION_PLAYERS_BY_OTHER,false);
        config.tryToSet(PlayerConfigOptions.CLAIM_EXCEPTION_BLOCKS_BY_EXPLOSIONS,false);
        config.tryToSet(PlayerConfigOptions.CLAIM_EXCEPTION_FIRE_SPREAD,false);
        config.tryToSet(PlayerConfigOptions.CLAIM_FLUID_BARRIER,true);
        config.tryToSet(PlayerConfigOptions.CLAIM_PISTON_BARRIER,true);
        config.tryToSet(PlayerConfigOptions.CLAIM_DISPENSER_BARRIER,true);
        int[] b=CampaignData.get(server).marketBounds;
        int margin=Settings.INSTANCE.marketSafeMargin;
        for(int x=(b[0]-margin)>>4;x<=(b[3]+margin)>>4;x++)for(int z=(b[2]-margin)>>4;z<=(b[5]+margin)>>4;z++) {
            api.getServerClaimsManager().claim(Level.OVERWORLD.location(),PlayerConfig.SERVER_CLAIM_UUID,0,x,z,false);
            var claim=api.getServerClaimsManager().get(Level.OVERWORLD.location(),x,z);
            if(claim==null || !claim.getPlayerId().equals(PlayerConfig.SERVER_CLAIM_UUID))throw new IllegalStateException("Market claim failed");
        }
        for(var p:server.getPlayerList().getPlayers())allowPlayer(p);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void damage(LivingIncomingDamageEvent event) {
        if(!Campaign.enabled())return;
        if(event.getEntity().getTags().contains(Shop.TRADER_TAG)){event.setCanceled(true);return;}
        if(event.getEntity() instanceof ServerPlayer victim) {
            if(onLand(victim.level(),victim.blockPosition())){event.setCanceled(true);return;}
            if(event.getSource().getEntity() instanceof ServerPlayer attacker) {
                if(OpeningTruce.remaining(CampaignData.get(victim.server))>0){event.setCanceled(true);return;}
                if(onLand(attacker.level(),attacker.blockPosition())){event.setCanceled(true);return;}
                long until=CampaignData.get(victim.server).clock+600;
                COMBAT.put(victim.getUUID(),until);COMBAT.put(attacker.getUUID(),until);
            }
        }
    }
    static int request(ServerPlayer player,boolean home) {
        var data=CampaignData.get(player.server);int side=data.account(player.getUUID()).faction;
        if(side<0)return fail(player,"Your team has not been assigned.");
        if(COMBAT.getOrDefault(player.getUUID(),0L)>data.clock)return fail(player,"You cannot travel during combat.");
        if(player.isPassenger() || !player.onGround() || player.level().dimension()!=Level.OVERWORLD)return fail(player,"Stand on solid ground to travel.");
        if(home ? !inside(player.level(),player.blockPosition()) : !nearHome(player,data,side))
            return fail(player,home?"Return travel starts at the Market.":"Travel starts near your base or bed.");
        var destination=home?WorldSetup.recoverRally(player.server.overworld(),data,side):WorldSetup.pos(data,2);
        if(destination==null || !WorldSetup.safe(player.server.overworld(),destination))return fail(player,"No safe arrival point is available.");
        Campaign.teleport(player,destination);return 1;
    }
    private static boolean nearHome(ServerPlayer player,CampaignData data,int faction) {
        if(player.blockPosition().distSqr(WorldSetup.pos(data,faction))<=48*48)return true;
        var bed=player.getRespawnPosition();
        return bed!=null && player.getRespawnDimension()==Level.OVERWORLD && player.blockPosition().distSqr(bed)<=16*16;
    }
    static void tick(MinecraftServer server,CampaignData data) {
        if(data.clock%20==0)for(var player:server.getPlayerList().getPlayers())
            if(inside(player.level(),player.blockPosition()) && player.getY()<-32)Campaign.teleport(player,WorldSetup.pos(data,2));
    }
    static int fail(ServerPlayer p,String message){FarSkies.LOG.info("ACTION_DENIED {} {}",p.getScoreboardName(),message);p.displayClientMessage(Component.literal(message),true);return 0;}
    static void touch(Player p) {
        if(p instanceof ServerPlayer player){var presence=Campaign.PRESENCE.get(player.getUUID());if(presence!=null)presence.active=CampaignData.get(player.server).clock;}
    }
    @SubscribeEvent public static void crafted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemCraftedEvent event){touch(event.getEntity());}
    @SubscribeEvent public static void smelted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event){touch(event.getEntity());}
    @SubscribeEvent public static void container(net.neoforged.neoforge.event.entity.player.PlayerContainerEvent event){touch(event.getEntity());}
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickItem event) {
        touch(event.getEntity());
        if(Campaign.enabled() && event.getEntity() instanceof ServerPlayer p && inside(p.level(),p.blockPosition(),12) && dangerousTool(event.getItemStack()))event.setCanceled(true);
    }
    @SubscribeEvent public static void left(PlayerInteractEvent.LeftClickBlock event){touch(event.getEntity());}
    private static boolean dangerousTool(net.minecraft.world.item.ItemStack stack) {
        var item=stack.getItem();
        return item instanceof net.minecraft.world.item.BucketItem || item instanceof net.minecraft.world.item.FlintAndSteelItem || item==net.minecraft.world.item.Items.FIRE_CHARGE;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void right(PlayerInteractEvent.RightClickBlock event) {
        touch(event.getEntity());
        if(!Campaign.enabled() || !(event.getEntity() instanceof ServerPlayer player))return;
        var level=player.level();var target=event.getPos();var item=event.getItemStack().getItem();var state=level.getBlockState(target);
        if(inside(level,target,12) && (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("chancecubes") || dangerousTool(event.getItemStack()))) {event.setCanceled(true);return;}
        var data=CampaignData.get(player.server);
        if(!data.marketBlocks.contains(target.asLong()))return;
        if(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock){event.setCanceled(true);return;}
        if(item instanceof net.minecraft.world.item.HoeItem || item instanceof net.minecraft.world.item.AxeItem || item instanceof net.minecraft.world.item.ShovelItem)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void place(BlockEvent.EntityPlaceEvent event) {
        if(!Campaign.enabled() || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || level.dimension()!=Level.OVERWORLD)return;
        var data=CampaignData.get(level.getServer());
        if(event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
            if(multi.getReplacedBlockSnapshots().stream().anyMatch(s->data.marketColumns.contains(WorldSetup.column(s.getPos()))))event.setCanceled(true);
        } else if(data.marketColumns.contains(WorldSetup.column(event.getPos())))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breakBlock(BlockEvent.BreakEvent event) {
        touch(event.getPlayer());
        if(!Campaign.enabled() || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) || level.dimension()!=Level.OVERWORLD)return;
        var data=CampaignData.get(level.getServer());
        if(data.marketBlocks.contains(event.getPos().asLong()) && !WorldSetup.foliage(event.getState()))event.setCanceled(true);
        if(inside(level,event.getPos(),12) && BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).getNamespace().equals("chancecubes"))event.setCanceled(true);
    }
    @SubscribeEvent public static void portal(BlockEvent.PortalSpawnEvent event){if(Campaign.enabled() && !Settings.INSTANCE.allowOtherDimensions)event.setCanceled(true);}
    @SubscribeEvent public static void dimension(EntityTravelToDimensionEvent event){if(Campaign.enabled() && !Settings.INSTANCE.allowOtherDimensions && event.getEntity() instanceof Player && event.getDimension()!=Level.OVERWORLD)event.setCanceled(true);}
}
