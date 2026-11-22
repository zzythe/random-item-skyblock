package dev.farskies.mixin;

import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Gui.class)
public interface GuiHudAccessor {
    @Accessor("overlayMessageTime") int farskies$overlayMessageTime();
    @Accessor("toolHighlightTimer") int farskies$toolHighlightTimer();
}
