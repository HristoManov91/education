package bg.hristomanov.education.quarkus.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreetingRulesTest {
    @Test
    void acceptsOneWellFormedGreeting() {
        assertEquals("Hello", GreetingRules.requireSingle(List.of("Hello")));
    }

    @Test
    void rejectsMissingOrDuplicateAnnotations() {
        assertThrows(IllegalStateException.class, () -> GreetingRules.requireSingle(List.of()));
        assertThrows(IllegalStateException.class,
                () -> GreetingRules.requireSingle(List.of("A", "B")));
    }

    @Test
    void rejectsEmptyOrOversizedMessage() {
        assertThrows(IllegalStateException.class,
                () -> GreetingRules.requireSingle(List.of(" ")));
        assertThrows(IllegalStateException.class,
                () -> GreetingRules.requireSingle(List.of("A".repeat(201))));
    }
}
