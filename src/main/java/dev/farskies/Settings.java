package dev.farskies;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.loading.FMLPaths;

public final class Settings {
    public int configVersion = 5;
    public int intervalSeconds = 30;
    public int truceMinutes = 30;
    public int activeSeconds = 180;
    public int merchantRestockMinutes = 30;
    public int homeDistance = 80;
    public int marketRadiusChunks = 2;
    public int marketSafeMargin = 12;
    public int eventMinutes = 45;
    public int marketOffsetZ = 24;
    public int spawnY = 150;
    public boolean campaignEnabled = false;
    public boolean allowOtherDimensions = false;
    public long[] tierThresholds = {0, 90, 240, 480, 900, 1440};
    public String[] tierNames = {"Tier 1", "Tier 2", "Tier 3", "Tier 4", "Tier 5", "Tier 6"};
    public String[] factionNames = {"Red", "Blue"};
    public List<List<Drop>> pools = defaultPools();
    public static Settings INSTANCE;

    public record Drop(String item, int count, int weight) {
        public Drop(String item, int count) { this(item, count, 8); }
        public int effectiveWeight() { return weight == 0 ? 8 : weight; }
    }

    public static Settings load() {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        Path file = FMLPaths.CONFIGDIR.get().resolve("farskies.json");
        try {
            Settings settings = Files.exists(file) ? gson.fromJson(Files.readString(file), Settings.class) : new Settings();
            if(Files.exists(file)) {
                var saved=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(!saved.has("configVersion") || saved.get("configVersion").getAsInt()<4) {
                    Path backup=file.resolveSibling("farskies-before-0.4.json.bak");
                    if(!Files.exists(backup))Files.copy(file,backup);
                    settings.configVersion=4;settings.intervalSeconds=30;settings.marketOffsetZ=24;
                    settings.factionNames=new String[]{"Red","Blue"};
                    settings.tierNames=new String[]{"Tier 1","Tier 2","Tier 3","Tier 4","Tier 5","Tier 6"};
                    settings.tierThresholds=new long[]{0,120,360,720,1440,2400};
                    settings.pools=settings.pools.stream().map(pool->pool.stream().map(d->new Drop(d.item(),1,d.weight())).toList()).toList();
                    Files.writeString(file,gson.toJson(settings)+"\n");
                }
            }
            if(settings.configVersion<5){
                Path backup=file.resolveSibling("farskies-before-0.5.json.bak");if(Files.exists(file)&&!Files.exists(backup))Files.copy(file,backup);
                settings.configVersion=5;settings.tierThresholds=new long[]{0,90,240,480,900,1440};settings.marketSafeMargin=12;
                Files.writeString(file,gson.toJson(settings)+"\n");
            }
            settings.validate();
            if (!Files.exists(file)) Files.writeString(file, gson.toJson(settings) + "\n");
            return INSTANCE = settings;
        } catch (Exception e) { throw new IllegalStateException("Invalid config/farskies.json", e); }
    }

    public void validate() {
        if (truceMinutes<0 || truceMinutes>180 || marketSafeMargin<6 || marketSafeMargin>24 || eventMinutes<10 || intervalSeconds < 10
                || activeSeconds < 10 || homeDistance < 64 || homeDistance > 20000
                || marketRadiusChunks < 2 || marketRadiusChunks > 12 || spawnY < 64 || spawnY > 290
                || merchantRestockMinutes < 5 || marketOffsetZ < 16 || marketOffsetZ > 48 || factionNames.length != 2
                || tierThresholds.length != 6 || tierNames.length != 6 || pools.size() != 6)
            throw new IllegalArgumentException("Invalid campaign settings");
        if (homeDistance <= (marketRadiusChunks + 1) * 16 + 16)
            throw new IllegalArgumentException("Home pads must be outside the Market claim and buffer");
        if (tierThresholds[0] != 0) throw new IllegalArgumentException("First tier must start at zero");
        for (int i = 0; i < 6; i++) {
            if (i > 0 && tierThresholds[i] <= tierThresholds[i - 1]) throw new IllegalArgumentException("Tier order");
            if (pools.get(i).isEmpty()) throw new IllegalArgumentException("Empty pool");
            for (Drop d : pools.get(i)) if (d.count < 1 || d.count > 64 || d.item == null || d.weight < 0 || d.weight > 1000)
                throw new IllegalArgumentException("Invalid drop");
        }
    }

