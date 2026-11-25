# Opt-in live experiment — Spring AI + Dokimos

**External model requests can cost money.** This separate module is not in the default reactor or CI. It uses only synthetic conference facts. No live run claimed. JDK25, Maven, approved API access; Spring AI 2.0.1 / Dokimos 0.31.0. Choose supported pinned agent and judge model IDs explicitly (do not assume model aliases remain stable). JudgeLM is a small adapter around actual Spring AI ChatModel, not a simulated judge.

From repository root, configure environment using approved secret management (never commit credentials):
 
    export RUN_LIVE_EVALS=true
    export EVAL_AGENT_MODEL='<approved-pinned-model-id>'
    export EVAL_JUDGE_MODEL='<approved-pinned-judge-id>'
    # OPENAI_API_KEY supplied securely in environment, not in repository
    mvn -B --no-transfer-progress -Plive-evals -pl ai/evaluation/live test

Or explicitly run `mvn -f ai/evaluation/live/pom.xml test`. Without opt-in/environment the test fails before any request. Standard `mvn verify` does not include this module. No automatic model selection or secret reads in offline module.

## What runs

[LiveJudgeTest](src/test/java/bg/hristomanov/education/eval/live/LiveJudgeTest.java): two human-authored frozen outputs (supported abstention vs invented parking), two repeats each, then one live agent + live Dokimos judge evaluation of the same synthetic question. Report small calibration agreement and false passes separately; require all four calibration decisions correct and live rubric pass. This is a smoke experiment, not statistical calibration or release certification. Threshold .8 is illustrative. A judge error/non-result is not evidence of rejection correctness. The adapter requires strict JSON with numeric score in [0,1] and nonblank rationale; the result must contain the actual output and a valid threshold. Library parsing failures cannot count as a calibrated negative. CI test-compiles this module but never executes live tests. Inspect errors rather than silently retrying. Limit at most 12 model adapter calls, max 600 completion tokens/call, 30s timeout, no SDK retries. Provider-specific token/pricing and internal framework calls vary; track invoice and usage, and enforce a provider budget. No temperature forced (some reasoning models reject it). If a judge cannot parse/complete within limit, report failure; don't retry until green. Run only approved synthetic data.

## Decision

Use when you need subjective/semantic evaluation and have accepted data/cost constraints; don't use for deterministic authorization/overlap or every default CI run. Benefits: real model behavior; cost and nondeterminism. Alternative: offline replay and human review. The agent here is a single model call, not the full conference tool/RAG application; [offline lab](../lab/README.md) covers trace and state policies.

## Original sources

[Spring AI 2.0.1 OpenAI options](https://docs.spring.io/spring-ai/docs/2.0.1/api/org/springframework/ai/openai/OpenAiChatOptions.html) · [Dokimos experiment API](https://dokimos.dev/getting-started/evaluation/) · [Evidence map](../SOURCES-AND-EVIDENCE.md).
