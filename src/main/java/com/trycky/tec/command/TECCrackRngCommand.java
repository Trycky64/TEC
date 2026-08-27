/*
 * Based on CrackRNGCommand from ClientCommands by Earthcomputer and contributors.
 * ClientCommands is licensed under LGPL-3.0-or-later.
 * Adapted for NeoForge 1.21.1 / TEC.
 */
package com.trycky.tec.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.trycky.tec.feature.CCrackRng;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Client-side /teccrackrng command. */
public final class TECCrackRngCommand {
    private TECCrackRngCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("teccrackrng")
                .executes(ctx -> {
                    if (!CCrackRng.start()) {
                        ctx.getSource().sendFailure(Component.translatable("commands.teccrackrng.already_running"));
                        return 0;
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
