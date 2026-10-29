package bg.hristomanov.education.integrationpatterns.event;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract proof for a real serialized Java record and intentionally malformed
 * JSON. The schema is read from the SAME shared AsyncAPI document used by the
 * producer and consumer docs. This does NOT replace the official AsyncAPI CLI
 * validation of the full documents or a real Kafka integration test.
 */
class OrderCreatedContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final String CONTRACT = "/asyncapi/order-events.yaml";

    @Test
    void producerAndConsumerReferenceTheSameSharedChannelAndOperationDirections() throws Exception {
        JsonNode shared = yaml(CONTRACT);
        JsonNode producer = yaml("/asyncapi/order-events-send.yaml");
        JsonNode consumer = yaml("/asyncapi/order-events-receive.yaml");

        for (JsonNode document : List.of(shared, producer, consumer)) {
            assertThat(document.path("asyncapi").asText()).isEqualTo("3.1.0");
        }

        String channelRef = "./order-events.yaml#/channels/orderCreated";
        assertThat(producer.at("/channels/orderCreated/$ref").asText()).isEqualTo(channelRef);
        assertThat(consumer.at("/channels/orderCreated/$ref").asText()).isEqualTo(channelRef);
        assertThat(shared.at("/channels/orderCreated/address").asText()).isEqualTo("orders.created");

        assertThat(producer.at("/operations/sendOrderCreated/action").asText()).isEqualTo("send");
        assertThat(consumer.at("/operations/receiveOrderCreated/action").asText()).isEqualTo("receive");
        assertThat(producer.at("/operations/sendOrderCreated/channel/$ref").asText())
                .isEqualTo("#/channels/orderCreated");
        assertThat(consumer.at("/operations/receiveOrderCreated/channel/$ref").asText())
                .isEqualTo("#/channels/orderCreated");

        assertThat(shared.at("/channels/orderCreated/messages/OrderCreated/$ref").asText())
                .isEqualTo("#/components/messages/OrderCreated");
        assertThat(shared.at("/components/messages/OrderCreated/contentType").asText())
                .isEqualTo("application/json");
        assertThat(shared.at("/components/messages/OrderCreated/payload/$ref").asText())
                .isEqualTo("#/components/schemas/OrderCreated");

        // The CLI resolves external refs and validates the whole AsyncAPI document.
        // This test checks explicit integration choices and linked contract ownership.
    }

    @Test
    void serializedJavaProducerMessageMatchesThePublishedSchema() throws Exception {
        OrderCreatedEvent event = exampleEvent();
        String json = JSON.writeValueAsString(event);

        assertThat(schema().validate(json, InputFormat.JSON)).isEmpty();
        assertThat(JSON.readTree(json).path("source").asText()).isEqualTo("CUSTOMER");
        assertThat(JSON.readTree(json).path("items").get(0).path("quantity").asInt())
                .isEqualTo(2);
    }

    @Test
    void publishedExampleAndJsonFixtureBothSatisfyTheSharedSchema() throws Exception {
        String fixture = text("/asyncapi/order-created-valid.json");
        JsonNode declaredExample = yaml(CONTRACT)
                .at("/components/messages/OrderCreated/examples/0/payload");

        assertThat(JSON.readTree(fixture)).isEqualTo(declaredExample);
        assertThat(schema().validate(fixture, InputFormat.JSON)).isEmpty();
    }

    @Test
    void missingRequiredFieldIsRejected() throws Exception {
        assertThat(schema().validate(
                text("/asyncapi/order-created-missing-source.json"), InputFormat.JSON))
                .isNotEmpty();
    }

    @Test
    void invalidBusinessQuantityIsRejected() throws Exception {
        assertThat(schema().validate(
                text("/asyncapi/order-created-invalid-quantity.json"), InputFormat.JSON))
                .isNotEmpty();
    }

    @Test
    void unknownEnumValueIsRejected() throws Exception {
        assertThat(schema().validate(
                text("/asyncapi/order-created-unknown-source.json"), InputFormat.JSON))
                .isNotEmpty();
    }

    @Test
    void undeclaredFieldsAreRejectedWithStrictCurrentSchema() throws Exception {
        ObjectNode unexpected = (ObjectNode) JSON.readTree(
                text("/asyncapi/order-created-valid.json"));
        unexpected.put("customerSegment", "VIP");

        assertThat(schema().validate(JSON.writeValueAsString(unexpected), InputFormat.JSON))
                .isNotEmpty();
    }

    @Test
    void addingRequiredFieldToPublishedSchemaWouldBreakExistingProducers() throws Exception {
        ObjectNode changed = (ObjectNode) payloadSchema().deepCopy();
        ((ArrayNode) changed.path("required")).add("customerSegment");
        ((ObjectNode) changed.path("properties")).putObject("customerSegment")
                .put("type", "string");

        String existingEvent = JSON.writeValueAsString(exampleEvent());

        assertThat(schema().validate(existingEvent, InputFormat.JSON)).isEmpty();
        assertThat(schemaFor(changed).validate(existingEvent, InputFormat.JSON))
                .isNotEmpty();
    }

    @Test
    void addingOptionalFieldAcceptsOldProducerButOldStrictConsumerRejectsNewField()
            throws Exception {
        ObjectNode expanded = (ObjectNode) payloadSchema().deepCopy();
        ((ObjectNode) expanded.path("properties")).putObject("customerSegment")
                .put("type", "string");
        Schema expandedSchema = schemaFor(expanded);

        String oldProducerEvent = JSON.writeValueAsString(exampleEvent());
        assertThat(expandedSchema.validate(oldProducerEvent, InputFormat.JSON)).isEmpty();

        ObjectNode newProducerEvent = (ObjectNode) JSON.readTree(oldProducerEvent);
        newProducerEvent.put("customerSegment", "VIP");
        String newJson = JSON.writeValueAsString(newProducerEvent);
        assertThat(expandedSchema.validate(newJson, InputFormat.JSON)).isEmpty();
        assertThat(schema().validate(newJson, InputFormat.JSON)).isNotEmpty();

        // "Optional" is backward-compatible for old payload -> new schema,
        // but strict additionalProperties:false consumers reject new payloads.
        // Compatibility MUST be assessed in both directions.
    }

    private static OrderCreatedEvent exampleEvent() {
        return new OrderCreatedEvent(
                "f1451166-a896-4f02-a741-69a51b19b277",
                OrderCreatedEvent.Source.CUSTOMER,
                List.of(new OrderCreatedEvent.Item("SKU-123", 2)));
    }

    private static JsonNode payloadSchema() throws Exception {
        JsonNode shared = yaml(CONTRACT);
        String reference = shared.at("/components/messages/OrderCreated/payload/$ref").asText();
        assertThat(reference).startsWith("#/components/schemas/");
        JsonNode schema = shared.at(reference.substring(1));
        assertThat(schema.isMissingNode()).isFalse();
        return schema;
    }

    private static Schema schema() throws Exception {
        return schemaFor(payloadSchema());
    }

    private static Schema schemaFor(JsonNode schemaNode) {
        return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
                .getSchema(SchemaLocation.of("https://education.example/schemas/order-created"),
                        schemaNode);
    }

    private static JsonNode yaml(String resource) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                OrderCreatedContractTest.class.getResourceAsStream(resource),
                "Missing resource " + resource)) {
            return YAML.readTree(in);
        }
    }

    private static String text(String resource) throws IOException {
        try (InputStream in = Objects.requireNonNull(
                OrderCreatedContractTest.class.getResourceAsStream(resource),
                "Missing resource " + resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
