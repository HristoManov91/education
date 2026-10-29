package bg.hristomanov.education.integrationpatterns.queue;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class ShufflePairAssignerTest {

    @Test
    void eightQueuesHave28PossiblePairsButPartialOverlapIsMuchMoreLikely() {
        ShufflePairAssigner assigner = new ShufflePairAssigner(8);
        assertThat(assigner.possiblePairs()).isEqualTo(28);

        // Under independent UNIFORM selection of two distinct queues:
        // identical pair = 1 / C(8,2) = 1 / 28
        // any overlap = 1 - C(6,2)/C(8,2) = 13 / 28.
        assertThat(assigner.exactPairCollisionProbability())
                .isCloseTo(1.0 / 28.0, offset(1e-12));
        assertThat(assigner.anyOverlapProbability())
                .isCloseTo(13.0 / 28.0, offset(1e-12));
    }

    @Test
    void hundredQueuesHave4950PairsWithoutClaimingZeroBlastRadius() {
        ShufflePairAssigner assigner = new ShufflePairAssigner(100);
        assertThat(assigner.possiblePairs()).isEqualTo(4_950);
        assertThat(assigner.exactPairCollisionProbability())
                .isCloseTo(1.0 / 4_950, offset(1e-12));
        assertThat(assigner.anyOverlapProbability())
                .isCloseTo(1.0 - 4_753.0 / 4_950, offset(1e-12));
        assertThat(assigner.anyOverlapProbability())
                .isGreaterThan(assigner.exactPairCollisionProbability());
    }

    @Test
    void fixedTenantMapsToTwoDistinctValidQueuesAcrossRepeatedCalls() {
        ShufflePairAssigner assigner = new ShufflePairAssigner(8);
        var original = assigner.pairFor("tenant-42");

        assertThat(original).hasSize(2);
        assertThat(original).allMatch(index -> index >= 0 && index < 8);
        assertThat(assigner.pairFor("tenant-42")).isEqualTo(original);
        assertThatThrownBy(() -> new ShufflePairAssigner(1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withOnlyTwoQueuesEveryTenantSharesTheSamePair() {
        ShufflePairAssigner assigner = new ShufflePairAssigner(2);
        assertThat(assigner.pairFor("tenant-A")).containsExactlyInAnyOrder(0, 1);
        assertThat(assigner.pairFor("tenant-B")).containsExactlyInAnyOrder(0, 1);
        assertThat(assigner.exactPairCollisionProbability()).isEqualTo(1.0);
        assertThat(assigner.anyOverlapProbability()).isEqualTo(1.0);
    }
}
