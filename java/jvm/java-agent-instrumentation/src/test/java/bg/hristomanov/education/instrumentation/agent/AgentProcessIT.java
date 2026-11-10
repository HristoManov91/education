package bg.hristomanov.education.instrumentation.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Истински child-JVM integration test, изпълняван след package от Maven Failsafe.
 * JUnit тест в същата JVM не доказва premain/manifest instrumentation.
 */
class AgentProcessIT {

    private static final Path AGENT = Path.of("target/java-agent-instrumentation-1.0-SNAPSHOT-agent.jar")
            .toAbsolutePath();
    private static final Path CLASSES = Path.of("target/classes").toAbsolutePath();

    @TempDir
    Path dumpFolder;

    @Test
    void manifestAndRealBytecodeTransformationPreserveTheBusinessOutput() throws Exception {
        assertTrue(Files.isRegularFile(AGENT), "Maven Assembly must create the -agent.jar in package");
        try (JarFile jar = new JarFile(AGENT.toFile())) {
            assertEquals("bg.hristomanov.education.instrumentation.agent.PriceTimingAgent",
                    jar.getManifest().getMainAttributes().getValue("Premain-Class"));
        }

        Result withoutAgent = run(false, false);
        Result withAgent = run(true, false);

        assertEquals(0, withoutAgent.exitCode(), withoutAgent.output());
        assertEquals(0, withAgent.exitCode(), withAgent.output());
        assertTrue(withoutAgent.output().contains("TOTAL_CENTS=2100"));
        assertTrue(withAgent.output().contains("TOTAL_CENTS=2100"));
        assertFalse(withoutAgent.output().contains("LAB_AGENT quoteCents"));
        assertTrue(withAgent.output().contains("LAB_AGENT quoteCents status=OK"),
                "Agent must actually inject exit advice, not only log from premain");

        try (java.util.stream.Stream<Path> paths = Files.walk(dumpFolder)) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().contains("PricingService")
                            && path.getFileName().toString().endsWith(".class")),
                    "Byte Buddy must dump a transformed PricingService class");
        }
    }

    @Test
    void instrumentationDoesNotSwallowBusinessExceptions() throws Exception {
        Result failure = run(true, true);
        assertTrue(failure.exitCode() != 0, failure.output());
        assertTrue(failure.output().contains("IllegalArgumentException"), failure.output());
        assertTrue(failure.output().contains("LAB_AGENT quoteCents status=ERROR"), failure.output());
    }

    private Result run(boolean withAgent, boolean fail) throws IOException, InterruptedException {
        String binary = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", binary);
        List<String> args = new ArrayList<>();
        args.add(java.toString());
        if (withAgent) {
            args.add("-Dnet.bytebuddy.dump=" + dumpFolder.toAbsolutePath());
            args.add("-javaagent:" + AGENT);
        }
        args.add("-cp");
        args.add(CLASSES.toString());
        args.add("bg.hristomanov.education.instrumentation.sample.PriceApplication");
        if (fail) {
            args.add("failure");
        }
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Child JVM did not finish in 30s");
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(process.exitValue(), output);
    }

    private record Result(int exitCode, String output) {
    }
}
