package bg.hristomanov.education.uow.service;

import bg.hristomanov.education.uow.domain.OrderRepository;
import bg.hristomanov.education.uow.domain.PurchaseOrder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Business transaction boundary.
 *
 * <p>Забележи markPaid(): няма repository.save(order). Entity-то е managed
 * от transaction-scoped persistence context. Hibernate dirty checking ще
 * открие промяната и ще я синхронизира при flush/commit.</p>
 */
@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;

    public OrderApplicationService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public long createOrder(String reference, BigDecimal totalAmount) {
        PurchaseOrder order = new PurchaseOrder(reference, totalAmount);
        orderRepository.add(order);
        return order.getId();
    }

    @Transactional
    public void markPaid(long orderId) {
        PurchaseOrder order = orderRepository.getRequired(orderId);
        order.markPaid();

        /*
         * Нарочно няма save().
         *
         * order е managed entity. Persistence context-ът следи state-а му
         * и Unit-of-Work semantics ще flush-нат UPDATE при transaction end.
         */
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getOrder(long orderId) {
        return orderRepository.getRequired(orderId);
    }
}
