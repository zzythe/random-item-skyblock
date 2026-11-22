package dev.farskies;

import dev.farskies.core.Deck;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.fml.loading.FMLPaths;

/** Cumulative catalog, with bounded category odds so decorative variants cannot crowd out resources. */
public final class ItemDrops {
    public enum Kind { MATERIAL, UTILITY, EQUIPMENT, DISCOVERY, DECORATION }
    public record Entry(Item item,int tier,int weight) {}
    private record Bucket(Kind kind,int chance,List<Entry> entries,int total) {}
    private static final List<List<Bucket>> POOLS=new ArrayList<>();
    private static final int[] CATEGORY_CHANCES={45,20,20,10,5};
    private static final Set<String> RAW=Set.of("dirt","coarse_dirt","rooted_dirt","grass_block","cobblestone","cobbled_deepslate","stone","andesite","diorite","granite","tuff","calcite","sand","red_sand","gravel","clay","clay_ball","coal","charcoal","string","bone","bone_meal","flint","leather","redstone","quartz","lapis_lazuli","gold","iron","copper","zinc","netherrack","soul_sand","soul_soil","moss_block","bamboo","sugar_cane","wheat","carrot","potato","onion","rice","cactus","kelp","white_wool","gunpowder","slime_ball","obsidian");
    /** Resource mods register pieces for optional metals; these four have a real ore in this pack. */
    private static final Set<String> AVAILABLE_METALS=Set.of("iron","gold","copper","zinc");
    public static boolean absentMetal(String mod,String name){
        return mod.equals("createsifter")&&name.startsWith("raw_")&&name.endsWith("_piece")&&!AVAILABLE_METALS.contains(name.substring(4,name.length()-6))
            ||mod.equals("exdeorum")&&name.endsWith("_ore_chunk")&&!AVAILABLE_METALS.contains(name.substring(0,name.length()-10));
    }
    /** Optional wood integrations reference textures from mods that are absent from this pack. */
    public static final List<String> GHOST_WOODS=List.of("archwood","blue_archwood","green_archwood","purple_archwood","red_archwood","bluebright","comet","crystallized","dead","dusk","fir","frostbright","golden_oak","hellbark","jacaranda","lunar","magic","mahogany","maple","palm","redwood","skyroot","starlit","umbran","willow");
    public static boolean ghost(String mod,String name){
        if(!mod.equals("exdeorum"))return false;
        for(String wood:GHOST_WOODS)for(String kind:new String[]{"_sieve","_compressed_sieve","_barrel","_crucible"})if(name.equals(wood+kind))return true;
        return false;
    }
    public static boolean unavailableIntegration(String mod,String name){
        return ghost(mod,name)||absentMetal(mod,name)||mod.equals("exdeorum")&&name.startsWith("mechanical_")||mod.equals("simpleplanes")&&(name.contains("electric")||name.contains("charging")||name.contains("solar"));
    }
    private static List<Entry> entries=List.of();
    private static MinecraftServer server;
    private static final Set<String> TECHNICAL=Set.of("air","barrier","sleep_barrier","light","bedrock","command_block","chain_command_block","repeating_command_block","command_block_minecart","structure_block","structure_void","jigsaw","debug_stick","knowledge_book","end_portal_frame","bundle_placeholder","reinforced_deepslate","cube_dispenser","handheld_worldshaper","reward_selector_pendant","single_use_reward_selector_pendant","filled_map");
    public static void initialize(MinecraftServer value) {
        server=value;
        Map<String,Entry> all=new TreeMap<>();
        for(Item item:BuiltInRegistries.ITEM) {
            var id=BuiltInRegistries.ITEM.getKey(item);String name=id.getPath();
            if(item==Items.AIR || unavailableIntegration(id.getNamespace(),name) || TECHNICAL.contains(name) || name.contains("creative_") || name.contains("_spawn_egg") && (name.contains("warden")||name.contains("wither")||name.contains("ender_dragon")) || name.startsWith("incomplete_"))continue;
            all.put(id.toString(),new Entry(item,classify(item,id.getNamespace(),name),1));
        }
        // Existing config entries act as earliest-tier/weight overrides, never as a small whitelist.
        for(int tier=Settings.INSTANCE.pools.size()-1;tier>=0;tier--)for(var drop:Settings.INSTANCE.pools.get(tier)) {
            if(all.containsKey(drop.item()))all.put(drop.item(),new Entry(Campaign.item(drop.item()),tier,drop.effectiveWeight()));
        }
        entries=List.copyOf(all.values());
        POOLS.clear();
        for(int tier=0;tier<6;tier++){
            final int unlocked=tier;var buckets=new ArrayList<Bucket>();
            for(var kind:Kind.values()){
                var items=entries.stream().filter(e->e.tier()<=unlocked&&kind(e.item())==kind).toList();
                if(!items.isEmpty())buckets.add(new Bucket(kind,CATEGORY_CHANCES[kind.ordinal()],items,items.stream().mapToInt(ItemDrops::effectiveWeight).sum()));
            }POOLS.add(List.copyOf(buckets));
        }
        var report=new StringBuilder("item,tier,weight,category\n");
        for(var e:entries)report.append(BuiltInRegistries.ITEM.getKey(e.item())).append(',').append(e.tier()+1).append(',').append(effectiveWeight(e)).append(',').append(kind(e.item())).append('\n');
        try{Files.writeString(FMLPaths.CONFIGDIR.get().resolve("random-item-pool.csv"),report);}catch(java.io.IOException e){throw new IllegalStateException("Cannot write item-pool report",e);}
        FarSkies.LOG.info("ITEM_POOL total={} cumulative={}",entries.size(),java.util.stream.IntStream.range(0,6).mapToObj(t->entries.stream().filter(e->e.tier()<=t).count()).toList());
    }
    public static List<Entry> entries(){return entries;}
    private static boolean has(String name,String... tokens){for(String token:tokens)if(name.contains(token))return true;return false;}
    private static int classify(Item item,String mod,String name) {
        if(mod.equals("create")){
            if(has(name,"creative_"))return 5;
            if(has(name,"package","packager","stock_link","stock_ticker","redstone_requester","factory_gauge","repackager","frogport"))return 4;
            if(has(name,"track","station","train","controls","schedule","steam_engine","mechanical_arm","deployer","precision_mechanism","sturdy_sheet","crushing_wheel","elevator"))return 3;
            if(has(name,"brass","blaze","rotation_speed_controller","mechanical_bearing","windmill","gantry","clockwork","hose_pulley","display_link"))return 2;
        }
        if(has(name,"netherite","ancient_debris","wither_skeleton_skull","elytra","totem","dragon_","nether_star","beacon","heavy_core","mace","enchanted_golden_apple"))return 5;
        if(has(name,"spawn_egg","spawner","trial_","ominous_","shulker","echo_shard","heart_of_the_sea","trident","conduit"))return 4;
        if(has(name,"diamond","end_stone","chorus","purpur","ender_eye","end_crystal","brewing_stand","enchanting_table","precision_mechanism","sturdy_sheet"))return 3;
        if(has(name,"blaze","brass","obsidian","ender_pearl","quartz","nether_","netherrack","soul_","crimson","warped","glowstone","potion","enchanted_book","lapis","redstone_block"))return 2;
        if(has(name,"iron_","gold_","golden_","copper","zinc","redstone","chainmail","anvil","hopper","piston","dispenser","dropper","rail","minecart","bucket","crossbow","slime","honey"))return 1;
        if(mod.equals("create"))return has(name,"shaft","cogwheel","water_wheel","hand_crank","andesite","basin","millstone","mechanical_press","mechanical_mixer")?0:1;
        if(mod.equals("createsifter"))return has(name,"brass","sturdy","advanced")?3:has(name,"mesh","pebble","piece")?0:1;
        if(mod.equals("exdeorum"))return 0;
        if(mod.equals("chancecubes"))return 2;
        if(mod.equals("simpleplanes"))return has(name,"shooter","large_plane","cargo_plane","helicopter","armor")?3:name.equals("plane")||has(name,"booster","healing","supply_crate","liquid_engine")?2:1;
        if(item instanceof BlockItem || item.getDefaultInstance().has(DataComponents.FOOD) || mod.equals("farmersdelight"))return 0;
        return 1;
    }
    private static int effectiveWeight(Entry e){return e.weight()*(1+e.tier()*2);}
    public static Kind kind(Item item){
        String mod=BuiltInRegistries.ITEM.getKey(item).getNamespace(),n=BuiltInRegistries.ITEM.getKey(item).getPath();
        if(RAW.contains(n)||has(n,"_log","_seeds","_seed","_ingot","_nugget","_ore","raw_","_pebble","_piece"))return Kind.MATERIAL;
        if(item instanceof TieredItem||item instanceof ArmorItem||item instanceof ProjectileWeaponItem||item instanceof ShieldItem||item instanceof TridentItem||item instanceof FishingRodItem||has(n,"potion","enchanted_book","arrow","elytra")||item.getDefaultInstance().has(DataComponents.FOOD))return Kind.EQUIPMENT;
        if(has(n,"stairs","slab","_wall","_fence","_pane","carpet","_banner","_sign","button","pressure_plate","terracotta","concrete","_wool","candle","_head","oxidized_","weathered_","exposed_","waxed_"))return Kind.DECORATION;
        if(has(n,"spawn_egg","music_disc","pottery_sherd","sherd","pattern","trim_")||mod.equals("chancecubes"))return Kind.DISCOVERY;
        if(item instanceof BlockItem||item instanceof BucketItem||mod.equals("create")||mod.equals("createsifter")||mod.equals("exdeorum"))return Kind.UTILITY;
        return Kind.DISCOVERY;
    }
    private static int quantity(Item item,long seed,long receipt,int tier){
        String name=BuiltInRegistries.ITEM.getKey(item).getPath();int lo=1,hi=1;
        if(java.util.Set.of("dirt","cobblestone","cobbled_deepslate","stone","sand","red_sand","gravel","andesite","diorite","granite","netherrack","tuff").contains(name)){lo=8+tier*2;hi=16+tier*4;}
        else if(has(name,"_log","_planks","_ingot","_seeds","_seed")||java.util.Set.of("coal","charcoal","bone_meal","string","redstone","lapis_lazuli","quartz","clay_ball").contains(name)){lo=2+tier/2;hi=4+tier;}
        else if(has(name,"_nugget")||name.equals("arrow")||name.equals("spectral_arrow")){lo=8;hi=16;}
        else if((name.endsWith("_piece")||name.endsWith("_ore_chunk")||name.endsWith("_pebble"))&&java.util.Set.of("createsifter","exdeorum").contains(BuiltInRegistries.ITEM.getKey(item).getNamespace())){lo=hi=4;}
        else if(kind(item)==Kind.DECORATION){lo=4;hi=8;}
        else if(item.getDefaultInstance().has(DataComponents.FOOD)&&!has(name,"golden_apple","stew","soup","bowl","bottle")){lo=2;hi=3;}
        return Math.min(item.getDefaultMaxStackSize(),lo+Deck.index(seed+71,receipt,hi-lo+1));
    }
    public static ItemStack roll(long seed,long receipt,int tier) {
        if(POOLS.isEmpty())throw new IllegalStateException("Item catalog not initialized");
        var buckets=POOLS.get(tier);int category=Deck.index(seed+13,receipt,buckets.stream().mapToInt(Bucket::chance).sum());
        Bucket bucket=buckets.getFirst();for(var candidate:buckets){category-=candidate.chance();if(category<0){bucket=candidate;break;}}
        int ticket=Deck.index(seed+29,receipt,bucket.total());Entry selected=bucket.entries().getFirst();
        for(var entry:bucket.entries()){ticket-=effectiveWeight(entry);if(ticket<0){selected=entry;break;}}
        ItemStack stack=new ItemStack(selected.item(),quantity(selected.item(),seed,receipt,tier));
        if(stack.is(Items.ENCHANTED_BOOK)) {
            var enchantments=server.registryAccess().registryOrThrow(Registries.ENCHANTMENT).holders().toList();
            var enchantment=enchantments.get(Deck.index(seed+43,receipt,enchantments.size()));
            stack.enchant(enchantment,1+Deck.index(seed+47,receipt,Math.min(tier+1,enchantment.value().getMaxLevel())));
        }
        if(stack.is(Items.POTION)||stack.is(Items.SPLASH_POTION)||stack.is(Items.LINGERING_POTION)||stack.is(Items.TIPPED_ARROW)) {
            var potions=BuiltInRegistries.POTION.holders().filter(h->!h.value().getEffects().isEmpty()).toList();
            stack.set(DataComponents.POTION_CONTENTS,new PotionContents(potions.get(Deck.index(seed+53,receipt,potions.size()))));
        }
        return stack;
    }
}
