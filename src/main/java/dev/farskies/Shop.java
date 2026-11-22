package dev.farskies;

import chanceCubes.items.CCubesItems;
import chanceCubes.items.ItemChanceCube;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class Shop {
    static final String TRADER_TAG="farskies_market_trader";
    static final UUID TRADER_ID=UUID.fromString("32cb3cc8-137d-423b-b092-d6ad12109da5");
    private static MerchantOffer offer(net.minecraft.world.level.ItemLike cost,int count,ItemStack result,int uses) {
        return new MerchantOffer(new ItemCost(cost,count),result,uses,0,0);
    }
    static MerchantOffers offers(int rotation) {
        var offers=new MerchantOffers();
        offers.add(offer(Items.COBBLESTONE,32,new ItemStack(Items.EMERALD),16));
        offers.add(offer(Items.OAK_LOG,16,new ItemStack(Items.EMERALD),16));
        offers.add(offer(Items.WHEAT,16,new ItemStack(Items.EMERALD),16));
        offers.add(offer(Items.IRON_INGOT,6,new ItemStack(Items.EMERALD,2),16));
        offers.add(offer(Items.EMERALD,4,cube(0),12));
        offers.add(offer(Items.EMERALD,10,cube(75),6));
        var kits=new String[]{"create:mechanical_press","create:encased_fan","create:mechanical_mixer","create:millstone","createsifter:sifter"};
        offers.add(offer(Items.EMERALD,6,new ItemStack(Campaign.item(kits[Math.floorMod(rotation,kits.length)])),2));
        offers.add(offer(Items.SPRUCE_LOG,16,new ItemStack(Items.EMERALD),16));
        offers.add(offer(Items.BIRCH_LOG,16,new ItemStack(Items.EMERALD),16));
        offers.add(offer(Items.CHERRY_LOG,16,new ItemStack(Items.EMERALD),16));
        offers.add(offer(Items.ACACIA_LOG,16,new ItemStack(Items.EMERALD),16));
        offers.add(offer(Items.EMERALD,1,new ItemStack(Items.BREAD,4),12));
        offers.add(offer(Items.EMERALD,2,new ItemStack(Items.ARROW,16),8));
        offers.add(offer(Items.EMERALD,3,new ItemStack(Items.REDSTONE,8),4));
        offers.add(offer(Items.EMERALD,3,new ItemStack(Items.WATER_BUCKET),4));
        offers.add(offer(Items.EMERALD,6,new ItemStack(Items.LAVA_BUCKET),2));
        offers.add(offer(Items.EMERALD,1,new ItemStack(Items.DIRT,16),16));
        offers.add(offer(Items.EMERALD,1,new ItemStack(Campaign.item("createsifter:string_mesh")),8));
        offers.add(offer(Items.EMERALD,12,new ItemStack(Campaign.item("create:blaze_burner")),2));
        offers.add(offer(Items.EMERALD,20,new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),1));
        for(String crop:new String[]{"tomato_seeds","cabbage_seeds","rice","onion"})offers.add(offer(Items.EMERALD,1,new ItemStack(Campaign.item("farmersdelight:"+crop)),8));
        offers.add(offer(Items.EMERALD,1,new ItemStack(Campaign.item("exdeorum:string_mesh")),8));
        offers.add(offer(Items.EMERALD,2,new ItemStack(Campaign.item("exdeorum:stone_hammer")),8));
        offers.add(offer(Items.EMERALD,3,new ItemStack(Campaign.item("createsifter:andesite_mesh")),4));
        offers.add(offer(Items.EMERALD,2,new ItemStack(Campaign.item("create:andesite_alloy"),4),8));
        offers.add(offer(Items.EMERALD,2,new ItemStack(Campaign.item("kaleidoscope_cookery:straw_hat")),8));
        offers.add(offer(Items.EMERALD,1,new ItemStack(Campaign.item("kaleidoscope_cookery:rice"),4),8));
        offers.add(offer(Items.EMERALD,1,new ItemStack(Campaign.item("kaleidoscope_cookery:tomato_seed"),4),8));
        // Production paths remain useful even when nobody is fighting.
        offers.add(offer(Items.CARROT,16,new ItemStack(Items.EMERALD),12));
        offers.add(offer(Items.POTATO,16,new ItemStack(Items.EMERALD),12));
        for(String crop:new String[]{"tomato","cabbage","onion","rice"})offers.add(offer(Campaign.item("farmersdelight:"+crop),16,new ItemStack(Items.EMERALD),12));
        offers.add(offer(Campaign.item("farmersdelight:vegetable_soup"),4,new ItemStack(Items.EMERALD,3),8));
        offers.add(offer(Campaign.item("kaleidoscope_cookery:egg_fried_rice"),4,new ItemStack(Items.EMERALD,3),8));
        return offers;
    }
    private static void setOffers(Villager trader,MerchantOffers offers){trader.getOffers().clear();trader.getOffers().addAll(offers);}
    static boolean canTrade(ServerPlayer p){return Campaign.enabled() && p.isAlive() && Market.inside(p.level(),p.blockPosition()) && CampaignData.get(p.server).account(p.getUUID()).faction>=0;}
    static int show(ServerPlayer p) {
        if(!canTrade(p))return 0;
        if(!(p.server.overworld().getEntity(TRADER_ID) instanceof Villager trader) || trader.distanceToSqr(p)>64)return 0;
        if(trader.getTradingPlayer()!=null && trader.getTradingPlayer()!=p)return Market.fail(p,"The villager is serving someone.");
        trader.setTradingPlayer(p);trader.openTradingScreen(p,Component.literal("Market"),0);return 1;
    }
    static void ensureTrader(MinecraftServer server) {
        var level=server.overworld();var data=CampaignData.get(server);var pos=WorldSetup.pos(data.trader);level.getChunkAt(pos);
        if(level.getEntity(TRADER_ID) instanceof Villager existing && !existing.isRemoved() && existing.isAlive())return;
        var trader=EntityType.VILLAGER.create(level);if(trader==null)throw new IllegalStateException("Could not spawn villager");
        trader.setUUID(TRADER_ID);trader.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,180,0);
        trader.setVillagerData(new VillagerData(VillagerType.PLAINS,VillagerProfession.ARMORER,1));trader.setVillagerXp(1);
        trader.setInvulnerable(true);trader.setPersistenceRequired();trader.setCanPickUpLoot(false);trader.restrictTo(pos,4);
        trader.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        trader.addTag(TRADER_TAG);
        MerchantOffers stock=data.marketOffers.isEmpty()?offers((int)data.marketRestock):MerchantOffers.CODEC.parse(level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),data.marketOffers).getOrThrow();
        setOffers(trader,stock);level.addFreshEntity(trader);
        var board=server.getScoreboard();var team=board.getPlayerTeam("ris_market_npc");
        if(team==null)team=board.addPlayerTeam("ris_market_npc");
        team.setCollisionRule(net.minecraft.world.scores.Team.CollisionRule.NEVER);
        board.addPlayerToTeam(trader.getStringUUID(),team);
    }
    static void refreshStock(MinecraftServer server){
        if(server.overworld().getEntity(TRADER_ID) instanceof Villager trader){setOffers(trader,offers((int)CampaignData.get(server).marketRestock));trader.setTradingPlayer(null);}
    }
    static void tickTrader(MinecraftServer server) {
        var d=CampaignData.get(server);if(d.clock%5!=0)return;
        if(!(server.overworld().getEntity(TRADER_ID) instanceof Villager trader)){ensureTrader(server);return;}
        var pos=WorldSetup.pos(d.trader);trader.stopRiding();
        trader.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        trader.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.POTENTIAL_JOB_SITE);
        // Walking is normal. Recover only if the NPC actually leaves the safe terrain or shop vicinity.
        if(!Market.inBounds(d,trader.blockPosition(),0) || trader.getY()<Settings.INSTANCE.spawnY-2 || trader.getY()>Settings.INSTANCE.spawnY+8 || trader.distanceToSqr(pos.getX()+.5,pos.getY(),pos.getZ()+.5)>100) {
            trader.getNavigation().stop();trader.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
            trader.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);trader.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);trader.fallDistance=0;
        }
        long restock=d.clock/(20L*60*Settings.INSTANCE.merchantRestockMinutes);
        if(d.marketRestock!=restock && !trader.isTrading()){setOffers(trader,offers((int)restock));d.marketRestock=restock;}
        if(d.clock%20==0) {
            d.marketOffers=(net.minecraft.nbt.CompoundTag)MerchantOffers.CODEC.encodeStart(server.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),trader.getOffers()).getOrThrow();
            d.setDirty();
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void interact(PlayerInteractEvent.EntityInteract event) {
        if(!Campaign.enabled() || !event.getTarget().getTags().contains(TRADER_TAG))return;
        event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if(event.getEntity() instanceof ServerPlayer player)show(player);
    }
    @SubscribeEvent public static void mount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if(Campaign.enabled() && event.getEntityMounting().getTags().contains(TRADER_TAG) && event.isMounting())event.setCanceled(true);
    }
    @SubscribeEvent public static void conversion(net.neoforged.neoforge.event.entity.living.LivingConversionEvent.Pre event) {
        if(Campaign.enabled() && event.getEntity().getTags().contains(TRADER_TAG))event.setCanceled(true);
    }
    public static ItemStack cube(int luck) {
        ItemStack stack = new ItemStack(CCubesItems.CHANCE_CUBE.get());
        ((ItemChanceCube) stack.getItem()).setChance(stack, luck);
        return stack;
    }

    static int corrupt(ServerPlayer p) {
        ItemStack held = p.getMainHandItem();
        if (!(held.getItem() instanceof ItemChanceCube item) || held.getCount() != 1 || !held.is(CCubesItems.CHANCE_CUBE.get())) return Market.fail(p, "Hold exactly one Chance Cube in your main hand.");
        if (item.getChance(held) < 0) return Market.fail(p, "That cube is already cursed or has an unknown luck value.");
        if (count(p, s -> s.is(Items.SPIDER_EYE)) < 1 || count(p, s -> s.is(Items.GUNPOWDER)) < 1) return Market.fail(p, "Corruption costs one spider eye and one gunpowder.");
        remove(p, s -> s.is(Items.SPIDER_EYE), 1); remove(p, s -> s.is(Items.GUNPOWDER), 1);
        item.setChance(held, -75); p.inventoryMenu.broadcastChanges();
        return 1;
    }

    static int count(ServerPlayer p, Predicate<ItemStack> predicate) { return p.getInventory().items.stream().filter(predicate).mapToInt(ItemStack::getCount).sum(); }
    static void remove(ServerPlayer p, Predicate<ItemStack> predicate, int left) {
        for (ItemStack s : p.getInventory().items) if (predicate.test(s)) { int n = Math.min(s.getCount(), left); s.shrink(n); left -= n; if (left == 0) break; }
    }

}
