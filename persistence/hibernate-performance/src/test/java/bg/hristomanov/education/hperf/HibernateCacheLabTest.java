package bg.hristomanov.education.hperf;

import bg.hristomanov.education.hperf.domain.CachedCatalogItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VM-19 fast H2 lab; the JCache provider is added only to TEST classpath.
 * This demonstrates a single JVM / SessionFactory, not multi-node consistency.
 */
@SpringBootTest(properties = {
        "spring.jpa.properties.hibernate.cache.region.factory_class=jcache",
        "spring.jpa.properties.hibernate.cache.use_second_level_cache=true",
        "spring.jpa.properties.hibernate.cache.use_query_cache=true",
        "spring.jpa.properties.hibernate.javax.cache.provider="
                + "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider",
        "spring.jpa.properties.hibernate.javax.cache.missing_cache_strategy=create",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class HibernateCacheLabTest {
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory emf;
    @Autowired private JdbcTemplate jdbc;

    private SessionFactory sessionFactory() {
        return emf.unwrap(SessionFactory.class);
    }

    @BeforeEach
    void resetFixture() {
        sessionFactory().getCache().evictAllRegions();
        jdbc.update("DELETE FROM vm19_catalog_items");
        sessionFactory().getCache().evictAllRegions();
        sessionFactory().getStatistics().clear();
    }

    @Test
    void secondLevelCacheSurvivesNewPersistenceContextsButNotAsTheSameJavaObject() {
        Long id = createItem("VM19-CASE-1", "12.00");
        sessionFactory().getCache().evictAllRegions();
        var stats = sessionFactory().getStatistics();
        stats.clear();

        CachedCatalogItem first = transactions.execute(status ->
                entityManager.find(CachedCatalogItem.class, id));
        CachedCatalogItem second = transactions.execute(status ->
                entityManager.find(CachedCatalogItem.class, id));

        assertThat(first).isNotSameAs(second);
        assertThat(first.getSku()).isEqualTo("VM19-CASE-1");
        assertThat(second.getSku()).isEqualTo("VM19-CASE-1");
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
        assertThat(stats.getSecondLevelCacheHitCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void ormWriteUpdatesVersionedCacheAndSubsequentSessionSeesCommittedPrice() {
        Long id = createItem("VM19-CASE-2", "12.00");
        transactions.executeWithoutResult(status ->
                entityManager.find(CachedCatalogItem.class, id)
                        .changePrice(new BigDecimal("18.00")));
        CachedCatalogItem after = transactions.execute(status ->
                entityManager.find(CachedCatalogItem.class, id));

        assertThat(after.getPrice()).isEqualByComparingTo("18.00");
        assertThat(after.getVersion()).isEqualTo(1);
    }

    @Test
    void queryResultCacheHitsAndInvalidatesAfterOrmUpdateToSameTable() {
        Long id = createItem("VM19-CASE-3", "15.00");
        sessionFactory().getCache().evictAllRegions();
        var stats = sessionFactory().getStatistics();
        stats.clear();

        assertThat(cacheableSkus(new BigDecimal("10.00"))).containsExactly("VM19-CASE-3");
        assertThat(cacheableSkus(new BigDecimal("10.00"))).containsExactly("VM19-CASE-3");
        assertThat(stats.getQueryCachePutCount()).isGreaterThanOrEqualTo(1);
        assertThat(stats.getQueryCacheHitCount()).isGreaterThanOrEqualTo(1);

        transactions.executeWithoutResult(status ->
                entityManager.find(CachedCatalogItem.class, id)
                        .changePrice(new BigDecimal("5.00")));
        assertThat(cacheableSkus(new BigDecimal("10.00"))).isEmpty();
        assertThat(stats.getQueryCacheMissCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void externalJdbcWriteBypassesHibernateInvalidationUntilExplicitEviction() {
        Long id = createItem("VM19-CASE-4", "20.00");
        var stats = sessionFactory().getStatistics();
        stats.clear();
        CachedCatalogItem cached = transactions.execute(status ->
                entityManager.find(CachedCatalogItem.class, id));
        assertThat(cached.getPrice()).isEqualByComparingTo("20.00");

        // Direct JDBC mutation bypasses Hibernate's cache invalidation & versioning.
        jdbc.update("UPDATE vm19_catalog_items SET price = ? WHERE id = ?",
                new BigDecimal("99.00"), id);

        CachedCatalogItem stale = transactions.execute(status ->
                entityManager.find(CachedCatalogItem.class, id));
        assertThat(stale.getPrice()).isEqualByComparingTo("20.00");
        assertThat(stats.getSecondLevelCacheHitCount()).isGreaterThanOrEqualTo(1);

        emf.getCache().evict(CachedCatalogItem.class, id);
        CachedCatalogItem reloaded = transactions.execute(status ->
                entityManager.find(CachedCatalogItem.class, id));
        assertThat(reloaded.getPrice()).isEqualByComparingTo("99.00");
    }

    private Long createItem(String sku, String price) {
        return transactions.execute(status -> {
            var item = new CachedCatalogItem(sku, new BigDecimal(price));
            entityManager.persist(item);
            entityManager.flush();
            return item.getId();
        });
    }

    private List<String> cacheableSkus(BigDecimal minimum) {
        return transactions.execute(status -> entityManager.createQuery(
                        "select i.sku from CachedCatalogItem i "
                                + "where i.price >= :floor order by i.sku", String.class)
                .setParameter("floor", minimum)
                .setHint("org.hibernate.cacheable", true)
                .getResultList());
    }
}
