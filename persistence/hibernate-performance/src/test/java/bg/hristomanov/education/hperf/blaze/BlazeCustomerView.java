package bg.hristomanov.education.hperf.blaze;

import bg.hristomanov.education.hperf.domain.PerfCustomer;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;

import java.util.List;

/**
 * A read model for a customer page. The nested orders are projected,
 * not returned as managed JPA entities.
 */
@EntityView(PerfCustomer.class)
public interface BlazeCustomerView {
    @IdMapping
    Long getId();

    String getName();

    List<BlazeOrderView> getOrders();
}
