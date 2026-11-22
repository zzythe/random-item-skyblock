package dev.farskies;

import java.util.HashMap;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class Factions {
    static void clear(){}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if(!Campaign.enabled() || !(event.getEntity() instanceof ServerPlayer player))return;
        var d=CampaignData.get(player.server);int side=d.account(player.getUUID()).faction;if(side<0)return;
        var bed=player.getRespawnPosition();if(bed!=null && bed.distSqr(player.blockPosition())<36)return;
        var pos=WorldSetup.recoverRally(player.server.overworld(),d,side);Campaign.teleport(player,pos==null?WorldSetup.pos(d,2):pos);
    }
}
