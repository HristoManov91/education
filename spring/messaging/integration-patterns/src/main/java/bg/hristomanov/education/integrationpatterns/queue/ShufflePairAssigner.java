package bg.hristomanov.education.integrationpatterns.queue;

import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Stable illustrative 2-of-N shard assignment. It does NOT model cross-shard
 * failover, queue acknowledgements, replication or queue processing capacity.
 * The probability methods assume ideal independent uniform pair assignment;
 * they do not prove collision rates for this non-cryptographic hash.
 */
public final class ShufflePairAssigner {
    private final int queueCount;

    public ShufflePairAssigner(int queueCount) {
        if (queueCount < 2) {
            throw new IllegalArgumentException("queueCount must be at least 2");
        }
        this.queueCount = queueCount;
    }

    public Set<Integer> pairFor(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        var random = new SplittableRandom(stableSeed(tenantId));
        int first = random.nextInt(queueCount);
        int second = random.nextInt(queueCount - 1);
        if (second >= first) {
            second++; // Select a distinct index without a retry loop.
        }
        return Set.of(first, second);
    }

    public long possiblePairs() {
        return (long) queueCount * (queueCount - 1) / 2;
    }

    public double exactPairCollisionProbability() {
        return 1.0 / possiblePairs();
    }

    public double anyOverlapProbability() {
        long disjointPairs = queueCount >= 4
                ? (long) (queueCount - 2) * (queueCount - 3) / 2
                : 0L;
        return 1.0 - ((double) disjointPairs / possiblePairs());
    }

    private static long stableSeed(String value) {
        long seed = 0xcbf29ce484222325L; // FNV-1a: stable, NOT cryptographic.
        for (int i = 0; i < value.length(); i++) {
            seed ^= value.charAt(i);
            seed *= 0x100000001b3L;
        }
        return seed;
    }
}
