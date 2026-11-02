package bg.hristomanov.education.performance.hardwood;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static bg.hristomanov.education.performance.hardwood.PageProcessingLab.Evidence;
import static bg.hristomanov.education.performance.hardwood.PageProcessingLab.Page;
import static bg.hristomanov.education.performance.hardwood.PageProcessingLab.Range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageProcessingLabTest {

    @Test
    void trustedStatisticsCanProveImpossibleFullAndPartialMatches() {
        Page page = page(0, true, 10, 20, 10, 15, 20);
        assertThat(PageProcessingLab.classify(page, new Range(21, 30)))
                .isEqualTo(Evidence.CANNOT_MATCH);
        assertThat(PageProcessingLab.classify(page, new Range(10, 20)))
                .isEqualTo(Evidence.ALL_MATCH);
        assertThat(PageProcessingLab.classify(page, new Range(15, 25)))
                .isEqualTo(Evidence.MAY_MATCH);
    }

    @Test
    void inclusiveBoundaryMustNotDiscardMatchingValue() throws Exception {
        Page page = page(0, true, 10, 20, 10, 15, 20);
        assertThat(PageProcessingLab.sequential(List.of(page), new Range(20, 20))
                .matchingRows()).isEqualTo(1);
        assertThat(PageProcessingLab.parallel(List.of(page), new Range(10, 10), 2)
                .matchingRows()).isEqualTo(1);
    }

    @Test
    void absentOrUntrustedMetadataRequiresDecodingRatherThanPruning() throws Exception {
        // Deliberately wrong stats: the filter matches 42, despite [0, 1].
        Page page = page(0, false, 0, 1, 42, -5, 42);
        Range predicate = new Range(42, 42);
        assertThat(PageProcessingLab.classify(page, predicate)).isEqualTo(Evidence.MAY_MATCH);

        PageProcessingLab.ScanResult sequential =
                PageProcessingLab.sequential(List.of(page), predicate);
        PageProcessingLab.ScanResult parallel =
                PageProcessingLab.parallel(List.of(page), predicate, 2);

        assertThat(sequential.matchingRows()).isEqualTo(2);
        assertThat(parallel.matchingRows()).isEqualTo(2);
        assertThat(sequential.decodedPages()).isEqualTo(1);
        assertThat(sequential.prunedPages()).isZero();
    }

    @Test
    void corruptedTrustedStatisticsWouldCauseWrongSkippingAndOracleCatchesIt() {
        // Negative educational proof: NEVER mark unknown or corrupt data
        // trustworthy. The reference oracle detects that the proposed skip
        // disagrees with actual row values, rather than silently adjusting it.
        Page lying = page(0, true, 0, 1, 42);
        Range predicate = new Range(42, 42);

        assertThat(PageProcessingLab.sequential(List.of(lying), predicate).matchingRows())
                .isZero();
        assertThat(PageProcessingLab.reference(List.of(lying), predicate).matchingRows())
                .isEqualTo(1);
    }

    @Test
    void emptyPagesSkipWithoutAnyMetadata() throws Exception {
        Page empty = page(0, false, 0, 0);
        assertThat(PageProcessingLab.classify(empty, new Range(1, 2)))
                .isEqualTo(Evidence.CANNOT_MATCH);
        assertThat(PageProcessingLab.sequential(List.of(empty), new Range(1, 2)))
                .extracting(PageProcessingLab.ScanResult::prunedPages)
                .isEqualTo(1);
        assertThat(PageProcessingLab.parallel(List.of(empty), new Range(1, 2), 2)
                .matchingRows()).isZero();
    }

    @Test
    void allMatchPageCanBeCountedWithoutDecoding() throws Exception {
        Page accepted = page(0, true, 2, 6, 2, 3, 6);
        Range range = new Range(0, 10);

        PageProcessingLab.ScanResult sequential =
                PageProcessingLab.sequential(List.of(accepted), range);
        PageProcessingLab.ScanResult parallel =
                PageProcessingLab.parallel(List.of(accepted), range, 2);

        assertThat(sequential.matchingRows()).isEqualTo(3);
        assertThat(sequential.acceptedPages()).isEqualTo(1);
        assertThat(sequential.decodedPages()).isZero();
        assertThat(parallel.matchingRows()).isEqualTo(3);
        assertThat(parallel.peakInFlightTasks()).isZero();
    }

    @Test
    void boundedParallelPagesAlwaysAgreeWithIndependentOracle() throws Exception {
        Random random = new Random(20261009L); // fixed seed, not flaky timing
        List<Page> pages = new ArrayList<>();
        for (int pageId = 0; pageId < 48; pageId++) {
            long[] values = new long[1 + random.nextInt(32)];
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            for (int i = 0; i < values.length; i++) {
                values[i] = random.nextInt(201) - 100L;
                min = Math.min(min, values[i]);
                max = Math.max(max, values[i]);
            }
            pages.add(new Page(pageId, values, pageId % 3 != 0, min, max));
        }

        for (Range range : List.of(
                new Range(-200, 200),
                new Range(-100, -100),
                new Range(-10, 10),
                new Range(0, 100),
                new Range(101, 200),
                new Range(Long.MIN_VALUE, Long.MAX_VALUE))) {
            PageProcessingLab.ScanResult oracle = PageProcessingLab.reference(pages, range);
            PageProcessingLab.ScanResult sequential = PageProcessingLab.sequential(pages, range);
            assertThat(sequential.matchingRows()).isEqualTo(oracle.matchingRows());

            for (int workers : List.of(1, 2, 4, 7)) {
                PageProcessingLab.ScanResult parallel =
                        PageProcessingLab.parallel(pages, range, workers);
                assertThat(parallel.matchingRows())
                        .as("Range %s workers %s", range, workers)
                        .isEqualTo(oracle.matchingRows());
                assertThat(parallel.peakInFlightTasks()).isLessThanOrEqualTo(2 * workers);
                assertThat(parallel.decodedPages() + parallel.prunedPages()
                        + parallel.acceptedPages()).isEqualTo(pages.size());
            }
        }
    }

    @Test
    void boundedSchedulingReportsActualOutstandingTaskCap() throws Exception {
        List<Page> pages = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            // Unknown metadata -> every page must actually be decoded.
            pages.add(page(i, false, 0, 0, i));
        }

        PageProcessingLab.ScanResult result =
                PageProcessingLab.parallel(pages, new Range(0, 30), 2);
        assertThat(result.matchingRows()).isEqualTo(25);
        assertThat(result.decodedPages()).isEqualTo(25);
        assertThat(result.peakInFlightTasks()).isBetween(1, 4);
    }

    @Test
    void pageArrayIsDefensivelyCopiedOnConstructionAndRead() {
        long[] input = {10, 20};
        Page page = new Page(0, input, true, 10, 20);
        input[0] = -100;

        assertThat(page.values()).containsExactly(10, 20);
        long[] readCopy = page.values();
        readCopy[1] = -100;
        assertThat(page.values()).containsExactly(10, 20);
    }

    @Test
    void invalidBoundsAndWorkerLimitsFailClosed() {
        assertThatThrownBy(() -> new Range(7, 3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> page(-1, false, 0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> page(0, true, 20, 10, 15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageProcessingLab.parallel(List.of(),
                new Range(0, 2), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PageProcessingLab.parallel(List.of(),
                new Range(0, 2), 65)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullInputAndNullPagesAreRejectedRatherThanSilentlySkipped() {
        Range range = new Range(1, 10);
        assertThatThrownBy(() -> PageProcessingLab.reference(null, range))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PageProcessingLab.sequential(List.of(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PageProcessingLab.parallel(
                Arrays.asList(page(0, true, 1, 2, 1), null), range, 1))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void referenceMustInspectAllPagesRegardlessOfStatistics() {
        List<Page> pages = List.of(
                page(0, true, 1, 3, 1, 2, 3),
                page(1, false, 0, 0, 4, 5),
                page(2, true, 8, 10, 8, 9, 10));
        Range range = new Range(3, 8);
        PageProcessingLab.ScanResult oracle = PageProcessingLab.reference(pages, range);
        PageProcessingLab.ScanResult optimized = PageProcessingLab.sequential(pages, range);
        assertThat(oracle.matchingRows()).isEqualTo(4);
        assertThat(oracle.decodedPages()).isEqualTo(3);
        assertThat(optimized.matchingRows()).isEqualTo(4);
    }

    private static Page page(int id, boolean trusted,
                             long minimum, long maximum, long... values) {
        return new Page(id, values, trusted, minimum, maximum);
    }
}
