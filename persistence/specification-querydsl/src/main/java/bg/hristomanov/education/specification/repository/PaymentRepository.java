package bg.hristomanov.education.specification.repository;

import bg.hristomanov.education.specification.domain.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PaymentRepository
        extends JpaRepository<PaymentEntity, Long>, JpaSpecificationExecutor<PaymentEntity> {
}
