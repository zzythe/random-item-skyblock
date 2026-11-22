package dev.farskies;

import java.util.*;
import dev.farskies.world.IslandStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.*;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.*;

/** Occasional competition for ordinary useful loot on existing generated land. */
public final class SkyEvents {
    static boolean active(CampaignData data){return data.eventPosition.length==3;}
    static long interval(){return Settings.INSTANCE.eventMinutes*60*20L;}
    public static String status(CampaignData data){
        if(!active(data))return "";var p=WorldSetup.pos(data.eventPosition);
        long seconds=Math.max(0,(data.eventOpensAt-data.clock+19)/20);
        return "Sky cache: "+p.getX()+", "+p.getZ()+(seconds>0?" | opens in "+seconds/60+":"+String.format("%02d",seconds%60):" | open");
    }
    static boolean bothTeams(MinecraftServer server,CampaignData data){
        boolean[] present=new boolean[2];
        for(var p:server.getPlayerList().getPlayers()){
            int side=data.account(p.getUUID()).faction;var activity=Campaign.PRESENCE.get(p.getUUID());
            if(side>=0&&p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&activity!=null&&data.clock-activity.active<=Settings.INSTANCE.activeSeconds*20L)present[side]=true;
        }return present[0]&&present[1];
    }
    static void tick(MinecraftServer server,CampaignData data){
        if(data.clock%20!=0)return;
        if(data.nextEventAt==0){data.nextEventAt=data.progressTicks+interval();data.setDirty();}
        if(active(data)){
            if(data.clock>=data.eventExpiresAt){end(server,data,true);return;}
            var pos=WorldSetup.pos(data.eventPosition);
            if(!(server.overworld().getBlockEntity(pos) instanceof BarrelBlockEntity)){end(server,data,false);return;}
            if(data.clock>=data.eventOpensAt&&data.clock<data.eventOpensAt+20)announce(server,"The sky cache is open.");
        }else if(OpeningTruce.remaining(data)==0&&data.progressTicks>=data.nextEventAt&&data.clock%(20*30)==0&&bothTeams(server,data))start(server);
    }
    static int start(MinecraftServer server){
        var data=CampaignData.get(server);if(active(data))return 0;var level=server.overworld();var choices=new ArrayList<BlockPos>();
        // Examine loaded structure starts only. Never generate a surprise island or distant chunks.
        for(int cx=-16;cx<=16;cx++)for(int cz=-16;cz<=16;cz++){
            var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null)continue;
            for(var start:chunk.getAllStarts().values())if(start.isValid()&&start.getStructure() instanceof IslandStructure){
                var b=start.getBoundingBox();
                for(int x=Math.max(-48,b.minX());x<=Math.min(48,b.maxX());x+=3)for(int z=b.minZ();z<=b.maxZ();z+=3){
                    if(Math.abs(z-Settings.INSTANCE.marketOffsetZ)<72||Math.abs(z)>300||!level.hasChunkAt(new BlockPos(x,150,z)))continue;
                    int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);var p=new BlockPos(x,y,z);
                    if(!b.isInside(p.below())||!WorldSetup.safe(level,p)||Market.inside(level,p,24)||data.eventHistory.stream().anyMatch(previous->BlockPos.of(previous).distSqr(p)<48*48))continue;
                    var floor=level.getBlockState(p.below());if(floor.is(net.minecraft.tags.BlockTags.DIRT)||floor.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)||floor.is(Blocks.SAND)||floor.is(Blocks.SNOW_BLOCK))choices.add(p);
                }
            }
        }
        if(choices.isEmpty())return 0;
        return openAt(server,choices.get(level.random.nextInt(choices.size())));
    }
    static int openAt(MinecraftServer server,BlockPos pos){
        var data=CampaignData.get(server);var level=server.overworld();if(active(data)||!level.getBlockState(pos).isAir())return 0;
        level.setBlockAndUpdate(pos,Blocks.BARREL.defaultBlockState());
        if(!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel))return 0;
        var named=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARREL);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Sky cache"));barrel.applyComponentsFromItemStack(named);
        var deck=new ArrayList<>(List.of(0,1,2,3,4,5,6,7));Collections.shuffle(deck,new Random(level.getSeed()^(data.eventIndex/8)*0x6A09E667L));
        int variant=deck.get(data.eventIndex%8);
        barrel.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse("farskies:island/contest_"+Progression.tier(data)+"_"+variant)),level.random.nextLong());
        data.eventPosition=WorldSetup.xyz(pos);data.eventOpensAt=data.clock+2*60*20;data.eventExpiresAt=data.clock+10*60*20;data.eventIndex++;
        data.eventHistory.add(pos.asLong());while(data.eventHistory.size()>3)data.eventHistory.removeFirst();data.setDirty();
        announce(server,"A sky cache was found at "+pos.getX()+", "+pos.getZ()+". It opens in two minutes.");return 1;
    }
    private static boolean matches(Level level,BlockPos pos,CampaignData data){return level.dimension()==Level.OVERWORLD&&active(data)&&pos.equals(WorldSetup.pos(data.eventPosition));}
    private static void claimed(ServerPlayer player,CampaignData data){
        int side=data.account(player.getUUID()).faction;if(side<0)return;
        data.cacheWins[side]++;
        data.eventPosition=new int[0];data.nextEventAt=data.progressTicks+interval();data.setDirty();
        var advancement=player.server.getAdvancements().get(ResourceLocation.parse("farskies:journey_cache"));if(advancement!=null)player.getAdvancements().award(advancement,"supply");
    }
    static void end(MinecraftServer server,CampaignData data,boolean remove){
        if(active(data)&&remove){var p=WorldSetup.pos(data.eventPosition);if(server.overworld().getBlockEntity(p) instanceof BarrelBlockEntity barrel&&barrel.getName().getString().equals("Sky cache"))server.overworld().removeBlock(p,false);}
        data.eventPosition=new int[0];data.nextEventAt=data.progressTicks+interval();data.setDirty();
    }
    static void announce(MinecraftServer server,String message){server.getPlayerList().broadcastSystemMessage(Component.literal(message),false);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(PlayerInteractEvent.RightClickBlock event){
        if(!Campaign.enabled()||!(event.getEntity() instanceof ServerPlayer player))return;var data=CampaignData.get(player.server);
        if(!matches(event.getLevel(),event.getPos(),data))return;
        if(data.clock<data.eventOpensAt){event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);player.displayClientMessage(Component.literal(status(data)),true);}
        else if(!player.isShiftKeyDown())claimed(player,data);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breakCache(BlockEvent.BreakEvent event){
        if(!Campaign.enabled()||!(event.getPlayer() instanceof ServerPlayer player))return;var data=CampaignData.get(player.server);if(!matches(player.level(),event.getPos(),data))return;
        if(data.clock<data.eventOpensAt)event.setCanceled(true);else claimed(player,data);
    }
    @SubscribeEvent public static void blast(ExplosionEvent.Detonate event){
        if(!Campaign.enabled()||!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level))return;var data=CampaignData.get(level.getServer());
        if(active(data)&&data.clock<data.eventOpensAt&&level.dimension()==Level.OVERWORLD)event.getAffectedBlocks().remove(WorldSetup.pos(data.eventPosition));
    }
}
