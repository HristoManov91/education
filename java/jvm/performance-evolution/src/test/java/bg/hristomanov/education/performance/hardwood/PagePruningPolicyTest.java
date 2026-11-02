package bg.hristomanov.education.performance.hardwood;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static bg.hristomanov.education.performance.hardwood.PagePruningPolicy.Decision.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagePruningPolicyTest {

    private static final PagePruningPolicy.PageStatistics NO_NULLS =
            new PagePruningPolicy.PageStatistics(3, 0L, 10L, 30L);

    @Test
    void greaterThanPrunesOnlyWhenMetadataProvesThePageCannotMatch() {
        assertThat(PagePruningPolicy.greaterThan(NO_NULLS, 30)).isEqualTo(CANNOT_MATCH);
        assertThat(PagePruningPolicy.greaterThan(NO_NULLS, 35)).isEqualTo(CANNOT_MATCH);
        assertThat(PagePruningPolicy.greaterThan(NO_NULLS, 9)).isEqualTo(ALWAYS_MATCHES);
        assertThat(PagePruningPolicy.greaterThan(NO_NULLS, 20)).isEqualTo(MIGHT_MATCH);
    }

    @Test
    void nullsPreventUnsafeAlwaysMatchesOptimization() {
        PagePruningPolicy.PageStatistics withNulls =
                new PagePruningPolicy.PageStatistics(3, 1L, 10L, 30L);
        PagePruningPolicy.PageStatistics unknownNulls =
                new PagePruningPolicy.PageStatistics(3, null, 10L, 30L);

        assertThat(PagePruningPolicy.greaterThan(withNulls, 5)).isEqualTo(MIGHT_MATCH);
        assertThat(PagePruningPolicy.greaterThan(unknownNulls, 5)).isEqualTo(MIGHT_MATCH);
        assertThat(PagePruningPolicy.equalTo(unknownNulls, 10, null)).isEqualTo(MIGHT_MATCH);
    }

    @Test
    void unknownStatisticsAreNotTreatedAsEmptyOrZero() {
        PagePruningPolicy.PageStatistics unknown =
                new PagePruningPolicy.PageStatistics(10, null, null, null);
        assertThat(PagePruningPolicy.greaterThan(unknown, Long.MAX_VALUE))
                .isEqualTo(MIGHT_MATCH);
        assertThat(PagePruningPolicy.equalTo(unknown, 1234, null))
                .isEqualTo(MIGHT_MATCH);
    }

    @Test
    void emptyAndAllNullPagesCanBePrunedForNonNullPredicates() {
        PagePruningPolicy.PageStatistics empty =
                new PagePruningPolicy.PageStatistics(0, null, null, null);
        PagePruningPolicy.PageStatistics allNull =
                new PagePruningPolicy.PageStatistics(4, 4L, null, null);

        for (PagePruningPolicy.PageStatistics statistics : List.of(empty, allNull)) {
            assertThat(PagePruningPolicy.greaterThan(statistics, 0)).isEqualTo(CANNOT_MATCH);
            assertThat(PagePruningPolicy.equalTo(statistics, 0, null)).isEqualTo(CANNOT_MATCH);
        }
    }

    @Test
    void equalityPruningRespectsBothEndsOfTheRange() {
        assertThat(PagePruningPolicy.equalTo(NO_NULLS, 5, null)).isEqualTo(CANNOT_MATCH);
        assertThat(PagePruningPolicy.equalTo(NO_NULLS, 35, null)).isEqualTo(CANNOT_MATCH);
        assertThat(PagePruningPolicy.equalTo(NO_NULLS, 20, null)).isEqualTo(MIGHT_MATCH);

        PagePruningPolicy.PageStatistics sameValue =
                new PagePruningPolicy.PageStatistics(5, 0L, 17L, 17L);
        assertThat(PagePruningPolicy.equalTo(sameValue, 17, null))
                .isEqualTo(ALWAYS_MATCHES);
    }

    @Test
    void bloomNegativeCanProveAbsenceButPositiveNeverProvesPresence() {
        PagePruningPolicy.PageStatistics between =
                new PagePruningPolicy.PageStatistics(2, 0L, 10L, 20L);

        // Grounded negative: fixture has only 10 and 20, so 15 is absent.
        assertThat(PagePruningPolicy.equalTo(between, 15, candidate -> false))
                .isEqualTo(CANNOT_MATCH);
        // Bloom false positives are legal: true is only "possibly present".
        assertThat(PagePruningPolicy.equalTo(between, 15, candidate -> true))
                .isEqualTo(MIGHT_MATCH);
    }

    @Test
    void independentValueOracleChecksNoFalseSkipOrFalseAllMatches() {
        // Explicit fixture values are the oracle. Statistics are only
        // optimization hints and must not change the correct scan result.
        List<Fixture> fixtures = List.of(
                new Fixture(List.of(2L, 5L, 8L),
                        new PagePruningPolicy.PageStatistics(3, 0L, 2L, 8L)),
                new Fixture(Arrays.asList(4L, null, 10L),
                        new PagePruningPolicy.PageStatistics(3, 1L, 4L, 10L)),
                new Fixture(List.of(7L, 7L),
                        new PagePruningPolicy.PageStatistics(2, null, 7L, 7L)),
                new Fixture(Arrays.asList(null, null),
                        new PagePruningPolicy.PageStatistics(2, 2L, null, null)),
                new Fixture(List.of(1L, 6L),
                        new PagePruningPolicy.PageStatistics(2, null, null, null))
        );

        for (Fixture fixture : fixtures) {
            for (long value = -1; value <= 12; value++) {
                final long candidate = value;
                List<Long> actualGreater = fixture.values().stream()
                        .filter(Objects::nonNull).filter(v -> v > candidate).toList();
                List<Long> actualEqual = fixture.values().stream()
                        .filter(Objects::nonNull).filter(v -> v == candidate).toList();

                PagePruningPolicy.Decision greater =
                        PagePruningPolicy.greaterThan(fixture.stats(), candidate);
                PagePruningPolicy.Decision equal =
                        PagePruningPolicy.equalTo(fixture.stats(), candidate, null);
                assertConservative(greater, fixture.values(), actualGreater);
                assertConservative(equal, fixture.values(), actualEqual);
            }
        }
    }

    @Test
    void invalidStatisticsAndNullArgumentsFailClosed() {
        assertThatThrownBy(() -> new PagePruningPolicy.PageStatistics(-1, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagePruningPolicy.PageStatistics(2, 3L, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagePruningPolicy.PageStatistics(2, 0L, 20L, 10L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PagePruningPolicy.greaterThan(null, 2))
                .isInstanceOf(NullPointerException.class);
    }

    private static void assertConservative(PagePruningPolicy.Decision decision,
                                           List<Long> allValues, List<Long> matchingValues) {
        if (decision == CANNOT_MATCH) {
            assertThat(matchingValues).isEmpty();
        }
        if (decision == ALWAYS_MATCHES) {
            assertThat(matchingValues).hasSize(allValues.size());
        }
    }

    private record Fixture(List<Long> values, PagePruningPolicy.PageStatistics stats) {
    }
}
