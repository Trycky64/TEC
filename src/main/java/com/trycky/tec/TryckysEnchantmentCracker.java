package com.trycky.tec;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

/**
 * Client-only entry point for Trycky's Enchantment Cracker.
 */
@Mod(value = TryckysEnchantmentCracker.MOD_ID, dist = Dist.CLIENT)
public final class TryckysEnchantmentCracker {
    public static final String MOD_ID = "tec";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TryckysEnchantmentCracker(IEventBus modEventBus) {
        modEventBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Trycky's Enchantment Cracker initialized.");
    }
}
