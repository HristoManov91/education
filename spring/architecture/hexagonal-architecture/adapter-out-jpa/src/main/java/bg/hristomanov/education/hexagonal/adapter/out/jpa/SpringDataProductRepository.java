package bg.hristomanov.education.hexagonal.adapter.out.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProductRepository
        extends JpaRepository<JpaProductEntity, String> {
}
