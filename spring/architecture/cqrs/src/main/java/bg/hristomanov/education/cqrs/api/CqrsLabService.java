package bg.hristomanov.education.cqrs.api;

import bg.hristomanov.education.cqrs.projection.ProjectionRefreshRequestRepository;
import bg.hristomanov.education.cqrs.read.OrderSummaryDto;
import bg.hristomanov.education.cqrs.read.OrderSummaryRepository;
import bg.hristomanov.education.cqrs.write.OrderWriteEntity;
import bg.hristomanov.education.cqrs.write.OrderWriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CqrsLabService {

    private final OrderWriteRepository writeRepository;
    private final OrderSummaryRepository readRepository;
    private final ProjectionRefreshRequestRepository refreshRepository;

    public CqrsLabService(
            OrderWriteRepository writeRepository,
            OrderSummaryRepository readRepository,
            ProjectionRefreshRequestRepository refreshRepository
    ) {
        this.writeRepository = writeRepository;
        this.readRepository = readRepository;
        this.refreshRepository = refreshRepository;
    }

    public CqrsState state(long orderId) {
        OrderWriteEntity writeModel = writeRepository.findById(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Unknown order: " + orderId)
                );

        OrderSummaryDto readModel = readRepository.findById(orderId)
                .map(OrderSummaryDto::from)
                .orElse(null);

        return new CqrsState(
                orderId,
                writeModel.getStatus().name(),
                writeModel.getVersion(),
                readModel,
                refreshRepository.countByProcessedAtIsNull()
        );
    }
}
