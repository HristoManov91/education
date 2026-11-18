package bg.hristomanov.education.graalvm.layers;

/**
 * Минимален standalone consumer на споделения JDK base layer.
 * При реален framework layer включваме и стабилните dependencies.
 */
public final class SpanishService {
    private SpanishService() {
    }

    public static void main(String[] args) throws InterruptedException {
        long holdMillis = 0L;
        for (String arg : args) {
            if (!arg.startsWith("--hold-ms=")) {
                throw new IllegalArgumentException("Unsupported argument: " + arg);
            }
            holdMillis = Long.parseLong(arg.substring("--hold-ms=".length()));
        }
        if (holdMillis < 0 || holdMillis > 120_000) {
            throw new IllegalArgumentException("hold-ms out of allowed range");
        }
        System.out.println("READY greeting=Hola!");
        System.out.flush();
        if (holdMillis > 0) {
            Thread.sleep(holdMillis);
        }
    }
}
