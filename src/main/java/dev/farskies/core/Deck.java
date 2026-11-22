package dev.farskies.core;

import java.util.Random;

public final class Deck {
    private Deck() {}

    public static int tier(long receipts, long[] thresholds) {
        int tier = 0;
        while (tier + 1 < thresholds.length && receipts >= thresholds[tier + 1]) tier++;
        return tier;
    }

    public static int index(long seed, long receipt, int bound) {
        return new Random(seed ^ receipt * 0xD1342543DE82EF95L).nextInt(bound);
    }
}
