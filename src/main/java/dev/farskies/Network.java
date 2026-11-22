package dev.farskies;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class Network {
    public record Status(int seconds,int bank,int state,int tier,long have,long need,int population,int redMembers,int blueMembers,boolean safe,boolean raidFatigue,int truceSeconds,String activity) implements CustomPacketPayload {
        public static final Type<Status> TYPE=new Type<>(ResourceLocation.parse("farskies:status"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Status> CODEC=StreamCodec.of((b,s)->{
            b.writeVarInt(s.seconds);b.writeVarInt(s.bank);b.writeVarInt(s.state);b.writeVarInt(s.tier);
            b.writeVarLong(s.have);b.writeVarLong(s.need);b.writeVarInt(s.population);b.writeVarInt(s.redMembers);b.writeVarInt(s.blueMembers);b.writeBoolean(s.safe);b.writeBoolean(s.raidFatigue);b.writeVarInt(s.truceSeconds);b.writeUtf(s.activity,256);
        },b->new Status(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarLong(),b.readVarLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readUtf(256)));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("9");
        registrar.playToClient(Status.TYPE,Status.CODEC,(data,context)->context.enqueueWork(()->dev.farskies.client.CampaignView.accept(data)));

    }
    static void clear() {}
    static void update(MinecraftServer server,CampaignData data) {
        int red=(int)data.players.values().stream().filter(a->a.faction==0).count();
        int blue=(int)data.players.values().stream().filter(a->a.faction==1).count();
        for(var player:server.getPlayerList().getPlayers()) {
            var a=data.account(player.getUUID());var presence=Campaign.PRESENCE.get(player.getUUID());int state=a.faction<0?3:0;
            if(state==0 && presence!=null && data.clock-presence.active>Settings.INSTANCE.activeSeconds*20L)state=1;
            if(state==0 && !Campaign.fits(player,Campaign.nextDrop(a,Progression.tier(data))))state=2;
            int seconds=Progression.seconds(data);
            PacketDistributor.sendToPlayer(player,new Status(seconds,a.pendingTier>=0?1:0,state,Progression.tier(data),Progression.drops(data),Progression.next(data),1,red,blue,Market.onLand(player.level(),player.blockPosition()),RaidRecovery.fatigued(player,data),OpeningTruce.remaining(data),SkyEvents.status(data)));

            player.connection.send(new ClientboundTabListPacket(Component.empty(),Progression.status(data).copy().append("\nYour drops: "+a.received+" | Red: "+red+" players | Blue: "+blue+" players").append(SkyEvents.status(data).isEmpty()?"":"\n"+SkyEvents.status(data))));
        }
    }
}
