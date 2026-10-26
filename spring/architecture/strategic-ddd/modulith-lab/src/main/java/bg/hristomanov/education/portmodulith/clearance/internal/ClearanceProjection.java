package bg.hristomanov.education.portmodulith.clearance.internal;

import bg.hristomanov.education.portmodulith.clearance.ClearanceStatus;
import bg.hristomanov.education.portmodulith.clearance.OperationalClearanceQueries;
import bg.hristomanov.education.portmodulith.dangerousgoods.events.DangerousGoodsAssessed;
import bg.hristomanov.education.portmodulith.health.events.HealthAssessed;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Owned projection of two cross-module facts, kept separate from either
 * producer's internal service/repository.
 *
 * This in-memory teaching projection has no durable offset, replay, versions,
 * transactional publication or distributed-delivery guarantees.
 */
@Component
public class ClearanceProjection implements OperationalClearanceQueries {

    private record Evidence(Boolean healthAccepted, Boolean goodsAccepted) {
        ClearanceStatus status() {
            if (Boolean.FALSE.equals(healthAccepted) || Boolean.FALSE.equals(goodsAccepted)) {
                return ClearanceStatus.DENIED;
            }
            return Boolean.TRUE.equals(healthAccepted) && Boolean.TRUE.equals(goodsAccepted)
                    ? ClearanceStatus.CLEARED : ClearanceStatus.PENDING;
        }
    }

    private final ConcurrentMap<String, Evidence> state = new ConcurrentHashMap<>();

    @EventListener
    public void onHealth(HealthAssessed event) {
        state.compute(event.vesselId(), (id, existing) -> new Evidence(
                event.accepted(),
                existing == null ? null : existing.goodsAccepted()));
    }

    @EventListener
    public void onDangerousGoods(DangerousGoodsAssessed event) {
        state.compute(event.vesselId(), (id, existing) -> new Evidence(
                existing == null ? null : existing.healthAccepted(),
                event.accepted()));
    }

    @Override
    public ClearanceStatus statusOf(String vesselId) {
        if (vesselId == null || vesselId.isBlank()) {
            throw new IllegalArgumentException("vesselId must be present");
        }
        Evidence evidence = state.get(vesselId);
        return evidence == null ? ClearanceStatus.PENDING : evidence.status();
    }
}
