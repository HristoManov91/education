package bg.hristomanov.education.integrationpatterns.queue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CellIsolationTest {

    @Test
    void noisyTenantOnlyDelaysOtherTenantsInItsCellWhenCapacityIsIndependent() {
        var router = new CellRouter(4);
        int noisyCell = router.cellFor("noisy");
        String sameCellTenant = findTenantInCell(router, noisyCell, "quiet-");
        String otherCellTenant = findTenantInCell(router,
                (noisyCell + 1) % router.cellCount(), "outside-");

        // One independent FIFO worker per cell. The 64-message burst enters
        // before both quiet tenants submit their work.
        List<GlobalFifoScheduler> cellQueues = IntStream.range(0, router.cellCount())
                .mapToObj(i -> new GlobalFifoScheduler())
                .toList();
        for (int i = 0; i < 64; i++) {
            cellQueues.get(noisyCell)
                    .enqueue(new QueuedJob("noisy", "hot-" + i, 1));
        }
        cellQueues.get(router.cellFor(sameCellTenant))
                .enqueue(new QueuedJob(sameCellTenant, "quiet-inside", 1));
        cellQueues.get(router.cellFor(otherCellTenant))
                .enqueue(new QueuedJob(otherCellTenant, "quiet-outside", 1));

        List<QueueSimulation.Result> independent = cellQueues.stream()
                .map(QueueSimulation::drain)
                .toList();

        assertThat(independent.get(noisyCell).p95DwellTicks(sameCellTenant))
                .isEqualTo(64);
        assertThat(independent.get(router.cellFor(otherCellTenant))
                .p95DwellTicks(otherCellTenant)).isZero();
        assertThat(independent.stream().mapToInt(QueueSimulation.Result::processedCount).sum())
                .isEqualTo(66);
    }

    @Test
    void tenantPlacementIsDeterministicWithinOneFixedTopology() {
        CellRouter router = new CellRouter(8);
        assertThat(router.cellFor("tenant-A")).isEqualTo(router.cellFor("tenant-A"));
        assertThat(router.cellFor("tenant-A")).isBetween(0, 7);
        assertThatThrownBy(() -> new CellRouter(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static String findTenantInCell(CellRouter router, int cell, String prefix) {
        for (int i = 0; i < 1000; i++) {
            String tenantId = prefix + i;
            if (router.cellFor(tenantId) == cell) {
                return tenantId;
            }
        }
        throw new AssertionError("No matching tenant found in deterministic fixture");
    }
}
