package bg.hristomanov.education.patterns.behavioral;

import bg.hristomanov.education.patterns.behavioral.chain.CustomerPresentValidation;
import bg.hristomanov.education.patterns.behavioral.chain.OrderDraft;
import bg.hristomanov.education.patterns.behavioral.chain.OrderValidationHandler;
import bg.hristomanov.education.patterns.behavioral.chain.PositiveTotalValidation;
import bg.hristomanov.education.patterns.behavioral.chain.SupportedCountryValidation;
import bg.hristomanov.education.patterns.behavioral.chain.ValidationResult;
import bg.hristomanov.education.patterns.behavioral.command.CommandBus;
import bg.hristomanov.education.patterns.behavioral.command.CreateInvoiceCommand;
import bg.hristomanov.education.patterns.behavioral.command.InvoiceService;
import bg.hristomanov.education.patterns.behavioral.observer.AuditOrderListener;
import bg.hristomanov.education.patterns.behavioral.observer.EmailOrderListener;
import bg.hristomanov.education.patterns.behavioral.observer.OrderEventPublisher;
import bg.hristomanov.education.patterns.behavioral.observer.OrderPlacedEvent;
import bg.hristomanov.education.patterns.behavioral.state.Order;
import bg.hristomanov.education.patterns.behavioral.strategy.CustomerSegment;
import bg.hristomanov.education.patterns.behavioral.strategy.DiscountCalculator;
import bg.hristomanov.education.patterns.behavioral.strategy.RegularDiscountStrategy;
import bg.hristomanov.education.patterns.behavioral.strategy.VipDiscountStrategy;
import bg.hristomanov.education.patterns.behavioral.templatemethod.AbstractOrderImportJob;
import bg.hristomanov.education.patterns.behavioral.templatemethod.CsvOrderImportJob;
import bg.hristomanov.education.patterns.behavioral.templatemethod.ImportResult;
import bg.hristomanov.education.patterns.behavioral.templatemethod.JsonOrderImportJob;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BehavioralPatternsTest {

    @Test
    void strategySelectsTheAlgorithmWithoutGrowingAConditionalBlock() {
        DiscountCalculator calculator = new DiscountCalculator(
                List.of(new RegularDiscountStrategy(), new VipDiscountStrategy())
        );

        BigDecimal regular = calculator.finalPrice(
                CustomerSegment.REGULAR,
                new BigDecimal("100.00")
        );
        BigDecimal vip = calculator.finalPrice(
                CustomerSegment.VIP,
                new BigDecimal("100.00")
        );

        assertThat(regular).isEqualByComparingTo("100.00");
        assertThat(vip).isEqualByComparingTo("90.0000");
    }

    @Test
    void observerLetsMultipleIndependentListenersReactToOneEvent() {
        List<String> sentEmails = new ArrayList<>();
        List<String> auditLog = new ArrayList<>();

        OrderEventPublisher publisher = new OrderEventPublisher(
                List.of(
                        new EmailOrderListener(sentEmails),
                        new AuditOrderListener(auditLog)
                )
        );

        publisher.publish(new OrderPlacedEvent(
                "O-42",
                "customer@example.com",
                new BigDecimal("120.00")
        ));

        assertThat(sentEmails).containsExactly("customer@example.com:order=O-42");
        assertThat(auditLog).containsExactly("ORDER_PLACED:O-42:120.00");
    }

    @Test
    void chainStopsAtTheFirstHandlerThatRejectsTheRequest() {
        OrderValidationHandler root = new CustomerPresentValidation();
        root.then(new PositiveTotalValidation())
                .then(new SupportedCountryValidation(Set.of("BG", "DE")));

        ValidationResult valid = root.validate(
                new OrderDraft("C-1", "BG", new BigDecimal("50.00"))
        );
        ValidationResult invalid = root.validate(
                new OrderDraft("C-1", "BG", BigDecimal.ZERO)
        );

        assertThat(valid.valid()).isTrue();
        assertThat(invalid.valid()).isFalse();
        assertThat(invalid.error()).isEqualTo("total must be positive");
    }

    @Test
    void stateMovesLifecycleRulesOutOfOneLargeSwitch() {
        Order order = new Order();

        assertThat(order.stateName()).isEqualTo("PENDING");

        order.pay();
        assertThat(order.stateName()).isEqualTo("PAID");

        order.ship();
        assertThat(order.stateName()).isEqualTo("SHIPPED");

        assertThatThrownBy(order::cancel)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be cancelled");
    }

    @Test
    void templateMethodKeepsTheWorkflowStableWhileSubclassesVarySteps() {
        AbstractOrderImportJob csvJob = new CsvOrderImportJob();
        AbstractOrderImportJob jsonJob = new JsonOrderImportJob();

        ImportResult csv = csvJob.execute("O-1, O-2");
        ImportResult json = jsonJob.execute("[\"O-3\", \"O-4\"]");

        assertThat(csv.importedCount()).isEqualTo(2);
        assertThat(csv.trace()).containsExactly(
                "load",
                "parse:csv",
                "validate",
                "persist"
        );

        assertThat(json.importedCount()).isEqualTo(2);
        assertThat(json.trace()).containsExactly(
                "load",
                "parse:json",
                "validate",
                "persist",
                "json-audit"
        );
    }

    @Test
    void commandTurnsAnActionIntoAnObjectThatAnInvokerCanExecuteAndTrack() {
        InvoiceService receiver = new InvoiceService();
        CreateInvoiceCommand command = new CreateInvoiceCommand(
                receiver,
                "O-42",
                new BigDecimal("120.00")
        );
        CommandBus commandBus = new CommandBus();

        String invoiceId = commandBus.execute(command);

        assertThat(invoiceId).isEqualTo("INV-O-42-120.00");
        assertThat(commandBus.executionHistory()).containsExactly("create-invoice:O-42");
    }
}
