package dev.farskies.mixin;

import dev.farskies.Market;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native cube rewards edit blocks directly rather than firing player break events. */
@Mixin(Level.class)
public abstract class MarketWorldEditMixin {
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),cancellable=true)
    private void keepMarket(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> result){
        if(Market.protectedWorldEdit((Level)(Object)this,pos,state))result.setReturnValue(false);
    }
}
