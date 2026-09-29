package bg.hristomanov.education.cache.domain;

/**
 * Малък snapshot за observability (наблюдаемост) на cache поведението.
 */
public record CacheStatistics(
        String cacheName,
        long hitCount,
        long missCount,
        long evictionCount,
        long estimatedSize
) {

    public static CacheStatistics empty(String cacheName) {
        return new CacheStatistics(cacheName, 0, 0, 0, 0);
    }
}
