/*
 * This file is based on the enchantment cracking implementation from
 * ClientCommands by Earthcomputer and contributors.
 *
 * ClientCommands is licensed under LGPL-3.0-or-later.
 * Adapted for Trycky's Enchantment Cracker / NeoForge 1.21.1.
 */
package com.trycky.tec.feature;

import com.trycky.tec.util.JavaRandom48;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.IdMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Core enchantment-seed cracker for Minecraft 1.21.1.
 *
 * <p>The server exposes only 12 useful bits of the 32-bit enchantment seed in
 * the enchanting menu. TEC reconstructs the 2^20 possible full seeds and then
 * eliminates candidates by replaying vanilla enchanting costs and clues.
 * Unlike ClientCommands, this class is deliberately fixed to Minecraft 1.21.1;
 * compatibility branches for older protocol versions are not carried over.</p>
 */
public final class EnchantmentCracker {
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final int INITIAL_CANDIDATE_COUNT = 1 << 20;

    private static final Set<Integer> POSSIBLE_XP_SEEDS = new HashSet<>(INITIAL_CANDIDATE_COUNT);

    private static CrackState crackState = CrackState.UNCRACKED;
    private static @Nullable BlockPos enchantingTablePos;
    private static int firstXpSeed;

    private EnchantmentCracker() {
    }

    public static void reset() {
        crackState = CrackState.UNCRACKED;
        POSSIBLE_XP_SEEDS.clear();
        enchantingTablePos = null;
        firstXpSeed = 0;
    }

    public static CrackState getCrackState() {
        return crackState;
    }

    public static int getPossibleSeedCount() {
        return POSSIBLE_XP_SEEDS.size();
    }

    public static Optional<Integer> getCrackedSeed() {
        if (crackState != CrackState.CRACKED || POSSIBLE_XP_SEEDS.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(POSSIBLE_XP_SEEDS.iterator().next());
    }

    public static Set<Integer> getPossibleSeedsSnapshot() {
        return Set.copyOf(POSSIBLE_XP_SEEDS);
    }

    public static @Nullable BlockPos getEnchantingTablePos() {
        return enchantingTablePos;
    }

    public static void setEnchantingTablePos(@Nullable BlockPos pos) {
        enchantingTablePos = pos == null ? null : pos.immutable();
    }

    /**
     * Expands the 12 server-visible enchantment seed bits into every possible
     * 32-bit XP seed. This is kept public to make the deterministic first phase
     * independently verifiable.
     */
    public static Set<Integer> expandServerReportedSeed(int serverReportedXPSeed) {
        int maskedSeed = serverReportedXPSeed & 0x0000fff0;
        Set<Integer> seeds = new HashSet<>(INITIAL_CANDIDATE_COUNT);

        for (int highBits = 0; highBits < 65536; highBits++) {
            for (int low4Bits = 0; low4Bits < 16; low4Bits++) {
                seeds.add((highBits << 16) | maskedSeed | low4Bits);
            }
        }

        return seeds;
    }

    private static void prepareForNextSeedCrack(int serverReportedXPSeed) {
        POSSIBLE_XP_SEEDS.clear();
        POSSIBLE_XP_SEEDS.addAll(expandServerReportedSeed(serverReportedXPSeed));
    }

    /**
     * Feeds the latest enchanting-menu information into the cracker.
     * Repeated calls with different enchantable items progressively eliminate
     * seed candidates until exactly one remains.
     */
    public static void addEnchantmentSeedInfo(Level level, EnchantmentMenu menu) {
        if (crackState == CrackState.CRACKED) {
            return;
        }

        ItemStack itemToEnchant = menu.getSlot(0).getItem();
        if (itemToEnchant.isEmpty() || !itemToEnchant.isEnchantable()) {
            return;
        }

        BlockPos tablePos = enchantingTablePos;
        if (tablePos == null) {
            return;
        }

        if (crackState == CrackState.UNCRACKED) {
            crackState = CrackState.CRACKING;
            prepareForNextSeedCrack(menu.getEnchantmentSeed());
        }

        int power = getEnchantPower(level, tablePos);
        filterCandidates(
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT),
                itemToEnchant,
                power,
                menu.costs,
                menu.enchantClue,
                menu.levelClue
        );

        if (POSSIBLE_XP_SEEDS.isEmpty()) {
            crackState = CrackState.UNCRACKED;
            LOGGER.warn("Invalid enchantment seed information; resetting the enchantment cracker.");
        } else if (POSSIBLE_XP_SEEDS.size() == 1) {
            crackState = CrackState.CRACKED;
            int crackedXpSeed = POSSIBLE_XP_SEEDS.iterator().next();
            LOGGER.info("Enchantment seed cracked: {}", String.format("%08X", crackedXpSeed));
            addPlayerRngInfo(crackedXpSeed);
        }
    }

