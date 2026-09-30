package bg.hristomanov.education.containers;

import bg.hristomanov.education.containers.cache.GreetingCache;
import bg.hristomanov.education.containers.customer.Customer;
import bg.hristomanov.education.containers.customer.CustomerDirectory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Testcontainers(disabledWithoutDocker = true)
class SpringBootServiceConnectionIntegrationTest {

    private static final int REDIS_PORT = 6379;

    /*
     * Testcontainers 2.x премести typed container класовете в module-specific packages.
     * За PostgreSQL това вече е org.testcontainers.postgresql.PostgreSQLContainer.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    /*
     * Redis тук е GenericContainer. Spring Boot не може да познае service type-а
     * само от Java типа, затова name="redis" е важен hint за RedisConnectionDetails.
     */
    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8.10.2"))
                    .withExposedPorts(REDIS_PORT)
                    .waitingFor(Wait.forListeningPort());

    private final CustomerDirectory customerDirectory;
    private final GreetingCache greetingCache;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    SpringBootServiceConnectionIntegrationTest(
            CustomerDirectory customerDirectory,
            GreetingCache greetingCache,
            JdbcTemplate jdbcTemplate) {
        this.customerDirectory = customerDirectory;
        this.greetingCache = greetingCache;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void cleanState() {
        customerDirectory.deleteAll();
        greetingCache.delete("customer-1");
    }

    @Test
    void springBootMustUseTheRealPostgresAndRedisStartedByTheTest() {
        Customer saved = customerDirectory.register("containers@example.test", "Containers Student");
        Optional<String> displayName = customerDirectory.findDisplayNameByEmail("containers@example.test");

        greetingCache.put("customer-1", "Hello from a real Redis container");
        Optional<String> cachedGreeting = greetingCache.find("customer-1");

        String databaseVersion = jdbcTemplate.queryForObject("select version()", String.class);
        Integer postgresMappedPort = POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
        Integer redisMappedPort = REDIS.getMappedPort(REDIS_PORT);

        assertThat(saved.getId()).isNotNull();
        assertThat(displayName).contains("Containers Student");
        assertThat(cachedGreeting).contains("Hello from a real Redis container");
        assertThat(databaseVersion).containsIgnoringCase("PostgreSQL");
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(REDIS.isRunning()).isTrue();
        assertThat(postgresMappedPort).isBetween(1, 65_535);
        assertThat(redisMappedPort).isBetween(1, 65_535);
    }
}
