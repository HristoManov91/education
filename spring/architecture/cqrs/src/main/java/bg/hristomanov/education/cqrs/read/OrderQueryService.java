package bg.hristomanov.education.cqrs.read;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Query side: връща read DTOs и не изпълнява business state changes.
 */
@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderSummaryRepository repository;

    public OrderQueryService(OrderSummaryRepository repository) {
        this.repository = repository;
    }

    public OrderSummaryDto get(long orderId) {
        return repository.findById(orderId)
                .map(OrderSummaryDto::from)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Read model not available for order: " + orderId
                        )
                );
    }

    public List<OrderSummaryDto> findByCustomer(String customerId) {
        return repository
                .findByCustomerIdOrderByProjectedAtDesc(customerId)
                .stream()
                .map(OrderSummaryDto::from)
                .toList();
    }
}
