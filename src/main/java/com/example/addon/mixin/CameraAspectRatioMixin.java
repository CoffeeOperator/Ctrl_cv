package com.example.addon.mixin;

import com.example.addon.modules.SydneyAspectRatio;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Camera; // Accurate 1.21.1 Mojmap camera path
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public class CameraAspectRatioMixin {

    @Inject(method = "getAspectRatio", at = @At("RETURN"), cancellable = true)
    private void onGetAspectRatio(CallbackInfoReturnable<Float> cir) {
        SydneyAspectRatio module = Modules.get().get(SydneyAspectRatio.class);
        if (module != null && module.isActive()) {
            // Overrides the calculation aspect loop ratio value directly before matrix rendering passes
            cir.setReturnValue(module.ratio.get().floatValue());
        }
    }
}
