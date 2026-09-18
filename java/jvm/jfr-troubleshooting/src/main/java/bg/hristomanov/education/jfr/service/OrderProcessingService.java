package bg.hristomanov.education.jfr.service;

import bg.hristomanov.education.jfr.domain.LabResults.CpuResult;
import bg.hristomanov.education.jfr.domain.LabResults.OrderResult;
import bg.hristomanov.education.jfr.jfr.OrderProcessingEvent;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Production-like business boundary, върху който демонстрираме custom JFR event.
 *
 * <p>Вместо да добавяме десетки INFO логове за всяка стъпка, записваме едно структурирано
 * събитие с duration, business identifier и result. JFR после може да го корелира
 * с JVM събитията около същия момент.</p>
 */
@Service
public class OrderProcessingService {

    private static final int MIN_ITEMS = 1;
    private static final int MAX_ITEMS = 50;

    private final CpuHotspotService cpuHotspotService;

    public OrderProcessingService(CpuHotspotService cpuHotspotService) {
        this.cpuHotspotService = cpuHotspotService;
    }

    public OrderResult process(int requestedItemCount) {
        int itemCount = Math.max(MIN_ITEMS, Math.min(requestedItemCount, MAX_ITEMS));
        String orderId = UUID.randomUUID().toString();

        OrderProcessingEvent event = new OrderProcessingEvent();
        event.orderId = orderId;
        event.itemCount = itemCount;

        long startedAt = System.nanoTime();
        double checksum = 0.0d;

        event.begin();

        try {
            /*
             * CPU работата е нарочно вътре в business operation-а, за да можем в JFR
             * да видим как application event-ът се припокрива с execution samples.
             */
            CpuResult cpuResult = cpuHotspotService.burnCpu(itemCount * 300_000);
            checksum = cpuResult.checksum();

            simulateDatabaseWait();

            event.result = "SUCCESS";

            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            return new OrderResult(orderId, itemCount, durationMillis, checksum);
        } catch (RuntimeException e) {
            event.result = "FAILURE";
            throw e;
        } finally {
            event.end();

            /*
             * shouldCommit() позволява да избегнем допълнителна скъпа подготовка на payload,
             * когато event-ът е disabled или не покрива threshold-а. Тук payload-ът е евтин,
             * но pattern-ът е важен за production custom events.
             */
            if (event.shouldCommit()) {
                event.commit();
            }
        }
    }

    private void simulateDatabaseWait() {
        try {
            Thread.sleep(60L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Order processing was interrupted", e);
        }
    }
}
