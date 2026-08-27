/*
 * Based on ClientCommands by Earthcomputer and contributors.
 * ClientCommands is licensed under LGPL-3.0-or-later.
 */
package com.trycky.tec.mixin.commands.enchant;

import com.trycky.tec.feature.EnchantmentCracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentScreen.class)
public abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
    protected EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void tec$renderPredictionOverlay(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            CallbackInfo ci
    ) {
        EnchantmentCracker.drawEnchantmentGuiOverlay(graphics);
    }

    @Inject(
            method = "mouseClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleInventoryButtonClick(II)V"
            )
    )
    private void tec$onItemEnchanted(
            double mouseX,
            double mouseY,
            int button,
            CallbackInfoReturnable<Boolean> cir
    ) {
        EnchantmentCracker.onEnchantedItem();
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tec$addCrackingButton(CallbackInfo ci) {
        addRenderableWidget(Button.builder(
                Component.translatable("enchCrack.addInfo"),
                button -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.level != null) {
                        EnchantmentCracker.addEnchantmentSeedInfo(minecraft.level, getMenu());
                    }
                }
        ).bounds(width - 154, 4, 150, 20).build());
    }
}
