package dev.farskies.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.farskies.Settings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;

/** Native seeded structure generation using upstream templates and processors. No map of island coordinates. */
public final class IslandStructure extends Structure {
    public static final MapCodec<IslandStructure> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        settingsCodec(i), StructureTemplatePool.CODEC.fieldOf("pool").forGetter(s->s.pool),
        Codec.STRING.fieldOf("island_biome").forGetter(s->s.biome),
        Codec.STRING.fieldOf("loot").forGetter(s->s.loot),
        Codec.intRange(0,7).optionalFieldOf("size",0).forGetter(s->s.size)
    ).apply(i,IslandStructure::new));
    private final Holder<StructureTemplatePool> pool;
    private final String biome,loot;
    private final int size;
    private final JigsawStructure delegate;
    public IslandStructure(StructureSettings settings,Holder<StructureTemplatePool> pool,String biome,String loot,int size) {
        super(settings);this.pool=pool;this.biome=biome;this.loot=loot;this.size=size;
        delegate=new JigsawStructure(settings,pool,size>0?size:large()?2:1,UniformHeight.of(VerticalAnchor.absolute(126),VerticalAnchor.absolute(143)),false);
    }
    /** Authored multi-piece landmarks keep their own chests, loot tables and hazards. */
    public boolean large(){return pool.unwrapKey().map(k->java.util.Set.of("floating_islands","mss").contains(k.location().getNamespace())).orElse(false);}
    public String loot(){return loot;}
    @Override public java.util.Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        int x=context.chunkPos().getMiddleBlockX(),z=context.chunkPos().getMiddleBlockZ();
        var c=Settings.INSTANCE;
        // Multi-piece Moog islands reach about 24 blocks further from their start than Ember's landmarks.
        int reach=size>2?24:0;
        if(Math.abs(z)<(large()?56+reach:32) && Math.abs(x)<c.homeDistance+40+reach)return java.util.Optional.empty();
        if(Math.abs(x)<(large()?80+reach:52) && z>c.marketOffsetZ-(large()?80+reach:52) && z<c.marketOffsetZ+(large()?90+reach:60))return java.util.Optional.empty();
        for(int side:new int[]{-1,1})if(Math.hypot(x-side*c.homeDistance,z)<(large()?76+reach:44))return java.util.Optional.empty();
        return delegate.findGenerationPoint(context);
    }
    @Override public StructureType<?> type(){return WorldContent.ISLAND.get();}
    @Override public void afterPlace(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunkPos,PiecesContainer pieces) {
        var bounds=pieces.calculateBoundingBox();
        var holder=level.registryAccess().registryOrThrow(Registries.BIOME).getHolder(ResourceLocation.parse(biome)).orElseThrow();
        var chunk=level.getChunk(chunkPos.x,chunkPos.z);
        chunk.fillBiomesFromNoise((qx,qy,qz,sampler)->{
            int x=qx*4,z=qz*4;
            return x>=bounds.minX()-8 && x<=bounds.maxX()+8 && z>=bounds.minZ()-8 && z<=bounds.maxZ()+8?holder:chunk.getNoiseBiome(qx,qy,qz);
        },level.getLevel().getChunkSource().randomState().sampler());
        for(BlockPos p:BlockPos.betweenClosed(Math.max(bounds.minX(),clip.minX()),bounds.minY(),Math.max(bounds.minZ(),clip.minZ()),Math.min(bounds.maxX(),clip.maxX()),bounds.maxY(),Math.min(bounds.maxZ(),clip.maxZ())))
            if(level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity box && !large()){box.clearContent();box.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse("farskies:island/"+loot)),random.nextLong());}
        if(loot.equals("none"))return;
        int cx=(bounds.minX()+bounds.maxX())/2,cz=(bounds.minZ()+bounds.maxZ())/2;
        if((cx>>4)!=chunkPos.x || (cz>>4)!=chunkPos.z)return;
        BlockPos best=null;double score=Double.MAX_VALUE;
        for(BlockPos p:BlockPos.betweenClosed(Math.max(bounds.minX(),clip.minX()),bounds.minY(),Math.max(bounds.minZ(),clip.minZ()),Math.min(bounds.maxX(),clip.maxX()),bounds.maxY(),Math.min(bounds.maxZ(),clip.maxZ()))) {
            if(!clip.isInside(p))continue;
            var state=level.getBlockState(p);
            if(!state.isCollisionShapeFullBlock(level,p) || state.is(net.minecraft.tags.BlockTags.LEAVES) || state.is(net.minecraft.tags.BlockTags.LOGS))continue;
            if((!level.getBlockState(p.above()).isAir()&&!dev.farskies.WorldSetup.foliage(level.getBlockState(p.above()))) || (!level.getBlockState(p.above(2)).isAir()&&!dev.farskies.WorldSetup.foliage(level.getBlockState(p.above(2)))))continue;
            double value=Math.abs(p.getX()-cx)+Math.abs(p.getZ()-cz)-p.getY()*.1;
            if(value<score){score=value;best=p.above();}
        }
        if(best!=null && clip.isInside(best)) {
            if(dev.farskies.WorldSetup.foliage(level.getBlockState(best.above())))level.setBlock(best.above(),Blocks.AIR.defaultBlockState(),2);
            level.setBlock(best,Blocks.CHEST.defaultBlockState(),2);
            String table=Math.abs(cx)<40 && Math.abs(cz)<150?"contested":loot;
            if(level.getBlockEntity(best) instanceof RandomizableContainerBlockEntity box)box.setLootTable(ResourceKey.create(Registries.LOOT_TABLE,ResourceLocation.parse("farskies:island/"+table)),random.nextLong());
        }
    }
}
