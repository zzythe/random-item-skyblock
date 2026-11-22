package dev.farskies.qa;

import dev.farskies.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.*;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Native immediate respawning and temporary damage grace, using the disposable review save. */
public final class RecoverySmoke {
    private static int stage,ticks;
    private static volatile boolean verified;
    private static volatile Throwable failure;
    public static void register(){Minecraft.getInstance().execute(()->org.lwjgl.glfw.GLFW.glfwHideWindow(Minecraft.getInstance().getWindow().getWindow()));net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(RecoverySmoke::tick);}
    private static void next(int value){stage=value;ticks=0;FarSkies.LOG.info("RECOVERY_QA stage {}",stage);}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);FarSkies.LOG.info("RECOVERY_QA PASS {}",message);}
    private static Button button(Minecraft mc){return mc.screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals("Respawn")).map(c->(Button)c).findFirst().orElseThrow();}
    private static net.minecraft.server.level.ServerPlayer attacker(net.minecraft.server.MinecraftServer s){
        var attacker=net.neoforged.neoforge.common.util.FakePlayerFactory.get(s.overworld(),new com.mojang.authlib.GameProfile(java.util.UUID.fromString("1645b468-c754-43fb-8592-215fbd77b077"),"RecoveryAttacker"));attacker.setPos(.5,151,-8.5);xaero.pac.common.server.api.OpenPACServerAPI.get(s).getChunkProtection().giveFullPass(attacker.getUUID());return attacker;
    }
    private static void die(Minecraft mc){mc.getSingleplayerServer().execute(()->{try{
        var s=mc.getSingleplayerServer();var p=s.getPlayerList().getPlayers().getFirst();var d=CampaignData.get(s);var a=d.account(p.getUUID());a.faction=0;a.graceUntil=0;
        var home=WorldSetup.pos(d,1);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(s.overworld(),home.getX()+.5,home.getY(),home.getZ()+.5,0,0);p.removeAllEffects();p.setHealth(20);p.invulnerableTime=0;
        check(p.hurt(p.damageSources().playerAttack(attacker(s)),10000)&&p.isDeadOrDying(),"Enemy-base death succeeds");check(a.fatigueUntil>d.clock,"Enemy-base death marks returning raider");
    }catch(Throwable t){failure=t;}});}
    static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();ticks++;mc.options.pauseOnLostFocus=false;
        try{
            if(failure!=null)throw new IllegalStateException(failure);
            if(stage==0&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){next(1);mc.createWorldOpenFlows().openWorld(System.getProperty("farskies.reviewWorld"),()->mc.setScreen(new TitleScreen()));}
            else if(stage==1&&mc.player!=null&&mc.screen==null&&ticks>140){next(2);die(mc);}
            else if(stage==2&&mc.screen instanceof DeathScreen&&ticks>45){check(button(mc).active,"Defeated raider uses normal immediate respawn");button(mc).onPress();next(3);}
            else if(stage==3&&mc.player!=null&&mc.player.isAlive()&&mc.screen==null&&ticks>80){next(4);mc.getSingleplayerServer().execute(()->{try{
                var s=mc.getSingleplayerServer();var d=CampaignData.get(s);var p=s.getPlayerList().getPlayers().getFirst();var a=d.account(p.getUUID());p.teleportTo(s.overworld(),.5,151,-6.5,0,0);p.setHealth(20);p.invulnerableTime=0;
                check(a.graceUntil>d.clock,"Native respawn grants six-second grace");p.hurt(p.damageSources().playerAttack(attacker(s)),5);check(p.getHealth()==20,"Grace blocks spawn camping damage");
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(p,attacker(s)));check(a.graceUntil==0,"Attacking ends respawn grace");
                p.invulnerableTime=0;check(p.hurt(p.damageSources().playerAttack(attacker(s)),5)&&p.getHealth()<20,"Damage resumes after attacking");verified=true;
            }catch(Throwable t){failure=t;}});}
            else if(stage==4&&verified&&ticks>140){next(5);die(mc);}
            else if(stage==5&&mc.screen instanceof DeathScreen&&ticks>45){check(button(mc).active,"Repeated raid death still allows immediate respawn");button(mc).onPress();next(6);}
            else if(stage==6&&ticks>40&&mc.player!=null&&mc.player.isAlive()&&mc.screen==null){FarSkies.LOG.info("RECOVERY_QA COMPLETE");mc.stop();next(99);}
            if(ticks>600&&stage!=99)throw new IllegalStateException("Recovery QA timed out at stage "+stage);
        }catch(Exception problem){FarSkies.LOG.error("RECOVERY_QA FAILED",problem);mc.stop();next(99);}
    }
}