    /**
     * Uses two consecutive fully cracked XP seeds to recover the 48-bit Player
     * RNG state. This is the same lattice-derived reconstruction used by
     * ClientCommands, fixed to the Java RNG used by Minecraft 1.21.1.
     */
    private static void addPlayerRngInfo(int enchantmentSeed) {
        PlayerRandCracker.CrackState playerState = PlayerRandCracker.getCrackState();

        if (playerState == PlayerRandCracker.CrackState.ENCH_CRACKING_1) {
            firstXpSeed = enchantmentSeed;
            PlayerRandCracker.setCrackState(PlayerRandCracker.CrackState.HALF_CRACKED);
            return;
        }

        if (playerState != PlayerRandCracker.CrackState.ENCH_CRACKING_2) {
            return;
        }

        long max1 = Integer.toUnsignedLong(firstXpSeed) + 1;
        long min1 = Integer.toUnsignedLong(firstXpSeed);
        long max2 = Integer.toUnsignedLong(enchantmentSeed) + 1;
        long a = (24667315L * max1 + 18218081L * max2) >> 32;
        long b = (-4824621L * min1 + 7847617L * max2) >> 32;

        long playerSeed = (7847617L * a - 18218081L * b) & PlayerRandCracker.MASK;
        boolean valid = (int) (playerSeed >>> 16) == firstXpSeed;

        playerSeed = (playerSeed * PlayerRandCracker.MULTIPLIER + PlayerRandCracker.ADDEND)
                & PlayerRandCracker.MASK;
        valid &= (int) (playerSeed >>> 16) == enchantmentSeed;

        if (valid) {
            PlayerRandCracker.markCracked(playerSeed);
        } else {
            PlayerRandCracker.resetCracker("invalidEnchantSequence");
            LOGGER.warn("Invalid player RNG information; the observed enchantment sequence was desynchronised.");
        }
    }

    /**
     * Must be called after the server accepts an enchantment. The table hook is
     * added in step 5; keeping the state transition here lets the RNG tracker
     * remain self-contained and testable now.
     */
    public static void onEnchantedItem() {
        PlayerRandCracker.CrackState playerState = PlayerRandCracker.getCrackState();

        if (playerState.knowsSeed()) {
            POSSIBLE_XP_SEEDS.clear();
            POSSIBLE_XP_SEEDS.add(PlayerRandCracker.nextInt());
            PlayerRandCracker.setCrackState(PlayerRandCracker.CrackState.CRACKED);
            crackState = CrackState.CRACKED;
        } else if (playerState == PlayerRandCracker.CrackState.HALF_CRACKED) {
            POSSIBLE_XP_SEEDS.clear();
            PlayerRandCracker.setCrackState(PlayerRandCracker.CrackState.ENCH_CRACKING_2);
            crackState = CrackState.UNCRACKED;
        } else if (playerState == PlayerRandCracker.CrackState.UNCRACKED
                || playerState == PlayerRandCracker.CrackState.ENCH_CRACKING_1
                || playerState == PlayerRandCracker.CrackState.ENCH_CRACKING_2) {
            POSSIBLE_XP_SEEDS.clear();
            PlayerRandCracker.setCrackState(PlayerRandCracker.CrackState.ENCH_CRACKING_1);
            crackState = CrackState.UNCRACKED;
        } else {
            PlayerRandCracker.onUnexpectedItemEnchant();
            crackState = CrackState.UNCRACKED;
        }
    }

