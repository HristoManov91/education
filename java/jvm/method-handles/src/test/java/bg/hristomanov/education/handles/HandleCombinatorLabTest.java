package bg.hristomanov.education.handles;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HandleCombinatorLabTest {

    @Test
    void guardWithTestAgreesWithPlainJavaAtPriceBoundaries() throws Throwable {
        for (int cents : new int[]{0, 1, 999, 1_000, 1_001, 100_000}) {
            assertEquals(HandleCombinatorLab.directPrice(cents),
                    HandleCombinatorLab.linkedPrice(cents));
        }
    }

    @Test
    void invalidNegativeInputNeverReachesPricingPath() {
        assertThrows(IllegalArgumentException.class,
                () -> HandleCombinatorLab.directPrice(-1));
        assertThrows(IllegalArgumentException.class,
                () -> HandleCombinatorLab.linkedPrice(-1));
    }
}
