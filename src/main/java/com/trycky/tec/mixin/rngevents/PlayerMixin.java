package com.trycky.tec.mixin.rngevents;

import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"))
    private void tec$onDrop(ItemStack stack, boolean randomDirection, boolean includeThrowerName, CallbackInfoReturnable<ItemEntity> cir) {
        if ((Object) this instanceof LocalPlayer) {
            PlayerRandCracker.onDropItem();
        }
    }
}
