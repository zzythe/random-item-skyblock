package dev.farskies.qa;

import dev.farskies.FarSkies;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Focused shader/boundary and authored-landmark visual pass. Never distributed. */
public final class VisualSmoke {
    private static int stage,ticks;
    public static void register(){Minecraft.getInstance().execute(()->org.lwjgl.glfw.GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(VisualSmoke::tick);}
    private static void next(int n){stage=n;ticks=0;FarSkies.LOG.info("VISUAL_QA stage {}",n);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"random-item-skyblock-0.6.1-"+name+".png",mc.getMainRenderTarget(),c->{});}
    static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();ticks++;mc.options.pauseOnLostFocus=false;
        try{
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){next(1);mc.createWorldOpenFlows().openWorld(System.getProperty("farskies.reviewWorld"),()->mc.setScreen(new TitleScreen()));}
            else if(stage==1&&mc.screen instanceof ConfirmScreen screen){screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().matches(".*(Proceed|Yes|Continue).*?")).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().ifPresent(net.minecraft.client.gui.components.Button::onPress);}
            else if(stage==1&&mc.player!=null&&mc.screen==null&&dev.farskies.client.CampaignView.status!=null){mc.options.guiScale().set(2);mc.resizeDisplay();mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.ABSORPTION,12000,2,false,false));var pos=dev.farskies.WorldSetup.pos(dev.farskies.CampaignData.get(s),2);p.teleportTo(s.overworld(),pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,5);});next(2);}
            else if(stage==2&&ticks>60){if(!dev.farskies.client.CampaignView.status.safe())throw new IllegalStateException("Market land is not safe");shot(mc,"market-safe-shaders");var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,false);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);next(3);}
            else if(stage==3&&ticks>60){shot(mc,"market-safe-vanilla");var iris=Class.forName("net.irisshaders.iris.Iris");var config=iris.getMethod("getIrisConfig").invoke(null);config.getClass().getMethod("setShadersEnabled",boolean.class).invoke(config,true);config.getClass().getMethod("save").invoke(config);iris.getMethod("reload").invoke(null);next(4);
                mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();p.teleportTo(s.overworld(),.5,151,-6.5,0,16);});
            }
            else if(stage==4&&ticks>60){if(dev.farskies.client.CampaignView.status.safe())throw new IllegalStateException("Player bridge is incorrectly safe");shot(mc,"market-bridge-unsafe");next(6);
                mc.getSingleplayerServer().execute(()->{var s=mc.getSingleplayerServer();var l=s.overworld();var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.STRUCTURE,net.minecraft.resources.ResourceLocation.parse("farskies:soaring_diorite_house"));var holder=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getHolderOrThrow(key);var found=l.getChunkSource().getGenerator().findNearestMapStructure(l,net.minecraft.core.HolderSet.direct(holder),new net.minecraft.core.BlockPos(400,140,200),40,false);if(found==null)throw new IllegalStateException("Native Moog island absent");var target=found.getFirst();
                    for(int x=(target.getX()>>4)-4;x<=(target.getX()>>4)+4;x++)for(int z=(target.getZ()>>4)-4;z<=(target.getZ()>>4)+4;z++)l.getChunk(x,z);
                    var p=s.getPlayerList().getPlayers().getFirst();p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);p.teleportTo(l,target.getX()+.5,215,target.getZ()-120.0,0,32);FarSkies.LOG.info("VISUAL_QA native Moog island {}",target);});
            }
            else if(stage==6&&ticks>120){shot(mc,"large-native-island");FarSkies.LOG.info("VISUAL_QA COMPLETE");next(5);}
            else if(stage==5&&ticks>20){mc.stop();next(99);}
        }catch(Exception error){FarSkies.LOG.error("VISUAL_QA FAILED",error);mc.stop();next(99);}
    }
}
