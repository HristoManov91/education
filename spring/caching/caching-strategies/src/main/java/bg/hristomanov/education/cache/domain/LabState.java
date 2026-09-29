package bg.hristomanov.education.cache.domain;

import java.util.Map;

/**
 * Диагностичен snapshot на laboratory state-а.
 */
public record LabState(
        Product persistedProduct,
        int repositoryReads,
        int repositoryWrites,
        int pendingWriteBehindWrites,
        Map<String, CacheStatistics> caches
) {
}
