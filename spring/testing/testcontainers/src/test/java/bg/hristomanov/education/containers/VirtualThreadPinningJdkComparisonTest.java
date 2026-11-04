package bg.hristomanov.education.containers;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in, heavier cross-JDK experiment. Does not assert time/speedup or JFR event counts:
 * those depend on CPU, scheduler, JDK build and the actual blocking library.
 */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfEnvironmentVariable(named = "RUN_JDK_COMPARISON", matches = "true")
class VirtualThreadPinningJdkComparisonTest {
    @Test
    @Timeout(240)
    void identicalJava21SourceRunsOnJava21AndJava25ThroughSameToxiproxy() throws Exception {
        try (ToxiproxyScenario scenario = new ToxiproxyScenario()) {
            scenario.start();
            scenario.proxy().toxics().latency("slow-downstream", ToxicDirection.DOWNSTREAM, 650);
            try {
                runProbe("eclipse-temurin:21-jdk-jammy", scenario);
                runProbe("eclipse-temurin:25-jdk-jammy", scenario);
            } finally {
                scenario.proxy().toxics().get("slow-downstream").remove();
            }
        }
    }

    private static void runProbe(String image, ToxiproxyScenario scenario) throws Exception {
        try (GenericContainer<?> jdk = new GenericContainer<>(DockerImageName.parse(image))
                .withNetwork(scenario.network())
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("pinning/PinningProbe.java"),
                        "/lab/PinningProbe.java")
                .withCommand("sleep", "300")) {
            jdk.start();
            Container.ExecResult run = jdk.execInContainer(
                    "java", "-Djdk.tracePinnedThreads=full", "--source", "21",
                    "/lab/PinningProbe.java", scenario.dockerUrl(), "12");
            System.out.println("=== " + image + " stdout ===\n" + run.getStdout());
            System.out.println("=== " + image + " stderr ===\n" + run.getStderr());
            assertThat(run.getExitCode()).isZero();
            assertThat(run.getStdout()).contains("PINNING_PROBE_OK");
        }
    }
}
