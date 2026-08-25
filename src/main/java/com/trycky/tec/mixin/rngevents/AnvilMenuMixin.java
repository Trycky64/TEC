package com.trycky.tec.mixin.rngevents;

import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public class AnvilMenuMixin {
    @Inject(method = "onTake", at = @At("HEAD"))
    private void tec$onTake(Player player, ItemStack stack, CallbackInfo ci) {
        if (!player.getAbilities().instabuild) {
            PlayerRandCracker.onAnvilUse();
        }
    }
}
