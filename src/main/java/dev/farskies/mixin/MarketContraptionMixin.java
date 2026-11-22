package dev.farskies.mixin;

import dev.farskies.Market;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets="com.simibubi.create.content.contraptions.Contraption",remap=false)
public abstract class MarketContraptionMixin {
    @Inject(method="moveBlock",at=@At("HEAD"),cancellable=true,remap=false)
    private void originalMarketStaysPut(Level level,Direction direction,Queue<BlockPos> frontier,Set<BlockPos> visited,CallbackInfoReturnable<Boolean> callback){
        if(!frontier.isEmpty()&&Market.protectedOriginal(level,frontier.peek()))callback.setReturnValue(false);
    }
}
