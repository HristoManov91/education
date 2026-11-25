# AI Evaluation — source map and evidence

Reviewed 2026-11-25. Original [Urs Peter — Eval Driven Development](https://www.youtube.com/watch?v=4qeUklTLyTs) — Devoxx Belgium 2026. Read provided English auto-generated SRT (~40 minutes); captions misrecognize evals, Dokimos, RAG, judge, Langfuse, Promptfoo. Timestamps approximate. No full transcript copied. Original runnable conference repository not identified from provided SRT; do not invent one.

| Range | Lecture content | Lesson |
| --- | --- | --- |
| 04:50–09:05 | Specification vs behavioral uncertainty, EDD goals, cost/safety | 01 |
| 09:05–13:40 | Agent goals/non-goals, personas, happy/rainy flows, input generation and review | 02 |
| 13:40–18:15 | Dokimos, dataset/task/evaluator, contains, deterministic checks | 03 |
| 18:15–23:30 | LLM judge, threshold, pinned model, tone demo | 04 |
| 23:30–28:10 | Retrieval relevance, tool recorder, claims/truths; unsupported parking | 05/06 |
| 28:10–31:05 | User/trajectory simulator, max turns, overlap failure | 07 |
| 31:05–32:30 | Frozen calibration set, repeat SD, human comparison | 04 |
| 32:30–34:00 | Security, red teaming and Promptfoo | 08 |
| 34:00–39:40 | Tracing, user feedback, sampling, triage, past-session test | 09/10 |
| 39:40–40:xx | Human review completes pyramid | 01/04/09 |

## Additional primary sources

- [Dokimos — official docs](https://dokimos.dev/), [source repository](https://github.com/dokimos-dev/dokimos), [setup](https://dokimos.dev/getting-started/installation/), [Dokimos datasets](https://dokimos.dev/evaluation/datasets/), [experiments](https://dokimos.dev/getting-started/evaluation/), [Dokimos Spring AI integration](https://dokimos.dev/integrations/spring-ai/). Docs reviewed; pin 0.31.0 (Maven Central release, not lecture version); verify actual dependency behavior in CI.
- [Spring AI evaluation](https://docs.spring.io/spring-ai/reference/api/testing.html), [Spring AI 2.0 usage](https://docs.spring.io/spring-ai/reference/2.0/api/usage-handling.html), [Spring AI 2.0.1 options API](https://docs.spring.io/spring-ai/docs/2.0.1/api/org/springframework/ai/openai/OpenAiChatOptions.html). Spring AI 2.0.1 pinned in optional lab.
- [Anthropic — Demystifying evals for AI agents](https://www.anthropic.com/research/demystifying-evals-for-ai-agents) — tasks, trials, graders, pass@k and pass^k; independent assumptions explicitly stated.
- [OpenAI — Evaluation best practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices) — task-specific, human agreement, continuous evaluation; principle guide not reliance on deprecated Evals platform.
- [Zheng et al. — Judging LLM-as-a-Judge](https://arxiv.org/abs/2306.05685) (abstract reviewed) — position/verbosity/self-enhancement bias; not proof of our judge reliability.
- [Langfuse evaluation](https://langfuse.com/docs/evaluation/overview) — online/offline feedback loop; [Promptfoo red teaming](https://www.promptfoo.dev/docs/red-team/quickstart/) — authorized red-team process; [OWASP GenAI](https://genai.owasp.org/) for threat-model reference.

## Boundaries

The speaker's survey percentages, claims about Gartner predictions and particular model scores are not universal metrics; we do not reproduce unverified statistics. Demo failures are speaker observations. Our dataset, clocks, labels, rules, traces, calibration numbers, diagrams and code are authored synthetic exercises. No live LLM measurements or production deployment claimed. Default harness tests captured traces and deterministic tool behavior, not language-model understanding; known structured claim IDs are not natural-language entailment. Dokimos deterministic experiment actual dependency; opt-in Spring AI/Dokimos judge code needs Java25/Maven and explicitly supplied credentials/model. No live execution in standard CI. Evaluation status belongs to PR/CI, not fabricated. Future sources extend these canonical chapters; note versions, evidence and new tests.
