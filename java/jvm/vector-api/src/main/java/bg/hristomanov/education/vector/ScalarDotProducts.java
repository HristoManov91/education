package bg.hristomanov.education.vector;

import java.util.Objects;

/** Scalar reference algorithms. Keep these independent of the incubating Vector API. */
public final class ScalarDotProducts {
    private ScalarDotProducts() { }

    public static float simple(float[] left, float[] right) {
        int length = validatedLength(left, right);
        float sum = 0f;
        for (int i = 0; i < length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }

    public static float unrolled4(float[] left, float[] right) {
        int length = validatedLength(left, right);
        float a0 = 0f, a1 = 0f, a2 = 0f, a3 = 0f;
        int i = 0;
        for (; i <= length - 4; i += 4) {
            // Independent accumulation chains reduce long dependency chains (ILP).
            a0 += left[i] * right[i];
            a1 += left[i + 1] * right[i + 1];
            a2 += left[i + 2] * right[i + 2];
            a3 += left[i + 3] * right[i + 3];
        }
        float total = ((a0 + a1) + a2) + a3;
        for (; i < length; i++) {
            total += left[i] * right[i];
        }
        return total;
    }

    public static float fused4(float[] left, float[] right) {
        int length = validatedLength(left, right);
        float a0 = 0f, a1 = 0f, a2 = 0f, a3 = 0f;
        int i = 0;
        for (; i <= length - 4; i += 4) {
            a0 = Math.fma(left[i], right[i], a0);
            a1 = Math.fma(left[i + 1], right[i + 1], a1);
            a2 = Math.fma(left[i + 2], right[i + 2], a2);
            a3 = Math.fma(left[i + 3], right[i + 3], a3);
        }
        float total = ((a0 + a1) + a2) + a3;
        for (; i < length; i++) {
            total = Math.fma(left[i], right[i], total);
        }
        return total;
    }

    static int validatedLength(float[] left, float[] right) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        if (left.length != right.length) {
            throw new IllegalArgumentException("Vector dimensions must match");
        }
        return left.length;
    }
}
