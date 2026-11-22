package dev.farskies;

import chanceCubes.registry.global.GlobalCCRewardRegistry;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("farskies")
@PrefixGameTestTemplate(false)
public final class IntegrationGameTests {
    @GameTest(template="empty") public static void tiersAdvanceOnceForTheWholeServer(GameTestHelper helper) {
        var data=new CampaignData();var a=data.account(UUID.randomUUID());var b=data.account(UUID.randomUUID());
        data.progressTicks=(Settings.INSTANCE.tierThresholds[1]-1)*Settings.INSTANCE.intervalSeconds*20L;
        a.received=3000;b.received=10;data.receipts=100000;
        helper.assertTrue(Progression.tier(data)==0&&Progression.next(data)-Progression.drops(data)==1,"Personal totals changed the server tier");
        data.progressTicks+=Settings.INSTANCE.intervalSeconds*20L;
        helper.assertTrue(Progression.tier(data)==1,"Shared active clock did not unlock Tier 2");
        for(int i=0;i<12;i++)data.account(UUID.randomUUID()).received=2400;
        helper.assertTrue(Progression.tier(data)==1,"Roster size accelerated the tier clock");helper.succeed();
    }

    @GameTest(template="empty") public static void randomAssignmentsStayBalanced(GameTestHelper helper) {
        var data=new CampaignData();var random=net.minecraft.util.RandomSource.create(7613);
        boolean redFirst=false,blueFirst=false;
        for(int i=0;i<40;i++) {
            int side=Campaign.randomSide(data,random);var assigned=data.account(UUID.randomUUID());assigned.faction=side;assigned.lastActiveMillis=System.currentTimeMillis();
            long red=data.players.values().stream().filter(a->a.faction==0).count();
            helper.assertTrue(Math.abs(red*2-data.players.size())<=1,"Random assignment unbalanced teams");
            if(i%2==0){redFirst|=side==0;blueFirst|=side==1;}
        }
        helper.assertTrue(redFirst && blueFirst,"Equal teams were not assigned randomly");helper.succeed();
    }

