package com.trycky.tec.mixin.rngevents;

import com.mojang.brigadier.StringReader;
import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "sendCommand", at = @At("HEAD"))
    private void tec$onSendCommand(String command, CallbackInfo ci) {
        StringReader reader = new StringReader(command);
        String name = reader.canRead() ? reader.readUnquotedString() : "";
        if ("give".equals(name)) {
            PlayerRandCracker.onGiveCommand();
        }
    }
}
