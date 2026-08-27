/*
 * Based on PlayerRandCracker from ClientCommands by Earthcomputer and contributors.
 * ClientCommands is licensed under LGPL-3.0-or-later.
 */
package com.trycky.tec.feature;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * Tracks the 48-bit java.util.Random state used by the server-side Player RNG.
 *
 * <p>For Minecraft 1.21.1 TEC deliberately uses a conservative policy: when an
 * observed action has a known number of RNG calls, the local state is advanced;
 * when the call count cannot be guaranteed, the cracker is invalidated. A false
 * UNCRACKED state is preferable to silently manipulating with a desynchronised
 * seed.</p>
 */
public final class PlayerRandCracker {
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final long MULTIPLIER = 0x5deece66dL;
    public static final long ADDEND = 0xbL;
    public static final long MASK = (1L << 48) - 1;

    private static long seed;
    private static CrackState crackState = CrackState.UNCRACKED;
    private static int expectedThrows;

    private PlayerRandCracker() {
    }

    private static int next(int bits) {
        seed = (seed * MULTIPLIER + ADDEND) & MASK;
        return (int) (seed >>> (48 - bits));
    }

    public static int nextInt() {
        return next(32);
    }

    public static int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        if ((bound & -bound) == bound) {
            return (int) ((bound * (long) next(31)) >> 31);
        }

        int bits;
        int value;
        do {
            bits = next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    public static float nextFloat() {
        return next(24) / (float) (1 << 24);
    }

    public static void setSeed(long newSeed) {
        seed = newSeed & MASK;
    }

    public static long getSeed() {
        return seed;
    }

    public static CrackState getCrackState() {
        return crackState;
    }

    public static void setCrackState(CrackState state) {
        crackState = state;
    }

    public static boolean knowsSeed() {
        return crackState.knowsSeed();
    }

    public static void markCracked(long crackedSeed) {
        setSeed(crackedSeed);
        crackState = CrackState.CRACKED;
        LOGGER.info("Player RNG cracked: {}", String.format("%012X", seed));
    }

    public static void resetCracker() {
        crackState = CrackState.UNCRACKED;
        expectedThrows = 0;
    }

    public static void resetCracker(String reason) {
        if (crackState != CrackState.UNCRACKED) {
            LOGGER.warn("Player RNG state invalidated: {}", reason);
        }
        resetCracker();
    }


    /**
     * Throws one ordinary inventory item for /teccrackrng. The existing LocalPlayer
     * drop hook marks this throw as expected so it cannot invalidate the cracker.
     */
    public static boolean throwItemForCracking() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return false;
        }

        Slot slot = player.containerMenu.slots.stream()
                .filter(Slot::hasItem)
                .filter(candidate -> !candidate.getItem().is(Items.CHORUS_FRUIT))
                .max(java.util.Comparator.comparingInt(candidate -> candidate.getItem().getCount()))
                .orElse(null);
        if (slot == null) {
            return false;
        }

        expectItemThrow();
        mc.gameMode.handleInventoryMouseClick(
                player.containerMenu.containerId, slot.index, 0, ClickType.THROW, player
        );
        return true;
    }

    /** A normal dropped item consumes four calls to Player.random. */
    public static void onDropItem() {
        if (expectedThrows > 0 || knowsSeed()) {
            advanceInts(4);
        } else {
            resetCracker("dropItem");
        }
        if (expectedThrows > 0) {
            expectedThrows--;
        }
    }

    /** Reserved for TEC's later automated throw task. */
    public static void expectItemThrow() {
        expectedThrows++;
    }

    /** Rewinds one predicted four-call item throw if the client could not send it. */
    public static void undoExpectedItemThrow() {
        if (expectedThrows > 0) {
            expectedThrows--;
        }
        // Inverse of four Java LCG steps, copied from ClientCommands.
        seed = (seed * 0xdba6ed0471f1L + 0x25493d2c3b3cL) & MASK;
    }

    public static void onEat(ItemStack stack, int particleCount, int itemUseTimeLeft) {
        if (!knowsSeed()) {
            resetCracker("food");
            return;
        }

        // Chorus fruit has additional random teleport behaviour. Until that
        // manipulation path is ported, invalidate instead of guessing.
        if (stack.is(Items.CHORUS_FRUIT)) {
            resetCracker("chorusFruit");
            return;
        }

        if (itemUseTimeLeft < 0 && particleCount != 16) {
            return;
        }

        advanceInts(particleCount * 3 + 3);
    }

    public static void onAnvilUse() {
        if (knowsSeed()) {
            nextInt();
        } else {
            resetCracker("anvil");
        }
    }

    public static void onEntityCramming() { resetCracker("entityCramming"); }
    public static void onUnderwater() { resetCracker("swim"); }
    public static void onSwimmingStart() { resetCracker("enterWater"); }
    public static void onAmethystChime() { resetCracker("amethystChime"); }
    public static void onDamage() { resetCracker("playerHurt"); }
    public static void onSprinting() { resetCracker("sprint"); }
    public static void onPotionParticles() { resetCracker("potion"); }
    public static void onGiveCommand() { resetCracker("give"); }
    public static void onMending() { resetCracker("mending"); }
    public static void onXpOrb() { resetCracker("xp"); }
    public static void onFrostWalker() { resetCracker("frostWalker"); }
    public static void onSoulSpeed() { resetCracker("soulSpeed"); }
    public static void onUnexpectedItemEnchant() { resetCracker("enchanted"); }
    public static void onWorldChanged() { resetCracker("worldChanged"); }

    private static void advanceInts(int count) {
        for (int i = 0; i < count; i++) {
            nextInt();
        }
    }

    public enum CrackState implements StringRepresentable {
        UNCRACKED("uncracked"),
        CRACKING("cracking"),
        CRACKED("cracked", true),
        ENCH_CRACKING_1("ench_cracking_1"),
        HALF_CRACKED("half_cracked"),
        ENCH_CRACKING_2("ench_cracking_2"),
        MANIPULATING_ENCHANTMENTS("manipulating_enchantments"),
        WAITING_DUMMY_ENCHANT("waiting_dummy_enchant", true);

        private final String serializedName;
        private final boolean knowsSeed;

        CrackState(String serializedName) {
            this(serializedName, false);
        }

        CrackState(String serializedName, boolean knowsSeed) {
            this.serializedName = serializedName;
            this.knowsSeed = knowsSeed;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }

        public boolean knowsSeed() {
            return knowsSeed;
        }
    }
}
