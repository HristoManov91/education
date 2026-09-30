package bg.hristomanov.education.containers;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Този тест е нарочно disabled по подразбиране, защото дублира PostgreSQL startup-а
 * на основния @ServiceConnection тест. Държим го компилируем като учебен fallback:
 * използвай го, когато Spring Boot няма ConnectionDetails integration за dependency-то.
 */
@Disabled("Educational fallback; enable manually when comparing @DynamicPropertySource with @ServiceConnection")
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
@Testcontainers(disabledWithoutDocker = true)
class DynamicPropertySourceFallbackIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));

    @DynamicPropertySource
    static void registerPostgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    DynamicPropertySourceFallbackIntegrationTest(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Test
    void manualDynamicPropertiesCanWireTheContainerWhenNoServiceConnectionExists() {
        String databaseVersion = jdbcTemplate.queryForObject("select version()", String.class);

        assertThat(databaseVersion).containsIgnoringCase("PostgreSQL");
    }
}
