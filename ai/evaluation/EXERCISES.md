# AI Evaluation — exercises and diagnostics

| ID | Самостоятелна задача | Какво проверяваме |
| --- | --- | --- |
| AE-01 | Define goals/non-goals for a refund agent; classify deterministic vs judge vs human. | EDD scope and hard rules |
| AE-02 | Add unknown/ambiguous case and a paraphrase; keep family in one split. | Independent labels, leakage |
| AE-03 | Run Dokimos offline; mutate one fixture label/observed output separately; explain false positive of contains. | Real framework, oracle |
| AE-04 | Human labels [T,F,T,F], grader [T,T,T,F]; compute agreement/false pass, repeat. | Calibration and bias |
| AE-05 | Retrieved [java,noise], relevant [java,loom]; diagnose unsupported parking with missing source. | Set precision/recall, faithfulness vs truth |
| AE-06 | Agent claims booked; trace contains search only. Test final state and required args. | Outcome/trajectory |
| AE-07 | Add overlapping and adjacent sessions; fixed time; separate owners. | Stateful invariants |
| AE-08 | Try adding another user's session in sandbox, no approval. | Deny before effect |
| AE-09 | Triage “past sessions”; convert to independent case with frozen clock. | Feedback and regression |
| AE-10 | 100×3×3 calls; mean high with one hard fail. Decide release and budget. | Hard gates, slices, trials |

## After own answer

AE-04: 3/4 agreement, 1/2 false-pass. AE-05: .5 precision and .5 recall; unsupported ≠ necessarily false. AE-06: don't trust assistant text. AE-07: [start,end) permits adjacency; preserve independent state. AE-10: 900 calls before extra; block any hard failure. For true evaluation use separate holdout and human labels; no mastery scores assigned.

Capstone: build a small conference agent around an approved model, retrieve from fixed sources, authorize schedule tools server-side, log minimized traces, implement paired baseline/candidate report and human calibration. Start with [Offline lab](lab/README.md); use [Opt-in live experiment](live/README.md) only after cost approval. No real production data.

[Sources](SOURCES-AND-EVIDENCE.md).
