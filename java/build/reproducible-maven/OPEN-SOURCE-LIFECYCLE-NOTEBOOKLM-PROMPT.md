# NotebookLM — Long-Haul Open Source: EOL, Security Backports, VEX и Live Patching

**Source:** [Sven Sellen — Engineering for the Long Haul: Operating Open Source at Scale](https://www.youtube.com/watch?v=Pg5kS8l9CpI), предоставен SRT (~32 мин), Devoxx Belgium 2026.

**Допълнителни материали в Education:** [EOL strategy](OPEN-SOURCE-LIFECYCLE-EOL.md), [Software Gardening](SOFTWARE-GARDENING.md), [VEX Backport Evidence](../../../spring/testing/testcontainers/VEX-BACKPORT-EVIDENCE.md), [SBOM Accuracy](../../../spring/testing/testcontainers/SBOM-ACCURACY.md), [Kubernetes Live Patch vs Rolling](../../../infrastructure/kubernetes/LIVE-PATCHING-VS-ROLLING-UPDATES.md), [CRA Risk & Release](../../../docs/security/cra/README.md).

Подготви **30–40 мин задълбочена българска аудио лекция за Senior Java/Spring Boot инженер**, структурирана като реален работен incident:

1. Имаме Spring Boot/Oracle микросървис с EOL Maven зависимост и нов scanner CVE, но не можем веднага да мигрираме 6 major versions. Покажи принципните решения **upgrade / backport / ELS / mitigation / retire**, кога и кога НЕ, бизнес цена, техни trade-offs, maintenance и exit plan.
2. За всяко решение **проблем → bad approach → good approach → реален работещ пример/тест → по-просто решение**. Не твърди, че зелен current-main build доказва deployed legacy artifact.
3. Разясни `SBOM` (inventory), `VEX` (конкретно impact state), `BOM-Link` (точен CycloneDX document/component), `resolved_with_pedigree`, `not_affected` и `in_triage`. Разграничи OpenVEX status enums; не смесвай `fixed` с `not_affected`.
4. Проследи нашата **синтетична** `orders-api 1.5.0` фикстура: `CVE-2099-90001/90002` са НЕИСТИНСКИ CVE. Разкажи как [verify_vex.py](../../../spring/testing/testcontainers/vex-backport-evidence/verify_vex.py) блокира чужд BOM, невалиден artifact SHA, missing patch, expired review, `in_triage`. **READY_FOR_HUMAN_REVIEW не значи SAFE**.
5. Разясни защо SHA hash, synth test report, неподписано VEX и SLSA badge не са достатъчни за production trust. Нужни са supplier attestation, verified signer/builder, actual patched source/artifact и independent tests.
6. Сравни **Linux kernel live patch**, userspace shared library patch, **JDK** и **Maven JAR** replacement, Kubernetes rolling update и node reboot. Използвай реалния [Education Deployment](../../../infrastructure/kubernetes/manifests/30-deployment.yaml) с 2 replicas, `maxUnavailable=0` и graceful termination, без да обещаваш универсално zero downtime.
7. Представи TuxCare SecureChain/ELS/KernelCare/LibCare като **commercial supplier claims от лекцията**. Не използвай техните статистики и 7/14 day SLA числа като независими факти без методология/contract; обсъди coverage, SLA clock, provenance, support cutoff и алтернативите.
8. Завърши с **10 Senior-level въпроса без отговори**, code review checklist и кратък план за security triage на истински enterprise Java release.

Всяко твърдение маркирай като **източник от SRT**, **официален стандарт**, **учебна фикстура** или **инженерна препоръка**. Не диктувай дълги code snippets в аудиото. Не заявявай, че сме инсталирали търговски инструменти или изпълнили patch върху реален binary.
