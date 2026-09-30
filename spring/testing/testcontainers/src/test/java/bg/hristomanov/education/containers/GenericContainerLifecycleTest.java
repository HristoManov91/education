package bg.hristomanov.education.containers;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class GenericContainerLifecycleTest {

    private static final int REDIS_PORT = 6379;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forListeningPort());

    @Test
    void testMustDiscoverTheRuntimeHostAndMappedPortInsteadOfHardCodingThem() throws Exception {
        String host = REDIS.getHost();
        Integer mappedPort = REDIS.getMappedPort(REDIS_PORT);
        String pingOutput = REDIS.execInContainer("redis-cli", "PING").getStdout().trim();

        assertThat(REDIS.isRunning()).isTrue();
        assertThat(host).isNotBlank();
        assertThat(mappedPort).isBetween(1, 65_535);
        assertThat(pingOutput).isEqualTo("PONG");
    }
}
