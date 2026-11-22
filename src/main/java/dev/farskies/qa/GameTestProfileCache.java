package dev.farskies.qa;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.players.GameProfileCache;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.minecraft.server.level.ServerLevel;

/** OPAC assumes a profile cache; vanilla's headless GameTestServer supplies null.
 * Test harness only. This class and all other QA fixtures are excluded from the release jar.
 */
@EventBusSubscriber(modid = "farskies")
public final class GameTestProfileCache {
    static final java.util.List<ServerLevel> SHADOWS = new java.util.ArrayList<>();
    @SubscribeEvent(priority = EventPriority.HIGHEST) public static void cache(ServerAboutToStartEvent event) {
        if (!Boolean.getBoolean("farskies.qa") || event.getServer().getProfileCache() != null) return;
        try {
            var field = MinecraftServer.class.getDeclaredField("services"); field.setAccessible(true);
            Services current = (Services) field.get(event.getServer());
            field.set(event.getServer(), new Services(current.sessionService(), current.servicesKeySet(), current.profileRepository(), new GameProfileCache(current.profileRepository(), new java.io.File("qa-usercache.json"))));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Could not initialize headless QA profile cache", e); }
    }
    @SubscribeEvent public static void close(ServerStoppingEvent event) {
        for (ServerLevel level : SHADOWS) try { level.close(); } catch (java.io.IOException e) { throw new RuntimeException(e); }
        SHADOWS.clear();
    }
}
