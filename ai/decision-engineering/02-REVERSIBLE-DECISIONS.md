# AI-DE-02 · Reversible Architectural Decisions (P1)

Казус: migration към нов claims workflow е планирана за години. AI намалява усилието за писане на adapter, но не връща вече изпратено съобщение, заличена таблица или юридически значимо плащане. Затова цената на reversal не е равна на цената на code revert.

**От лекцията (~05:15–08:24):** не следвай решение само заради вече вложеното; проверявай новите условия и пази пространство за промяна. **От нас:** изричен decision contract за reversibility.

| Избор | Възможен reversal | Ограничение |
| --- | --- | --- |
| Вътрешен refactor | revert на малък PR | само ако contract и data са непроменени |
| Нов read path | flag + стар path + shadow compare | extra load, dual reads, stale comparisons |
| Schema change | expand → migrate → switch → contract | след destructive contract downgrade не е безусловно възможен |
| Плащане / външно действие | компенсация, reconciliation | не е rollback на същия физически ефект |

### ADR пример (non-runnable architecture note)

- Context: legacy claim outcome трудно се разбира; SME потвърждава правило, но downtime е неприемлив.
- Options: keep/refactor; rewrite big bang; new implementation behind versioned adapter.
- Decision: започваме със read-only shadow на синтетични/одобрени данни и запис на differences, без duplicate side effects.
- Guardrails: old client compatibility, no duplicate payouts, no PII in logs, latency budget, unknown-rule quarantine.
- Reversal: flag off, traffic стар path, verify data/state; след irreversible data migration използваме forward fix/restore с RPO/RTO, не обещаваме безплатно връщане.
- Revisit: при business approval + discrepancy threshold; stop при неподписана промяна на правило.

Bad: огромен PR, защото AI го генерира бързо. Good: малки стъпки с доказуеми prerequisites, решение и rollback rehearsal. Затваряме миграцията едва след наблюдение на реалните consumers и retention на необходимите данни.

Decision contract: използвай при висок uncertainty/impact; не добавяй flags и shadow infra за trivial pure function; печелиш ограничен blast radius и learnability, губиш временно двойна поддръжка, сложност и ресурси. Алтернатива: една малка deploy стъпка с backup. Доказателство: tabletop rehearsal в [exercises](EXERCISES.md) и [ADR/fitness functions](../../system-design/06-C4-ADR-REVIEWS.md); отделно [lab parity](lab/README.md) демонстрира само функционалната граница, не deployment rollback.

## Оригинални източници

[Лекция](https://www.youtube.com/watch?v=2pSyF44THEc) · [Executive brief](https://www.equalexperts.com/blog/ai/ai-in-the-sdlc/) · [Patterns of Legacy Displacement](https://martinfowler.com/articles/patterns-legacy-displacement/) · [ADR и C4 в Education](../../system-design/06-C4-ADR-REVIEWS.md).
