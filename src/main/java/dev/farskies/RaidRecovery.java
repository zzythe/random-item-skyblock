package dev.farskies;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Returning defeated raiders mine slowly at the enemy home. Respawning never has a delay. */
public final class RaidRecovery {
    static boolean enemyHome(ServerPlayer player,CampaignData data){
        return enemyHome(player,data,player.blockPosition());
    }
    static boolean enemyHome(ServerPlayer player,CampaignData data,net.minecraft.core.BlockPos pos){
        int side=data.account(player.getUUID()).faction;
        return side>=0&&player.level().dimension()==Level.OVERWORLD&&pos.distSqr(WorldSetup.pos(data,1-side))<=48*48;
    }
    public static boolean fatigued(ServerPlayer player,CampaignData data){return data.account(player.getUUID()).fatigueUntil>data.clock&&enemyHome(player,data);}
    @SubscribeEvent public static void died(LivingDeathEvent event){
        if(!Campaign.enabled()||!(event.getEntity() instanceof ServerPlayer player))return;
        var data=CampaignData.get(player.server);var account=data.account(player.getUUID());account.graceUntil=0;
        if(enemyHome(player,data))account.fatigueUntil=data.clock+(account.fatigueUntil>data.clock?6:3)*60*20;
        data.setDirty();
    }
    @SubscribeEvent public static void speed(PlayerEvent.BreakSpeed event){
        if(!Campaign.enabled()||!(event.getEntity() instanceof ServerPlayer player))return;
        var data=CampaignData.get(player.server);
        if(fatigued(player,data)){
            event.setNewSpeed(event.getNewSpeed()*.25f);
            if(player.tickCount%20==0)player.displayClientMessage(Component.literal("Worn out: mining is slower near the enemy base."),true);
        }
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){
        if(Campaign.enabled()&&!event.isEndConquered()&&event.getEntity() instanceof ServerPlayer player){
            var data=CampaignData.get(player.server);data.account(player.getUUID()).graceUntil=data.clock+120;
        }
    }
    static void endGrace(net.minecraft.world.entity.player.Player player){
        if(Campaign.enabled()&&player instanceof ServerPlayer serverPlayer)CampaignData.get(serverPlayer.server).account(player.getUUID()).graceUntil=0;
    }
    @SubscribeEvent public static void attack(AttackEntityEvent event){endGrace(event.getEntity());}
    @SubscribeEvent public static void ranged(PlayerInteractEvent.RightClickItem event){
        if(event.getItemStack().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem||event.getItemStack().getItem() instanceof net.minecraft.world.item.TridentItem)endGrace(event.getEntity());
    }
    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> RESTRICTED=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,net.minecraft.resources.ResourceLocation.parse("farskies:raid_restricted"));
    static boolean restricted(net.minecraft.world.item.ItemStack stack){return stack.is(RESTRICTED);}
    static boolean blocked(ServerPlayer player,net.minecraft.core.BlockPos pos,net.minecraft.world.item.ItemStack stack){
        if(!Campaign.enabled()||!restricted(stack))return false;
        var data=CampaignData.get(player.server);
        return data.account(player.getUUID()).fatigueUntil>data.clock&&enemyHome(player,data,pos);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void destructiveItem(PlayerInteractEvent.RightClickItem event){
        if(event.getEntity() instanceof ServerPlayer player&&blocked(player,player.blockPosition(),event.getItemStack()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void destructiveBlock(PlayerInteractEvent.RightClickBlock event){
        if(event.getEntity() instanceof ServerPlayer player&&blocked(player,event.getPos(),event.getItemStack()))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void destructivePlacement(BlockEvent.EntityPlaceEvent event){
        if(event.getEntity() instanceof ServerPlayer player&&blocked(player,event.getPos(),new net.minecraft.world.item.ItemStack(event.getPlacedBlock().getBlock())))event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void cubeBreak(BlockEvent.BreakEvent event){
        if(event.getPlayer() instanceof ServerPlayer player&&blocked(player,event.getPos(),new net.minecraft.world.item.ItemStack(event.getState().getBlock())))event.setCanceled(true);
    }
    @SubscribeEvent public static void mine(PlayerInteractEvent.LeftClickBlock event){endGrace(event.getEntity());}
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent event){if(event.getEntity() instanceof net.minecraft.world.entity.player.Player player)endGrace(player);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void damage(LivingIncomingDamageEvent event){
        if(!Campaign.enabled())return;
        if(event.getSource().getEntity() instanceof ServerPlayer attacker)endGrace(attacker);
        if(event.getEntity() instanceof ServerPlayer player){
            var data=CampaignData.get(player.server);
            if(data.account(player.getUUID()).graceUntil>data.clock&&!event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY))event.setCanceled(true);
        }
    }
}
