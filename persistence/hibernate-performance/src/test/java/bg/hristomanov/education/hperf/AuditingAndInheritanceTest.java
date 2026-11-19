package bg.hristomanov.education.hperf;

import bg.hristomanov.education.hperf.domain.AuditedInvoice;
import bg.hristomanov.education.hperf.domain.FleetDrone;
import bg.hristomanov.education.hperf.domain.FleetVan;
import bg.hristomanov.education.hperf.domain.TransportAsset;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditingAndInheritanceTest {
    @Autowired EntityManager entityManager;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void enversKeepsImmutableRevisionsForCommittedEntityUpdates() {
        String ref = "VM27-" + UUID.randomUUID();
        Long id = transactions.execute(status -> {
            var invoice = new AuditedInvoice(ref, new BigDecimal("52.00"));
            entityManager.persist(invoice);
            entityManager.flush();
            return invoice.getId();
        });

        transactions.executeWithoutResult(status -> {
            var managed = entityManager.find(AuditedInvoice.class, id);
            managed.approve();
        });

        transactions.executeWithoutResult(status -> {
            entityManager.clear();
            var reader = AuditReaderFactory.get(entityManager);
            var revisions = reader.getRevisions(AuditedInvoice.class, id);
            assertThat(revisions).hasSize(2);
            var first = reader.find(AuditedInvoice.class, id, revisions.get(0));
            var second = reader.find(AuditedInvoice.class, id, revisions.get(1));
            assertThat(first.getStatus()).isEqualTo("DRAFT");
            assertThat(second.getStatus()).isEqualTo("APPROVED");
            assertThat(second.getTotal()).isEqualByComparingTo("52.00");
        });
    }

    @Test
    void joinedInheritanceReturnsCorrectSubtypesWithSeparateTables() {
        String code = "VM28-" + UUID.randomUUID();
        transactions.executeWithoutResult(status -> {
            entityManager.persist(new FleetVan(code + "-V", 900));
            entityManager.persist(new FleetDrone(code + "-D", 30));
        });

        transactions.executeWithoutResult(status -> {
            entityManager.clear();
            var assets = entityManager.createQuery("""
                    select a from TransportAsset a
                    where a.code like :code
                    order by a.code
                    """, TransportAsset.class)
                    .setParameter("code", code + "%")
                    .getResultList();
            assertThat(assets).hasSize(2);
            assertThat(assets).anySatisfy(a -> {
                assertThat(a).isInstanceOf(FleetVan.class);
                assertThat(((FleetVan) a).getPayloadKg()).isEqualTo(900);
            });
            assertThat(assets).anySatisfy(a -> {
                assertThat(a).isInstanceOf(FleetDrone.class);
                assertThat(((FleetDrone) a).getFlightMinutes()).isEqualTo(30);
            });
        });

        assertThat(jdbc.queryForObject(
                "select count(*) from vm28_assets where code like ?", Integer.class, code + "%"))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from vm28_vans", Integer.class))
                .isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from vm28_drones", Integer.class))
                .isGreaterThanOrEqualTo(1);
    }
}
