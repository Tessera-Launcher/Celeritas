//? if >=1.8 {
package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.VideoOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.gui.CeleritasVideoOptionsScreen;

@Mixin(Minecraft.class)
public abstract class MinecraftVideoOptionsMixin {
    @Shadow
    private Screen screen;

    @Inject(method = "openScreen", at = @At("HEAD"), cancellable = true)
    private void celeritas$replaceVideoOptions(Screen target, CallbackInfo ci) {
        if (target instanceof VideoOptionsScreen) {
            Screen parent = this.screen instanceof VideoOptionsScreen ? null : this.screen;
            ((Minecraft) (Object) this).openScreen(new CeleritasVideoOptionsScreen(parent));
            ci.cancel();
        }
    }
}
//?}
