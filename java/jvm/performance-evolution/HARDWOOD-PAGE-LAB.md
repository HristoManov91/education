# P2 Lab — Hardwood ideas: bounded page processing + metadata skipping

> **Java 25 + Maven + JUnit 5, no Parquet dependency, no external files.**  
> Companion to [Case Study 005](../../../docs/case-studies/005-hardwood-ai-assisted-parquet-performance.md), inspired by [Gunnar Morling's public Hardwood sources](https://www.morling.dev/blog/hardwood-new-parser-for-apache-parquet/). **Our independent teaching code, NOT actual Hardwood implementation.**

## 1. Problem / acceptance criteria

A pretend columnar dataset contains pages represented as primitive `long[]`. Some pages have trustworthy `min/max` statistics. We need to count values within an inclusive predicate range while:

1. **Never skipping** an uncertain page (metadata missing/untrusted).
2. Treating inclusive boundaries correctly.
3. Matching a **reference implementation that always scans every value**.
4. Allowing page-level parallel CPU work with a fixed pool and bounded pending tasks.
5. Not claiming speedups just because concurrent code produces the same count.
6. Preventing callers from mutating arrays after handing them to tasks.

**Do not conflate this with real Parquet**: Parquet nulls, nested values, NaN, dictionary encodings, RLE, repetition/definition levels, footer/index checks, Bloom filters, page compression and network reads are **not implemented**. We intentionally isolate one scheduling/correctness idea.

## 2. README → code → tests

| Функция | Изпълним Java код | JUnit |
| --- | --- | --- |
| `Evidence` classifier for reliable min/max | [PageProcessingLab.java](src/main/java/bg/hristomanov/education/performance/hardwood/PageProcessingLab.java) | [PageProcessingLabTest.java](src/test/java/bg/hristomanov/education/performance/hardwood/PageProcessingLabTest.java) |
| Independent reference oracle | `PageProcessingLab.reference` | Always scans each row, ignores all stats |
| Optimized sequential processing | `PageProcessingLab.sequential` | Cannot / All / May match checks |
| Bounded parallel processing | `PageProcessingLab.parallel` | Reference parity across worker counts |
| Negative proof: corrupted trusted metadata | `PageProcessingLab.classify` | Oracle catches a false-negative skip |
| Memory / lifecycle safety | `Page.values()` defensive clone; `ExecutorService` closes | No shared mutable buffer races |

## 3. Commands (from repository root)

```bash
# Focused 12 JUnit tests:
mvn -B --no-transfer-progress \
  -pl java/jvm/performance-evolution \
  -Dtest=PageProcessingLabTest test

# Full existing JVM performance module:
mvn -B --no-transfer-progress -pl java/jvm/performance-evolution verify

# Entire Education Maven reactor:
mvn -B --no-transfer-progress verify
```

Windows PowerShell: run the same `mvn` arguments on a single line or use IntelliJ Maven Run Configuration; the line continuation above is Bash syntax.

Expected: 12 new tests PASS, with no external dataset, Docker or native library. **These tests measure correctness only**, not wall-clock performance.

## 4. Understand the three-state decision

```text
Page stats reliable?
  no  → MAY_MATCH → scan
  yes:
    page.max < wanted.min → CANNOT_MATCH → skip
    page.min > wanted.max → CANNOT_MATCH → skip
    page.min >= wanted.min AND page.max <= wanted.max
         → ALL_MATCH → accept count without decoding values
    otherwise → MAY_MATCH → scan values
```

Use `Range` inclusive bounds: page [10,20] **might match** [20,20]. A common off-by-one bug is to write `<=` instead of `<` in the disjoint comparison.

**Negative example: metadata lies.** With actual values `[42]` but claimed trusted stats `[0,1]`, the fast algorithm skips incorrectly; the independent reference still finds the match. This JUnit test is an **intentional demonstration of unsafe input assumptions**, not evidence that all Parquet statistics are corrupt. In a real pipeline, never declare arbitrary stats trustworthy without validating the underlying format's semantics and compatibility.

## 5. Why bounded page work?

```text
BAD:
  for each row:
    executor.submit(() -> decode(row))    // huge queue + scheduling overhead

GOOD:
  for each page:
    if metadata proves no match → no task
    if metadata proves full match → no task
    else → submit decode(page) to bounded in-flight queue
  combine page results with Math.addExact(...)
```

[PageProcessingLab.parallel](src/main/java/bg/hristomanov/education/performance/hardwood/PageProcessingLab.java) uses `Executors.newFixedThreadPool(workers)` plus `ExecutorCompletionService`. We submit no more than **2 × workers** unfinished/queued tasks at any time; reaching the bound forces the producer to collect one completed task before submitting more.

This is **a simplified CPU-bound decoding lab**, so platform threads with a fixed limit are intentional. Virtual threads may help coordinating blocking I/O, but do not create extra CPU cores. The real Hardwood decoder has other concerns — prefetch depth, row ordering, memory ownership, dictionary decoding and nested schema semantics.

## 6. Exercises with evidence

**Exercise A — independent oracle:** Run a seeded test that creates 48 pages with varying data and trusted/untrusted statistics. Compare `reference`, `sequential` and `parallel` for six ranges and four worker counts. Assert **exactly the same matching-row count**.

**Exercise B — unsafe optimization:** Temporarily alter `classify` to always skip when a page's metadata says non-overlap, ignoring `trustedStatistics`. Verify `absentOrUntrustedMetadataRequiresDecodingRatherThanPruning` fails. Restore code.

**Exercise C — off-by-one:** Change a `<` boundary comparison to `<=`. Observe the inclusive-boundary test fail, then restore.

**Exercise D — data race:** Remove the defensive clone in `Page`, mutate original arrays after submission, and reason about incorrect/unstable counts. Restore copies; do not confuse reproducibility under one scheduling run with thread safety.

**Exercise E — performance hypothesis:** Record actual `workers`, CPU usage, memory and elapsed times for a *large enough* dataset with identical data and same reference correctness. Use JMH and [Benchmark Correctness](BENCHMARK-CORRECTNESS.md) for controlled measurements; do not invent `2x` results from this tiny lab.

**Exercise F — error handling:** How should a production parser cancel all pending pages and close external resources if one decoder throws? This demo relies on a scoped executor and propagates `ExecutionException`; it is not a ready-made production parser recovery policy.

## 7. When / when NOT / trade-offs

| Approach | Кога | Кога НЕ | Benefit ↔ cost |
| --- | --- | --- | --- |
| Skip pages via stats | Reliable stats, narrow predicate | Unsupported nullable/NaN types, uncertain stats | Fewer reads ↔ risk of false-negative semantics |
| Fixed CPU workers | Independent CPU tasks and many pages | Small tasks/few pages, no CPU bottleneck | More CPU utilization ↔ scheduler and memory overhead |
| Bounded pending tasks | Streaming huge number of pages | Tiny fixed workload with controlled memory | Backpressure ↔ extra coordination |
| Independent reference oracle | Optimizations that may change logical output | Never skip just because optimized code has passing self-tests | Detect subtle data loss ↔ slower test-only path |

### Code review checklist

- [ ] Is the independent oracle **more straightforward** than the optimized path?
- [ ] Are page statistics truly trustworthy for the encoded data type?
- [ ] Do the tests cover equal boundaries, empty pages and unknown stats?
- [ ] Are input buffers immutable or thread-safe for background readers?
- [ ] Is the queued/in-flight work explicitly bounded?
- [ ] Is error handling/recovery defined for failed pages?
- [ ] Are performance numbers measured on a representative workload?
- [ ] Is this actual Parquet decoding or a simplified model? (**This lab is only a model.**)

## Sources

- [Case Study 005 — Hardwood](../../../docs/case-studies/005-hardwood-ai-assisted-parquet-performance.md).
- [Hardwood author, Feb 2026](https://www.morling.dev/blog/hardwood-new-parser-for-apache-parquet/).
- [Hardwood source repository](https://github.com/hardwood-hq/hardwood).
- [Our Benchmark Correctness lab](BENCHMARK-CORRECTNESS-LABS.md).
