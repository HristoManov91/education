package bg.hristomanov.education.dop.amber;

import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NumericConversionExactnessLabTest {

    @Test
    void maximumIntShowsWhyNaiveIntFloatIntRoundTripIsMisleading() {
        int number = Integer.MAX_VALUE;
        float converted = (float) number;

        assertEquals(2_147_483_648.0, (double) converted);
        assertTrue(NumericConversionExactnessLab.misleadingRoundTrip(number));
        assertFalse(NumericConversionExactnessLab.exactlyRepresentableAsFloat(number));
        assertThrows(ArithmeticException.class,
                () -> NumericConversionExactnessLab.requireExactFloat(number));
    }

    @Test
    void mantissaBoundaryAndNegativeBoundaryAreHandledPrecisely() {
        assertTrue(NumericConversionExactnessLab.exactlyRepresentableAsFloat(16_777_216));
        assertFalse(NumericConversionExactnessLab.exactlyRepresentableAsFloat(16_777_217));
        assertFalse(NumericConversionExactnessLab.exactlyRepresentableAsFloat(-16_777_217));
        assertTrue(NumericConversionExactnessLab.exactlyRepresentableAsFloat(Integer.MIN_VALUE));
        assertFalse(NumericConversionExactnessLab.exactlyRepresentableAsFloat(Integer.MIN_VALUE + 1));
        assertTrue(NumericConversionExactnessLab.exactlyRepresentableAsFloat(0));
        assertEquals(16_777_216.0f,
                NumericConversionExactnessLab.requireExactFloat(16_777_216));
    }

    @Test
    void highMagnitudeExactValuesMustBeMultiplesOfTheirFloatSpacing() {
        // At |x| >= 2^24, float ULP grows with the binary exponent.
        assertTrue(NumericConversionExactnessLab.exactlyRepresentableAsFloat(1_000_000_000));
        assertFalse(NumericConversionExactnessLab.exactlyRepresentableAsFloat(1_000_000_001));
        assertTrue(NumericConversionExactnessLab.exactlyRepresentableAsFloat(-1_000_000_000));
    }

    @Test
    void independentIeee754BitSpacingOracleAgreesAcrossSeededIntegers() {
        Random random = new Random(2026_1109L);
        for (int count = 0; count < 10_000; count++) {
            int value = random.nextInt();
            boolean expected = bitSpacingOracle(value);
            assertEquals(expected,
                    NumericConversionExactnessLab.exactlyRepresentableAsFloat(value),
                    "Wrong exactness result for " + value);
        }
    }

    /** Distinct mathematical oracle, independent of float/double conversion. */
    private static boolean bitSpacingOracle(int value) {
        long abs = Math.abs((long) value);
        if (abs == 0) {
            return true;
        }
        int exponent = 63 - Long.numberOfLeadingZeros(abs);
        int shift = Math.max(0, exponent - 23); // binary32 precision: 24 bits
        long lowBitsMask = (1L << shift) - 1;
        return (abs & lowBitsMask) == 0;
    }
}
