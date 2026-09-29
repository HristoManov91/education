package bg.hristomanov.education.patterns.structural;

import bg.hristomanov.education.patterns.structural.adapter.LegacyCourierAdapter;
import bg.hristomanov.education.patterns.structural.adapter.LegacyCourierClient;
import bg.hristomanov.education.patterns.structural.adapter.ShippingQuote;
import bg.hristomanov.education.patterns.structural.composite.Bundle;
import bg.hristomanov.education.patterns.structural.composite.PriceComponent;
import bg.hristomanov.education.patterns.structural.composite.ProductLine;
import bg.hristomanov.education.patterns.structural.decorator.AuditNotificationDecorator;
import bg.hristomanov.education.patterns.structural.decorator.EmailNotificationSender;
import bg.hristomanov.education.patterns.structural.decorator.MetricsNotificationDecorator;
import bg.hristomanov.education.patterns.structural.decorator.Notification;
import bg.hristomanov.education.patterns.structural.decorator.NotificationSender;
import bg.hristomanov.education.patterns.structural.facade.CheckoutFacade;
import bg.hristomanov.education.patterns.structural.facade.CheckoutRequest;
import bg.hristomanov.education.patterns.structural.facade.CheckoutResult;
import bg.hristomanov.education.patterns.structural.facade.InventoryService;
import bg.hristomanov.education.patterns.structural.facade.PaymentService;
import bg.hristomanov.education.patterns.structural.facade.ShippingService;
import bg.hristomanov.education.patterns.structural.proxy.CachingProductCatalogProxy;
import bg.hristomanov.education.patterns.structural.proxy.ProductCatalog;
import bg.hristomanov.education.patterns.structural.proxy.RemoteProductCatalog;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StructuralPatternsTest {

    @Test
    void adapterTranslatesOurDomainContractToLegacyApi() {
        LegacyCourierAdapter adapter = new LegacyCourierAdapter(new LegacyCourierClient());

        ShippingQuote quote = adapter.quote("1000", new BigDecimal("2.50"));

        assertThat(quote.provider()).isEqualTo("legacy-courier");
        assertThat(quote.priceEur()).isEqualByComparingTo("5.25");
    }

    @Test
    void facadeHidesSubsystemOrchestrationBehindOneUseCaseMethod() {
        CheckoutFacade facade = new CheckoutFacade(
                new InventoryService(),
                new PaymentService(),
                new ShippingService()
        );

        CheckoutResult result = facade.checkout(
                new CheckoutRequest("C-7", "SKU-1", 2, new BigDecimal("399.80"))
        );

        assertThat(result.reservationId()).isEqualTo("RES-SKU-1-2");
        assertThat(result.paymentId()).startsWith("PAY-C-7-");
        assertThat(result.shipmentId()).isEqualTo("SHIP-RES-SKU-1-2");
    }

    @Test
    void decoratorsCanBeStackedWithoutChangingTheNotificationContract() {
        List<String> outbox = new ArrayList<>();
        List<String> auditLog = new ArrayList<>();
        AtomicInteger sentCounter = new AtomicInteger();

        NotificationSender sender = new MetricsNotificationDecorator(
                new AuditNotificationDecorator(
                        new EmailNotificationSender(outbox),
                        auditLog
                ),
                sentCounter
        );

        sender.send(new Notification("user@example.com", "Order shipped"));

        assertThat(outbox).containsExactly("user@example.com:Order shipped");
        assertThat(auditLog).containsExactly(
                "before-send:user@example.com",
                "after-send:user@example.com"
        );
        assertThat(sentCounter.get()).isEqualTo(1);
    }

    @Test
    void proxyCanAddCachingWithoutChangingTheConsumerInterface() {
        RemoteProductCatalog remote = new RemoteProductCatalog();
        ProductCatalog catalog = new CachingProductCatalogProxy(remote);

        BigDecimal first = catalog.priceFor("SKU-1");
        BigDecimal second = catalog.priceFor("SKU-1");

        assertThat(first).isEqualByComparingTo(second);
        assertThat(remote.callCount()).isEqualTo(1);
    }

    @Test
    void compositeTreatsLeafAndNestedBundleThroughTheSameInterface() {
        PriceComponent keyboard = new ProductLine("KEYBOARD", new BigDecimal("100.00"), 1);
        PriceComponent mouse = new ProductLine("MOUSE", new BigDecimal("40.00"), 2);
        PriceComponent deskSet = new Bundle("Desk set", List.of(keyboard, mouse));
        PriceComponent officeBundle = new Bundle(
                "Office",
                List.of(deskSet, new ProductLine("CABLE", new BigDecimal("10.00"), 1))
        );

        assertThat(officeBundle.total()).isEqualByComparingTo("190.00");
    }
}
