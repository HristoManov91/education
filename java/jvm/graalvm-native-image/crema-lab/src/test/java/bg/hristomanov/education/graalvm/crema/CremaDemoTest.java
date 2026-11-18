package bg.hristomanov.education.graalvm.crema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CremaDemoTest {
    @Test
    void dynamicallyGeneratedClassProducesCorrectAnswer() throws Exception {
        CremaDemo.Announcement actual = CremaDemo.generate("Alina", "Devoxx");
        assertEquals("Hello Alina at Devoxx", actual.text());
        assertTrue(actual.generatedClassName().contains("GeneratedGreeting"));
    }

    @Test
    void consecutiveCallsDefineDifferentClassesAtRuntime() throws Exception {
        CremaDemo.Announcement first = CremaDemo.generate("Alina", "Belgium");
        CremaDemo.Announcement second = CremaDemo.generate("Thomas", "France");

        assertNotEquals(first.generatedClassName(), second.generatedClassName());
        assertNotEquals(first.text(), second.text());
    }

    @Test
    void rejectsEmptyOrOversizedInputs() {
        assertThrows(IllegalArgumentException.class, () -> CremaDemo.generate("", "Devoxx"));
        assertThrows(IllegalArgumentException.class, () -> CremaDemo.generate("Alina", " "));
        assertThrows(IllegalArgumentException.class,
                () -> CremaDemo.generate("a".repeat(61), "Devoxx"));
    }
}
