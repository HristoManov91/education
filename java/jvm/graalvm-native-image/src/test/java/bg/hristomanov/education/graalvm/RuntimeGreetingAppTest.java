package bg.hristomanov.education.graalvm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RuntimeGreetingAppTest {
    @Test
    void staticAndReflectiveExecutionReturnTheSameAnswer() throws Exception {
        String normal = outputOf("--name=Ada", "--iterations=3");
        String reflection = outputOf("--name=Ada", "--iterations=3", "--reflection");

        assertEquals("READY greeting=Hello, Ada! checksum=33 mode=static", normal);
        assertEquals("READY greeting=Hello, Ada! checksum=33 mode=reflection", reflection);
    }

    @Test
    void rejectsUnapprovedClassWithoutTryingToLoadIt() {
        assertThrows(IllegalArgumentException.class,
                () -> GreetingFactory.reflectiveProvider("java.lang.Runtime"));
    }

    @Test
    void rejectsUnknownFlagsAndInvalidWorkloads() {
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeGreetingApp.Configuration.parse(new String[]{"--bogus"}));
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeGreetingApp.Configuration.parse(new String[]{"--iterations=0"}));
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeGreetingApp.Configuration.parse(new String[]{"--hold-ms=60001"}));
    }

    @Test
    void deterministicChecksumForRepeatedExecution() throws Exception {
        String one = outputOf("--name=Java", "--iterations=11");
        String two = outputOf("--name=Java", "--iterations=11");
        assertEquals(one, two);
        assertTrue(one.contains("checksum=132"));
    }

    private static String outputOf(String... args) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            RuntimeGreetingApp.run(args, output);
        }
        return bytes.toString(StandardCharsets.UTF_8).strip();
    }
}