    private static CampaignData marketFixture(GameTestHelper helper) {
        var data=CampaignData.get(helper.getLevel().getServer());data.marketBounds=new int[]{-9,142,17,9,160,31};
        data.spawns[2]=new int[]{0,152,24};return data;
    }
    @GameTest(template="empty",timeoutTicks=500) public static void createDrillsRespectTheMarket(GameTestHelper helper) {
        boolean previous=Settings.INSTANCE.campaignEnabled;Settings.INSTANCE.campaignEnabled=true;
        var level=helper.getLevel();marketFixture(helper);Market.claim(level.getServer());
        int z=24;
        var motor=net.minecraft.world.level.block.Block.byItem(Campaign.item("create:creative_motor"));
        var drill=net.minecraft.world.level.block.Block.byItem(Campaign.item("create:mechanical_drill"));
        level.setChunkForced(-4,1,true);level.setChunkForced(-3,1,true);level.setChunkForced(-2,1,true);level.setChunkForced(-1,1,true);
        CampaignData.get(level.getServer()).marketBlocks.add(new BlockPos(-16,110,z).asLong());
        for(int x:new int[]{-18,-50}) {
            level.setBlockAndUpdate(new BlockPos(x,110,z),motor.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,net.minecraft.core.Direction.EAST));
            level.setBlockAndUpdate(new BlockPos(x+1,110,z),drill.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,net.minecraft.core.Direction.EAST));
            level.setBlockAndUpdate(new BlockPos(x+2,110,z),Blocks.COBBLESTONE.defaultBlockState());
        }
        helper.runAfterDelay(180,()->{
            try {
                helper.assertTrue(level.getBlockState(new BlockPos(-48,110,z)).isAir(),"Control drill did not break unclaimed block");
                helper.assertTrue(level.getBlockState(new BlockPos(-16,110,z)).is(Blocks.COBBLESTONE),"Create drill destroyed protected Market");helper.succeed();
            } finally {
                for(int x:new int[]{-18,-17,-16,-50,-49,-48})level.removeBlock(new BlockPos(x,110,z),false);
                level.setChunkForced(-4,1,false);level.setChunkForced(-3,1,false);level.setChunkForced(-2,1,false);level.setChunkForced(-1,1,false);
                CampaignData.get(level.getServer()).marketBlocks.remove(new BlockPos(-16,110,z).asLong());Settings.INSTANCE.campaignEnabled=previous;
            }
        });
    }
    @GameTest(template = "empty") public static void inventoryReservationIsAtomic(GameTestHelper helper) {
        var p = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "InventoryQA"));
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        p.getInventory().setItem(0, ItemStack.EMPTY);
        List<ItemStack> card = List.of(new ItemStack(Items.WATER_BUCKET), new ItemStack(Items.OAK_SAPLING));
        helper.assertTrue(!Campaign.fits(p, card), "Two different items fit into one free slot");
        helper.assertTrue(p.getInventory().getItem(0).isEmpty(), "Reservation mutated a live slot");
        p.getInventory().setItem(1, ItemStack.EMPTY);
        helper.assertTrue(Campaign.fits(p, card), "Two-item survival card did not fit in two slots");
        helper.succeed();
    }

    @GameTest(template = "empty") public static void everyDropPoolResolves(GameTestHelper helper) {
        for (var pool : Settings.INSTANCE.pools) for (var drop : pool) Campaign.item(drop.item());
        var a = new CampaignData.Account(0, 142);
        for (int tier = 0; tier < 6; tier++) for (int i = 0; i < 300; i++) {
            a.received = i; var result = Campaign.nextDrop(a, tier);
            helper.assertTrue(result.size()==1 && result.getFirst().getCount()>=1 && result.getFirst().getCount()<=result.getFirst().getMaxStackSize() && !result.getFirst().isEmpty(), "Delivery is not one valid stack");
        }
        helper.succeed();
    }

    @GameTest(template="empty") public static void broadPoolsExpandAndNewWorldsChangeSequences(GameTestHelper helper) {
        helper.assertTrue(ItemDrops.entries().size()>1200,"Registry catalog is still a tiny whitelist");
        helper.assertTrue(ItemDrops.entries().stream().noneMatch(e->java.util.Set.of("minecraft:command_block","create:handheld_worldshaper","chancecubes:cube_dispenser","chancecubes:reward_selector_pendant").contains(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(e.item()).toString())),"Operator tools entered the survival pool");
        var seen=new java.util.HashSet<net.minecraft.world.item.Item>();
        for(int tier=0;tier<6;tier++) {
            var unlocked=new java.util.HashSet<net.minecraft.world.item.Item>();
            for(var e:ItemDrops.entries())if(e.tier()<=tier)unlocked.add(e.item());
            helper.assertTrue(unlocked.containsAll(seen),"A tier removed lower-tier items");seen=unlocked;
        }
        helper.assertTrue(seen.contains(Campaign.item("create:steam_engine")) && seen.contains(Campaign.item("farmersdelight:cooking_pot")),"Cooking or steam progression absent from rolls");
        UUID id=UUID.randomUUID();var first=new CampaignData().account(id);var second=new CampaignData().account(id);
        helper.assertTrue(first.seed!=second.seed,"New worlds reuse the account's fixed drop seed");
        long different=java.util.stream.IntStream.range(0,100).filter(i->!net.minecraft.world.item.ItemStack.isSameItemSameComponents(ItemDrops.roll(first.seed,i,0),ItemDrops.roll(second.seed,i,0))).count();
        helper.assertTrue(different>80,"Different new worlds repeat their drop sequence");helper.succeed();
    }

    @GameTest(template="empty",timeoutTicks=250) public static void cookingIsAWorkingRenewableFoodActivity(GameTestHelper helper) {
        var level=helper.getLevel();var stove=helper.absolutePos(new BlockPos(2,1,2));var pot=stove.above();
        level.setBlockAndUpdate(stove,net.minecraft.world.level.block.Block.byItem(Campaign.item("farmersdelight:stove")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,true));
        level.setBlockAndUpdate(pot,net.minecraft.world.level.block.Block.byItem(Campaign.item("farmersdelight:cooking_pot")).defaultBlockState());
        var block=(vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity)level.getBlockEntity(pot);
        helper.assertTrue(block.isHeated(),"Public stove cannot heat a cooking pot");
        block.getInventory().setStackInSlot(0,new ItemStack(Campaign.item("farmersdelight:rice")));
        block.getInventory().setStackInSlot(vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity.CONTAINER_SLOT,new ItemStack(Items.BOWL));
        helper.runAfterDelay(140,()->{
            helper.assertTrue(block.getInventory().getStackInSlot(vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity.OUTPUT_SLOT).is(Campaign.item("farmersdelight:cooked_rice")),"Rice did not cook into a usable meal");helper.succeed();
        });
    }

    @GameTest(template = "empty") public static void luckyLootNeverDiscardsPartialOverflow(GameTestHelper helper) {
        var level = helper.getLevel();
        var p = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "LootQA"));
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 63));
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        LuckyEvents.give(level, pos, p, new ItemStack(Items.DIAMOND, 5));
        helper.assertTrue(p.getInventory().getItem(0).getCount() == 64, "The available slot was not filled");
        int overflow = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3)).stream().filter(e -> e.getItem().is(Items.DIAMOND)).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(overflow == 4, "Partial inventory insertion discarded lucky-block loot");
        helper.succeed();
    }

    @GameTest(template = "empty") public static void nativeAndCustomChanceCubesPoolLoads(GameTestHelper helper) {
        LuckyEvents.install();
        int count=GlobalCCRewardRegistry.DEFAULT.getRewardNames().size(),giant=GlobalCCRewardRegistry.GIANT.getRewardNames().size();
        helper.assertTrue(count>50&&giant>9,"Native cube events were not restored: "+count+" / "+giant);
        helper.assertTrue(GlobalCCRewardRegistry.DEFAULT.getRewardNames().stream().filter(n->n.startsWith("farskies:luck_")).count()==9,"Custom events are missing or duplicated");
        LuckyEvents.install();
        helper.assertTrue(GlobalCCRewardRegistry.DEFAULT.getRewardNames().size()==count&&GlobalCCRewardRegistry.GIANT.getRewardNames().size()==giant,"Reloading duplicates native/custom rewards");
        helper.assertTrue(!Shop.cube(-75).isEmpty() && !Shop.cube(75).isEmpty(), "Base mod cube item unavailable");
        helper.succeed();
    }

    @GameTest(template = "empty") public static void cursedRecipeAndShopTransactions(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("farskies:cursed_cube")).orElseThrow();
        var result = recipe.value().getResultItem(level.registryAccess());
        helper.assertTrue(((chanceCubes.items.ItemChanceCube)result.getItem()).getChance(result) == -75, "Crafting did not make a cursed cube");
        helper.assertTrue(level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse("chancecubes:chance_cube_crafting")).isEmpty(), "Cheap base-mod cube recipe bypasses the market");
        var offers=Shop.offers(0);
        var buy=offers.get(4);
        helper.assertTrue(buy.getCostA().is(Items.EMERALD) && buy.getCostA().getCount()==4,"Cube price is not four physical emeralds");
        ItemStack coins=new ItemStack(Items.EMERALD,4);
        helper.assertTrue(buy.take(coins,ItemStack.EMPTY) && coins.isEmpty(),"Merchant does not consume physical payment");
        helper.assertTrue(buy.assemble().is(chanceCubes.items.CCubesItems.CHANCE_CUBE.get()),"Merchant result is not a cube");
        for(int i=0;i<buy.getMaxUses();i++)buy.increaseUses();
        helper.assertTrue(buy.isOutOfStock(),"Merchant stock limit is missing");
        helper.succeed();
    }

    @GameTest(template="empty") public static void marketProtectsFoundationsButAllowsFoliageAndBridges(GameTestHelper helper) {
        boolean previous=Settings.INSTANCE.campaignEnabled;Settings.INSTANCE.campaignEnabled=true;
        var level=helper.getLevel();var data=marketFixture(helper);Market.claim(level.getServer());
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"MarketQA"));
        var protection=xaero.pac.common.server.api.OpenPACServerAPI.get(level.getServer()).getChunkProtection();protection.giveFullPass(p.getUUID());
        BlockPos foundation=new BlockPos(8,110,24),leaf=foundation.above(),bridge=new BlockPos(-10,110,24);
        try {
            level.setBlockAndUpdate(foundation,Blocks.GLASS.defaultBlockState());data.marketBlocks.add(foundation.asLong());data.marketColumns.add(WorldSetup.column(foundation));
            level.explode(null,8.5,111,24.5,4,Level.ExplosionInteraction.TNT);
            helper.assertTrue(level.getBlockState(foundation).is(Blocks.GLASS),"Explosion destroyed foundation");
            var event=new BlockEvent.BreakEvent(level,foundation,Blocks.GLASS.defaultBlockState(),p);NeoForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled(),"Full-pass player can break original foundation");
            var approach=new BlockEvent.BreakEvent(level,bridge,Blocks.COBBLESTONE.defaultBlockState(),p);NeoForge.EVENT_BUS.post(approach);
            helper.assertTrue(!approach.isCanceled(),"Player cannot modify bridge beside Market");
            var beside=new BlockEvent.EntityPlaceEvent(net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,bridge),Blocks.GLASS.defaultBlockState(),p);NeoForge.EVENT_BUS.post(beside);
            helper.assertTrue(!beside.isCanceled(),"Player cannot place beside Market");
            var above=new BlockEvent.EntityPlaceEvent(net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(),level,foundation.above(30)),Blocks.AIR.defaultBlockState(),p);NeoForge.EVENT_BUS.post(above);
            helper.assertTrue(above.isCanceled(),"Player can build above original Market footprint");
            var foliage=new BlockEvent.BreakEvent(level,leaf,Blocks.OAK_LEAVES.defaultBlockState(),p);NeoForge.EVENT_BUS.post(foliage);
            helper.assertTrue(!foliage.isCanceled(),"Removable foliage is protected");
            helper.assertTrue(!Market.inside(level,new BlockPos(80,151,0)),"Home is protected");
        } finally {data.marketBlocks.remove(foundation.asLong());data.marketColumns.remove(WorldSetup.column(foundation));level.removeBlock(foundation,false);protection.removeFullPass(p.getUUID());Settings.INSTANCE.campaignEnabled=previous;}
        helper.succeed();
    }
    @GameTest(template="empty") public static void instantTravelAndMarketPeaceRespectCombat(GameTestHelper helper) {
        boolean previous=Settings.INSTANCE.campaignEnabled;Settings.INSTANCE.campaignEnabled=true;int truce=Settings.INSTANCE.truceMinutes;Settings.INSTANCE.truceMinutes=0;
        var savedColumns=new java.util.HashSet<>(CampaignData.get(helper.getLevel().getServer()).marketColumns);CampaignData.get(helper.getLevel().getServer()).marketColumns.clear();
        var protection=xaero.pac.common.server.api.OpenPACServerAPI.get(helper.getLevel().getServer()).getChunkProtection();var testPlayers=new java.util.ArrayList<UUID>();
        try {
            var level=helper.getLevel();var data=marketFixture(helper);
            var defender=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"DefenderQA"));
            var attacker=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"AttackerQA"));
            for(var player:java.util.List.of(defender,attacker)){protection.giveFullPass(player.getUUID());testPlayers.add(player.getUUID());}
            attacker.setPos(-79,151,0);data.account(defender.getUUID()).faction=0;data.spawns[0]=new int[]{-80,151,0};
            level.setBlockAndUpdate(new BlockPos(0,151,24),Blocks.STONE.defaultBlockState());data.marketColumns.add(WorldSetup.column(new BlockPos(0,151,24)));
            defender.setPos(-80,151,0);defender.setOnGround(true);
            helper.assertTrue(Market.request(defender,false)==1,"Market travel prerequisites were refused");
            // FakePlayer's network teleport is a no-op; the native client check covers arrival.
            defender.setPos(0,152,24);
            var peace=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(defender,new net.neoforged.neoforge.common.damagesource.DamageContainer(attacker.damageSources().playerAttack(attacker),3));NeoForge.EVENT_BUS.post(peace);
            helper.assertTrue(peace.isCanceled(),"PvP allowed in Market");
            // A bridge just beside the island (still inside the old rectangle) is ordinary PvP ground.
            defender.setPos(-11,152,24);
            var bridge=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(defender,new net.neoforged.neoforge.common.damagesource.DamageContainer(attacker.damageSources().playerAttack(attacker),3));NeoForge.EVENT_BUS.post(bridge);
            helper.assertTrue(!bridge.isCanceled(),"Market peace extends past its land");
            defender.setPos(-80,151,0);defender.setOnGround(true);attacker.setPos(-79,151,0);
            var hit=new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(defender,new net.neoforged.neoforge.common.damagesource.DamageContainer(attacker.damageSources().playerAttack(attacker),3));NeoForge.EVENT_BUS.post(hit);
            helper.assertTrue(!hit.isCanceled(),"Home PvP blocked");helper.assertTrue(Market.request(defender,false)==0,"Combat allowed travel escape");
            level.removeBlock(new BlockPos(0,151,24),false);
        } finally {testPlayers.forEach(protection::removeFullPass);CampaignData.get(helper.getLevel().getServer()).marketColumns.clear();CampaignData.get(helper.getLevel().getServer()).marketColumns.addAll(savedColumns);Settings.INSTANCE.campaignEnabled=previous;Settings.INSTANCE.truceMinutes=truce;Market.clear();}
        helper.succeed();
    }
    @GameTest(template="empty") public static void villagerWalksRecoversAndPreservesStock(GameTestHelper helper) {
        var level=helper.getLevel();var data=marketFixture(helper);data.trader=new int[]{0,152,24};
        for(var pos:BlockPos.betweenClosed(new BlockPos(-2,151,22),new BlockPos(2,151,26)))level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
        Shop.ensureTrader(level.getServer());
        var villager=(net.minecraft.world.entity.npc.Villager)level.getEntity(Shop.TRADER_ID);
        helper.assertTrue(villager!=null && !villager.isNoAi() && villager.isInvulnerable(),"Not a protected walking villager");
        helper.assertTrue(villager.getTeam().getCollisionRule()==net.minecraft.world.scores.Team.CollisionRule.NEVER,"Villager is pushable by player collision");
        data.clock=20;Shop.tickTrader(level.getServer());
        int used=villager.getOffers().get(4).getUses()+1;
        villager.getOffers().get(4).increaseUses();
        villager.restock();
        helper.assertTrue(!villager.showProgressBar() && !villager.shouldRestock() && !villager.canRestock() && villager.getOffers().get(4).getUses()==used,"Vanilla job-site restocking bypasses market stock");
        Shop.tickTrader(level.getServer());
        villager.teleportTo(0,130,24);data.clock=25;Shop.tickTrader(level.getServer());
        helper.assertTrue(villager.getY()==152,"Fallen villager was not returned to shop");
        villager.discard();Shop.ensureTrader(level.getServer());
        var restored=(net.minecraft.world.entity.npc.Villager)level.getEntity(Shop.TRADER_ID);
        helper.assertTrue(restored!=null && restored.getOffers().get(4).getUses()==used,"Respawn lost stock limits");restored.discard();
        helper.succeed();
    }
    @GameTest(template = "empty") public static void destroyedArrivalUsesExistingTerrain(GameTestHelper helper) {
        var level = helper.getLevel(); var data = new CampaignData();
        int x = Settings.INSTANCE.homeDistance;
        data.spawns[1] = new int[]{x, 270, 0};
        helper.assertTrue(!WorldSetup.safe(level, WorldSetup.pos(data, 1)), "The test arrival point was not destroyed");
        var destination = WorldSetup.recoverRally(level, data, 1);
        helper.assertTrue(destination != null && WorldSetup.safe(level, destination), "Players were stranded after a raid");
        helper.assertTrue(data.spawns[1][1]==270,"Temporary fallback permanently moved the home");
        helper.assertTrue(destination.getY() < 270 && level.getBlockState(new BlockPos(x, 269, 0)).isAir(), "Recovery regenerated the destroyed home");
        helper.succeed();
    }
}
