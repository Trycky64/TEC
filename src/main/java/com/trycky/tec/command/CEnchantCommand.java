/*
 * Based on CEnchantCommand from ClientCommands by Earthcomputer and contributors.
 * ClientCommands is licensed under LGPL-3.0-or-later.
 * Adapted for NeoForge 1.21.1 / TEC.
 */
package com.trycky.tec.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.trycky.tec.command.argument.ItemAndEnchantmentsPredicateArgument;
import com.trycky.tec.command.argument.ItemAndEnchantmentsPredicateArgument.ItemAndEnchantmentsPredicate;
import com.trycky.tec.feature.EnchantmentCracker;
import com.trycky.tec.feature.PlayerRandCracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Client-side /tecenchant command. */
public final class CEnchantCommand {
    private CEnchantCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
        TECCrackRngCommand.register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("tecenchant")
                .then(Commands.argument(
                                "itemAndEnchantmentsPredicate",
                                ItemAndEnchantmentsPredicateArgument.itemAndEnchantmentsPredicate(context)
                                        .withEnchantmentPredicate(CEnchantCommand::enchantmentPredicate)
                                        .constrainMaxLevel()
                        )
                        .executes(ctx -> execute(
                                ctx.getSource(),
                                ItemAndEnchantmentsPredicateArgument.getItemAndEnchantmentsPredicate(
                                        ctx, "itemAndEnchantmentsPredicate"
                                )
                        ))));
    }

    private static boolean enchantmentPredicate(Item item, Holder<Enchantment> enchantment) {
        return enchantment.is(EnchantmentTags.IN_ENCHANTING_TABLE)
                && (item == Items.BOOK || enchantment.value().canEnchant(new ItemStack(item)));
    }

    private static int execute(CommandSourceStack source, ItemAndEnchantmentsPredicate request) {
        if (Minecraft.getInstance().player == null) {
            source.sendFailure(Component.translatable("commands.cenchant.no_player"));
            return 0;
        }

        if (!PlayerRandCracker.knowsSeed()
                && EnchantmentCracker.getCrackState() != EnchantmentCracker.CrackState.CRACKED) {
            source.sendFailure(Component.translatable("commands.cenchant.uncracked"));
            return Command.SINGLE_SUCCESS;
        }

        source.sendSuccess(() -> Component.translatable("commands.cenchant.searching"), false);

        CompletableFuture
                .supplyAsync(() -> EnchantmentCracker.findManipulationResult(request.item(), request.predicate()))
                .whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        EnchantmentCracker.LOGGER.error("Failed to search for enchantment manipulation", error);
                        source.sendFailure(Component.translatable("commands.cenchant.failed"));
                        return;
                    }

                    if (result == null) {
                        source.sendFailure(Component.translatable("commands.cenchant.failed"));
                        return;
                    }

                    showResult(source, result);
                }));

        return Command.SINGLE_SUCCESS;
    }

    private static void showResult(CommandSourceStack source, EnchantmentCracker.ManipulateResult result) {
        source.sendSuccess(() -> Component.translatable("commands.cenchant.success")
                .withStyle(ChatFormatting.GREEN), false);

        if (result.itemThrows() == EnchantmentCracker.ManipulateResult.NO_DUMMY) {
            source.sendSuccess(() -> Component.translatable("enchCrack.insn.itemThrows.noDummy"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "enchCrack.insn.itemThrows", result.itemThrows()
            ), false);
        }

        source.sendSuccess(() -> Component.translatable(
                "enchCrack.insn.bookshelves", result.bookshelves()
        ), false);
        source.sendSuccess(() -> Component.translatable(
                "enchCrack.insn.slot", result.slot() + 1
        ), false);
        source.sendSuccess(() -> Component.translatable("enchCrack.insn.enchantments"), false);

        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        List<EnchantmentInstance> enchantments = new ArrayList<>(result.enchantments());
        EnchantmentCracker.sortIntoTooltipOrder(
                player.registryAccess().registryOrThrow(Registries.ENCHANTMENT),
                enchantments
        );

        for (EnchantmentInstance enchantment : enchantments) {
            source.sendSuccess(() -> Component.literal("- ")
                    .append(Enchantment.getFullname(enchantment.enchantment, enchantment.level)), false);
        }
    }
}
