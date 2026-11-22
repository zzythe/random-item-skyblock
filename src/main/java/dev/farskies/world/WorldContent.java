package dev.farskies.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WorldContent {
    public static final DeferredRegister<StructureType<?>> TYPES=DeferredRegister.create(Registries.STRUCTURE_TYPE,"farskies");
    public static final java.util.function.Supplier<StructureType<IslandStructure>> ISLAND=TYPES.register("island",()->()->IslandStructure.CODEC);
}
