package bg.hristomanov.education.otel;

/** Wire contract на учебната upstream HTTP услуга. */
public record PriceDto(String sku, long unitPriceCents) {
}
