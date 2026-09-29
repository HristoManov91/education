package bg.hristomanov.education.events.service;

import bg.hristomanov.education.events.domain.OrderAggregate;
import bg.hristomanov.education.events.repository.OrderAggregateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Сравнява два важни JPA/Spring Data paths:
 *
 * <p>1) managed entity + repository.save() -> dirty checking + DomainEvents publication;</p>
 * <p>2) managed entity without save() -> dirty checking still persists state,
 * но Spring Data repository domain-event hook не се извиква.</p>
 */
@Service
public class OrderDomainEventService {

    private final OrderAggregateRepository repository;

    public OrderDomainEventService(OrderAggregateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public long createOrder(String reference) {
        return repository.saveAndFlush(new OrderAggregate(reference)).getId();
    }

    @Transactional
    public void markPaidAndSave(long orderId) {
        OrderAggregate order = required(orderId);
        order.markPaid();

        /*
         * За managed entity save не е нужен само за dirty checking.
         * Тук обаче има допълнителна semantics: Spring Data repository save()
         * е trigger за @DomainEvents publication.
         */
        repository.save(order);
    }

    @Transactional
    public void markPaidWithoutSave(long orderId) {
        OrderAggregate order = required(orderId);
        order.markPaid();

        /*
         * JPA ще persist-не PAID чрез dirty checking.
         * Но Spring Data repository domain event publication не се trigger-ва,
         * защото няма repository save/delete call с aggregate instance.
         */
    }

    @Transactional(readOnly = true)
    public OrderAggregate get(long orderId) {
        return required(orderId);
    }

    private OrderAggregate required(long orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown order: " + orderId));
    }
}
