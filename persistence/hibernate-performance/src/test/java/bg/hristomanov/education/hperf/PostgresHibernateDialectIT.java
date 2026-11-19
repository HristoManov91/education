package bg.hristomanov.education.hperf;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class PostgresHibernateDialectIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired EntityManagerFactory factory;
    @Autowired JdbcTemplate jdbc;

    @Test void dialectMatchesRealDatabaseNotAnH2CompatibilityMode() {
        var sessionFactory = factory.unwrap(SessionFactoryImplementor.class);
        assertThat(sessionFactory.getJdbcServices().getDialect())
                .isInstanceOf(PostgreSQLDialect.class);
        assertThat(jdbc.queryForObject("SELECT current_setting('server_version_num')", String.class))
                .startsWith("17");
    }
}
