package bg.hristomanov.education.patterns.creational;

import bg.hristomanov.education.patterns.creational.abstractfactory.BulgarianCommerceFactory;
import bg.hristomanov.education.patterns.creational.abstractfactory.CommerceFactory;
import bg.hristomanov.education.patterns.creational.abstractfactory.UsCommerceFactory;
import bg.hristomanov.education.patterns.creational.builder.ReportRequest;
import bg.hristomanov.education.patterns.creational.factorymethod.BankTransferPaymentProcessorCreator;
import bg.hristomanov.education.patterns.creational.factorymethod.CardPaymentProcessorCreator;
import bg.hristomanov.education.patterns.creational.factorymethod.PaymentProcessorCreator;
import bg.hristomanov.education.patterns.creational.factorymethod.PaymentReceipt;
import bg.hristomanov.education.patterns.creational.factorymethod.PaymentRequest;
import bg.hristomanov.education.patterns.creational.singleton.CountryCodeRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreationalPatternsTest {

    @Test
    void singletonAlwaysExposesTheSameEnumInstance() {
        CountryCodeRegistry first = CountryCodeRegistry.INSTANCE;
        CountryCodeRegistry second = CountryCodeRegistry.INSTANCE;

        assertThat(first).isSameAs(second);
        assertThat(first.isSupported("BG")).isTrue();
    }

    @Test
    void builderMakesOptionalConfigurationExplicitAndValidatesAtBuildBoundary() {
        ReportRequest request = ReportRequest
                .between(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
                .includeDetails()
                .format("PDF")
                .addFilter("PAID")
                .build();

        assertThat(request.includeDetails()).isTrue();
        assertThat(request.includeArchived()).isFalse();
        assertThat(request.format()).isEqualTo("PDF");
        assertThat(request.filters()).containsExactly("PAID");

        assertThatThrownBy(() -> ReportRequest
                .between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 1))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void factoryMethodLetsSubclassChooseTheConcreteProcessor() {
        PaymentRequest request = new PaymentRequest("INV-42", new BigDecimal("100.00"));

        PaymentProcessorCreator cardCreator = new CardPaymentProcessorCreator();
        PaymentProcessorCreator bankCreator = new BankTransferPaymentProcessorCreator();

        PaymentReceipt cardReceipt = cardCreator.process(request);
        PaymentReceipt bankReceipt = bankCreator.process(request);

        assertThat(cardReceipt.provider()).isEqualTo("card");
        assertThat(bankReceipt.provider()).isEqualTo("bank-transfer");
    }

    @Test
    void abstractFactoryCreatesCompatibleRegionalFamilies() {
        CommerceFactory bgFactory = new BulgarianCommerceFactory();
        CommerceFactory usFactory = new UsCommerceFactory();

        assertThat(bgFactory.createTaxCalculator().taxFor(new BigDecimal("100.00")))
                .isEqualByComparingTo("20.0000");
        assertThat(bgFactory.createPaymentGateway().charge(new BigDecimal("120.00")))
                .startsWith("BG-EUR:");

        assertThat(usFactory.createTaxCalculator().taxFor(new BigDecimal("100.00")))
                .isEqualByComparingTo("7.0000");
        assertThat(usFactory.createPaymentGateway().charge(new BigDecimal("107.00")))
                .startsWith("US-USD:");
    }
}
