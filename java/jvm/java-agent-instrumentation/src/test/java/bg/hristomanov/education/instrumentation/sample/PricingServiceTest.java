package bg.hristomanov.education.instrumentation.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingServiceTest {

    private final PricingService service = new PricingService();

    @Test
    void returnsSameBusinessValueIndependentlyOfInstrumentation() {
        assertEquals(2100L, service.quoteCents(3, 700));
        assertEquals(0L, service.quoteCents(0, 700));
    }

    @Test
    void rejectsNegativeInputsAndArithmeticOverflow() {
        assertThrows(IllegalArgumentException.class, () -> service.quoteCents(-1, 700));
        assertThrows(IllegalArgumentException.class, () -> service.quoteCents(2, -1));
        assertThrows(ArithmeticException.class, () -> service.quoteCents(2, Long.MAX_VALUE));
    }
}
