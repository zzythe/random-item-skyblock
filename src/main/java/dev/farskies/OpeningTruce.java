package dev.farskies;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** A saved active-play clock gives each new campaign one preparation period. */
public final class OpeningTruce {
    public static int remaining(CampaignData data){return (int)Math.max(0,(Settings.INSTANCE.truceMinutes*1200L-data.progressTicks+19)/20);}
    static boolean enemy(ServerPlayer player,BlockPos pos){
        return Campaign.enabled()&&RaidRecovery.enemyHome(player,CampaignData.get(player.server),pos);
    }
    static boolean protectedFrom(ServerPlayer player,BlockPos pos){return enemy(player,pos)&&remaining(CampaignData.get(player.server))>0;}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breaking(BlockEvent.BreakEvent event){
        if(event.getPlayer() instanceof ServerPlayer player&&protectedFrom(player,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void right(PlayerInteractEvent.RightClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player&&protectedFrom(player,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void left(PlayerInteractEvent.LeftClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player&&protectedFrom(player,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void place(BlockEvent.EntityPlaceEvent event){
        if(event.getEntity() instanceof ServerPlayer player&&protectedFrom(player,event.getPos()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void item(PlayerInteractEvent.RightClickItem event){
        if(event.getEntity() instanceof ServerPlayer player&&protectedFrom(player,player.blockPosition())&&RaidRecovery.restricted(event.getItemStack()))event.setCanceled(true);
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event){
        if(!Campaign.enabled()||!(event.getExplosion().getIndirectSourceEntity() instanceof ServerPlayer player)||remaining(CampaignData.get(player.server))==0)return;
        event.getAffectedBlocks().removeIf(pos->enemy(player,pos));
    }
}
