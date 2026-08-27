package com.trycky.tec;

import com.trycky.tec.command.CEnchantCommand;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TryckysEnchantmentCracker.MODID)
public final class TryckysEnchantmentCracker {

    public static final String MODID = "tec";
    public static final Logger LOGGER = LoggerFactory.getLogger("TEC");

    public TryckysEnchantmentCracker() {
        NeoForge.EVENT_BUS.addListener(
                RegisterClientCommandsEvent.class,
                CEnchantCommand::register
        );

        LOGGER.info("Trycky's Enchantment Cracker initialized.");
    }
}