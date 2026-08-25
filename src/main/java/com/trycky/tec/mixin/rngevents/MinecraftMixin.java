package com.trycky.tec.mixin.rngevents;

import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "setLevel", at = @At("HEAD"))
    private void tec$onSetLevel(
            ClientLevel level,
            ReceivingLevelScreen.Reason reason,
            CallbackInfo ci
    ) {
        PlayerRandCracker.onWorldChanged();
    }
}
