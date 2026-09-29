package bg.hristomanov.education.outbox.service;

import bg.hristomanov.education.outbox.domain.OrderEntity;
import bg.hristomanov.education.outbox.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class NaiveOrderPersistenceService {

    private final OrderRepository orderRepository;

    public NaiveOrderPersistenceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderEntity create(
            String reference,
            String customerId,
            BigDecimal totalAmount
    ) {
        OrderEntity order = new OrderEntity(
                reference,
                customerId,
                totalAmount,
                Instant.now()
        );
        return orderRepository.saveAndFlush(order);
    }
}
