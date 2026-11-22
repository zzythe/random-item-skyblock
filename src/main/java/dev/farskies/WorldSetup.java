package dev.farskies;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Places upstream biome templates intact, with their upstream processors. */
public final class WorldSetup {
    private WorldSetup() {}
    private static final String PREFIX = "skyvoid_biome_islands:";
    public static boolean supports(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof FlatLevelSource flat
            && flat.settings().getBiome().unwrapKey().map(k -> k.location().equals(ResourceLocation.parse("farskies:open_sky"))).orElse(false);
    }
    public static void initialize(MinecraftServer server, CampaignData data) { initialize(server.overworld(), data); }
    public static void initialize(ServerLevel level, CampaignData data) {
        if (!supports(level)) throw new IllegalStateException("A new Random Item Skyblock void world is required");
        var c = Settings.INSTANCE;
        for (int side=0; side<2; side++) {
            BlockPos center = new BlockPos(side==0 ? -c.homeDistance : c.homeDistance, c.spawnY, 0);
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1,0,-1), center.offset(1,2,1)))
                if (!level.getBlockState(pos).isAir()) throw new IllegalStateException("Starter overlaps existing blocks");
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1,0,-1), center.offset(1,0,1))) level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(),2);
            data.spawns[side] = xyz(center.above());
            tint(level, center.offset(-4,-8,-4), center.offset(4,16,4), "plains");
        }
        createMarket(level,data);
        level.setDefaultSpawnPos(pos(data,2),180);
        level.getGameRules().getRule(GameRules.RULE_SPAWN_RADIUS).set(0,level.getServer());
        level.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false,level.getServer());
        level.setDayTime(3000);
        data.startedAtMillis=System.currentTimeMillis(); data.initialized=true;data.layoutVersion=4; data.setDirty();
        FarSkies.LOG.info("VOID_WORLD west={} east={} market={} islands={}",pos(data,0),pos(data,1),pos(data,2),data.islands.size());
    }
    private static void createMarket(ServerLevel level,CampaignData data) {
        var c=Settings.INSTANCE;
        // Fuse three existing fragments, preserving solids where the supplied air volumes overlap.
        BlockPos origin=place(level,data,"market","robs_floating_islands:mooshroom",-6,c.spawnY-8,c.marketOffsetZ+6,491L);
        place(level,data,"market_part","robs_floating_islands:mooshroom",6,c.spawnY-8,c.marketOffsetZ+6,492L);
        place(level,data,"market_part","robs_floating_islands:mooshroom",0,c.spawnY-8,c.marketOffsetZ-4,493L);
        place(level,data,"market_part","robs_floating_islands:mooshroom",-12,c.spawnY-8,c.marketOffsetZ+2,494L);
        origin=new BlockPos(-21,c.spawnY-8,c.marketOffsetZ-11);
        var size=new net.minecraft.core.Vec3i(37,17,25);
        data.marketBounds=new int[]{-21,origin.getY(),origin.getZ(),15,origin.getY()+16,c.marketOffsetZ+13};
        for(BlockPos p:BlockPos.betweenClosed(origin,origin.offset(size.getX()-1,size.getY()-1,size.getZ()-1))) {
            var state=level.getBlockState(p);var block=state.getBlock();
            if(level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container){container.setLootTable(null);container.clearContent();}
            if(block==Blocks.MUSHROOM_STEM || block==Blocks.RED_MUSHROOM_BLOCK || block==Blocks.BROWN_MUSHROOM_BLOCK || foliage(state) || block==Blocks.CHEST)
                level.removeBlock(p,false);
            else if(block==Blocks.MYCELIUM)level.setBlock(p,Blocks.GRASS_BLOCK.defaultBlockState(),2);
        }
        tint(level,origin,origin.offset(size),"plains");
        // Grade only the existing building footprint, retaining the island's outer silhouette.
        int front=c.marketOffsetZ-2;
        for(int x=-5;x<=6;x++)for(int z=front;z<=front+10;z++) {
            for(int y=c.spawnY+1;y<=c.spawnY+12;y++)level.removeBlock(new BlockPos(x,y,z),false);
            for(int y=c.spawnY-2;y<c.spawnY;y++)if(level.getBlockState(new BlockPos(x,y,z)).isAir())level.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,c.spawnY,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        }
        for(int x=-7;x<=8;x++)for(int z=front-8;z<front;z++) {
            boolean supported=false;
            for(int y=c.spawnY-8;y<=c.spawnY;y++)if(!level.getBlockState(new BlockPos(x,y,z)).isAir()){supported=true;break;}
            if(!supported)continue;
            for(int y=c.spawnY+1;y<=c.spawnY+12;y++)level.removeBlock(new BlockPos(x,y,z),false);
            for(int y=c.spawnY-2;y<c.spawnY;y++)if(level.getBlockState(new BlockPos(x,y,z)).isAir())level.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,c.spawnY,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        }
        var house=required(level,"minecraft:village/plains/houses/plains_butcher_shop_1");
        var houseOrigin=new BlockPos(6,c.spawnY+1,front);
        var placement=new StructurePlaceSettings().setRotation(net.minecraft.world.level.block.Rotation.CLOCKWISE_90).setIgnoreEntities(true)
            .addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE);
        if(!house.placeInWorld(level,houseOrigin,houseOrigin,placement,RandomSource.create(921),2))throw new IllegalStateException("Could not place the village shop");
        // A supplied village forge gives the outpost a useful public workshop and kitchen.
        var workshopOrigin=new BlockPos(-18,c.spawnY+1,front+1);
        for(int x=-18;x<=-10;x++)for(int z=front+1;z<=front+11;z++) {
            for(int y=c.spawnY+1;y<=c.spawnY+10;y++)level.removeBlock(new BlockPos(x,y,z),false);
            for(int y=c.spawnY-2;y<c.spawnY;y++)if(level.getBlockState(new BlockPos(x,y,z)).isAir())level.setBlock(new BlockPos(x,y,z),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(new BlockPos(x,c.spawnY,z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        }
        required(level,"minecraft:village/plains/houses/plains_weaponsmith_1").placeInWorld(level,workshopOrigin,workshopOrigin,
            new StructurePlaceSettings().setIgnoreEntities(true).addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor.INSTANCE),RandomSource.create(level.getSeed()+614),2);
        BlockPos stove=workshopOrigin.offset(4,2,3);
        var stoveState=net.minecraft.world.level.block.Block.byItem(Campaign.item("farmersdelight:stove")).defaultBlockState();
        if(stoveState.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT))stoveState=stoveState.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT,true);
        level.setBlock(stove,stoveState,2);
        level.setBlock(stove.above(),net.minecraft.world.level.block.Block.byItem(Campaign.item("farmersdelight:cooking_pot")).defaultBlockState(),2);
        var oldChest=workshopOrigin.offset(6,1,4);
        if(level.getBlockEntity(oldChest) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container){container.setLootTable(null);container.clearContent();}
        level.setBlock(oldChest,Blocks.CRAFTING_TABLE.defaultBlockState(),2);
        data.spawns[2]=xyz(new BlockPos(3,c.spawnY+1,front-5));
        data.trader=xyz(new BlockPos(3,c.spawnY+2,front+5));
        data.communityChest=xyz(new BlockPos(5,c.spawnY+1,front-2));
        level.setBlock(pos(data.communityChest),Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING,net.minecraft.core.Direction.NORTH),2);
        if(level.getBlockEntity(pos(data.communityChest)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
            var named=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST);
            named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Community chest"));
            chest.applyComponentsFromItemStack(named);
        }
        // Record the placed terrain and shop, excluding removable plants.
        BlockPos top=new BlockPos(data.marketBounds[3],Math.max(data.marketBounds[4],c.spawnY+9),data.marketBounds[5]);
        data.marketBounds[4]=top.getY();
        for(BlockPos p:BlockPos.betweenClosed(origin,top)) {
            var state=level.getBlockState(p);
            if(!state.isAir() && !foliage(state)){data.marketBlocks.add(p.asLong());data.marketColumns.add(column(p));}
        }
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(origin),net.minecraft.world.phys.Vec3.atLowerCornerOf(top)).inflate(3)).forEach(net.minecraft.world.entity.Entity::discard);
    }
    private static BlockPos place(ServerLevel level, CampaignData data, String kind, String name, int x, int y, int z, long seed) {
        var template=required(level,name);var size=template.getSize();var origin=new BlockPos(x-size.getX()/2,y,z-size.getZ()/2);
        var settings=new StructurePlaceSettings().setIgnoreEntities(kind.startsWith("market"));
        if(kind.startsWith("market"))settings.addProcessor(net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor.AIR);
        if (name.startsWith(PREFIX)) level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolder(ResourceLocation.parse(name)).orElseThrow().value().list().forEach(settings::addProcessor);
        if (!template.placeInWorld(level,origin,origin,settings,RandomSource.create(seed),2)) throw new IllegalStateException("Could not place "+name);
        String biome=name.substring(name.indexOf(':')+1);
        if (name.startsWith("robs_")) biome=switch(biome){case "oasis"->"desert";case "igloo","tundra"->"snowy_plains";case "mesa_mine"->"badlands";default->"forest";};
        tint(level,origin.offset(-12,-16,-12),origin.offset(size.getX()+12,size.getY()+24,size.getZ()+12),biome);
        for (BlockPos p:BlockPos.betweenClosed(origin,origin.offset(size.getX()-1,size.getY()-1,size.getZ()-1))) {
            if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity box) {
                box.clearContent(); box.setLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse("farskies:island/treasure")),seed);
            }
        }
        if(!kind.startsWith("market") && !kind.equals("barren")) {
            // Put rewards on the actual walkable surface, rather than hiding every chest in a landmark.
            BlockPos cache=cacheSurface(level,origin,template);
            if(cache!=null) {
                level.setBlock(cache,Blocks.CHEST.defaultBlockState(),2);
                if(level.getBlockEntity(cache) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity box)
                    box.setLootTable(net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse("farskies:island/"+(kind.equals("contested")?"contested":kind.equals("treasure")?"treasure":"supplies"))),seed);
            }
        }
        data.islands.add(kind+" "+name+" "+x+","+y+","+z);
        return origin;
    }
    public static boolean foliage(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.LEAVES) || state.getBlock() instanceof net.minecraft.world.level.block.BushBlock
            || state.getBlock() instanceof net.minecraft.world.level.block.VineBlock;
    }
    public static long column(BlockPos p){return net.minecraft.world.level.ChunkPos.asLong(p.getX(),p.getZ());}
    private static BlockPos cacheSurface(ServerLevel level,BlockPos origin,StructureTemplate template) {
        var size=template.getSize();var middle=origin.offset(size.getX()/2,0,size.getZ()/2);
        BlockPos best=null;double score=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.betweenClosed(origin,origin.offset(size.getX()-1,size.getY()-1,size.getZ()-1))) {
            var state=level.getBlockState(p);
            if(!state.isCollisionShapeFullBlock(level,p) || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS))continue;
            var above=p.above();
            if(!(level.getBlockState(above).isAir()||foliage(level.getBlockState(above))) || !(level.getBlockState(above.above()).isAir()||foliage(level.getBlockState(above.above()))))continue;
            double distance=Math.abs(p.getX()-middle.getX())+Math.abs(p.getZ()-middle.getZ())-p.getY()*.15;
            if(distance<score){score=distance;best=above;}
        }
        if(best!=null){level.removeBlock(best,false);if(foliage(level.getBlockState(best.above())))level.removeBlock(best.above(),false);}
        return best;
    }
    private static void tint(ServerLevel level,BlockPos min,BlockPos max,String biome) {
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++) for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++) level.getChunk(x,z);
        var holder=level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(ResourceLocation.withDefaultNamespace(biome)).orElseThrow();
        // The native fill command caps volume. Small chunk-aligned batches also bound work per edit.
        for(int x=min.getX()>>4;x<=max.getX()>>4;x++)for(int z=min.getZ()>>4;z<=max.getZ()>>4;z++)for(int y=min.getY();y<=max.getY();y+=128) {
            var from=new BlockPos(Math.max(min.getX(),x*16),y,Math.max(min.getZ(),z*16));
            var to=new BlockPos(Math.min(max.getX(),x*16+15),Math.min(max.getY(),y+127),Math.min(max.getZ(),z*16+15));
            net.minecraft.server.commands.FillBiomeCommand.fill(level,from,to,holder).ifRight(e->{throw new IllegalStateException(e);});
        }
    }
    public static StructureTemplate required(ServerLevel level,String id) { return level.getStructureManager().get(ResourceLocation.parse(id)).orElseThrow(()->new IllegalStateException("Missing template "+id)); }
    private static BlockPos surface(ServerLevel level,BlockPos origin,StructureTemplate template,int offsetX) {
        var size=template.getSize();int cx=origin.getX()+size.getX()/2+offsetX,cz=origin.getZ()+size.getZ()/2;
        for(int radius=0;radius<size.getX();radius++) for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            for(int y=origin.getY()+size.getY();y>=origin.getY();y--) {
                BlockPos p=new BlockPos(cx+dx,y,cz+dz);
                if(safe(level,p) && level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.DIRT))return p;
            }
        }
        throw new IllegalStateException("No safe ground in "+origin);
    }
    public static int[] xyz(BlockPos p) {return new int[]{p.getX(),p.getY(),p.getZ()};}
    public static BlockPos pos(int[] p) {return new BlockPos(p[0],p[1],p[2]);}
    public static boolean safe(ServerLevel level, BlockPos p) {
        BlockState floor = level.getBlockState(p.below());
        return floor.isCollisionShapeFullBlock(level, p.below()) && level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir();
    }

    /** A destroyed home never creates an invulnerable replacement or restores loot. */
    public static BlockPos recoverRally(ServerLevel level, CampaignData data, int side) {
        BlockPos original = pos(data, side);
        if (safe(level, original)) return original;
        for (int radius = 0; radius <= 512; radius += 16) {
            for (int dx = -radius; dx <= radius; dx += 16) for (int dz = -radius; dz <= radius; dz += 16) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                int x = original.getX() + dx, z = original.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos candidate = new BlockPos(x, y, z);
                if (y > level.getMinBuildHeight() && safe(level, candidate)) {
                    FarSkies.LOG.info("RALLY {} temporarily using {} instead of destroyed {}", side, candidate, original);
                    return candidate;
                }
            }
        }
        return null;
    }

    public static BlockPos pos(CampaignData data, int index) { int[] p = data.spawns[index]; return new BlockPos(p[0], p[1], p[2]); }
}
