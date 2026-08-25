package com.trycky.tec;

import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TryckysEnchantmentCracker.MODID)
public final class TryckysEnchantmentCracker {
    public static final String MODID = "tec";
    public static final Logger LOGGER = LoggerFactory.getLogger("TEC");

    public TryckysEnchantmentCracker() {
        LOGGER.info("Trycky's Enchantment Cracker initialized.");
    }
}
