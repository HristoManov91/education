package bg.hristomanov.education.performance.hardwood;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagePipelineLabTest {

    @Test
    void oneRowGroupCanStillProduceSeveralPageLevelTasksInOriginalOrder()
            throws ExecutionException, InterruptedException {
        List<PagePipelineLab.PageRef> pages = List.of(
                new PagePipelineLab.PageRef(0, "fare", 0),
                new PagePipelineLab.PageRef(0, "fare", 1),
                new PagePipelineLab.PageRef(0, "fare", 2),
                new PagePipelineLab.PageRef(0, "fare", 3));

        AtomicInteger loaded = new AtomicInteger();
        AtomicInteger decoded = new AtomicInteger();

        PagePipelineLab.RunReport report = PagePipelineLab.run(pages, 2, 2,
                page -> {
                    assertThat(Thread.currentThread().isVirtual())
                            .as("Blocking page I/O is modeled on virtual threads").isTrue();
                    loaded.incrementAndGet();
                    return new byte[] {(byte) (page.pageIndex() + 10)};
                },
                (page, encoded) -> {
                    assertThat(Thread.currentThread().isVirtual())
                            .as("CPU decoding uses the fixed platform worker pool").isFalse();
                    decoded.incrementAndGet();
                    return encoded[0] & 0xff;
                });

        assertThat(report.results().stream()
                .map(PagePipelineLab.PageResult::decodedValue))
                .containsExactly(10L, 11L, 12L, 13L);
        assertThat(report.results().stream()
                .map(PagePipelineLab.PageResult::page))
                .containsExactlyElementsOf(pages);
        assertThat(report.maxOutstandingPages()).isEqualTo(2);
        assertThat(report.cpuWorkerLimit()).isEqualTo(2);
        assertThat(loaded.get()).isEqualTo(4);
        assertThat(decoded.get()).isEqualTo(4);
    }

    @Test
    void fixedCpuWorkersAreABoundNotAnUnlimitedVirtualThreadPool()
            throws ExecutionException, InterruptedException {
        List<PagePipelineLab.PageRef> pages = java.util.stream.IntStream.range(0, 12)
                .mapToObj(index -> new PagePipelineLab.PageRef(0, "total", index))
                .toList();

        AtomicInteger decoding = new AtomicInteger();
        AtomicInteger peakDecodes = new AtomicInteger();
        PagePipelineLab.RunReport report = PagePipelineLab.run(pages, 3, 2,
                page -> new byte[] {1, 2, 3},
                (page, raw) -> {
                    int active = decoding.incrementAndGet();
                    peakDecodes.accumulateAndGet(active, Math::max);
                    try {
                        return (long) raw[0] + raw[1] + raw[2];
                    } finally {
                        decoding.decrementAndGet();
                    }
                });

        assertThat(report.results()).hasSize(pages.size());
        assertThat(report.maxOutstandingPages()).isLessThanOrEqualTo(3);
        assertThat(peakDecodes.get()).isBetween(1, 2);
        assertThat(decoding.get()).isZero();
        assertThat(report.results()).allSatisfy(result ->
                assertThat(result.decodedValue()).isEqualTo(6L));
    }

    @Test
    void failingPageLoadingIsNotSilentlyConvertedIntoAnEmptySuccess() {
        PagePipelineLab.PageRef page = new PagePipelineLab.PageRef(0, "id", 0);

        assertThatThrownBy(() -> PagePipelineLab.run(List.of(page), 1, 1,
                ignored -> {
                    throw new IOException("Corrupt remote page");
                },
                (ignored, bytes) -> 0L))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IOException.class)
                .hasRootCauseMessage("Corrupt remote page");
    }

    @Test
    void decodingErrorsPropagateInsteadOfPublishingPartialSuccess() {
        PagePipelineLab.PageRef page = new PagePipelineLab.PageRef(0, "id", 0);

        assertThatThrownBy(() -> PagePipelineLab.run(List.of(page), 1, 1,
                ignored -> new byte[] {12},
                (ignored, bytes) -> {
                    throw new IllegalArgumentException("Unexpected encoding");
                }))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Unexpected encoding");
    }

    @Test
    void missingPayloadAndInvalidLimitsAreRejected() {
        PagePipelineLab.PageRef page = new PagePipelineLab.PageRef(0, "id", 0);

        assertThatThrownBy(() -> PagePipelineLab.run(List.of(page), 1, 1,
                ignored -> null, (ignored, bytes) -> 0L))
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> PagePipelineLab.run(List.of(page), 0, 1,
                ignored -> new byte[0], (ignored, bytes) -> 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PagePipelineLab.run(List.of(page), 1, 0,
                ignored -> new byte[0], (ignored, bytes) -> 0L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagePipelineLab.PageRef(-1, "id", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PagePipelineLab.PageRef(0, "  ", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyPlanDoesNotInvokeLoaderOrDecoder()
            throws ExecutionException, InterruptedException {
        PagePipelineLab.RunReport report = PagePipelineLab.run(List.of(), 2, 1,
                page -> {
                    throw new AssertionError("No page should be loaded");
                },
                (page, bytes) -> {
                    throw new AssertionError("No page should be decoded");
                });
        assertThat(report.results()).isEmpty();
        assertThat(report.maxOutstandingPages()).isZero();
    }
}
