package bg.hristomanov.education.cache.repository;

import bg.hristomanov.education.cache.domain.Product;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Persistence boundary за лабораторията.
 *
 * <p>Реалният проект би имал JPA/JDBC/remote store. Тук държим store-а
 * in-memory, за да можем тестовете да докажат cache semantics без Docker
 * или външна база.</p>
 */
public interface ProductRepository {

    Optional<Product> findById(long productId);

    Product save(long productId, String name, BigDecimal price);

    void seed(Product product);

    Product peek(long productId);

    int readCount();

    int writeCount();

    void reset();
}
