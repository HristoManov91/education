package bg.hristomanov.education.hperf;

import bg.hristomanov.education.hperf.blaze.BlazeCustomerView;
import bg.hristomanov.education.hperf.blaze.BlazeOrderView;
import bg.hristomanov.education.hperf.domain.PerfCustomer;
import com.blazebit.persistence.Criteria;
import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.CriteriaBuilderFactory;
import com.blazebit.persistence.PagedList;
import com.blazebit.persistence.PaginatedCriteriaBuilder;
import com.blazebit.persistence.view.EntityViewManager;
import com.blazebit.persistence.view.EntityViewSetting;
import com.blazebit.persistence.view.EntityViews;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Blaze-Persistence Entity View integration on the module's Hibernate 7.4/H2 stack.
 * Dependencies are test-only; the production application has no Blaze dependency.
 */
@SpringBootTest
@Import(BlazeEntityViewTest.BlazeTestConfiguration.class)
class BlazeEntityViewTest {
    @Autowired TransactionTemplate transactions;
    @Autowired EntityManager entityManager;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired CriteriaBuilderFactory criteriaBuilderFactory;
    @Autowired EntityViewManager entityViewManager;

    @BeforeEach
    void clean() {
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("delete from PerfOrder").executeUpdate();
            entityManager.createQuery("delete from PerfCustomer").executeUpdate();
            entityManager.clear();
        });
    }

    @Test
    void entityViewPaginationKeepsParentPageAndNestedCollectionsIntact() {
        seedCustomers();

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.clear();

        PagedList<BlazeCustomerView> page = transactions.execute(status -> {
            CriteriaBuilder<PerfCustomer> criteriaBuilder =
                    criteriaBuilderFactory.create(entityManager, PerfCustomer.class);
            criteriaBuilder.orderByAsc("id");

            EntityViewSetting<BlazeCustomerView, PaginatedCriteriaBuilder<BlazeCustomerView>> setting =
                    EntityViewSetting.create(BlazeCustomerView.class, 0, 2);

            return entityViewManager.applySetting(setting, criteriaBuilder).getResultList();
        });

        assertThat(page).isNotNull().hasSize(2);
        assertThat(page.getTotalSize()).isEqualTo(3L);
        assertThat(page.get(0).getName()).isEqualTo("Blaze-customer-1");
        assertThat(page.get(0).getOrders()).hasSize(2);
        assertThat(page.get(0).getOrders())
                .extracting(BlazeOrderView::getTotal)
                .containsExactlyInAnyOrder(new BigDecimal("10.00"), new BigDecimal("11.00"));
        assertThat(page.get(1).getName()).isEqualTo("Blaze-customer-2");
        assertThat(page.get(1).getOrders()).hasSize(1);

        // Pagination may use a count query, a parent-id page, and one or more bounded
        // view/collection fetches. The important invariant is no per-parent N+1 fan-out.
        assertThat(statistics.getPrepareStatementCount()).isBetween(2L, 4L);
    }

    private void seedCustomers() {
        transactions.executeWithoutResult(status -> {
            LocalDateTime createdAt = LocalDateTime.parse("2026-10-01T12:00:00");

            PerfCustomer first = new PerfCustomer("Blaze-customer-1");
            first.placeOrder(new BigDecimal("10.00"), createdAt);
            first.placeOrder(new BigDecimal("11.00"), createdAt.plusMinutes(1));
            entityManager.persist(first);

            PerfCustomer second = new PerfCustomer("Blaze-customer-2");
            second.placeOrder(new BigDecimal("20.00"), createdAt.plusMinutes(2));
            entityManager.persist(second);

            PerfCustomer third = new PerfCustomer("Blaze-customer-3");
            entityManager.persist(third);

            entityManager.flush();
            entityManager.clear();
        });
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BlazeTestConfiguration {
        @Bean
        CriteriaBuilderFactory criteriaBuilderFactory(EntityManagerFactory entityManagerFactory) {
            // Blaze requires one eagerly-created factory per EntityManagerFactory.
            return Criteria.getDefault().createCriteriaBuilderFactory(entityManagerFactory);
        }

        @Bean
        EntityViewManager entityViewManager(CriteriaBuilderFactory criteriaBuilderFactory) {
            com.blazebit.persistence.view.spi.EntityViewConfiguration configuration =
                    EntityViews.createDefaultConfiguration();
            configuration.addEntityView(BlazeCustomerView.class);
            configuration.addEntityView(BlazeOrderView.class);
            return configuration.createEntityViewManager(criteriaBuilderFactory);
        }
    }
}
