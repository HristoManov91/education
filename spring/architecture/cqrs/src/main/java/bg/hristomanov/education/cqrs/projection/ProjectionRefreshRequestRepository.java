package bg.hristomanov.education.cqrs.projection;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectionRefreshRequestRepository
        extends JpaRepository<ProjectionRefreshRequest, String> {

    Optional<ProjectionRefreshRequest>
    findFirstByProcessedAtIsNullOrderByCreatedAtAscIdAsc();

    long countByProcessedAtIsNull();
}
