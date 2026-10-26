# Java Evolution Guide — 17 → 21 → 25 → 26 → 27 → 28 (as of 2026-10-26)

> **P2 focused reference + P3 API notes**, based on [Modern Java in Practice — Nicolai Parlog & Falk Sippach, Devoxx 2026](https://youtu.be/zC2tbq7S5UE).  
> Main education/ CI baseline is **Java 25 LTS**. We distinguish **released and final**, **released preview/opt-in**, and **proposed/EA** features; code that requires 26/27/28 **is not compiled in Java 25 modules**.

## 1. How to navigate this guide

| Concern | Module | What is runnable in Java 25? |
| --- | --- | --- |
| **P1 Data-Oriented Programming** | [DOP lesson](language/data-oriented-programming/README.md) | Records, sealed variants, switch/record patterns, guards |
| **P1 Stream Gatherers** | [Gatherers](streams/stream-gatherers/README.md) | Five built-ins + custom stateful Gatherer |
| **P1 JVM performance** | [Project Leyden + Compact Object Headers](jvm/performance-evolution/README.md) | Synthetic workload, JDK 25 AOT training/compare, object-header opt-in |
| **P2 Sequenced Collections** | [SequencedCollections](collections/sequenced-collections/README.md) | Live reversed views, LinkedHashSet, LinkedHashMap, TreeSet unsupported operations |
| **Targeted Loom expansion** | [MDC/ThreadLocal context](concurrency/project-loom/MDC-CONTEXT-PROPAGATION.md) | Virtual ThreadFactory propagation + cleanup tests |
| **Targeted JFR expansion** | [JDK 27 redaction](jvm/jfr-troubleshooting/JDK-27-JFR-REDACTION.md) | Conceptual note, JDK 27 runtime only |
| **Earlier Loom versions** | [Java 25→28 API status](concurrency/project-loom/JAVA-28.md) | Java 25 preview code and version compatibility guide |

## 2. Release and feature maturity matrix

| Release | Feature | Status for this release | What we do in education |
| --- | --- | --- | --- |
| **Java 17 LTS** | Sealed Classes (JEP 409), records already final in 16 | Final | Conceptual prerequisite for DOP |
| **Java 21 LTS** | Virtual Threads (444); Record Patterns (440); Pattern Matching for switch (441); Sequenced Collections (431) | Final | Runnable in Java 25, no preview |
| **Java 22** | Foreign Function & Memory API (454); unnamed variables/patterns (456) | Final | Targeted note, not rewriting JNI everywhere |
| **Java 24** | Stream Gatherers (485), virtual-thread pinning improvements (491), AOT Class Loading & Linking (483) | Final/JVM capability | Java 25 Gatherers code, Loom/JVM links |
| **Java 25 LTS** | Scoped Values (506), Compact Object Headers (519, opt-in), AOT ergonomics/profiling (514/515), JFR timing/tracing (520) | Stable, some VM options opt-in | Main runnable modules and tests |
| **Java 26** | HTTP/3 in java.net.http client (517) | Available, **opt-in** | Version-specific reference, no Java 25 source compilation |
| **Java 27 (Sept 2026)** | Compact Object Headers **default on** (534), JFR In-Process Data Redaction (536), hybrid TLS post-quantum, PEM APIs preview | Released; preview APIs remain opt-in | Security/JFR review notes; no baseline migration |
| **Java 28 (planned 2027)** | Structured Concurrency proposed final via JEP 543; JSON APIs being explored | **Not GA** as of this guide | Track official JEP status; no Java 28 production claims |
| **Java 29 (planned Sept 2027)** | Prospective LTS adoption target | Future | Re-evaluate vendor support, JEPs, dependencies and CI when available |

**Technical distinction:** LTS is a support promise from a JDK vendor; OpenJDK project releases are not intrinsically maintained forever. Always check the concrete distribution/support contract.

## 3. P3 — Java 25 compact source files and instance main methods

JEP 512 makes first scripts/learning examples concise, and JEP 511 supports module imports. E.g. a simple file outside a Maven project:

```java
void main() {
    IO.println("Modern Java can start small");
}
```

Launch with:

```bash
java Hello.java
```

This is useful for a local CLI or data migration helper, **not a reason to replace well-structured services with scripts**. If you need several classes/imports or large dependencies, make a small Maven/Gradle module. Check executable source launch behavior separately from packaged deployments.

Versions: single-file source launch appeared earlier (JDK 11); multi-file source launch came later (JDK 22); compact source / simplified entry point finalized in 25. Do not attribute all of this to Java 27.

## 4. P2 — JDK 26 HTTP/3 support

In JDK 26+ `HttpClient.Version.HTTP_3` is available:

```java
// Java 26+ example; NOT compiled by Java 25 education CI:
HttpClient client = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_3)
        .build();
HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.com"))
        .GET().build();
```

- Still **opt-in**; HTTP/2 remains the default preference.
- HTTP/3 uses QUIC/UDP, not ordinary HTTP/2 TCP transport.
- First attempt might be slowed by unsupported UDP/QUIC middleboxes; fallback to HTTP/2/1.1 is possible. Confirm negotiated protocol using the returned response version / network diagnostics.
- HTTP/3 does not automatically solve DNS, TLS, retry/idempotency or proxy compatibility issues.
- Do not enable simply because protocol version 3 is a larger number.

**Production experiment proposal:** control same URL, load, TLS infrastructure, network/path quality; compare time to first successful response, connection reuse and tail latency across clients.

Sources: [JEP 517](https://openjdk.org/jeps/517), [JDK 26 HttpClient.Version](https://docs.oracle.com/en/java/javase/26/docs/api/java.net.http/java/net/http/HttpClient.Version.html), [Inside Java HTTP client 26](https://inside.java/2026/03/04/jdk-26-http-client/).

## 5. P2 — Lazy Constants / Stable Values

Java 25–27 expose evolving **preview** Stable Values APIs for safely publishing lazily initialized immutable references (exact signatures change between previews).

Problem:

```text
Start application
→ eagerly load giant lookup table / ML model
→ slow startup even if no request needs it
```

Lazy initialization moves cost to first use, so:
- improves startup if value is rarely needed;
- **first access may become slower** and initialization failures move to runtime;
- thread-safe single successful initialization must be guaranteed;
- JIT can sometimes optimize stable references better than hand-written double-checked locking.

**Do not** copy a Java 27 preview `StableValue` snippet into Java 25 main modules. Keep the capability in this guide until release becomes final, and compare against ordinary safe Java lazy holder idiom when designing code.

Source: [JEP 502 — Stable Values (Preview)](https://openjdk.org/jeps/502).

## 6. P2 — Security: post-quantum, PEM and migration

### Post-Quantum Hybrid Key Exchange

The JDK gained ML-KEM/ML-DSA crypto primitives around Java 24; Java 27's TLS 1.3 hybrid groups can negotiate classical **and** post-quantum key establishment when the peer supports them.

- **Harvest now, decrypt later** is the motivating threat for long-lived confidential data.
- Hybrid algorithms deliberately combine a classical and a quantum-resistant mechanism, because ecosystem rollout and cryptanalysis maturity differ.
- TLS negotiation is between both parties; a Java client upgrade alone is not proof of using hybrid key exchange.
- Check trust-store, TLS provider, supported groups, intermediary compatibility and actual negotiated group. Use scoped handshake debug only on safe test secrets.

### PEM Encoder/Decoder

JDK 27 includes a **third preview** of PEM encoding/decoding API (JEP 538): `PEMEncoder`, `PEMDecoder` and `BinaryEncodable`. These are **not** Java 25 stable APIs.

Always validate key format, provider, encryption and authorization; PEM is a serialization envelope, **not encryption by itself**. Never print private keys in test logs.

Version sources: [Inside Java 27 Security](https://inside.java/2026/09/16/jdk27-security-enhancements/), [PEMDecoder Java 27](https://docs.oracle.com/en/java/javase/27/docs/api/java.base/java/security/PEMDecoder.html), [Oracle JDK 27 release notes](https://www.oracle.com/java/technologies/javase/27all-relnotes.html).

### Removal / deprecation watch

- Finalization is being retired; avoid cleanup semantics based on finalizers. Prefer try-with-resources and explicit lifecycle.
- Unsafe native/memory access requires review; changes in defaults may surface warnings/errors.
- Dynamic agents and native access flags may require explicit enablement across releases.
- These are **migration risks**; do not remove a production dependency from our repository only because a keynote mentioned deprecation. Scan and test actual usage.

## 7. P3 — JavaDoc Markdown, snippets and docs-as-code

**Java 23+** supports Markdown documentation comments with `///` (JEP 467). The inline/source-code `{@snippet}` tag predates Java 25 (JEP 413).

Our compiled Java 25 example: [DocumentationExample.java](language/data-oriented-programming/src/main/java/bg/hristomanov/education/dop/DocumentationExample.java). Its behavior is asserted by [DataOrientedProgrammingTest.java](language/data-oriented-programming/src/test/java/bg/hristomanov/education/dop/DataOrientedProgrammingTest.java).

```java
/// A simple example written with **Markdown** JavaDoc.
/// {@snippet :
/// var example = new Tablets(1, 0, 1);
/// String description = MedicationOperations.describe(example);
/// }
public final class DocumentationExample { /* ... */ }
```

**Important distinction:** An **inline** snippet in a doc comment is not itself compiled/tested just because the surrounding class compiles. For mission-critical docs-as-code, store the example in a **real Java source** that is executed by test or launch and refer to it from JavaDoc, rather than copy-and-paste diverging examples.

The repository Maven tests verify `DocumentationExample.sampleDescription()`, **not JavaDoc HTML generation** or standalone snippet validation. Add dedicated documentation build verification only when it materially improves the project.

Sources: [JEP 467](https://openjdk.org/jeps/467), [JEP 413](https://openjdk.org/jeps/413), [Oracle Java 25 Markdown JavaDoc](https://docs.oracle.com/en/java/javase/25/javadoc/using-markdown-documentation-comments.html).

## 8. Data-oriented style and security promises

Sealed types/record patterns allow closed **domain alternatives** and compiler exhaustiveness, but don't mean:
- missing network data is impossible;
- JSON is schema validated;
- untrusted inputs can bypass constructors;
- cryptographic fields can be logged safely;
- all downstream integrations are transactionally consistent.

Prefer [our DOP lab](language/data-oriented-programming/README.md) for business model state and [Strategic DDD](../spring/architecture/strategic-ddd/README.md) for where contexts live.

## 9. Migration decision checklist

1. Which **specific JDK release** and vendor are running in production?
2. Is the feature final, preview, experimental VM option or upcoming JEP?
3. What is the concrete business benefit: fewer invalid states, lower p99, reduced heap, faster startup?
4. What compatibility constraints exist: bytecode, libraries, agents, containers, OS/arch, build tools?
5. What is the smallest executable test or **measured baseline** that validates it?
6. Do we need a canary and rollback flag? How would we turn the feature off?
7. Which learning module or version note owns the knowledge?

## Sources / ongoing updates

- [Original 2h41 Devoxx session](https://youtu.be/zC2tbq7S5UE)
- [JDK 27 release notes (Sept 2026)](https://www.oracle.com/java/technologies/javase/27-relnote-issues.html)
- [Java 25 API documentation](https://docs.oracle.com/en/java/javase/25/docs/api/)
- [OpenJDK JEP index](https://openjdk.org/jeps/0)
- [Java Almanac — compare API versions](https://javaalmanac.io/)
- [Structured Concurrency JEP 543](https://openjdk.org/jeps/543)
