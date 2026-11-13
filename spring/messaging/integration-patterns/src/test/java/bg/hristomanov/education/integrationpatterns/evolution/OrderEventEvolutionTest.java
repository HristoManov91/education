package bg.hristomanov.education.integrationpatterns.evolution;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static bg.hristomanov.education.integrationpatterns.evolution.OrderEventEvolution.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Four compatibility combinations with real JSON wire roundtrip, plus
 * deployment-flag, fan-out capability and semantic-downgrade regression tests.
 */
class OrderEventEvolutionTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Order EUR = new Order("ORD-10", 1250L, Currency.EUR);
    private static final Order USD = new Order("ORD-11", 1750L, Currency.USD);

    @SuppressWarnings("unchecked")
    private static Map<String, Object> transmitted(Map<String, Object> fields) throws Exception {
        String json = JSON.writeValueAsString(fields);
        assertThat(JSON.readTree(json).isObject()).isTrue();
        return JSON.readValue(json, Map.class);
    }

    @Test
    void oldProducerToOldConsumerWorksWithRealJson() throws Exception {
        assertThat(legacyConsumer(transmitted(legacyProducer(EUR)))).isEqualTo(EUR);
    }

    @Test
    void oldProducerToNewConsumerWorksDuringExpandReadersPhase() throws Exception {
        assertThat(upgradedConsumer(transmitted(legacyProducer(EUR)))).isEqualTo(EUR);
    }

    @Test
    void newProducerToOldStrictConsumerIsAnIncompatibleCombination() throws Exception {
        Map<String, Object> newWire = transmitted(v2Producer(EUR));
        assertThatThrownBy(() -> legacyConsumer(newWire))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unexpected fields/version");
    }

    @Test
    void newProducerToNewConsumerWorksWithRealJson() throws Exception {
        assertThat(upgradedConsumer(transmitted(v2Producer(USD)))).isEqualTo(USD);
    }

    @Test
    void mixedConsumersForceV1UntilTheLastOldConsumerIsMigrated() throws Exception {
        var recipients = commonFormats(List.of(
                ReceiverCapabilities.v1Only(), ReceiverCapabilities.v1AndV2()));
        Map<String, Object> before = transmitted(stagedProducer(EUR, recipients, true));
        assertThat(before.get("schemaVersion")).isEqualTo(1);
        assertThat(legacyConsumer(before)).isEqualTo(EUR);
        assertThat(upgradedConsumer(before)).isEqualTo(EUR);

        var upgraded = commonFormats(List.of(
                ReceiverCapabilities.v1AndV2(), ReceiverCapabilities.v1AndV2()));
        Map<String, Object> after = transmitted(stagedProducer(EUR, upgraded, true));
        assertThat(after.get("schemaVersion")).isEqualTo(2);
        assertThat(upgradedConsumer(after)).isEqualTo(EUR);
    }

    @Test
    void featureFlagOffPreservesV1EvenIfAllConsumersSupportV2() throws Exception {
        var wire = transmitted(stagedProducer(EUR, ReceiverCapabilities.v1AndV2(), false));
        assertThat(wire.get("schemaVersion")).isEqualTo(1);
        assertThat(legacyConsumer(wire)).isEqualTo(EUR);
    }

    @Test
    void disablingV2AfterPublishingUsdMustNotSilentlyChangeCurrency() {
        assertThatThrownBy(() -> stagedProducer(USD, ReceiverCapabilities.v1AndV2(), false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot downgrade");
        assertThatThrownBy(() -> stagedProducer(USD, ReceiverCapabilities.v1Only(), true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot downgrade");
    }

    @Test
    void producerFailsClosedWhenThereIsNoSharedFormat() {
        var onlyV2 = new ReceiverCapabilities(Set.of(Format.V2));
        assertThatThrownBy(() -> commonFormats(List.of(ReceiverCapabilities.v1Only(), onlyV2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No common");
        assertThatThrownBy(() -> stagedProducer(EUR, onlyV2, false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No enabled");
    }

    @Test
    void unknownFieldsAndVersionsDoNotSilentlyPass() {
        assertThatThrownBy(() -> upgradedConsumer(Map.of(
                "schemaVersion", 3, "orderId", "ORD-1", "amountCents", 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> upgradedConsumer(Map.of(
                "schemaVersion", 1, "orderId", "ORD-1", "amountCents", 10,
                "currency", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> upgradedConsumer(Map.of(
                "schemaVersion", 2, "orderId", "ORD-1", "amountCents", 10,
                "currency", "GBP")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currency");
    }

    @Test
    void fractionalAmountsAndInvalidOrdersMustNotBeTruncatedOrAccepted() {
        assertThatThrownBy(() -> upgradedConsumer(Map.of(
                "schemaVersion", 2, "orderId", "ORD-1", "amountCents", 2.5,
                "currency", "EUR")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Integral");
        assertThatThrownBy(() -> new Order(" ", 10, Currency.EUR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Order("ORD-1", 0, Currency.EUR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void capabilitiesAreDefensivelyCopied() {
        Set<Format> mutable = java.util.EnumSet.of(Format.V1);
        var advertised = new ReceiverCapabilities(mutable);
        mutable.add(Format.V2);
        assertThat(advertised.supported()).containsExactly(Format.V1);
        assertThatThrownBy(() -> advertised.supported().add(Format.V2))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingRecipientsCannotBeAssumedCompatible() {
        assertThatThrownBy(() -> commonFormats(List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("known recipients");
        assertThatThrownBy(() -> new ReceiverCapabilities(Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