    private static void filterCandidates(
            Registry<Enchantment> enchantmentRegistry,
            ItemStack itemToEnchant,
            int power,
            int[] actualEnchantCosts,
            int[] actualEnchantmentClues,
            int[] actualLevelClues
    ) {
        RandomSource rand = RandomSource.create();
        IdMap<Holder<Enchantment>> enchantmentIdMap = enchantmentRegistry.asHolderIdMap();

        Iterator<Integer> xpSeedIterator = POSSIBLE_XP_SEEDS.iterator();
        seedLoop:
        while (xpSeedIterator.hasNext()) {
            int xpSeed = xpSeedIterator.next();
            rand.setSeed(xpSeed);

            for (int slot = 0; slot < 3; slot++) {
                int cost = EnchantmentHelper.getEnchantmentCost(rand, slot, power, itemToEnchant);
                if (cost < slot + 1) {
                    cost = 0;
                }
                if (cost != actualEnchantCosts[slot]) {
                    xpSeedIterator.remove();
                    continue seedLoop;
                }
            }

            for (int slot = 0; slot < 3; slot++) {
                if (actualEnchantCosts[slot] <= 0) {
                    continue;
                }

                List<EnchantmentInstance> enchantments = getEnchantmentList(
                        enchantmentRegistry,
                        rand,
                        xpSeed,
                        itemToEnchant,
                        slot,
                        actualEnchantCosts[slot]
                );

                if (enchantments.isEmpty()) {
                    if (actualEnchantmentClues[slot] != -1 || actualLevelClues[slot] != -1) {
                        xpSeedIterator.remove();
                        continue seedLoop;
                    }
                } else {
                    EnchantmentInstance clue = enchantments.get(rand.nextInt(enchantments.size()));
                    if (enchantmentIdMap.getId(clue.enchantment) != actualEnchantmentClues[slot]
                            || clue.level != actualLevelClues[slot]) {
                        xpSeedIterator.remove();
                        continue seedLoop;
                    }
                }
            }
        }
    }

    /**
     * Minecraft 1.21.1 enchanting simulation. Older-version compatibility code
     * from ClientCommands is intentionally omitted.
     */
    public static List<EnchantmentInstance> getEnchantmentList(
            Registry<Enchantment> enchantmentRegistry,
            RandomSource rand,
            int xpSeed,
            ItemStack stack,
            int enchantSlot,
            int level
    ) {
        rand.setSeed(xpSeed + enchantSlot);

        List<EnchantmentInstance> list = enchantmentRegistry.getTag(EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(tag -> EnchantmentHelper.selectEnchantment(rand, stack, level, tag.stream()))
                .orElseGet(ArrayList::new);

        if (stack.getItem() == Items.BOOK && list.size() > 1) {
            list.remove(rand.nextInt(list.size()));
        }

        return list;
    }

    /**
     * Returns either the full predicted enchantment list when cracked or the
     * single clue currently supplied by the server.
     */
    public static @Nullable List<EnchantmentInstance> getEnchantmentsInTable(int slot) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(player.containerMenu instanceof EnchantmentMenu menu)) {
            return null;
        }

        Registry<Enchantment> enchantmentRegistry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        if (crackState != CrackState.CRACKED) {
            if (menu.enchantClue[slot] == -1) {
                return null;
            }

            Holder<Enchantment> enchantment = enchantmentRegistry.asHolderIdMap().byId(menu.enchantClue[slot]);
            if (enchantment == null) {
                return null;
            }

            return new ArrayList<>(Collections.singletonList(
                    new EnchantmentInstance(enchantment, menu.levelClue[slot])
            ));
        }

