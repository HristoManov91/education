# Offline AI evaluation lab (Java 25)

## Case and evidence

A conference assistant can return fluent but invalid results. We evaluate separate, curated [golden-v1](src/test/resources/golden-v1.json) (10 synthetic cases, 5 dev + 5 holdout) and [replay-v1](src/test/resources/replay-v1.json) (20 observed fixture traces, one good and one deliberately bad per case). The public holdout is illustrative, not truly hidden. Clock and catalog are fictional. No real user data or API keys.

| What | Code | Proof |
| --- | --- | --- |
| Domain and typed trace | [EvalModel](src/main/java/bg/hristomanov/education/eval/EvalModel.java) | [EvalHarnessTest](src/test/java/bg/hristomanov/education/eval/EvalHarnessTest.java) |
| Independent hard checks | [EvalChecks](src/main/java/bg/hristomanov/education/eval/EvalChecks.java) | same harness, 20 replay invocations plus 4 checks |
| Trusted tool authorization, adjacency/overlap, state | [ScheduleTool](src/main/java/bg/hristomanov/education/eval/ScheduleTool.java) | [ScheduleToolTest](src/test/java/bg/hristomanov/education/eval/ScheduleToolTest.java) |
| Retrieval/calibration/trial metrics | [EvalMetrics](src/main/java/bg/hristomanov/education/eval/EvalMetrics.java) | [EvalMetricsTest](src/test/java/bg/hristomanov/education/eval/EvalMetricsTest.java) |
| Hard gate, per-slice regression and call budget | [ReleaseGate](src/main/java/bg/hristomanov/education/eval/ReleaseGate.java) | [ReleaseGateTest](src/test/java/bg/hristomanov/education/eval/ReleaseGateTest.java) |
| Real Dokimos core 0.31.0 | [DokimosOfflineTest](src/test/java/bg/hristomanov/education/eval/DokimosOfflineTest.java) | 2 labels, one mismatch, expected pass rate .5 |

## Run

JDK25, Maven; dependency download required on cold cache, no live model/DB/server/Docker:

    mvn -B --no-transfer-progress -pl ai/evaluation/lab test

Default root verify also includes this module. Expected: 40 JUnit invocations (20 parameterized replay, 4 harness, 5 tool, 5 metrics, 5 gate, 1 Dokimos). Counts are design inventory until CI reports execution.

## Walkthrough

The bad parking trace has high synthetic quality but unsupported fact ID. The overlap trace has invalid actual side effect and final state. Past recommendation is rejected by fixed clock. Good traces pass; every bad trace must fail the hard oracle (the negative controls themselves are successful JUnit tests when caught). Change one bad fixture to pass an invariant, observe which violation remains, and never edit gold expected values just to turn the suite green. For live LLM/semantic judging see [opt-in experiment](../live/README.md).

## Limits

This is an actual evaluator and tool boundary applied to synthetic **recorded/replayed** trajectories, not a complete LLM agent or RAG retriever. The fixture's extracted claim IDs and actual calls/post-state are assumed captured by trusted instrumentation; production must validate actual schema, capture trusted results, perform real persistence/auth and evaluate free-text extraction/entailment. In-memory schedule is not transactional DB, no concurrency/outage proof beyond synchronized local operations. A trace's quality is a fixture, not a measured judge score. No real production security, performance or model quality claim. Dokimos integration is deterministic exact-match, not fake model evaluation.

[Curriculum](../README.md) · [Original sources/evidence](../SOURCES-AND-EVIDENCE.md).
