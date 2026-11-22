package dev.farskies.qa;

import dev.farskies.*;
import dev.farskies.mixin.GuiHudAccessor;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Capture the real selected-item label while it is still visible, with native HUD combinations. */
public final class HudSmoke {
    private static int stage,ticks;
    private static volatile boolean prepared;
    private static long sleepProgress;
    public static void register(){Minecraft.getInstance().execute(()->org.lwjgl.glfw.GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(HudSmoke::tick);}
    private static void next(int n){stage=n;ticks=0;FarSkies.LOG.info("HUD_QA stage {}",n);}
    private static void select(Minecraft mc,int scale,boolean armour,boolean message,boolean underwater){
        mc.options.guiScale().set(scale);mc.resizeDisplay();prepared=false;mc.player.getInventory().selected=1;
        mc.getSingleplayerServer().execute(()->{
            var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.removeAllEffects();
            for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})p.setItemSlot(slot,ItemStack.EMPTY);
            if(armour){p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.DIAMOND_CHESTPLATE));p.setItemSlot(EquipmentSlot.LEGS,new ItemStack(Items.DIAMOND_LEGGINGS));p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,12000,2,false,false));}
            p.getInventory().setItem(0,new ItemStack(Items.STRIPPED_MANGROVE_LOG));p.getInventory().setItem(1,new ItemStack(Items.DIAMOND_PICKAXE));p.getInventory().selected=1;p.inventoryMenu.broadcastChanges();
            if(underwater){for(var pos:net.minecraft.core.BlockPos.betweenClosed(-2,151,-9,2,154,-5))s.overworld().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());p.setAirSupply(200);}
            else{for(var pos:net.minecraft.core.BlockPos.betweenClosed(-2,151,-9,2,154,-5))s.overworld().setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());p.setAirSupply(300);}
            p.teleportTo(s.overworld(),.5,151,-6.5,0,16);prepared=true;
        });
    }
    private static void shot(Minecraft mc,String name,boolean itemName,boolean message){
        var hud=(GuiHudAccessor)mc.gui;
        if(name.equals("scale2-item")&&mc.player.getAirSupply()!=300)throw new IllegalStateException("Plain HUD fixture still has air bubbles");
        if(itemName&&hud.farskies$toolHighlightTimer()<=0)throw new IllegalStateException("Selected-item label expired before screenshot");
        if(message&&hud.farskies$overlayMessageTime()<=0)throw new IllegalStateException("Action-bar message expired before screenshot");
        Screenshot.grab(mc.gameDirectory,"random-item-skyblock-0.6.1-hud-"+name+".png",mc.getMainRenderTarget(),c->{});
        FarSkies.LOG.info("HUD_QA PASS {} itemTimer={} messageTimer={} air={}",name+" guiScale="+mc.getWindow().getGuiScale(),hud.farskies$toolHighlightTimer(),hud.farskies$overlayMessageTime(),mc.player.getAirSupply());
    }
    static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();ticks++;mc.options.pauseOnLostFocus=false;
        try{
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1920,1080);next(1);mc.createWorldOpenFlows().openWorld(System.getProperty("farskies.reviewWorld"),()->mc.setScreen(new TitleScreen()));}
            else if(stage==1&&mc.player!=null&&mc.screen==null&&ticks>80){select(mc,2,false,false,false);next(2);}
            else if(stage==2&&prepared&&ticks>10){mc.player.getInventory().selected=0;next(3);}
            else if(stage==3&&ticks==8){shot(mc,"scale2-item",true,false);}
            else if(stage==3&&ticks>65){shot(mc,"scale2-expired",false,false);select(mc,4,true,false,false);next(4);}
            else if(stage==4&&prepared&&ticks>10){mc.player.getInventory().selected=0;next(5);}
            else if(stage==5&&ticks==8){shot(mc,"scale4-armour-item",true,false);}
            else if(stage==5&&ticks>12){mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.literal("Worn out: mining is slower near the enemy base."),false);mc.player.getInventory().selected=1;next(6);}
            else if(stage==6&&ticks>8){shot(mc,"scale4-message-item",true,true);mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(),false);select(mc,2,true,false,true);next(7);}
            else if(stage==7&&prepared&&ticks>10){mc.player.getInventory().selected=0;next(8);}
            else if(stage==8&&ticks>8){shot(mc,"scale2-air-item",true,false);FarSkies.LOG.info("HUD_QA COMPLETE");next(9);}
            else if(stage==9&&ticks>20){
                prepared=false;
                mc.getSingleplayerServer().execute(()->{
                    var s=mc.getSingleplayerServer();var l=s.overworld();var p=s.getPlayerList().getPlayers().getFirst();var d=CampaignData.get(s);sleepProgress=d.progressTicks;
                    int x=dev.farskies.WorldSetup.pos(d,d.account(p.getUUID()).faction).getX();
                    var foot=new net.minecraft.core.BlockPos(x,151,3);var facing=net.minecraft.core.Direction.NORTH;
                    for(var pos:net.minecraft.core.BlockPos.betweenClosed(x-2,150,0,x+2,150,5))l.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState());
                    var bed=net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING,facing);
                    l.setBlockAndUpdate(foot,bed);l.setBlockAndUpdate(foot.relative(facing),bed.setValue(net.minecraft.world.level.block.BedBlock.PART,net.minecraft.world.level.block.state.properties.BedPart.HEAD));
                    l.setDayTime(14000);l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(true,s);p.removeAllEffects();p.teleportTo(l,x+.5,151,4.5,180,0);
                    for(var mob:l.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,new net.minecraft.world.phys.AABB(foot).inflate(16)))mob.discard();
                    var reward=chanceCubes.registry.global.GlobalCCRewardRegistry.DEFAULT.getRewardByName("chancecubes:redstone_diamond");
                    if(reward==null)throw new IllegalStateException("Native Chance Cubes sample is missing");
                    chanceCubes.registry.global.GlobalCCRewardRegistry.triggerReward(reward,l,foot,p);
                    var result=p.startSleepInBed(foot);FarSkies.LOG.info("POLISH_QA sleep result={} sleeping={}",result,p.isSleeping());prepared=true;
                });next(10);
            }
            else if(stage==10&&prepared&&mc.player.isSleeping()&&ticks>3){
                if(mc.screen==null)throw new IllegalStateException("No native sleep screen");
                Screenshot.grab(mc.gameDirectory,"random-item-skyblock-0.6.1-sleep.png",mc.getMainRenderTarget(),c->{});next(11);
            }
            else if(stage==11&&!mc.player.isSleeping()&&mc.screen==null&&ticks>20){
                var d=CampaignData.get(mc.getSingleplayerServer());if(d.progressTicks-sleepProgress>600)throw new IllegalStateException("Sleep fast-forwarded the shared progression clock");
                FarSkies.LOG.info("POLISH_QA PASS native reward on real island; sleep transition; progressDelta={}",d.progressTicks-sleepProgress);
                mc.stop();next(99);
            }
            if(ticks>600&&stage!=99)throw new IllegalStateException("HUD QA timed out at "+stage);
        }catch(Exception failure){FarSkies.LOG.error("HUD_QA FAILED",failure);mc.stop();next(99);}
    }
}
