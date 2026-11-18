package bg.hristomanov.education.graalvm;

import java.io.PrintStream;
import java.util.Objects;

/**
 * Същият workload за сравнението JVM / Leyden / Native Image.
 *
 * READY от този процес е application-level събитие; външният
 * benchmark измерва целия process launch, а не само време в main().
 */
public final class RuntimeGreetingApp {
    private RuntimeGreetingApp() {
    }

    public static void main(String[] args) throws InterruptedException {
        run(args, System.out);
    }

    static void run(String[] args, PrintStream out) throws InterruptedException {
        Objects.requireNonNull(args, "args");
        Objects.requireNonNull(out, "out");
        Configuration configuration = Configuration.parse(args);
        GreetingProvider provider = configuration.reflective()
                ? GreetingFactory.reflectiveProvider(
                        "bg.hristomanov.education.graalvm.ReflectionGreeting")
                : GreetingFactory.staticProvider();

        long checksum = 0L;
        String greeting = provider.greet(configuration.name());
        for (int index = 0; index < configuration.iterations(); index++) {
            // Детерминистично и лесно проверимо; НЕ е микро-бенчмарк за JIT throughput.
            checksum += greeting.length();
        }
        out.println("READY greeting=" + greeting
                + " checksum=" + checksum
                + " mode=" + (configuration.reflective() ? "reflection" : "static"));
        out.flush();

        if (configuration.holdMillis() > 0L) {
            Thread.sleep(configuration.holdMillis());
        }
    }

    record Configuration(String name, int iterations, boolean reflective, long holdMillis) {
        static Configuration parse(String[] args) {
            String name = "Java";
            int iterations = 50_000;
            boolean reflective = false;
            long holdMillis = 0L;
            for (String arg : args) {
                if (arg.startsWith("--name=")) {
                    name = arg.substring("--name=".length());
                } else if (arg.startsWith("--iterations=")) {
                    iterations = Integer.parseInt(arg.substring("--iterations=".length()));
                } else if (arg.startsWith("--hold-ms=")) {
                    holdMillis = Long.parseLong(arg.substring("--hold-ms=".length()));
                } else if (arg.equals("--reflection")) {
                    reflective = true;
                } else {
                    throw new IllegalArgumentException("Unknown argument: " + arg);
                }
            }
            if (name.isBlank() || name.length() > 80 || iterations < 1
                    || iterations > 10_000_000 || holdMillis < 0 || holdMillis > 60_000) {
                throw new IllegalArgumentException("Invalid name, iterations or holdMillis");
            }
            return new Configuration(name, iterations, reflective, holdMillis);
        }
    }
}
