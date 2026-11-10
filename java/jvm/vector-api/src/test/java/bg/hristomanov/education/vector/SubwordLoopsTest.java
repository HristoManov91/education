package bg.hristomanov.education.vector;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SubwordLoopsTest {

    @Test
    void narrowingAndSignExtensionRemainCorrectAtTailAndOverflowBoundaries() {
        for (int size : new int[]{0, 1, 15, 16, 17, 63, 64, 65, 128, 129}) {
            int[] original = new int[size];
            short[] shorts = new short[size];
            for (int index = 0; index < size; index++) {
                original[index] = index * 101 - 400;
                shorts[index] = (short) (index * 1_101 - 32_768);
            }
            byte[] expectedNarrow = new byte[size];
            int[] expectedWide = new int[size];
            for (int index = 0; index < size; index++) {
                expectedNarrow[index] = (byte) original[index];
            }
            Arrays.setAll(expectedWide, index -> (int) shorts[index]);
            assertArrayEquals(expectedNarrow, SubwordLoops.narrowInts(original));
            assertArrayEquals(expectedWide, SubwordLoops.widenShorts(shorts));
        }
    }

    @Test
    void negativeByteAndShortValuesAreSignedNotUnsigned() {
        assertEquals((byte) -1, SubwordLoops.narrowInts(new int[]{255})[0]);
        assertEquals(-1, SubwordLoops.widenShorts(new short[]{-1})[0]);
    }
}
