package dev.farskies;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(FarSkies.ID)
public final class FarSkies {
    public static final String ID = "farskies";
    public static final Logger LOG = LogUtils.getLogger();

    public FarSkies(IEventBus bus) {
        Settings.load();
        dev.farskies.world.WorldContent.TYPES.register(bus);
        bus.addListener(Network::register);
        NeoForge.EVENT_BUS.register(Factions.class);
        NeoForge.EVENT_BUS.register(Campaign.class);
        NeoForge.EVENT_BUS.register(Market.class);
        NeoForge.EVENT_BUS.register(LuckyEvents.class);
        NeoForge.EVENT_BUS.register(Shop.class);
        NeoForge.EVENT_BUS.register(RaidRecovery.class);
        NeoForge.EVENT_BUS.register(OpeningTruce.class);
        NeoForge.EVENT_BUS.register(SkyEvents.class);
        NeoForge.EVENT_BUS.register(IslandRewards.class);
        if (Boolean.getBoolean("farskies.clientSmoke")) try {
            Class.forName(Boolean.getBoolean("farskies.hudSmoke")?"dev.farskies.qa.HudSmoke":Boolean.getBoolean("farskies.recoverySmoke")?"dev.farskies.qa.RecoverySmoke":Boolean.getBoolean("farskies.visualSmoke")?"dev.farskies.qa.VisualSmoke":Boolean.getBoolean("farskies.gallerySmoke")?"dev.farskies.qa.GallerySmoke":"dev.farskies.qa.ClientSmoke").getMethod("register").invoke(null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Client QA driver unavailable",e); }
    }
}
