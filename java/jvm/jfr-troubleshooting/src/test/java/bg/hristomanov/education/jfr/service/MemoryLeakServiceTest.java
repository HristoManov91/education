package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.jfr.domain.LabResults.MemoryResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryLeakServiceTest {

    @Test
    void shouldRetainAndThenReleaseReferences() {
        MemoryLeakService service = new MemoryLeakService();

        MemoryResult first = service.retain(1);
        MemoryResult second = service.retain(1);

        assertThat(first.retainedBlocks()).isEqualTo(1);
        assertThat(second.retainedBlocks()).isEqualTo(2);
        assertThat(second.retainedBytes()).isEqualTo(2L * 1024L * 1024L);

        MemoryResult cleared = service.clear();

        assertThat(cleared.retainedBlocks()).isZero();
        assertThat(cleared.retainedBytes()).isZero();
    }
}
