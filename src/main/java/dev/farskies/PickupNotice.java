package dev.farskies;

import fuzs.pickupnotifier.PickUpNotifier;
import fuzs.pickupnotifier.network.S2CTakeItemStackMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Direct grants do not fire ground-pickup events. Use the notifier's real network entry point. */
public final class PickupNotice {
    private PickupNotice() {}
    public static void send(ServerPlayer player, ItemStack item) {
        if (player instanceof net.neoforged.neoforge.common.util.FakePlayer) return;
        PickUpNotifier.NETWORK.sendTo(player, new S2CTakeItemStackMessage(item.copy()).toClientboundMessage());
    }
}
