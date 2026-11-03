package bg.hristomanov.education.vector;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

/** Explicit SIMD experiments; runtime must enable --add-modules jdk.incubator.vector. */
public final class VectorDotProducts {
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private VectorDotProducts() { }

    public static int lanes() {
        return SPECIES.length();
    }

    public static float multiplyAdd(float[] left, float[] right) {
        return compute(left, right, false);
    }

    public static float fusedMultiplyAdd(float[] left, float[] right) {
        return compute(left, right, true);
    }

    /** Illustrative policy, not an auto-tuned CPU benchmark or a cross-JVM capability guarantee. */
    public static float smallInputFallback(float[] left, float[] right) {
        int length = ScalarDotProducts.validatedLength(left, right);
        if (length < SPECIES.length() * 4) {
            return ScalarDotProducts.unrolled4(left, right);
        }
        return multiplyAdd(left, right);
    }

    private static float compute(float[] left, float[] right, boolean fused) {
        int length = ScalarDotProducts.validatedLength(left, right);
        int lanes = SPECIES.length();
        int stride = lanes * 4;
        FloatVector a0 = FloatVector.zero(SPECIES);
        FloatVector a1 = FloatVector.zero(SPECIES);
        FloatVector a2 = FloatVector.zero(SPECIES);
        FloatVector a3 = FloatVector.zero(SPECIES);
        int i = 0;
        for (; i <= length - stride; i += stride) {
            FloatVector x0 = FloatVector.fromArray(SPECIES, left, i);
            FloatVector y0 = FloatVector.fromArray(SPECIES, right, i);
            FloatVector x1 = FloatVector.fromArray(SPECIES, left, i + lanes);
            FloatVector y1 = FloatVector.fromArray(SPECIES, right, i + lanes);
            FloatVector x2 = FloatVector.fromArray(SPECIES, left, i + 2 * lanes);
            FloatVector y2 = FloatVector.fromArray(SPECIES, right, i + 2 * lanes);
            FloatVector x3 = FloatVector.fromArray(SPECIES, left, i + 3 * lanes);
            FloatVector y3 = FloatVector.fromArray(SPECIES, right, i + 3 * lanes);
            if (fused) {
                a0 = x0.fma(y0, a0);
                a1 = x1.fma(y1, a1);
                a2 = x2.fma(y2, a2);
                a3 = x3.fma(y3, a3);
            } else {
                a0 = a0.add(x0.mul(y0));
                a1 = a1.add(x1.mul(y1));
                a2 = a2.add(x2.mul(y2));
                a3 = a3.add(x3.mul(y3));
            }
        }
        FloatVector accumulator = a0.add(a1).add(a2).add(a3);
        for (; i <= length - lanes; i += lanes) {
            FloatVector x = FloatVector.fromArray(SPECIES, left, i);
            FloatVector y = FloatVector.fromArray(SPECIES, right, i);
            accumulator = fused ? x.fma(y, accumulator) : accumulator.add(x.mul(y));
        }
        float result = accumulator.reduceLanes(VectorOperators.ADD);
        // Correct scalar tail when dimensions are not a multiple of the selected species.
        for (; i < length; i++) {
            result = fused ? Math.fma(left[i], right[i], result) : result + left[i] * right[i];
        }
        return result;
    }
}
