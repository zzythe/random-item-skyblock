package dev.farskies;

import dev.farskies.world.IslandStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Gives previously generated barren islands a one-time cache, without refilling looted chests. */
public final class IslandRewards {
    @SubscribeEvent public static void loaded(ChunkEvent.Load event){
        if(event.isNewChunk()||!Campaign.enabled()||!(event.getLevel() instanceof ServerLevel level)||level.dimension()!=Level.OVERWORLD)return;
        var position=event.getChunk().getPos();level.getServer().execute(()->{
            if(!Campaign.enabled())return;var data=CampaignData.get(level.getServer());if(!data.initialized||data.layoutVersion!=4)return;
            for(int cx=position.x-1;cx<=position.x+1;cx++)for(int cz=position.z-1;cz<=position.z+1;cz++){
                var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null)continue;
                for(var start:chunk.getAllStarts().values())if(start.isValid()&&start.getStructure() instanceof IslandStructure island){
                    String key=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(island)+"@"+chunk.getPos().toLong();if(data.checkedIslands.contains(key))continue;
                    var bounds=start.getBoundingBox();boolean complete=true;
                    for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++)if(level.getChunkSource().getChunkNow(x,z)==null)complete=false;
                    if(!complete)continue;
                    boolean chest=false;
                    for(BlockPos p:BlockPos.betweenClosed(bounds.minX(),bounds.minY(),bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()))if(level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity){chest=true;break;}
                    if(!chest){
                        BlockPos best=null;double score=Double.MAX_VALUE;
                        for(int x=bounds.minX();x<=bounds.maxX();x++)for(int z=bounds.minZ();z<=bounds.maxZ();z++){
                            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);var p=new BlockPos(x,y,z);if(!bounds.isInside(p.below())||!WorldSetup.safe(level,p))continue;
                            var floor=level.getBlockState(p.below());if(floor.is(net.minecraft.tags.BlockTags.LOGS)||floor.is(net.minecraft.tags.BlockTags.LEAVES))continue;
                            double distance=Math.abs(x-(bounds.minX()+bounds.maxX())/2)+Math.abs(z-(bounds.minZ()+bounds.maxZ())/2);
                            if(distance<score){best=p;score=distance;}
                        }
                        if(best==null)continue;
                        level.setBlockAndUpdate(best,Blocks.CHEST.defaultBlockState());
                        if(level.getBlockEntity(best) instanceof RandomizableContainerBlockEntity box)box.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse("farskies:island/"+(island.loot().equals("none")?"supplies":island.loot()))),level.random.nextLong());
                    }
                    data.checkedIslands.add(key);data.setDirty();
                }
            }
        });
    }
}
