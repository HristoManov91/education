package bg.hristomanov.education.jfr.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import jdk.jfr.Recording;

/**
 * Optional CLI lab: writes a LOCAL synthetic JFR file and prints ONLY the
 * allowlisted aggregate evidence. Do not upload the raw .jfr to AI services.
 */
public final class RuntimeContextDemo {
    private RuntimeContextDemo() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: RuntimeContextDemo <output.jfr>");
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        if (!output.getFileName().toString().endsWith(".jfr")) {
            throw new IllegalArgumentException("Expected an output .jfr file");
        }
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        try (Recording recording = new Recording()) {
            recording.enable(RuntimeTraceEvent.class).withThreshold(Duration.ZERO);
            recording.enable(RuntimeWorkEvent.class).withThreshold(Duration.ZERO);
            recording.start();
            RuntimeContextLab lab = new RuntimeContextLab();
            lab.run("lab-checkout-a", 150_000);
            lab.run("lab-checkout-b", 120_000);
            recording.stop();
            recording.dump(output);
        }
        System.out.print(JfrEvidenceSummary.analyze(output).toReviewText());
    }
}
