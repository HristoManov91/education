package bg.hristomanov.education.jfr.domain;

/**
 * Малки immutable DTO-та за резултатите от лабораторните сценарии.
 *
 * <p>Те не са същината на JFR темата. Държим ги на едно място, за да останат service класовете
 * фокусирани върху поведението, което искаме да наблюдаваме във Flight Recorder.</p>
 */
public final class LabResults {

    private LabResults() {
    }

    public record LabInfo(
            long pid,
            String javaVersion,
            String vmName,
            String osName) {
    }

    public record CpuResult(
            int iterations,
            long durationMillis,
            double checksum) {
    }

    public record MemoryResult(
            int retainedBlocks,
            long retainedBytes,
            int lastAllocationMiB) {
    }

    public record LockResult(
            int workers,
            int holdMillis,
            long durationMillis,
            double averageWaitMillis,
            double maxWaitMillis) {
    }

    public record FileIoResult(
            int megabytes,
            long bytesWritten,
            long bytesRead,
            long durationMillis,
            String temporaryFile) {
    }

    public record OrderResult(
            String orderId,
            int itemCount,
            long durationMillis,
            double calculationChecksum) {
    }

    public record ScenarioResult(
            CpuResult cpu,
            LockResult locks,
            FileIoResult fileIo,
            OrderResult order) {
    }
}
