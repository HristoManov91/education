package bg.hristomanov.education.patterns.advanced.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Illustrative Data Mapper between a persistence-shaped row (EUR cents) and a domain amount.
 * This is not an ORM or a complete currency model; other currencies have different minor units.
 */
public final class InvoiceMapper {

    public record Invoice(String reference, BigDecimal amountEur) {
        public Invoice {
            if (reference == null || reference.isBlank()) {
                throw new IllegalArgumentException("reference is required");
            }
            Objects.requireNonNull(amountEur, "amountEur");
            if (amountEur.signum() < 0) {
                throw new IllegalArgumentException("negative invoice amount");
            }
            amountEur = amountEur.setScale(2, RoundingMode.UNNECESSARY);
        }
    }

    public record InvoiceRow(String invoiceKey, long amountCents) {}

    public Invoice toDomain(InvoiceRow row) {
        Objects.requireNonNull(row, "row");
        return new Invoice(row.invoiceKey(), BigDecimal.valueOf(row.amountCents(), 2));
    }

    public InvoiceRow toRow(Invoice invoice) {
        Objects.requireNonNull(invoice, "invoice");
        long cents = invoice.amountEur().movePointRight(2).longValueExact();
        return new InvoiceRow(invoice.reference(), cents);
    }
}
