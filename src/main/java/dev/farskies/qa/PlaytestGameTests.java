package dev.farskies;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("farskies") @PrefixGameTestTemplate(false)
public final class PlaytestGameTests {
    @GameTest(template="empty") public static void originalMarketCannotBeAssembledOrPushed(GameTestHelper h)throws Exception{
        boolean previous=Settings.INSTANCE.campaignEnabled;Settings.INSTANCE.campaignEnabled=true;var l=h.getLevel();var data=CampaignData.get(l.getServer());var pos=h.absolutePos(new BlockPos(3,1,2));
        try{
            l.setBlockAndUpdate(pos,Blocks.COBBLESTONE.defaultBlockState());
            var control=new com.simibubi.create.content.contraptions.bearing.BearingContraption(false,net.minecraft.core.Direction.UP);
            h.assertTrue(control.searchMovedStructure(l,pos,net.minecraft.core.Direction.UP),"Control contraption could not assemble an ordinary block");
            data.marketBlocks.add(pos.asLong());var protectedAssembly=new com.simibubi.create.content.contraptions.bearing.BearingContraption(false,net.minecraft.core.Direction.UP);
            h.assertTrue(!protectedAssembly.searchMovedStructure(l,pos,net.minecraft.core.Direction.UP),"A bearing assembled protected market terrain");
            l.setBlockAndUpdate(pos.west(),Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,net.minecraft.core.Direction.EAST));
            var piston=new net.neoforged.neoforge.event.level.PistonEvent.Pre(l,pos.west(),net.minecraft.core.Direction.EAST,net.neoforged.neoforge.event.level.PistonEvent.PistonMoveType.EXTEND);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(piston);h.assertTrue(piston.isCanceled(),"A piston can move the market foundation");
        }finally{data.marketBlocks.remove(pos.asLong());l.removeBlock(pos,false);l.removeBlock(pos.west(),false);Settings.INSTANCE.campaignEnabled=previous;}h.succeed();
    }
    @GameTest(template="empty") public static void usefulRollsStayRandomAndDecorationsStayRare(GameTestHelper h){
        int materials=0,decorations=0,stacks=0,advanced=0;var tiers=new HashMap<Item,Integer>();ItemDrops.entries().forEach(e->tiers.put(e.item(),e.tier()));
        for(int i=0;i<6000;i++){
            var low=ItemDrops.roll(477L,i,0);var high=ItemDrops.roll(477L,i,5);var kind=ItemDrops.kind(low.getItem());
            if(kind==ItemDrops.Kind.MATERIAL)materials++;if(kind==ItemDrops.Kind.DECORATION)decorations++;if(low.getCount()>1)stacks++;if(tiers.get(high.getItem())>=3)advanced++;
            h.assertTrue(ItemStack.matches(high,ItemDrops.roll(477L,i,5)),"A pending roll changed its contents");
        }
        h.assertTrue(materials>2400&&materials<3000,"Resource odds are not near 45%: "+materials);
        h.assertTrue(decorations>150&&decorations<500,"Decorative variants flooded the pool: "+decorations);
        h.assertTrue(stacks>2500,"Building materials still arrive as unusable single pieces");h.assertTrue(advanced>1000,"Later tiers rarely deliver advanced finds: "+advanced);
        FarSkies.LOG.info("ROLL_QA resources={} decor={} stacks={} advanced={}/6000",materials,decorations,stacks,advanced);h.succeed();
    }
    @GameTest(template="empty") public static void absentWoodIntegrationsNeverRoll(GameTestHelper h){
        int absent=0,supported=0;
        for(var item:net.minecraft.core.registries.BuiltInRegistries.ITEM){var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            if(ItemDrops.unavailableIntegration(id.getNamespace(),id.getPath())){absent++;h.assertTrue(ItemDrops.entries().stream().noneMatch(e->e.item()==item),"Unsupported integration can roll: "+id);}
            if(id.getNamespace().equals("exdeorum")&&java.util.Set.of("oak_sieve","oak_barrel","oak_crucible","flint_mesh","stone_barrel","porcelain_crucible").contains(id.getPath())){supported++;h.assertTrue(ItemDrops.entries().stream().anyMatch(e->e.item()==item),"Working Ex Deorum item was excluded: "+id);}
        }
        h.assertTrue(absent>=80&&supported==6,"Compatibility coverage is incomplete: "+absent+" / "+supported);h.succeed();
    }
    @GameTest(template="empty") public static void handSieveMakesMetalWithoutCreate(GameTestHelper h){
        var l=h.getLevel();var pos=h.absolutePos(new BlockPos(2,1,2));l.setBlockAndUpdate(pos,net.minecraft.world.level.block.Block.byItem(Campaign.item("exdeorum:oak_sieve")).defaultBlockState());
        var sieve=(thedarkcolour.exdeorum.blockentity.AbstractSieveBlockEntity)l.getBlockEntity(pos);var logic=sieve.getLogic();logic.setMesh(l.registryAccess(),new ItemStack(Campaign.item("exdeorum:flint_mesh")));
        h.assertTrue(logic.isValidInput(new ItemStack(Items.GRAVEL)),"Gravel is not a valid manual input");
        for(int i=0;i<128;i++){logic.startSifting(new ItemStack(Items.GRAVEL));logic.sift(2,l.getGameTime()+i+1);}
        int metal=l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(pos).inflate(3)).stream().filter(e->e.getItem().is(Campaign.item("exdeorum:iron_ore_chunk"))).mapToInt(e->e.getItem().getCount()).sum();
        h.assertTrue(metal>0,"Manual gravel sifting did not make any iron");l.removeBlock(pos,false);h.succeed();
    }
    @GameTest(template="empty") public static void offlineRosterAndNewStatePersist(GameTestHelper h){
        var d=new CampaignData();for(int i=0;i<4;i++){var a=d.account(UUID.randomUUID());a.faction=i<3?0:1;a.lastActiveMillis=1;}
        h.assertTrue(Campaign.randomSide(d,net.minecraft.util.RandomSource.create(5))==1,"Inactive members disappeared from team balance");
        var a=d.players.values().iterator().next();a.clearBedOnLogin=true;a.fatigueUntil=500;a.pendingTier=2;
        d.progressTicks=54000;d.eventPosition=new int[]{3,150,-90};d.nextEventAt=80000;d.eventOpensAt=200;d.eventExpiresAt=300;d.eventIndex=5;d.cacheWins[0]=2;d.checkedIslands.add("old_island");
        var loaded=CampaignData.load(d.save(new CompoundTag(),h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.progressTicks==54000&&loaded.eventIndex==5&&loaded.cacheWins[0]==2&&loaded.checkedIslands.contains("old_island")&&loaded.eventPosition[2]==-90,"Shared world state did not persist");
        var id=d.players.entrySet().stream().filter(e->e.getValue()==a).findFirst().orElseThrow().getKey();var b=loaded.players.get(id);h.assertTrue(b.faction==a.faction&&b.clearBedOnLogin&&b.fatigueUntil==500&&b.pendingTier==2,"Offline swap or recovery state was lost");h.succeed();
    }
    @GameTest(template="empty") public static void sharedDeliveryCycleAndPendingRollStayInSync(GameTestHelper h){
        var d=new CampaignData();long interval=Settings.INSTANCE.intervalSeconds*20L;
        for(int i=0;i<12;i++)d.account(UUID.randomUUID()).faction=i%2;
        d.progressTicks=interval-20;h.assertTrue(!Campaign.advanceCycle(d,false)&&Progression.drops(d)==0,"An unattended server advances tiers");
        h.assertTrue(Campaign.advanceCycle(d,true)&&Progression.drops(d)==1&&Progression.seconds(d)==Settings.INSTANCE.intervalSeconds,"One cycle did not advance exactly once");
        h.assertTrue(!Campaign.advanceCycle(d,true)&&Progression.drops(d)==1,"A cycle fired twice");
        d.progressTicks=interval-100;d.account(UUID.randomUUID());h.assertTrue(Progression.seconds(d)==5,"Late joiner changed the shared countdown");
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"DeliveryQA"));var a=d.account(p.getUUID());a.faction=0;
        for(int i=0;i<p.getInventory().items.size();i++)p.getInventory().items.set(i,new ItemStack(Items.BARRIER,64));
        h.assertTrue(!Campaign.deliver(p,d)&&a.received==0&&a.pendingTier==0,"Overflow consumed or rerolled the delivery");
        var held=Campaign.nextDrop(a,0).getFirst();d.progressTicks=Settings.INSTANCE.tierThresholds[5]*interval;
        h.assertTrue(ItemStack.matches(held,Campaign.nextDrop(a,5).getFirst()),"A held roll changed on tier-up");
        p.getInventory().clearContent();h.assertTrue(Campaign.deliver(p,d)&&a.received==1&&a.pendingTier==-1,"A held delivery could not resume exactly once");
        h.assertTrue(p.getInventory().contains(held),"Delivered stack differs from the held roll");p.getInventory().clearContent();h.succeed();
    }
    @GameTest(template="empty") public static void defeatedRaidersMineSlowlyButDefendersAndRespawningStayFree(GameTestHelper h){
        boolean previous=Settings.INSTANCE.campaignEnabled;Settings.INSTANCE.campaignEnabled=true;var l=h.getLevel();var d=CampaignData.get(l.getServer());long clock=d.clock;int[] red=d.spawns[0],blue=d.spawns[1];d.spawns[0]=new int[]{-80,151,0};d.spawns[1]=new int[]{80,151,0};
        var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"RecoveryQA"));var attacker=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"AttackerQA"));
        try{var a=d.account(p.getUUID());a.faction=0;var enemy=WorldSetup.pos(d,1);p.setPos(enemy.getX()+.5,enemy.getY(),enemy.getZ()+.5);d.clock=100;
            RaidRecovery.died(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p,p.damageSources().playerAttack(attacker)));
            h.assertTrue(a.fatigueUntil==3700&&RaidRecovery.fatigued(p,d),"A defeated returning raider has no local fatigue");
            var slow=new net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed(p,Blocks.STONE.defaultBlockState(),8,p.blockPosition());RaidRecovery.speed(slow);h.assertTrue(slow.getNewSpeed()==2,"Raider mining is not quarter speed");
            var home=WorldSetup.pos(d,0);p.setPos(home.getX()+.5,home.getY(),home.getZ()+.5);d.clock=300;
            RaidRecovery.died(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(p,p.damageSources().playerAttack(attacker)));h.assertTrue(a.fatigueUntil==3700&&!RaidRecovery.fatigued(p,d),"Defending home adds a penalty");
            var normal=new net.neoforged.neoforge.event.entity.player.PlayerEvent.BreakSpeed(p,Blocks.STONE.defaultBlockState(),8,p.blockPosition());RaidRecovery.speed(normal);h.assertTrue(normal.getNewSpeed()==8,"Mining outside the enemy base is slowed");
            RaidRecovery.respawn(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(p,false));h.assertTrue(a.graceUntil==420,"Respawn grace is not six seconds");
            RaidRecovery.attack(new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(p,attacker));h.assertTrue(a.graceUntil==0,"A protected player can attack without ending grace");
            p.setPos(enemy.getX()+.5,enemy.getY(),enemy.getZ()+.5);d.clock=3700;h.assertTrue(!RaidRecovery.fatigued(p,d),"Fatigue never expires");
        }finally{d.clock=clock;d.spawns[0]=red;d.spawns[1]=blue;d.players.remove(p.getUUID());Settings.INSTANCE.campaignEnabled=previous;}h.succeed();
    }
    @GameTest(template="empty") public static void cacheIsSealedThenClaimableAndDoesNotRefill(GameTestHelper h){
        boolean previous=Settings.INSTANCE.campaignEnabled;Settings.INSTANCE.campaignEnabled=true;var l=h.getLevel();var d=CampaignData.get(l.getServer());var pos=h.absolutePos(new BlockPos(2,2,2));
        var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"CacheQA"));d.account(p.getUUID()).faction=0;
        try{SkyEvents.end(l.getServer(),d,true);h.assertTrue(SkyEvents.openAt(l.getServer(),pos)==1,"Cache could not spawn on existing terrain");
            var click=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));
            SkyEvents.use(click);h.assertTrue(click.isCanceled(),"Cache opens before the contest begins");int wins=d.cacheWins[0];d.eventOpensAt=d.clock;
            click=new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(p,net.minecraft.world.InteractionHand.MAIN_HAND,pos,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));SkyEvents.use(click);
            h.assertTrue(!click.isCanceled()&&!SkyEvents.active(d)&&d.cacheWins[0]==wins+1,"Open cache did not resolve to the claiming team");var barrel=(BarrelBlockEntity)l.getBlockEntity(pos);barrel.unpackLootTable(p);h.assertTrue(!barrel.isEmpty(),"Contested reward is empty");SkyEvents.use(click);h.assertTrue(d.cacheWins[0]==wins+1,"A claimed cache can farm wins");
        }finally{l.removeBlock(pos,false);d.players.remove(p.getUUID());Settings.INSTANCE.campaignEnabled=previous;}h.succeed();
    }
}
