package com.trycky.tec.mixin.rngevents;

import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {
    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"))
    private void tec$onWaterSplash(CallbackInfo ci) {
        if (tec$isLocalPlayer()) PlayerRandCracker.onSwimmingStart();
    }

    @Inject(method = "playAmethystStepSound", at = @At("HEAD"))
    private void tec$onAmethystChime(CallbackInfo ci) {
        if (tec$isLocalPlayer()) PlayerRandCracker.onAmethystChime();
    }

    @Inject(method = "spawnSprintParticle", at = @At("HEAD"))
    private void tec$onSprintParticle(CallbackInfo ci) {
        if (tec$isLocalPlayer()) PlayerRandCracker.onSprinting();
    }

    @Unique
    private boolean tec$isLocalPlayer() {
        return (Object) this instanceof LocalPlayer;
    }
}
