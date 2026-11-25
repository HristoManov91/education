# Local Cloud Architecture Lab — Java 25 / Spring Security

Изпълнимо без cloud account, Docker или secret. Maven изтегля dependencies при първия run; tests bind ephemeral localhost ports. Версии: Java 25, Spring Boot parent 4.1.1, managed Spring Security / Nimbus; не стартираме Boot web server, използваме Spring decoder/introspector и JDK HttpServer. Това е локален lab, не AWS/GCP benchmark.

## Вход и карта README → код → test

| Case | Implementation | Evidence |
| --- | --- | --- |
| Optional analytics failure | [AnalyticsClient](src/main/java/bg/hristomanov/education/cloud/AnalyticsClient.java), [FleetDashboard](src/main/java/bg/hristomanov/education/cloud/FleetDashboard.java) | [CrossCloudFailureTest](src/test/java/bg/hristomanov/education/cloud/CrossCloudFailureTest.java): HTTP 503, deadline before response headers, core independent, cold/warm cache, expiry, recovery, no fallback for auth/contract errors, stale ordering |
| IdP / JWKS outage | [FleetJwtDecoder](src/main/java/bg/hristomanov/education/cloud/FleetJwtDecoder.java) | [CrossCloudJwtTest](src/test/java/bg/hristomanov/education/cloud/CrossCloudJwtTest.java): actual RSA-signed JWT, local JWKS, Spring introspection, cache, rotation, cold/unknown key rejection, negative claims/signature/time |
| Partition / replay | [FleetSynchronization](src/main/java/bg/hristomanov/education/cloud/FleetSynchronization.java) | [FleetSynchronizationTest](src/test/java/bg/hristomanov/education/cloud/FleetSynchronizationTest.java): bounded outbox, ACK loss, duplicate, stale, conflict, tombstone, invalid ACK |
| Availability and traffic | [CloudBudget](src/main/java/bg/hristomanov/education/cloud/CloudBudget.java) | [CloudBudgetTest](src/test/java/bg/hristomanov/education/cloud/CloudBudgetTest.java): independent series/parallel, bounds, directional decimal GB, cost/retry, negative/overflow |

Run from root (JDK 25 and Maven):

    mvn -B --no-transfer-progress -f system-design/cloud-architecture/lab/pom.xml test

Or root reactor:

    mvn -B --no-transfer-progress -pl system-design/cloud-architecture/lab test

## Walkthrough / negative controls

1. HTTP: 503 breaks mandatory endpoint; optional endpoint returns 12 vehicles plus UNAVAILABLE or explicitly STALE snapshot, whose source asOf is within 15 minutes. Successful fresh response recovers. 403 or malformed contract is not hidden. A deadline test stalls before response headers; no arbitrary wall-clock performance assertion. HttpClient request timeout is not a proof of an end-to-end body deadline; production must bound response body, all phases, cancellation, queue/bulkhead and caller budget. The dashboard is serial, fixed single fleet, explicit evaluation Instant; production obtains trusted clock at response/fallback time and caches per tenant/authorization.
2. IAM: signed token uses at+jwt, allowed RS256 and expected issuer/audience/client, required profile claims and time. Warm known key works without another JWKS HTTP call while cache remains usable. New/unknown key and cold cache during 503 reject; published overlapping key can be fetched. The fixture uses loopback HTTP (production TLS); no real IdP/login, full servlet filter chain, token revocation cache or authz policy. Use [CA-05](../05-DISTRIBUTED-IAM.md) and real resource-level authorization for deployment. Never treat stale auth as data fallback.
3. Sync: source event/state update is synchronized in one JVM; relay leaves event pending until ACK. Retry cannot resurrect a deleted vehicle or overwrite newer full snapshot. Backlog rejection precedes write. No real persistence/crash, Kafka/CDC, multi-writer consensus or broker transaction; use existing [DB outbox/inbox](../../../spring/messaging/outbox-inbox/README.md) for durable guarantees. ACK ownership assumes one relay.
4. Numeric: independent 99% and 95% mandatory -> 94.05%; same marginal availability alone only bounds 94–95%. 100 calls/s, 30 days, 500/1000 B -> 129.6/259.2 GB, 388.8 total. Synthetic price rates, no cloud tariff assertions.

## Exit

The source of failure is a required dependency, not the provider logo. Cached keys remove only some runtime network calls, not security validation. Async delivery enables local progress only within durability/retention/backlog limits. Rehearse actual DR and measure actual traffic before choosing multi-cloud. [8 chapters and diagrams](../README.md) · [Exercises](../EXERCISES.md) · [Sources](../SOURCES-AND-EVIDENCE.md).