        int xpSeed = POSSIBLE_XP_SEEDS.iterator().next();
        return getEnchantmentList(
                enchantmentRegistry,
                RandomSource.create(),
                xpSeed,
                menu.getSlot(0).getItem(),
                slot,
                menu.costs[slot]
        );
    }

    public static void sortIntoTooltipOrder(
            Registry<Enchantment> enchantmentRegistry,
            List<EnchantmentInstance> list
    ) {
        Optional<HolderSet.Named<Enchantment>> tooltipOrder = enchantmentRegistry.getTag(EnchantmentTags.TOOLTIP_ORDER);
        if (tooltipOrder.isEmpty()) {
            return;
        }

        Object2IntMap<Holder<Enchantment>> tooltipIndex = new Object2IntOpenHashMap<>(tooltipOrder.get().size());
        int index = 0;
        for (Holder<Enchantment> enchantment : tooltipOrder.get()) {
            tooltipIndex.put(enchantment, index++);
        }

        list.sort(Comparator.comparingInt(enchantment -> tooltipIndex.getInt(enchantment.enchantment)));
    }

    /**
     * Searches the same manipulation space used by ClientCommands for its default
     * /cenchant configuration. This step only computes the plan; automatic item
     * throwing/dummy-enchant execution is intentionally deferred.
     */
    public static @Nullable ManipulateResult findManipulationResult(
            Item item,
            Predicate<List<EnchantmentInstance>> enchantmentsPredicate
    ) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }

        Registry<Enchantment> enchantmentRegistry = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemStack stack = new ItemStack(item);
        boolean playerSeedKnown = PlayerRandCracker.knowsSeed();
        boolean enchantmentSeedKnown = crackState == CrackState.CRACKED && POSSIBLE_XP_SEEDS.size() == 1;

        if (!playerSeedKnown && !enchantmentSeedKnown) {
            return null;
        }

        int noDummyXpSeed = enchantmentSeedKnown ? POSSIBLE_XP_SEEDS.iterator().next() : 0;
        long playerSeed = PlayerRandCracker.getSeed();
        int firstThrowCount = enchantmentSeedKnown ? ManipulateResult.NO_DUMMY : 0;
        int exclusiveMaxThrowCount = playerSeedKnown ? 16_384 : 0;

        for (int itemThrows = firstThrowCount; itemThrows < exclusiveMaxThrowCount; itemThrows++) {
            int xpSeed = itemThrows == ManipulateResult.NO_DUMMY
                    ? noDummyXpSeed
                    : JavaRandom48.nextIntAfterAdvances(playerSeed, itemThrows * 4L);

            RandomSource rand = RandomSource.create();
            int[] enchantmentLevels = new int[3];

            for (int bookshelves = 0; bookshelves <= 15; bookshelves++) {
                rand.setSeed(xpSeed);
                for (int slot = 0; slot < 3; slot++) {
                    int level = EnchantmentHelper.getEnchantmentCost(rand, slot, bookshelves, stack);
                    if (level < slot + 1) {
                        level = 0;
                    }
                    enchantmentLevels[slot] = level;
                }

                for (int slot = 0; slot < 3; slot++) {
                    int level = enchantmentLevels[slot];
                    if (level < 1 || level > 30) {
                        continue;
                    }

                    List<EnchantmentInstance> enchantments = getEnchantmentList(
                            enchantmentRegistry, rand, xpSeed, stack, slot, level
                    );
                    if (enchantmentsPredicate.test(enchantments)) {
                        return new ManipulateResult(itemThrows, bookshelves, slot, List.copyOf(enchantments));
                    }
                }
            }
        }

        return null;
    }

    public record ManipulateResult(
            int itemThrows,
            int bookshelves,
            int slot,
            List<EnchantmentInstance> enchantments
    ) {
        public static final int NO_DUMMY = -1;
    }

    private static int getEnchantPower(Level level, BlockPos tablePos) {
        int power = 0;
        for (BlockPos bookshelfOffset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (EnchantingTableBlock.isValidBookShelf(level, tablePos, bookshelfOffset)) {
                power++;
            }
        }
        return power;
    }

    public enum CrackState implements StringRepresentable {
        UNCRACKED("uncracked"),
        CRACKING("cracking"),
        CRACKED("cracked");

        private final String serializedName;

        CrackState(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
