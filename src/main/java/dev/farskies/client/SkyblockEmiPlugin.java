package dev.farskies.client;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.farskies.ItemDrops;

/** Keep absent wood integrations out of the recipe index, using the same filter as supplies. */
@EmiEntrypoint
public final class SkyblockEmiPlugin implements EmiPlugin {
    @Override public void register(EmiRegistry registry){
        registry.removeEmiStacks(stack->{var id=stack.getId();return id!=null&&ItemDrops.unavailableIntegration(id.getNamespace(),id.getPath());});
    }
}
