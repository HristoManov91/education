package bg.hristomanov.education.performance.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HandlerDispatchLabTest {
    @Test
    void allStrategiesAgreeForKnownTypesAndNegativeInputs() {
        HandlerDispatchLab.Chain chain = new HandlerDispatchLab.Chain(
                List.of(new HandlerDispatchLab.Add(7),
                        new HandlerDispatchLab.Multiply(3),
                        new HandlerDispatchLab.Xor(0x35),
                        new HandlerDispatchLab.Add(-4)));
        for (int input = -200; input <= 200; input++) {
            int expected = chain.viaVirtual(input);
            assertEquals(expected, chain.viaInstanceof(input));
            assertEquals(expected, chain.viaPreclassified(input));
        }
    }

    @Test
    void unknownExtensionTypeUsesFallback() {
        HandlerDispatchLab.Handler extension = value -> value / 2;
        HandlerDispatchLab.Chain chain = new HandlerDispatchLab.Chain(
                List.of(new HandlerDispatchLab.Add(10), extension,
                        new HandlerDispatchLab.Xor(5)));
        assertEquals(chain.viaVirtual(13), chain.viaInstanceof(13));
        assertEquals(chain.viaVirtual(13), chain.viaPreclassified(13));
    }

    @Test
    void emptyChainIsIdentityAndInputListIsDefensivelyCopied() {
        assertEquals(-123, new HandlerDispatchLab.Chain(List.of()).viaPreclassified(-123));
        List<HandlerDispatchLab.Handler> source = new ArrayList<>();
        source.add(new HandlerDispatchLab.Add(1));
        HandlerDispatchLab.Chain bound = new HandlerDispatchLab.Chain(source);
        source.add(new HandlerDispatchLab.Add(100));
        assertEquals(1, bound.size());
        assertEquals(42, bound.viaPreclassified(41));
    }

    @Test
    void nullHandlerIsRejectedWhenBindingChain() {
        assertThrows(NullPointerException.class,
                () -> new HandlerDispatchLab.Chain(null));
        List<HandlerDispatchLab.Handler> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class,
                () -> new HandlerDispatchLab.Chain(withNull));
    }
}
