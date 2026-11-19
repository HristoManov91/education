package bg.hristomanov.education.hperf;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Version;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.dialect.H2Dialect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VM-30: failing guard on an unreviewed Hibernate major upgrade, and explicit
 * evidence that H2 tests are not PostgreSQL/Oracle dialect tests.
 */
@SpringBootTest
class HibernateUpgradeGuardTest {
    @Autowired EntityManagerFactory entityManagerFactory;

    @Test void reviewedHibernateMajorVersionAndLocalDialectAreExplicit() {
        assertThat(Version.getVersionString()).startsWith("7.");
        var factory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        assertThat(factory.getJdbcServices().getDialect()).isInstanceOf(H2Dialect.class);
    }
}
