package com.trycky.tec.mixin.commands.rng;

import com.trycky.tec.feature.CCrackRng;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CCrackRngPacketMixin {
    @Inject(method = "handleAddEntity", at = @At("HEAD"))
    private void tec$observeDroppedItem(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        CCrackRng.onEntityCreation(packet);
    }
}
