package bg.hristomanov.education.vector;

/**
 * Обикновени Java 25 цикли без Vector API.
 * JDK 27 C2 SuperWord може да ги векторизира по-добре (subword casts);
 * наличието на този код НЕ доказва SIMD машинни инструкции.
 */
public final class SubwordLoops {

    private SubwordLoops() {
    }

    public static byte[] narrowInts(int[] input) {
        byte[] output = new byte[input.length];
        for (int index = 0; index < input.length; index++) {
            output[index] = (byte) input[index];
        }
        return output;
    }

    public static int[] widenShorts(short[] input) {
        int[] output = new int[input.length];
        for (int index = 0; index < input.length; index++) {
            output[index] = input[index];
        }
        return output;
    }
}
