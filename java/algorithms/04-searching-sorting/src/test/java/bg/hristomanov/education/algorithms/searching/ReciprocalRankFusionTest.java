package bg.hristomanov.education.algorithms.searching;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReciprocalRankFusionTest {
    @Test
    void documentRelevantInBothRankingsCanBeatSingleRankingLeader() {
        var results = ReciprocalRankFusion.fuse(
                List.of(List.of("A", "B", "C"), List.of("B", "C", "D")), 60, 4);
        assertThat(results).extracting(ReciprocalRankFusion.RankedDocument::document)
                .containsExactly("B", "C", "A", "D");
        assertThat(results.getFirst().score()).isCloseTo(1d / 62 + 1d / 61,
                org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void tiesAreDeterministicallyResolvedByFirstEncounterAndLimit() {
        var result = ReciprocalRankFusion.fuse(
                List.of(List.of("A"), List.of("B")), 60, 1);
        assertThat(result).extracting(ReciprocalRankFusion.RankedDocument::document)
                .containsExactly("A");
        assertThat(ReciprocalRankFusion.fuse(List.of(List.of("A")), 60, 0)).isEmpty();
        assertThat(ReciprocalRankFusion.fuse(List.of(), 60, 10)).isEmpty();
    }

    @Test
    void rejectsInvalidInputsAndRepeatedDocumentWithinSameList() {
        assertThatThrownBy(() -> ReciprocalRankFusion.fuse(List.of(List.of("A", "A")), 60, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReciprocalRankFusion.fuse(List.of(List.of("A")), 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReciprocalRankFusion.fuse(List.of(List.of("A")), 60, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReciprocalRankFusion.fuse(null, 60, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
