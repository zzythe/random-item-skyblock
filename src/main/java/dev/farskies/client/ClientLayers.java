package dev.farskies.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid="farskies",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClientLayers {
    @SubscribeEvent public static void register(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CROSSHAIR,ResourceLocation.parse("farskies:market_fade"),MarketBoundary::hud);
        event.registerAbove(VanillaGuiLayers.SELECTED_ITEM_NAME,ResourceLocation.parse("farskies:delivery"),CampaignView::hud);
    }
}
