package dev.farskies.client;

import dev.farskies.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid="farskies",value=Dist.CLIENT)
public final class CampaignView {
    public static Network.Status status;
    private static long receivedAt;
    public static void accept(Network.Status data){status=data;receivedAt=System.currentTimeMillis();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){status=null;}
    @SubscribeEvent public static void breakSpeed(net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed event){
        if(event.getEntity()==Minecraft.getInstance().player&&status!=null&&status.raidFatigue())event.setNewSpeed(event.getNewSpeed()*.25f);
    }
    @SubscribeEvent public static void cubeTooltip(ItemTooltipEvent event) {
        if(event.getItemStack().is(chanceCubes.items.CCubesItems.CHANCE_CUBE.get()))
            event.getToolTip().removeIf(c->c.getString().startsWith("Warning: It is recommended"));
    }
    private static float fontScale(Minecraft mc) {
        double scale=mc.getWindow().getGuiScale();
        return (float)(Math.max(1,Math.round(scale*.75))/scale);
    }
    public static void hud(GuiGraphics gui,net.minecraft.client.DeltaTracker partial) {
        var mc=Minecraft.getInstance();var s=status;
        if(s==null || s.state()==3 || mc.player==null || mc.player.isSpectator() || mc.options.hideGui || mc.screen!=null)return;
        int seconds=s.state()==0?Math.max(0,s.seconds()-(int)((System.currentTimeMillis()-receivedAt)/1000)):s.seconds();
        String next=s.state()==1?"Drops paused":s.state()==2?"Inventory full":"Drop in "+String.format("%d:%02d",seconds/60,seconds%60);
        String progress=s.tier()==5?"All tiers unlocked":Math.max(0,s.need()-s.have())+" drops to Tier "+(s.tier()+2);
        String text=next+"  \u00b7  "+progress;
        float scale=Math.min(fontScale(mc),(gui.guiWidth()-24f)/mc.font.width(text));
        int height=(int)Math.ceil(mc.font.lineHeight*scale)+3;
        int occupied=Math.max(mc.gui.leftHeight,mc.gui.rightHeight);
        // NeoForge places the selected-item name using these heights. Render after it,
        // otherwise reserving our row moves the item name onto the row we just drew.
        var nativeHud=(dev.farskies.mixin.GuiHudAccessor)mc.gui;
        int clearTop=gui.guiHeight()-occupied;
        if(nativeHud.farskies$toolHighlightTimer()>0&&!mc.player.getMainHandItem().isEmpty()){
            int itemNameTop=gui.guiHeight()-Math.max(occupied,59)+(mc.gameMode.canHurtPlayer()?0:14);
            clearTop=Math.min(clearTop,itemNameTop);
        }
        int y=clearTop-height;
        gui.pose().pushPose();gui.pose().scale(scale,scale,1);
        int x=(int)((gui.guiWidth()/scale-mc.font.width(text))/2);
        gui.drawString(mc.font,next,x,(int)(y/scale),0xEEEEEE,true);
        gui.drawString(mc.font,"  \u00b7  "+progress,x+mc.font.width(next),(int)(y/scale),0xB8B8B8,true);
        gui.pose().popPose();
        if(s.truceSeconds()>0){
            // Top-left avoids Jade's centered block tooltip and native boss bars.
            gui.pose().pushPose();gui.pose().scale(scale,scale,1);
            gui.drawString(mc.font,"Opening truce  "+String.format("%d:%02d",s.truceSeconds()/60,s.truceSeconds()%60),(int)(8/scale),(int)(8/scale),0xE3D8A5,true);
            gui.pose().popPose();
        }
        // Later native action-bar messages also use these heights and stack above our row.
        mc.gui.leftHeight=Math.max(mc.gui.leftHeight,gui.guiHeight()-y);
        mc.gui.rightHeight=Math.max(mc.gui.rightHeight,gui.guiHeight()-y);
    }
}
