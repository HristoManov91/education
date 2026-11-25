# AI-Era Software Engineering — decisions, architecture & modernization

> P1/P2 · по Dave Sammut и João Rosa, [AI Made Building Cheap Now Decisions Are the Bottleneck](https://www.youtube.com/watch?v=2pSyF44THEc). Техните истории са от големи организации; нашите правила и синтетичният Java казус са адаптация за backend инженери.

## Входен казус

Застрахователна платформа има неясни правила за обработка на искове, бавни approvals и legacy код, който екипите избягват. Coding agent генерира нов service и десетки тестове за часове, но не може да установи сам дали старо изключение е законово изискване или ненужна историческа особеност. Междувременно review и business sign-off чакат.

Наивното решение е да увеличим броя на agents, да пренапишем целия service и да измерим броя PR-и. По-доброто е да видим къде реално се бави полезният резултат; да разделим механичния refactor от промяната на политика и да изберем малък обратим експеримент.

## Учебен ред

| ID | Глава | Приоритет и доказателство |
| --- | --- | --- |
| AI-DE-01 | [Decisions & hypotheses](01-DECISION-HYPOTHESES.md) | P1 · experiment card + flow case |
| AI-DE-02 | [Reversible architectural decisions](02-REVERSIBLE-DECISIONS.md) | P1 · ADR + rollback/expand-contract rehearsal |
| AI-DE-03 | [AI-assisted refactoring](03-AI-REFACTORING.md) | P1 · executable parity and independent policy tests |
| AI-DE-04 | [AI archaeology & legacy modernization](04-AI-ARCHAEOLOGY.md) | P1 · executable synthetic claims lab |
| AI-DE-05 | [AI-assisted discovery & EventStorming](05-AI-DISCOVERY.md) | P2 · workshop / evidence cards |
| AI-DE-06 | [Team Topologies & bottlenecks](06-TEAM-TOPOLOGIES.md) | P2 · flow mapping / adoption experiment |
| AI-DE-07 | [AI FinOps, ROI & governance](07-AI-FINOPS-GOVERNANCE.md) | P2 · worked cost case, quality gate |

Глави 01/02/05/06/07 са архитектурни/организационни бележки с възпроизводими упражнения, не отделни runnable Java labs. 03 и 04 споделят [Java 25/JUnit claims lab](lab/README.md). Не използваме live LLM; проверяваме механичните и бизнес гаранциите, не способностите на конкретен модел.

## README → код / упражнения

- [Legacy claims service](lab/src/main/java/bg/hristomanov/education/ai/claims/LegacyClaimsService.java) → [структурно рефакториран вариант](lab/src/main/java/bg/hristomanov/education/ai/claims/RefactoredClaimsService.java).
- [Approved claims policy](lab/src/main/java/bg/hristomanov/education/ai/claims/ApprovedClaimsPolicy.java) → [tests](lab/src/test/java/bg/hristomanov/education/ai/claims/ClaimsModernizationTest.java).
- [Exercises](EXERCISES.md) · [NotebookLM prompt](NOTEBOOKLM-PROMPT.md) · [Sources and evidence](SOURCES-AND-EVIDENCE.md).

## Изход

След урока можеш да обясниш защо по-бърз код не гарантира по-бърза доставка, да измериш ограничението, да предложиш обратима промяна, да отделиш observed behavior от intended policy и да не превърнеш AI-generated артефакт в източник на истина. Проверяваш outcome, safety, общ cost и ownership преди scale. Не заменяш Domain Expert, architecture review или тестова стратегия с LLM.

## Оригинални източници

[Лекцията](https://www.youtube.com/watch?v=2pSyF44THEc) · [Executive brief](https://www.equalexperts.com/blog/ai/ai-in-the-sdlc/) · [White paper landing page](https://campaign.equalexperts.com/ai-in-the-sdlc-transformation). Подробни timestamps, ограничения и допълнителни първични източници в [SOURCES-AND-EVIDENCE](SOURCES-AND-EVIDENCE.md).
