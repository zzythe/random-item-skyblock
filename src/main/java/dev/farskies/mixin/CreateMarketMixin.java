package dev.farskies.mixin;

import dev.farskies.Market;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Protect the original market even when a machine's source is inside the same claim. */
@Mixin(targets="com.simibubi.create.foundation.utility.BlockHelper",remap=false)
public abstract class CreateMarketMixin {
    @Inject(method="destroyBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;F)V",at=@At("HEAD"),cancellable=true,remap=false)
    private static void protect(Level level,BlockPos pos,float chance,CallbackInfo callback){if(Market.protectedOriginal(level,pos))callback.cancel();}
    @Inject(method="destroyBlock(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FLjava/util/function/Consumer;)V",at=@At("HEAD"),cancellable=true,remap=false)
    private static void protectDrop(Level level,BlockPos pos,float chance,Consumer<ItemStack> output,CallbackInfo callback){if(Market.protectedOriginal(level,pos))callback.cancel();}
    @Inject(method="destroyBlockAs",at=@At("HEAD"),cancellable=true,remap=false)
    private static void protectPlayer(Level level,BlockPos pos,Player player,ItemStack tool,float chance,Consumer<ItemStack> output,CallbackInfo callback){if(Market.protectedOriginal(level,pos))callback.cancel();}
}
