package bg.hristomanov.education.instrumentation.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Минимален PROCESS startup експеримент: измерва от launch до exit на child JVM.
 * НЕ измерва CPU, RSS, p99 на HTTP или steady throughput; README ги различава.
 */
public final class AgentStartupComparison {

    private static final int PAIRS = 10;

    private AgentStartupComparison() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected: <absolute path to shaded agent JAR>");
        }
        Path agentJar = Path.of(args[0]).toAbsolutePath();
        Path classes = Path.of("target/classes").toAbsolutePath();
        if (!Files.isRegularFile(agentJar) || !Files.isDirectory(classes)) {
            throw new IllegalArgumentException("Run from module directory after mvn verify");
        }

        // Startup warmup на OS/JVM файловите кешове; не се включва в статистиката.
        run(classes, agentJar, false);
        run(classes, agentJar, true);

        List<Long> baseline = new ArrayList<>();
        List<Long> instrumented = new ArrayList<>();
        for (int index = 0; index < PAIRS; index++) {
            // Редуваме реда, за да не е винаги agent-ът втори.
            if (index % 2 == 0) {
                baseline.add(run(classes, agentJar, false));
                instrumented.add(run(classes, agentJar, true));
            } else {
                instrumented.add(run(classes, agentJar, true));
                baseline.add(run(classes, agentJar, false));
            }
        }

        System.out.println("baseline median ms=" + (median(baseline) / 1_000_000.0));
        System.out.println("agent    median ms=" + (median(instrumented) / 1_000_000.0));
        System.out.println("baseline runs ns=" + baseline);
        System.out.println("agent    runs ns=" + instrumented);
        System.out.println("Only process wall-clock, not a production performance claim.");
    }

    static long median(List<Long> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("At least one measurement is required");
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : sorted.get(middle - 1) / 2 + sorted.get(middle) / 2
                    + (sorted.get(middle - 1) % 2 + sorted.get(middle) % 2) / 2;
    }

    private static long run(Path classes, Path agentJar, boolean withAgent)
            throws IOException, InterruptedException {
        String executable = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        List<String> arguments = new ArrayList<>();
        arguments.add(java.toString());
        if (withAgent) {
            arguments.add("-javaagent:" + agentJar);
        }
        arguments.add("-cp");
        arguments.add(classes.toString());
        arguments.add("bg.hristomanov.education.instrumentation.sample.PriceApplication");

        long started = System.nanoTime();
        Process process = new ProcessBuilder(arguments).redirectErrorStream(true).start();
        if (!process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Child JVM exceeded timeout");
        }
        long elapsed = System.nanoTime() - started;
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.exitValue() != 0 || !output.contains("TOTAL_CENTS=2100")
                || (withAgent && !output.contains("LAB_AGENT quoteCents status=OK"))) {
            throw new IllegalStateException("Startup control failed: " + output);
        }
        return elapsed;
    }
}
