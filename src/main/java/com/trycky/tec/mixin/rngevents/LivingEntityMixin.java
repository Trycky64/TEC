package com.trycky.tec.mixin.rngevents;

import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow protected int useItemRemaining;
    @Shadow public abstract boolean isAlive();

    protected LivingEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "pushEntities", at = @At("HEAD"))
    private void tec$onEntityCramming(CallbackInfo ci) {
        if (tec$isLocalPlayer() && level().getEntities(this, getBoundingBox(), Entity::isPushable).size() >= 24) {
            PlayerRandCracker.onEntityCramming();
        }
    }

    @Inject(method = "triggerItemUseEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getEatingSound(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/sounds/SoundEvent;"))
    private void tec$onEat(ItemStack stack, int particleCount, CallbackInfo ci) {
        if (tec$isLocalPlayer()) {
            PlayerRandCracker.onEat(stack, particleCount, useItemRemaining);
        }
    }

    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isAlive()Z", ordinal = 0))
    private void tec$onUnderwater(CallbackInfo ci) {
        if (tec$isLocalPlayer() && isAlive() && isEyeInFluid(FluidTags.WATER)) {
            PlayerRandCracker.onUnderwater();
        }
    }

    @Inject(method = "tickEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isInvisible()Z"))
    private void tec$onPotionParticles(CallbackInfo ci) {
        if (tec$isLocalPlayer()) {
            PlayerRandCracker.onPotionParticles();
        }
    }

    @Unique
    private boolean tec$isLocalPlayer() {
        return (Object) this instanceof LocalPlayer;
    }
}
