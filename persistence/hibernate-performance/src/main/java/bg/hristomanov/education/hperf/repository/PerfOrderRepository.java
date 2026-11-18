package bg.hristomanov.education.hperf.repository;

import bg.hristomanov.education.hperf.domain.PerfOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.util.List;

public interface PerfOrderRepository extends JpaRepository<PerfOrder, Long> {
    @EntityGraph(attributePaths = "customer")
    List<PerfOrder> findAllByOrderByIdAsc();

    Slice<PerfOrder> findByTotalGreaterThan(BigDecimal minimumTotal, Pageable page);
}
