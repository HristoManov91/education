package bg.hristomanov.education.hperf.read;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable read model; not a managed entity and has no lazy associations. */
public record OrderRow(Long id, String customerName, BigDecimal total, LocalDateTime createdAt) {}
