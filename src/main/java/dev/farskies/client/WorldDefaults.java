package dev.farskies.client;

import dev.farskies.FarSkies;
import dev.farskies.Settings;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Select once per creation screen, allowing the player to change the selection afterward. */
@EventBusSubscriber(modid = FarSkies.ID, value = Dist.CLIENT)
public final class WorldDefaults {
    private static final Set<CreateWorldScreen> SELECTED = Collections.newSetFromMap(new WeakHashMap<>());

    @SubscribeEvent
    public static void init(ScreenEvent.Init.Post event) {
        if (!Settings.INSTANCE.campaignEnabled || !(event.getScreen() instanceof CreateWorldScreen screen)
                || !SELECTED.add(screen)) return;
        var state = screen.getUiState();
        state.setGenerateStructures(true);
        var presets = state.getSettings().worldgenLoadContext().registryOrThrow(Registries.WORLD_PRESET);
        presets.getHolder(ResourceLocation.fromNamespaceAndPath(FarSkies.ID, "far_skies"))
                .ifPresent(preset -> state.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset)));
    }
}
