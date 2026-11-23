package bg.hristomanov.education.hperf.blaze;

import bg.hristomanov.education.hperf.domain.PerfOrder;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.IdMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read-only projection for an order inside a paged customer view.
 * It intentionally exposes only the fields required by the screen.
 */
@EntityView(PerfOrder.class)
public interface BlazeOrderView {
    @IdMapping
    Long getId();

    BigDecimal getTotal();

    LocalDateTime getCreatedAt();
}
