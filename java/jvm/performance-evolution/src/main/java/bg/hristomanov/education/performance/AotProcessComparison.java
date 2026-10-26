package bg.hristomanov.education.performance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manual experiment only: compare multiple separate child JVM process durations.
 * The AOT cache must first be trained for the SAME JDK + classpath + workload.
 *
 * Do NOT use results as a throughput benchmark or a production SLA prediction.
 * Measurements are intentionally NOT run in Maven CI.
 */
public final class AotProcessComparison {

    private static final int WARMUPS = 2;
    private static final int SAMPLES = 7;
    private static final Duration PROCESS_BUDGET = Duration.ofSeconds(30);

    private AotProcessComparison() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: java ... AotProcessComparison <absolute-or-relative-cache.aot>");
        }
        Path cache = Path.of(args[0]).toAbsolutePath();
        if (!Files.isRegularFile(cache)) {
            throw new IllegalArgumentException("AOT cache file does not exist: " + cache);
        }
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java")
                .toString();
        String classPath = System.getProperty("java.class.path");

        // Alternate process launches to reduce drift from thermal/OS effects.
        List<Long> normal = new ArrayList<>();
        List<Long> withCache = new ArrayList<>();
        for (int i = 0; i < WARMUPS + SAMPLES; i++) {
            long cold = run(javaExecutable, classPath, null);
            long cached = run(javaExecutable, classPath, cache);
            if (i >= WARMUPS) {
                normal.add(cold);
                withCache.add(cached);
            }
        }

        System.out.printf("Process wall-clock median (ms), no AOT cache: %.2f%n",
                BenchmarkStatistics.medianMillis(normal));
        System.out.printf("Process wall-clock median (ms), with AOT cache: %.2f%n",
                BenchmarkStatistics.medianMillis(withCache));
        System.out.println("NOT a JMH throughput test. Investigate GC/JFR/CPU separately.");
    }

    private static long run(String javaExecutable, String classPath, Path cache)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                javaExecutable,
                "-cp", classPath));
        if (cache != null) {
            command.add(1, "-XX:AOTCache=" + cache);
        }
        command.add(StartupWorkload.class.getName());
        command.add("25000");

        long started = System.nanoTime();
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(
                PROCESS_BUDGET.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Child JVM timed out: " + command);
        }
        long duration = System.nanoTime() - started;
        String output = new String(process.getInputStream().readAllBytes());
        if (process.exitValue() != 0 || !output.contains("checksum=")) {
            throw new IllegalStateException("Process failed (exit=" +
                    process.exitValue() + "): " + output);
        }
        return duration;
    }
}
