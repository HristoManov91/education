package bg.hristomanov.education.otel;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Два различни HTTP boundary-а: клиентската заявка и локална fake upstream цена.
 * Self-HTTP е само за обученията; НЕ е production архитектурна препоръка.
 */
@RestController
public final class QuoteController {

    private final PriceLookupService lookup;

    public QuoteController(PriceLookupService lookup) {
        this.lookup = lookup;
    }

    @GetMapping("/api/quotes/{sku}")
    public QuoteDto quote(@PathVariable String sku) {
        return lookup.quote(sku);
    }

    @GetMapping("/internal/prices/{sku}")
    public PriceDto fakeUpstream(@PathVariable String sku) {
        if (!"widget".equals(sku)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown SKU");
        }
        return new PriceDto(sku, 1250);
    }
}
