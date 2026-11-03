package bg.hristomanov.education.vector;

import org.junit.jupiter.api.Test;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DotProductsTest {
    @Test
    void matchesDoublePrecisionReferenceAcrossLengthsAndTails() {
        int lanes = VectorDotProducts.lanes();
        int[] lengths = {0, 1, 2, 3, 4, lanes - 1, lanes, lanes + 1,
                4 * lanes - 1, 4 * lanes, 4 * lanes + 1, 257, 1023, 8193};
        Random random = new Random(0xC0FFEE);
        for (int length : lengths) {
            float[] left = new float[length];
            float[] right = new float[length];
            double reference = 0d;
            for (int i = 0; i < length; i++) {
                left[i] = 2f * random.nextFloat() - 1f;
                right[i] = 2f * random.nextFloat() - 1f;
                reference += (double) left[i] * right[i];
            }
            double tolerance = 0.0002 + Math.abs(reference) * 0.00005;
            assertEquals(reference, ScalarDotProducts.simple(left, right), tolerance, "simple length=" + length);
            assertEquals(reference, ScalarDotProducts.unrolled4(left, right), tolerance, "unrolled length=" + length);
            assertEquals(reference, ScalarDotProducts.fused4(left, right), tolerance, "scalar fma length=" + length);
            assertEquals(reference, VectorDotProducts.multiplyAdd(left, right), tolerance, "SIMD length=" + length);
            assertEquals(reference, VectorDotProducts.fusedMultiplyAdd(left, right), tolerance, "SIMD fma length=" + length);
            assertEquals(reference, VectorDotProducts.smallInputFallback(left, right), tolerance, "fallback length=" + length);
        }
    }

    @Test
    void rejectsMismatchedDimensionsAndNull() {
        float[] one = {1f};
        float[] two = {1f, 2f};
        assertThrows(IllegalArgumentException.class, () -> ScalarDotProducts.simple(one, two));
        assertThrows(IllegalArgumentException.class, () -> ScalarDotProducts.unrolled4(one, two));
        assertThrows(IllegalArgumentException.class, () -> ScalarDotProducts.fused4(one, two));
        assertThrows(IllegalArgumentException.class, () -> VectorDotProducts.multiplyAdd(one, two));
        assertThrows(IllegalArgumentException.class, () -> VectorDotProducts.fusedMultiplyAdd(one, two));
        assertThrows(IllegalArgumentException.class, () -> VectorDotProducts.smallInputFallback(one, two));
        assertThrows(NullPointerException.class, () -> ScalarDotProducts.simple(null, one));
        assertThrows(NullPointerException.class, () -> VectorDotProducts.multiplyAdd(one, null));
        assertThrows(NullPointerException.class, () -> VectorDotProducts.smallInputFallback(null, null));
    }

    @Test
    void illustratesFloatingPointOrderingDifferenceWithoutBitwiseEqualityClaim() {
        float[] left = {10_000_000f, 1f, -10_000_000f, 3f, 5f};
        float[] right = {1f, 1f, 1f, 1f, 1f};
        double reference = 9d;
        assertEquals(reference, ScalarDotProducts.unrolled4(left, right), 1d);
        assertEquals(reference, VectorDotProducts.multiplyAdd(left, right), 1d);
        assertEquals(reference, VectorDotProducts.fusedMultiplyAdd(left, right), 1d);
    }
}