    private static List<List<Drop>> defaultPools() {
        List<List<Drop>> result = new ArrayList<>();
        String[][] tiers = {
            {"dirt", "cobblestone", "oak_log", "sand", "gravel", "clay_ball", "moss_block", "wheat_seeds", "carrot", "potato", "string", "coal", "torch", "bone_meal", "spruce_sapling", "birch_sapling", "andesite", "cactus", "sugar_cane", "pumpkin_seeds", "melon_seeds", "bamboo", "brown_mushroom", "red_mushroom", "glass", "baked_potato"},
            {"iron_ingot", "copper_ingot", "leather", "redstone", "white_wool", "flint", "slime_ball", "kelp", "dripstone_block", "pointed_dripstone", "honey_bottle", "cocoa_beans", "sweet_berries", "cherry_sapling", "jungle_sapling", "lily_pad", "diorite", "granite", "tuff", "calcite"},
            {"create:zinc_ingot", "create:andesite_alloy", "create:shaft", "create:cogwheel", "create:water_wheel", "create:electron_tube", "create:rose_quartz", "quartz", "lapis_lazuli", "gold_ingot", "obsidian", "gunpowder", "spider_eye", "fermented_spider_eye", "glow_berries", "sea_pickle"},
            {"create:brass_ingot", "create:precision_mechanism", "create:electron_tube", "create:sturdy_sheet", "diamond", "emerald", "blaze_rod", "nether_wart", "soul_sand", "netherrack", "glowstone_dust", "amethyst_shard", "prismarine_shard", "ender_pearl", "crying_obsidian"},
            {"diamond", "emerald", "blaze_powder", "ghast_tear", "magma_cream", "create:precision_mechanism", "create:brass_ingot", "sculk", "echo_shard", "end_stone", "chorus_fruit", "nautilus_shell", "sponge", "sea_lantern", "experience_bottle"},
            {"netherite_scrap", "netherite_upgrade_smithing_template", "shulker_shell", "heart_of_the_sea", "totem_of_undying", "diamond", "emerald", "dragon_breath", "experience_bottle", "breeze_rod", "wither_skeleton_skull", "enchanted_golden_apple", "turtle_scute", "armadillo_scute"}
        };
        for (String[] tier : tiers) {
            var pool = new ArrayList<Drop>();
            for (String name : tier) pool.add(new Drop(name.contains(":") ? name : "minecraft:" + name, 1,
                List.of("netherite_scrap", "netherite_upgrade_smithing_template", "totem_of_undying", "heart_of_the_sea", "wither_skeleton_skull", "enchanted_golden_apple").contains(name) ? 1 : 8));
            result.add(pool);
        }
        for (String name : List.of("iron_pickaxe", "iron_sword", "shield", "bow", "chainmail_chestplate"))
            result.getFirst().add(new Drop("minecraft:" + name, 1, 1));
        result.getFirst().add(new Drop("minecraft:arrow", 12, 1));
        result.getFirst().add(new Drop("minecraft:iron_ingot", 4, 1));
        for (String name : List.of("andesite_alloy", "shaft", "cogwheel", "large_cogwheel", "water_wheel", "hand_crank", "gearbox", "mechanical_press", "mechanical_mixer", "basin"))
            result.getFirst().add(new Drop("create:" + name, name.equals("shaft") ? 4 : 1, 1));
        result.getFirst().add(new Drop("createsifter:string_mesh",1,2));
        return result;
    }
}
