package dev.farskies;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("farskies") @PrefixGameTestTemplate(false)
public final class SandboxUpdateGameTests {
    @GameTest(template="empty") public static void truceExpiresAndReturningRaidersKeepMovementTools(GameTestHelper h){
        var l=h.getLevel();var d=CampaignData.get(l.getServer());boolean enabled=Settings.INSTANCE.campaignEnabled;int minutes=Settings.INSTANCE.truceMinutes;
        long progress=d.progressTicks,clock=d.clock;int[] red=d.spawns[0],blue=d.spawns[1];
        var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"TruceQA"));var victim=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"TruceVictimQA"));
        try{
            Settings.INSTANCE.campaignEnabled=true;Settings.INSTANCE.truceMinutes=30;d.progressTicks=0;d.clock=100;
            d.spawns[0]=new int[]{-80,151,0};d.spawns[1]=new int[]{80,151,0};d.account(p.getUUID()).faction=0;d.account(victim.getUUID()).faction=1;
            var enemy=WorldSetup.pos(d,1);p.setPos(80,151,0);victim.setPos(80,151,0);
            h.assertTrue(OpeningTruce.remaining(d)==1800&&OpeningTruce.protectedFrom(p,enemy),"Opening preparation is absent");
            var hit=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(victim,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().playerAttack(p),3));Market.damage(hit);h.assertTrue(hit.isCanceled(),"Truce allows melee");
            var arrow=net.minecraft.world.entity.EntityType.ARROW.create(l);arrow.setOwner(p);
            hit=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(victim,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().arrow(arrow,p),3));Market.damage(hit);h.assertTrue(hit.isCanceled(),"Truce allows arrows");
            var block=new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(l,enemy,Blocks.STONE.defaultBlockState(),p);OpeningTruce.breaking(block);h.assertTrue(block.isCanceled(),"Enemy blocks can be mined during truce");
            h.assertTrue(!OpeningTruce.protectedFrom(p,WorldSetup.pos(d,0)),"Own base is restricted");
            d.progressTicks=30*1200;h.assertTrue(OpeningTruce.remaining(d)==0&&!OpeningTruce.protectedFrom(p,enemy),"Truce never expires");
            hit=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(victim,new net.neoforged.neoforge.common.damagesource.DamageContainer(p.damageSources().playerAttack(p),3));Market.damage(hit);h.assertTrue(!hit.isCanceled(),"Truce still blocks later PvP");
            RaidRecovery.died(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p,p.damageSources().playerAttack(victim)));
            h.assertTrue(d.account(p.getUUID()).fatigueUntil==3700,"First raid death is not three minutes");
            RaidRecovery.died(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p,p.damageSources().playerAttack(victim)));
            h.assertTrue(d.account(p.getUUID()).fatigueUntil==7300,"Repeat raid death is not six minutes");
            h.assertTrue(RaidRecovery.blocked(p,enemy,new ItemStack(Items.LAVA_BUCKET))&&RaidRecovery.blocked(p,enemy,Shop.cube(-75)),"Fatigued raiders can still use local destructive items");
            h.assertTrue(!RaidRecovery.blocked(p,enemy,new ItemStack(Items.WATER_BUCKET))&&!RaidRecovery.blocked(p,enemy,new ItemStack(Items.COBBLESTONE)),"Water landings or bridging are blocked");
            h.assertTrue(!RaidRecovery.blocked(p,WorldSetup.pos(d,0),new ItemStack(Items.TNT)),"Defending home is restricted");
        }finally{Settings.INSTANCE.campaignEnabled=enabled;Settings.INSTANCE.truceMinutes=minutes;d.progressTicks=progress;d.clock=clock;d.spawns[0]=red;d.spawns[1]=blue;d.players.remove(p.getUUID());d.players.remove(victim.getUUID());Market.clear();}h.succeed();
    }
    @GameTest(template="empty") public static void directRewardEditsLeaveMarketIntact(GameTestHelper h){
        var l=h.getLevel();var d=CampaignData.get(l.getServer());boolean enabled=Settings.INSTANCE.campaignEnabled,initialized=d.initialized;var pos=h.absolutePos(new BlockPos(2,1,2));
        try{
            Settings.INSTANCE.campaignEnabled=true;l.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());d.initialized=true;d.marketBlocks.add(pos.asLong());
            h.assertTrue(!l.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState())&&l.getBlockState(pos).is(Blocks.STONE),"Direct native reward destroyed market land");
            h.assertTrue(l.setBlockAndUpdate(pos.east(),Blocks.GOLD_BLOCK.defaultBlockState()),"Native world edits are blocked outside market land");
        }finally{d.marketBlocks.remove(pos.asLong());d.initialized=initialized;l.removeBlock(pos,false);l.removeBlock(pos.east(),false);Settings.INSTANCE.campaignEnabled=enabled;}h.succeed();
    }
}
