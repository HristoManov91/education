package bg.hristomanov.education.cloud;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CloudBudgetTest {
    private static final long MONTH = 30L * 24 * 3600;

    @Test
    void mandatoryAndRedundantAreDifferentFailureModels() {
        assertEquals(.9405, CloudBudget.independentMandatory(.99, .95), 1e-12);
        var bounds = CloudBudget.mandatoryBounds(.99, .95);
        assertEquals(.94, bounds.lower(), 1e-12);
        assertEquals(.95, bounds.upper(), 1e-12);
        assertEquals(.9995, CloudBudget.independentRedundant(.99, .95), 1e-12);
    }

    @Test
    void invalidProbabilityIsNotAnAvailabilityEstimate() {
        assertThrows(IllegalArgumentException.class, () -> CloudBudget.independentMandatory(1.01, .95));
        assertThrows(IllegalArgumentException.class, () -> CloudBudget.mandatoryBounds(Double.NaN, .95));
        assertThrows(IllegalArgumentException.class, () -> CloudBudget.independentRedundant(-.1, .95));
    }

    @Test
    void directionalPayloadExplainsLectureEstimate() {
        var traffic = CloudBudget.traffic(100, MONTH, 500, 1000);
        decimal("129.6", traffic.requestGB());
        decimal("259.2", traffic.responseGB());
        decimal("388.8", traffic.totalGB());
    }

    @Test
    void ratesApplyToSenderDirectionAndRetriesIncreaseCost() {
        var fixed = new BigDecimal("30");
        var request = new BigDecimal("0.09");
        var response = new BigDecimal("0.12");
        decimal("72.768", CloudBudget.cost(CloudBudget.traffic(100, MONTH, 500, 1000),
                request, response, fixed));
        decimal("81.3216", CloudBudget.cost(CloudBudget.traffic(120, MONTH, 500, 1000),
                request, response, fixed));
    }

    @Test
    void zeroNegativeAndOverflowInputsHaveExplicitBehavior() {
        decimal("0", CloudBudget.traffic(0, MONTH, 500, 1000).totalGB());
        assertThrows(IllegalArgumentException.class, () -> CloudBudget.traffic(1, 1, -1, 1));
        assertThrows(ArithmeticException.class, () -> CloudBudget.traffic(Long.MAX_VALUE, 2, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> CloudBudget.cost(
                CloudBudget.traffic(1, 1, 1, 1), BigDecimal.valueOf(-1), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static void decimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), actual.toPlainString());
    }
}
