package bg.hristomanov.education.patterns.advanced.mapper;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class InvoiceMapperTest {
    private final InvoiceMapper mapper = new InvoiceMapper();

    @Test void mapsPersistenceMinorUnitsToValidatedDomainAndBack() {
        InvoiceMapper.Invoice invoice = mapper.toDomain(new InvoiceMapper.InvoiceRow("INV-1", 1099));
        assertThat(invoice.amountEur()).isEqualByComparingTo("10.99");
        assertThat(mapper.toRow(invoice)).isEqualTo(new InvoiceMapper.InvoiceRow("INV-1", 1099));
    }

    @Test void refusesSilentRoundingAndOverflow() {
        assertThatThrownBy(() -> new InvoiceMapper.Invoice("INV-2", new BigDecimal("1.001")))
                .isInstanceOf(ArithmeticException.class);
        assertThatThrownBy(() -> mapper.toRow(new InvoiceMapper.Invoice("INV-3", new BigDecimal("999999999999999999.00"))))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test void rejectsInvalidPersistenceAndDomainState() {
        assertThatThrownBy(() -> mapper.toDomain(new InvoiceMapper.InvoiceRow("", 100)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.toDomain(new InvoiceMapper.InvoiceRow("INV-4", -1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
