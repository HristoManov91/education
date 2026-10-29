# 03 — AsyncAPI Contract-First: Order Service → Kafka → Stock Service

> **P2 · Devoxx Belgium 2026 · Java 25 · Spring Boot 4.1.1 BOM · AsyncAPI 3.1.0**
>
> Source: [Michel Schudel — AsyncAPI in action: OpenAPI for event driven applications](https://www.youtube.com/watch?v=A_fp7RaG5UY). This learning chapter follows the user-provided speech-to-text transcript and the [verified original demo](https://github.com/MichelSchudel/asyncapi-demo); our implementation is **deliberately smaller**. The speaker's demo uses **AsyncAPI 3.0.0** in its YAML files; we use the published **3.1.0** specification. The lesson does not claim that the original demo used the newer version.

## 1. Real production-like problem — the JSON in Confluence is not a contract

We have:

```text
HTTP POST /orders
       |
       v
Order Service (owns the order)
       |
       | publishes OrderCreated JSON
       v
Kafka topic orders.created
       |
       v
Stock Service (reacts to the published fact)
```

Producer and consumer are independently deployed. Stock assumes an event contains an `id`, a `source` and one or more `items`, each with a `productId` and a positive `quantity`.

A new Order Service version starts sending `quantity: 0` or renames `id` to `orderId`. The consumer still compiles; messages may now fail at runtime. A copied JSON example in Confluence does not enforce anything, and asking the two teams to "remember" the contract is unreliable.

**Goal:** describe the message interface as a machine-readable, versioned source of truth, use it for documentation and prove that the producer's serialized JSON satisfies its constraints. Do **not** confuse a valid AsyncAPI document with a verified running integration or a guaranteed compatible schema change.

## 2. OpenAPI vs AsyncAPI — two complementary contracts

| | OpenAPI | AsyncAPI |
| --- | --- | --- |
| Typical API | `POST /orders` | `orders.created` event channel |
| Main concern | HTTP operations, request/response | Message channels, messages and application operations |
| Direction | HTTP method and request/response | `send` / `receive` relative to the described **application** |
| Underlying infrastructure | HTTP interface | Kafka, AMQP, MQTT, WebSocket and others |
| Tooling | Docs, API/model generation, validation | Docs, schema/model/integration generation through templates, validation |

**Mental model:** *AsyncAPI is a description of your message-driven API, not a message broker, serializer, schema registry, delivery guarantee or runtime adapter.*

A `channel` tells us **where** messages move. A `message` tells us **what** data is exchanged. An `operation` tells us whether the **specific application** sends or receives. Kafka bindings add protocol-specific metadata. The same channel can be used by a sender and receiver with different application-level operations.

## 3. Naive design → the hidden source of drift

**Bad:**

```text
Order Service: Java record { id, source, items }
Stock Service: independently written record { id, source, items }
Confluence: one sample JSON, updated irregularly
```

This works until one side changes a field. Compile-time type safety inside each JVM does **not** protect the inter-service JSON contract, and the consumer's copy is not automatically updated by producer deployments.

**Good — one published shared definition:**

```text
order-events.yaml (canonical channel + message schema)
      |                                      |
      v                                      v
order-events-send.yaml              order-events-receive.yaml
     action: send                           action: receive
     Order Service                          Stock Service
```

Real files, in [src/main/resources/asyncapi](src/main/resources/asyncapi):

- [order-events.yaml](src/main/resources/asyncapi/order-events.yaml) — producer-owned published `OrderCreated` definition, channel `orders.created`, payload constraints, example.
- [order-events-send.yaml](src/main/resources/asyncapi/order-events-send.yaml) — Order Service view and Kafka `clientId` binding; references the shared channel.
- [order-events-receive.yaml](src/main/resources/asyncapi/order-events-receive.yaml) — Stock Service view and Kafka `groupId` binding; references the **same** channel.

Our three files live together for a small learning module. In a production multi-repository system, each application would consume a **versioned released contract** or a synced copy under explicit ownership, not magically follow a mutable local file. The source-of-truth strategy and compatibility approvals are an organizational decision.

### The central YAML — read it as an engineer

Simplified excerpt of the [actual shared file](src/main/resources/asyncapi/order-events.yaml):

```yaml
asyncapi: 3.1.0
info:
  title: OrderCreated shared event contract
  version: 1.0.0
channels:
  orderCreated:
    address: orders.created
    messages:
      OrderCreated:
        $ref: '#/components/messages/OrderCreated'
components:
  messages:
    OrderCreated:
      contentType: application/json
      payload:
        $ref: '#/components/schemas/OrderCreated'
  schemas:
    OrderCreated:
      type: object
      additionalProperties: false
      required: [id, source, items]
      properties:
        id:
          type: string
          format: uuid
        source:
          type: string
          enum: [CUSTOMER, WAREHOUSE]
        items:
          type: array
          minItems: 1
          items:
            type: object
            additionalProperties: false
            required: [productId, quantity]
            properties:
              productId: { type: string, minLength: 1 }
              quantity: { type: integer, minimum: 1, maximum: 10 }
```

**Two version numbers, two purposes:** `asyncapi: 3.1.0` identifies the **specification format**. `info.version: 1.0.0` versions **our OrderCreated API contract**. Incrementing one does not automatically mean incrementing the other. The schema's `required`, `enum`, `minimum`, `maxItems` etc. are contractual statements, not Java validation annotations.

**What is NOT specified:** exactly-once delivery, retry policy, broker retention, partition count, backpressure, transaction boundaries, consumer idempotency, business payment invariants or who compensates an unsuccessful stock update. These remain architecture and runtime concerns covered elsewhere in `education`.

### Send vs Receive is relative to the service

Producer:

```yaml
channels:
  orderCreated:
    $ref: './order-events.yaml#/channels/orderCreated'
operations:
  sendOrderCreated:
    action: send
    channel:
      $ref: '#/channels/orderCreated'
```

Consumer:

```yaml
channels:
  orderCreated:
    $ref: './order-events.yaml#/channels/orderCreated'
operations:
  receiveOrderCreated:
    action: receive
    channel:
      $ref: '#/channels/orderCreated'
```

The *channel* is shared, but the *application operation* is not. This aligns with the AsyncAPI 3.x model: the specification does not assume that one service's full AsyncAPI file can simply be interpreted as another service's file.

## 4. Three different checks — don't claim one proves the others

| Proof | What it checks | Executable source / tool |
| --- | --- | --- |
| **AsyncAPI syntax and references** | Is the document a valid AsyncAPI 3.1 contract? Are external `$ref` entries resolvable? | Official [AsyncAPI CLI](package.json) `npm run validate`, checked in a dedicated GitHub Actions job |
| **JSON payload validity** | Does a particular `OrderCreated` JSON satisfy `required`, enum, numeric range, unknown-field policy? | Java [OrderCreatedContractTest](src/test/java/bg/hristomanov/education/integrationpatterns/event/OrderCreatedContractTest.java), using the schema read **directly from the shared YAML** |
| **Compatible evolution** | Can old/new producer and consumer versions communicate under a proposed field change? | Negative evolution tests + independent compatibility review; schema validity **alone** is insufficient |

The Java test reads the single source-of-truth [order-events.yaml](src/main/resources/asyncapi/order-events.yaml), resolves its payload `$ref` locally and compiles the referenced schema with **JSON Schema Draft 7** validation using [networknt json-schema-validator 3.x](https://github.com/networknt/json-schema-validator) (Jackson 3-compatible). This is possible because the example uses a subset of AsyncAPI's JSON Schema-based Schema Object. It is **not** a full AsyncAPI parser; the official CLI is needed for document-level specification checks.

### README → code → executable proof

| Concept | Source | Proof |
| --- | --- | --- |
| Published channel and message | [Shared AsyncAPI](src/main/resources/asyncapi/order-events.yaml) | `producerAndConsumerReferenceTheSameSharedChannelAndOperationDirections` |
| Producer and consumer view | [send.yaml](src/main/resources/asyncapi/order-events-send.yaml), [receive.yaml](src/main/resources/asyncapi/order-events-receive.yaml) | Same + official CLI |
| Java producer's serialized message | [OrderCreatedEvent.java](src/main/java/bg/hristomanov/education/integrationpatterns/event/OrderCreatedEvent.java) | `serializedJavaProducerMessageMatchesThePublishedSchema` |
| Actual JSON examples | [valid fixture](src/test/resources/asyncapi/order-created-valid.json), [missing source](src/test/resources/asyncapi/order-created-missing-source.json), [zero quantity](src/test/resources/asyncapi/order-created-invalid-quantity.json), [unknown enum](src/test/resources/asyncapi/order-created-unknown-source.json) | `publishedExampleAndJsonFixtureBothSatisfyTheSharedSchema` and negative tests |
| Evolution and strict consumer behavior | `required` and `additionalProperties: false` | `addingRequiredFieldToPublishedSchemaWouldBreakExistingProducers`, `addingOptionalFieldAcceptsOldProducerButOldStrictConsumerRejectsNewField` |
| CLI and generated HTML | [package.json](package.json) | Dedicated [AsyncAPI CI workflow](../../../.github/workflows/asyncapi-contract.yml) |

## 5. Run it — Maven tests first, then optional Node CLI

From the repository root, **only JDK 25 + Maven** are required to execute the contract tests:

```bash
mvn -pl spring/messaging/integration-patterns -Dtest=OrderCreatedContractTest test

# Or the full module, including Noisy Neighbor and Fair Queues:
mvn -pl spring/messaging/integration-patterns test

# Everything:
mvn --no-transfer-progress verify
```

The Maven tests use **test-scope only** JSON Schema tooling. They do not start Kafka/Redpanda, a Spring context, a database or cloud resources. The actual serialized Java record is validated, not just a hand-written happy-path JSON example.

### CLI validation — checks the full AsyncAPI documents and external references

Install **Node 24 and npm 11** only if you want to run the CLI/generator (the optional tooling is not part of Maven). Then in the module directory:

```bash
cd spring/messaging/integration-patterns
npm install
npm run validate
```

The pinned CLI package from [package.json](package.json) validates **both** producer and consumer specs with `asyncapi validate`. The [dedicated GitHub Actions workflow](../../../.github/workflows/asyncapi-contract.yml) performs the same validation in CI. It is independent of the Maven job — **Maven green does not mean the AsyncAPI CLI has passed**.

### Generate HTML documentation — no handwritten HTML

In the same directory:

```bash
npm run docs
```

The scripts call the AsyncAPI Generator using the pinned `@asyncapi/html-template@3.5.4` template. Results are written to ignored build paths:

```text
target/asyncapi-docs/order-service/index.html
target/asyncapi-docs/stock-service/index.html
```

The AsyncAPI workflow can also upload generated HTML as an Actions artifact. **Generated docs are build artifacts**, not independent sources of truth; edit the AsyncAPI source file and regenerate. Generation is template-dependent and must be checked for compatibility with the chosen AsyncAPI specification version and Node runtime.

**Important operational caveat:** the speaker's original demo documents generator/Node version problems. We deliberately pin the CLI/template versions and keep Node installation **outside the default Maven test path**. Avoid silent automatic CLI upgrades or copying arbitrary community templates without review.

## 6. A schema change can be valid and still break consumers

Consider this currently valid JSON:

```json
{
  "id": "f1451166-a896-4f02-a741-69a51b19b277",
  "source": "CUSTOMER",
  "items": [{ "productId": "SKU-123", "quantity": 2 }]
}
```

**Change A — a new required field `customerSegment`.** The new schema may be perfectly valid. But old producer versions don't send this field, so they are **rejected by the new consumer**. The negative test demonstrates this.

**Change B — a new optional field `customerSegment`.** An old message remains valid **against the new schema**. However, the old schema uses `additionalProperties: false`: if a new producer starts sending `customerSegment`, an old strict consumer rejects the event. This asymmetry is explicitly tested; adding an optional field is **not automatically safe in both directions**.

**Change C — tighten quantity from 1..10 to 1..3.** Existing messages with `quantity: 8` may become invalid. Changing only `info.version` does not fix compatibility. Renaming `id` to `orderId` or shrinking enum values can likewise break older readers/writers.

A Senior-level design must define the **compatibility direction** explicitly: old producer → new consumer, new producer → old consumer, and what rolling deployment sequence and retention/replay mean for both. Distinguish semantic compatibility (same business meaning) from syntactic schema validation.

A possible mature follow-up: Schema Registry, producer/consumer-driven contract testing, backward/forward compatibility checks at PR time, versioned topics, migration adapters and replay tests. **None** of those are implicitly supplied just by adding AsyncAPI.

## 7. Where code generation fits — and why we do not reimplement the speaker's generator

Michel demonstrates:
1. specification-first descriptions, shared message definitions and Kafka bindings;
2. AsyncAPI CLI validation and HTML documentation generation;
3. Java model generation;
4. a **custom AsyncAPI Generator template** (Node/JavaScript) producing Java records, publisher/consumer adapters and Spring Cloud Stream configuration;
5. invocation from Maven phases in the example project.

Our **new training module** implements **1–2**, plus an independent Java/JSON Schema contract test and a hand-written example record to prove actual serialized JSON. We **do not claim** the Java record is generated or that a running Kafka integration exists.

The complete original source is available for optional further study:

- [MichelSchudel/asyncapi-demo](https://github.com/MichelSchudel/asyncapi-demo).
- [Shared order schema](https://github.com/MichelSchudel/asyncapi-demo/blob/master/order-service/src/main/resources/asyncapi/order-events.yaml).
- [Order Service send operation](https://github.com/MichelSchudel/asyncapi-demo/blob/master/order-service/src/main/resources/asyncapi/order-events-send.yaml).
- [Stock Service receive operation](https://github.com/MichelSchudel/asyncapi-demo/blob/master/stock-service/src/main/resources/asyncapi/order-events-receive.yaml).
- [Custom Generator template and its limitations](https://github.com/MichelSchudel/asyncapi-demo/blob/master/asyncapi-kafka-template/README.md).

Do not automatically add code generation to every small Spring service: generated code has ownership, review, upgrade and compatibility costs. Add it when it concretely reduces repeated boilerplate across many events/services and the toolchain is maintainable.

## 8. When you would actually use AsyncAPI

**Good fit:** multiple independently deployed teams produce/consume shared Kafka events, schema changes are frequent, debugging costs are high and humans cannot safely infer event contracts from source code in another repository. Formal documents can be reviewed, published and validated in CI.

**Not necessarily worth it:** a tiny single-process `ApplicationEvent` (not an external integration contract), an experiment with one short-lived internal consumer, or an app where the team has already standardized strongly on another versioned schema ecosystem (Avro/Protobuf/JSON Schema in a registry). AsyncAPI can document those schema formats too, but it need not replace the existing registry or contract governance.

## 9. Quick self-check before production

- [ ] Do the Kafka topic, operation direction and payload refer to the **same versioned published contract**?
- [ ] Is `asyncapi` specification version distinct from the application's `info.version`?
- [ ] Can the CLI resolve all local/external `$ref` and validate both complete documents?
- [ ] Is real serialized producer JSON tested against the published schema, including negative cases?
- [ ] Have we checked BOTH compatibility directions for rolling deployment and old queued messages?
- [ ] Do our `additionalProperties`, enum, numeric bounds and required fields reflect actual consumer tolerance?
- [ ] Are generated sources and HTML kept in build folders and produced reproducibly from pinned tooling?
- [ ] Is reliability delegated to the right system: [Outbox/Inbox](../outbox-inbox/README.md) for durability and idempotent consumption, [Domain Events](../../events/domain-events/README.md) for business semantics, [Integration Decision Guide](01-INTEGRATION-DECISION-GUIDE.md) for transport?
- [ ] Do real integration tests also verify broker settings, publisher behavior, consumer group semantics, retries, ordering and business effects where necessary?

**Final mental model:** *AsyncAPI documents which messages flow where and under which application operation. Schema validation proves one payload conforms; compatibility is a separate temporal promise; business and delivery correctness must be tested independently.*

## Primary/official sources

1. [Devoxx lecture — AsyncAPI in Action, Michel Schudel](https://www.youtube.com/watch?v=A_fp7RaG5UY), supplied transcript (recording ~29 min; timestamps approximate).
2. [Original source code — MichelSchudel/asyncapi-demo](https://github.com/MichelSchudel/asyncapi-demo).
3. [AsyncAPI Specification 3.1.0](https://www.asyncapi.com/docs/reference/specification/v3.1.0).
4. [AsyncAPI CLI — validation, usage and generation](https://www.asyncapi.com/docs/tools/cli/usage).
5. [AsyncAPI Generator — CLI and HTML template](https://www.asyncapi.com/docs/tools/generator/usage).
6. [AsyncAPI HTML Template](https://github.com/asyncapi/html-template).
7. [networknt/json-schema-validator 3.x — Jackson 3-compatible schema proof](https://github.com/networknt/json-schema-validator).
8. [JSON Schema Draft 7](https://json-schema.org/draft-07).

**Scope:** the YAML and Java tests are present and executable; generated documentation is produced via the optional CLI path. No Kafka broker, real services, code generation plugin, Schema Registry or production deployment is claimed.
