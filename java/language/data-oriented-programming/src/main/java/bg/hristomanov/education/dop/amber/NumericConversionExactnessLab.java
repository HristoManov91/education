package bg.hristomanov.education.dop.amber;

/**
 * Portable Java 25 int -> float exactness without preview primitive patterns.
 * This is NOT a universal numeric-conversion algorithm for all primitive types.
 */
public final class NumericConversionExactnessLab {

    private NumericConversionExactnessLab() {
    }

    /** BAD: int saturation can hide an earlier loss of precision. */
    public static boolean misleadingRoundTrip(int number) {
        float converted = (float) number;
        return (int) converted == number;
    }

    /**
     * Every 32-bit int is exactly representable as double; promotion from
     * float to double preserves its exact (possibly rounded) float value.
     * Therefore this comparison is exact for this PARTICULAR conversion.
     */
    public static boolean exactlyRepresentableAsFloat(int number) {
        float converted = (float) number;
        return (double) converted == (double) number;
    }

    /** A fail-closed conversion for persistence/DTO/financial boundaries. */
    public static float requireExactFloat(int number) {
        if (!exactlyRepresentableAsFloat(number)) {
            throw new ArithmeticException("int cannot be represented exactly as float: " + number);
        }
        return (float) number;
    }
}
