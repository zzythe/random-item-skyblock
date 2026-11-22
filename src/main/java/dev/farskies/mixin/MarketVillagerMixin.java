package dev.farskies.mixin;

import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The market has one saved server-time stock cycle, independent of village job sites. */
@Mixin(Villager.class)
public abstract class MarketVillagerMixin {
    @org.spongepowered.asm.mixin.Unique
    private boolean isMarket() { return ((Villager)(Object)this).getTags().contains("farskies_market_trader"); }
    // Override Merchant's default for this villager without changing the vanilla trading screen.
    public boolean showProgressBar() { return !isMarket(); }
    @Inject(method="restock",at=@At("HEAD"),cancellable=true)
    private void keepMarketStock(CallbackInfo ci) { if(isMarket())ci.cancel(); }
    @Inject(method={"shouldRestock","canRestock"},at=@At("HEAD"),cancellable=true)
    private void useMarketClock(CallbackInfoReturnable<Boolean> ci) { if(isMarket())ci.setReturnValue(false); }
}
