package bg.hristomanov.education.cache.domain;

import java.math.BigDecimal;

/**
 * Малък immutable domain object, използван от всички стратегии.
 *
 * <p>{@code version} ни помага визуално да разпознаем stale data
 * (остарели данни): ако repository-то вече е версия 3, а cache-ът връща
 * версия 2, consistency проблемът се вижда веднага.</p>
 */
public record Product(
        long id,
        String name,
        BigDecimal price,
        long version
) {
}
