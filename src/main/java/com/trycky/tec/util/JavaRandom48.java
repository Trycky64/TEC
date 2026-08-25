package com.trycky.tec.util;

import com.seedfinding.mcseed.lcg.LCG;
import com.seedfinding.mcseed.rand.Rand;

/**
 * Small adapter around SeedFinding's Java LCG implementation.
 *
 * <p>This is intentionally tiny for step 2. PlayerRandCracker will build on
 * this dependency in the next implementation step.</p>
 */
public final class JavaRandom48 {
    private JavaRandom48() {
    }

    /**
     * Advances an already-internalized 48-bit java.util.Random seed and returns
     * the next unbounded 32-bit int, matching the primitive used by
     * ClientCommands for enchantment manipulation calculations.
     */
    public static int nextIntAfterAdvances(long internalSeed, long advances) {
        Rand rand = new Rand(LCG.JAVA, internalSeed);
        rand.advance(advances);
        return (int) rand.nextBits(32);
    }
}
