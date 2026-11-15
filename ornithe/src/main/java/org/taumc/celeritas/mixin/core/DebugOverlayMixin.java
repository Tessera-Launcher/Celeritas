package org.taumc.celeritas.mixin.core;

//? if >=1.8 {
/*import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.overlay.DebugOverlay;
import net.minecraft.text.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.taumc.celeritas.impl.debug.CeleritasDebugStrings;

import java.util.List;

@Mixin(DebugOverlay.class)
public class DebugOverlayMixin {
    @ModifyReturnValue(method = "getSystemInfo", at = @At("RETURN"))
    private List<String> appendCeleritasSystemInfo(List<String> original) {
        original.add("");
        original.addAll(CeleritasDebugStrings.getStringsToRender().stream().map(pair -> {
            // TODO
            if (pair.right() == 0xFF55FF55) {
                return Formatting.GREEN + pair.left();
            } else {
                return pair.left();
            }
        }).toList());
        return original;
    }
}
*///?}
