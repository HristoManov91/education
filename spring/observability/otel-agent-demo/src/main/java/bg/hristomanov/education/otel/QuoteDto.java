package bg.hristomanov.education.otel;

/** cached=true показва Redis hit и липсващ downstream HTTP span при втория request. */
public record QuoteDto(String sku, long totalCents, boolean cached) {
}
