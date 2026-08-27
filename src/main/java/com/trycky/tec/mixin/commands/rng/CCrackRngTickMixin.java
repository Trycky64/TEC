package com.trycky.tec.mixin.commands.rng;

import com.trycky.tec.feature.CCrackRng;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class CCrackRngTickMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tec$tickRngCracker(CallbackInfo ci) {
        CCrackRng.tick();
    }
}
