import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValue;
import jdk.incubator.json.JsonValueException;

/**
 * JEP 540 reads a REAL HotSpot jcmd JSON thread dump. The JDK provides JSON
 * diagnostic output independent of JEP 540; this lab only uses the incubator
 * API to parse it. No snapshots with volatile thread IDs/PIDs are committed.
 */
public final class Jdk28JsonThreadDumpLab {

    private static final String TARGET_NAME = "education-json-virtual-thread";

    private Jdk28JsonThreadDumpLab() {}

    public static void main(String[] args) throws Exception {
        String synthetic = """
            {"threadDump":{"threadContainers":[
                {"threads":[{"name":"old-platform"},
                            {"name":"example-virtual","virtual":true},
                            {"name":"example-false","virtual":false}]}
            ]}}
            """;
        JsonValue example = Json.parse(synthetic);
        check(countVirtualThreads(example) == 1, "synthetic: optional virtual=false");
        check(!containsVirtualThread(example, "old-platform"), "synthetic: absent virtual flag means false");
        check(containsVirtualThread(example, "example-virtual"), "synthetic: positive virtual flag");
        JsonValue corrupt = Json.parse("""
                {"threadDump":{"threadContainers":[{"threads":[{"name":"bad","virtual":"true"}]}]}}
                """);
        expect(JsonValueException.class, () -> countVirtualThreads(corrupt));
        System.out.println("PASS JSON_THREAD_DUMP_SYNTHETIC: optional property and wrong type negative");

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread virtual = Thread.ofVirtual().name(TARGET_NAME).start(() -> {
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });

        Path directory = Files.createTempDirectory("jdk28-simple-json-thread-dump");
        Path file = directory.resolve("threads.json"); // must NOT preexist without -overwrite
        try {
            check(started.await(5, TimeUnit.SECONDS), "virtual thread failed to start");
            Process command = new ProcessBuilder(
                    "jcmd", Long.toString(ProcessHandle.current().pid()),
                    "Thread.dump_to_file", "-format=json", file.toString())
                    .redirectErrorStream(true)
                    .start();
            if (!command.waitFor(30, TimeUnit.SECONDS)) {
                command.destroyForcibly();
                throw new AssertionError("jcmd timed out");
            }
            String diagnostics = new String(command.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check(command.exitValue() == 0, "jcmd failed: " + diagnostics);
            String json = Files.readString(file);
            check(!json.isBlank() && json.length() < 8_000_000, "Unexpected dump size");
            JsonValue root = Json.parse(json);
            // JDK 27+ writes numeric IDs; JDK 28 must not be treated as an older format.
            check(root.get("threadDump").get("processId").asLong()
                  == ProcessHandle.current().pid(), "thread dump belongs to this PID");
            int virtualCount = countVirtualThreads(root);
            check(virtualCount >= 1, "expected a virtual thread in the real dump");
            check(containsVirtualThread(root, TARGET_NAME),
                    "named virtual thread not present in the real dump");
            System.out.println("PASS JDK28_REAL_THREAD_DUMP: virtualCount=" + virtualCount
                    + ", named thread present, JDK-process ID verified");
        } finally {
            release.countDown();
            virtual.join(5_000);
            check(!virtual.isAlive(), "virtual thread did not stop");
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static int countVirtualThreads(JsonValue root) {
        int count = 0;
        for (JsonValue container : root.get("threadDump").get("threadContainers").asList()) {
            for (JsonValue thread : container.get("threads").asList()) {
                if (isVirtual(thread)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean containsVirtualThread(JsonValue root, String name) {
        for (JsonValue container : root.get("threadDump").get("threadContainers").asList()) {
            for (JsonValue thread : container.get("threads").asList()) {
                if (isVirtual(thread) && thread.tryGet("name")
                        .map(JsonValue::asString).orElse("").equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isVirtual(JsonValue thread) {
        return thread.tryGet("virtual").map(JsonValue::asBoolean).orElse(false);
    }

    private static void check(boolean condition, String error) {
        if (!condition) {
            throw new AssertionError(error);
        }
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable found) {
            if (type.isInstance(found)) return;
            throw new AssertionError("Expected " + type + " but got " + found, found);
        }
        throw new AssertionError("Expected exception: " + type.getSimpleName());
    }
}
