package com.trycky.tec.mixin.rngevents;

import com.mojang.authlib.GameProfile;
import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin extends AbstractClientPlayer {
    protected LocalPlayerMixin(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V"))
    private void tec$detectNearbyXp(CallbackInfo ci) {
        if (!level().getEntitiesOfClass(ExperienceOrb.class, getBoundingBox().inflate(0.5), entity -> true).isEmpty()) {
            // 1.21 XP pickup/mending is not predictable enough from client state.
            PlayerRandCracker.onXpOrb();
            PlayerRandCracker.onMending();
        }

        if (PlayerRandCracker.knowsSeed()) {
            ItemStack boots = getItemBySlot(EquipmentSlot.FEET);
            var enchantments = registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            var frostWalker = enchantments.getHolderOrThrow(Enchantments.FROST_WALKER);
            var soulSpeed = enchantments.getHolderOrThrow(Enchantments.SOUL_SPEED);
            if (EnchantmentHelper.getItemEnchantmentLevel(frostWalker, boots) > 0) {
                PlayerRandCracker.onFrostWalker();
            } else if (EnchantmentHelper.getItemEnchantmentLevel(soulSpeed, boots) > 0) {
                PlayerRandCracker.onSoulSpeed();
            }
        }
    }

    @Inject(method = "drop", at = @At("HEAD"))
    private void tec$onDrop(boolean dropAll, CallbackInfoReturnable<ItemEntity> cir) {
        PlayerRandCracker.onDropItem();
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void tec$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        PlayerRandCracker.onDamage();
    }
}
