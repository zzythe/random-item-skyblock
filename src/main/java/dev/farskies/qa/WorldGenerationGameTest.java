package dev.farskies.qa;

import dev.farskies.CampaignData;
import dev.farskies.FarSkies;
import dev.farskies.Settings;
import dev.farskies.WorldSetup;
import java.util.List;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LoggerChunkProgressListener;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the real upstream generator and real supplied templates in a fresh isolated dimension. */
@GameTestHolder("farskies")
@PrefixGameTestTemplate(false)
public final class WorldGenerationGameTest {
    @GameTest(template = "empty", timeoutTicks = 2400) public static void newCampaignWorldBoots(GameTestHelper helper) {
        try {
            MinecraftServer server = helper.getLevel().getServer();
            var field = MinecraftServer.class.getDeclaredField("storageSource"); field.setAccessible(true);
            var storage = (LevelStorageSource.LevelStorageAccess) field.get(server);
            var preset = server.registryAccess().registryOrThrow(Registries.WORLD_PRESET).get(ResourceLocation.parse("farskies:far_skies"));
            helper.assertTrue(preset != null, "Far Skies world preset missing");
            var key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("farskies", "qa_" + UUID.randomUUID().toString().replace("-", "")));
            var levelData = new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData());
            var options=server.getWorldData().getClass().getDeclaredField("worldOptions");options.setAccessible(true);
            Object previousOptions=options.get(server.getWorldData());
            options.set(server.getWorldData(),new net.minecraft.world.level.levelgen.WorldOptions(3232L,true,false));
            try {
            ServerLevel level = new ServerLevel(server, Util.backgroundExecutor(), storage, levelData, key, preset.overworld().orElseThrow(), LoggerChunkProgressListener.createCompleted(), false, 3232L, List.of(), false, null);
            GameTestProfileCache.SHADOWS.add(level);
                CampaignData data = new CampaignData(); WorldSetup.initialize(level, data);
                helper.assertTrue(data.initialized, "Campaign initialization did not complete");
                var sky=level.registryAccess().registryOrThrow(Registries.BIOME).get(ResourceLocation.parse("farskies:open_sky"));
                helper.assertTrue(!sky.getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.MONSTER).isEmpty(),"Player-built mob farms cannot spawn anything in the void");
                for (int i = 0; i < 3; i++) helper.assertTrue(WorldSetup.safe(level, WorldSetup.pos(data, i)), "Unsafe supplied-template spawn " + i);
                helper.assertTrue(Math.abs(data.spawns[0][0] - data.spawns[1][0]) > Settings.INSTANCE.homeDistance * 1.8, "Faction spawns are too close");
                int land = 0, voids = 0;
                var generator = level.getChunkSource().getGenerator();
                for (int x = 300; x < 1800; x += 32) for (int z = 300; z < 1800; z += 32) {
                    int height = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, level.getChunkSource().randomState());
                    if (height > level.getMinBuildHeight()) land++; else voids++;
                }
                FarSkies.LOG.info("WORLD_QA landSamples={} voidSamples={} West={} East={} Market={}", land, voids, WorldSetup.pos(data, 0), WorldSetup.pos(data, 1), WorldSetup.pos(data, 2));
                helper.assertTrue(land == 0 && voids > 0, "Void generator produced unwanted terrain");
                helper.assertTrue(data.islands.size()==4,"Only the core market should be placed explicitly");
                helper.assertTrue(data.spawns[0][1]==data.spawns[1][1] && data.spawns[0][2]==data.spawns[1][2],"Bases are not aligned");
                helper.assertTrue(data.marketBounds[3]-data.marketBounds[0]>=18,"Market did not get its larger island");
                helper.assertTrue(data.marketBounds[2]<30,"Market is still too far from the faction line");
                helper.assertTrue(WorldSetup.safe(level,WorldSetup.pos(data.trader)),"Shop villager anchor is obstructed");
                helper.assertTrue(level.getBlockEntity(WorldSetup.pos(data.communityChest)) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity,"Shared chest missing");
                helper.assertTrue(data.marketBlocks.size()>300 && !data.marketColumns.isEmpty(),"Market protection mask missing");
                int starts=0,caches=0;
                for(int x=20;x<32;x++)for(int z=20;z<32;z++) {
                    var chunk=level.getChunk(x,z);
                    starts+=chunk.getAllStarts().values().stream().filter(net.minecraft.world.level.levelgen.structure.StructureStart::isValid).count();
                    caches+=chunk.getBlockEntitiesPos().stream().filter(p->level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity).count();
                }
                helper.assertTrue(starts>=3,"Native worldgen did not generate exploration islands in newly explored chunks: "+starts);
                helper.assertTrue(caches>=2,"Native islands have no loot caches: "+caches);
                FarSkies.LOG.info("NATIVE_WORLD_QA structures={} caches={}",starts,caches);
                // Moog's soaring islands: rarer, multi-piece, and must actually build blocks and chests in the void.
                var structures=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                var soaring=new java.util.ArrayList<net.minecraft.world.level.levelgen.structure.StructureStart>();var kinds=new java.util.HashSet<String>();
                for(int x=-40;x<40;x++)for(int z=-40;z<40;z++)
                    for(var start:level.getChunk(x,z,net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS).getAllStarts().values()) {
                        var id=structures.getKey(start.getStructure());
                        if(start.isValid() && id!=null && id.getPath().startsWith("soaring_")){soaring.add(start);kinds.add(id.getPath());}
                    }
                helper.assertTrue(soaring.size()>=8 && kinds.size()>=4,"Too few soaring islands: "+soaring.size()+" "+kinds);
                var large=soaring.stream().filter(s->s.getPieces().size()>1).findFirst().orElseThrow(()->new IllegalStateException("No multi-piece soaring island"));
                var box=large.getBoundingBox();int solid=0,boxes=0;
                for(int cx=box.minX()>>4;cx<=box.maxX()>>4;cx++)for(int cz=box.minZ()>>4;cz<=box.maxZ()>>4;cz++)level.getChunk(cx,cz);
                for(var p:net.minecraft.core.BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
                    if(!level.getBlockState(p).isAir())solid++;
                    if(level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity)boxes++;
                }
                helper.assertTrue(solid>3000 && boxes>0,"Soaring island did not build: blocks="+solid+" containers="+boxes);
                FarSkies.LOG.info("SOARING_QA starts={} kinds={} sample={} pieces={} blocks={} containers={} box={}",soaring.size(),kinds.size(),structures.getKey(large.getStructure()),large.getPieces().size(),solid,boxes,box);


            } finally { options.set(server.getWorldData(),previousOptions); }
        } catch (Exception e) { throw new RuntimeException("World generation smoke test failed", e); }
        helper.succeed();
    }
}
