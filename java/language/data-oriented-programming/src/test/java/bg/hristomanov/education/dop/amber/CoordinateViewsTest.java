package bg.hristomanov.education.dop.amber;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoordinateViewsTest {

    @Test
    void factoryReturnsStablePublicInterfaceWithoutExposingImplementationType() {
        CoordinateView view = CoordinateViews.of(3, -4);
        assertEquals(3, view.x());
        assertEquals(-4, view.y());
        assertEquals(7, CoordinateViews.manhattanDistanceFromOrigin(view));
        assertFalse(Modifier.isPublic(view.getClass().getModifiers()));
        assertFalse(view.getClass().isRecord());
    }

    @Test
    void clientsCanImplementTheSameStableInterfaceWithoutRecordPatterns() {
        CoordinateView externalImplementation = new CoordinateView() {
            @Override
            public int x() {
                return 5;
            }

            @Override
            public int y() {
                return 12;
            }
        };
        assertEquals(17, CoordinateViews.manhattanDistanceFromOrigin(externalImplementation));
    }

    @Test
    void nullAndArithmeticOverflowFailExplicitly() {
        assertThrows(NullPointerException.class,
                () -> CoordinateViews.manhattanDistanceFromOrigin(null));
        assertThrows(ArithmeticException.class,
                () -> CoordinateViews.manhattanDistanceFromOrigin(
                        CoordinateViews.of(Integer.MIN_VALUE, 1)));
        assertThrows(ArithmeticException.class,
                () -> CoordinateViews.manhattanDistanceFromOrigin(
                        CoordinateViews.of(Integer.MAX_VALUE, 1)));
    }
}
