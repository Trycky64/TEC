/*
 * Based on CCrackRng from ClientCommands by Earthcomputer and contributors.
 * ClientCommands is licensed under LGPL-3.0-or-later.
 * Adapted for NeoForge 1.21.1 / TEC.
 */
package com.trycky.tec.feature;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.EntityType;

import java.util.concurrent.CompletableFuture;

/**
 * Cracks the server-side player java.util.Random state from the horizontal
 * velocities of ten deliberately dropped item entities.
 */
public final class CCrackRng {
    public static final int NUM_THROWS = 10;
    public static final float MAX_ERROR = 0.00883889f;
    private static final int MAX_ATTEMPTS = 5;
    private static final int THROW_TIMEOUT_TICKS = 100;

    private static final float[] nextFloats = new float[NUM_THROWS];

    private static boolean active;
    private static boolean solving;
    private static int attemptCount;
    private static int sentThrows;
    private static int receivedThrows;
    private static int waitTicks;

    private CCrackRng() {
    }

    public static boolean start() {
        if (active) {
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return false;
        }

        active = true;
        attemptCount = 1;
        PlayerRandCracker.setCrackState(PlayerRandCracker.CrackState.CRACKING);
        beginAttempt();
        chat(Component.translatable("commands.teccrackrng.starting", attemptCount, MAX_ATTEMPTS)
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    private static void beginAttempt() {
        sentThrows = 0;
        receivedThrows = 0;
        waitTicks = 0;
        solving = false;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null) {
            player.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 90.0F);
            player.connection.send(new ServerboundMovePlayerPacket.Rot(player.getYRot(), 90.0F, true));
        }
    }

    /** Called once each client tick by CCrackRngTickMixin. */
    public static void tick() {
        if (!active || solving) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.level == null) {
            fail("commands.teccrackrng.failed.left_world");
            return;
        }

        if (receivedThrows == NUM_THROWS) {
            solveAsync();
            return;
        }

        // Send only one throw at a time. Waiting for its spawn packet makes the
        // observation order deterministic and avoids flooding an integrated or
        // remote server with inventory-click packets.
        if (sentThrows == receivedThrows && sentThrows < NUM_THROWS) {
            if (!PlayerRandCracker.throwItemForCracking()) {
                fail("commands.teccrackrng.failed.items");
                return;
            }
            sentThrows++;
            waitTicks = 0;
            return;
        }

        if (sentThrows > receivedThrows && ++waitTicks > THROW_TIMEOUT_TICKS) {
            fail("commands.teccrackrng.failed.timeout");
        }
    }

    /** Called for every ClientboundAddEntityPacket by CCrackRngPacketMixin. */
    public static void onEntityCreation(ClientboundAddEntityPacket packet) {
        if (!active || solving || packet.getType() != EntityType.ITEM || receivedThrows >= NUM_THROWS) {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        // Only accept item entities spawned essentially at our eye position.
        if (player.getEyePosition().distanceToSqr(packet.getX(), packet.getY(), packet.getZ()) > 1.0D) {
            return;
        }

        float horizontalVelocity = (float) Math.sqrt(
                packet.getXa() * packet.getXa() + packet.getZa() * packet.getZa()
        ) * 50.0F;
        nextFloats[receivedThrows++] = horizontalVelocity;
        waitTicks = 0;
    }

    private static void solveAsync() {
        solving = true;
        float[] sample = nextFloats.clone();

        CompletableFuture.supplyAsync(() -> CCrackRngGen.getSeeds(
                low(sample[0]), high(sample[0]),
                low(sample[1]), high(sample[1]),
                low(sample[2]), high(sample[2]),
                low(sample[3]), high(sample[3]),
                low(sample[4]), high(sample[4]),
                low(sample[5]), high(sample[5]),
                low(sample[6]), high(sample[6]),
                low(sample[7]), high(sample[7]),
                low(sample[8]), high(sample[8]),
                low(sample[9]), high(sample[9])
        ).limit(2).toArray()).whenComplete((seeds, error) -> Minecraft.getInstance().execute(() -> {
            if (!active) {
                return;
            }
            if (error != null) {
                PlayerRandCracker.LOGGER.error("Failed to crack player RNG", error);
                fail("commands.teccrackrng.failed");
                return;
            }

            if (seeds.length == 1) {
                long seed = seeds[0];
                PlayerRandCracker.markCracked(seed);
                active = false;
                solving = false;
                chat(Component.translatable("commands.teccrackrng.success", String.format("%012X", seed))
                        .withStyle(ChatFormatting.GREEN));
                return;
            }

            if (attemptCount >= MAX_ATTEMPTS) {
                fail("commands.teccrackrng.failed");
                return;
            }

            attemptCount++;
            PlayerRandCracker.setCrackState(PlayerRandCracker.CrackState.CRACKING);
            beginAttempt();
            chat(Component.translatable("commands.teccrackrng.retry", attemptCount, MAX_ATTEMPTS)
                    .withStyle(ChatFormatting.YELLOW));
        }));
    }

    private static float low(float value) {
        return Math.max(0.0F, value - MAX_ERROR);
    }

    private static float high(float value) {
        return Math.min(1.0F, value + MAX_ERROR);
    }

    private static void fail(String translationKey) {
        active = false;
        solving = false;
        PlayerRandCracker.resetCracker();
        chat(Component.translatable(translationKey).withStyle(ChatFormatting.RED));
    }

    private static void chat(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(message);
        }
    }
}
