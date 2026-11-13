# NotebookLM — Legacy Software, PDF/Brotli и безопасна protocol adoption

**Оригинален материал:** [Guust Ysebie — Engineering Lessons from Working on Software Older Than Me](https://www.youtube.com/watch?v=VaT7DgmVSms), Devoxx Belgium 2026, предоставен транскрипт (~34 мин). Не го превръщай в generic PDF syntax tutorial.

**Използвай също конкретните Education материали:** [Solve the Right Integration Problem](04-SOLVE-THE-RIGHT-INTEGRATION-PROBLEM.md), [Protocol Adoption & Compatibility](05-PROTOCOL-ADOPTION-AND-COMPATIBILITY.md), [Java25 class + JUnit](src/test/java/bg/hristomanov/education/integrationpatterns/evolution/OrderEventEvolutionTest.java), [AsyncAPI Contract-First](03-ASYNCAPI-CONTRACT-FIRST.md), [API Versioning](../../boot4/03-API-VERSIONING-RESILIENCE.md), [Case Study 007 PDF/Brotli](../../../docs/case-studies/007-pdf-brotli-standards-adoption.md), [PDF Association official Brotli extension](https://pdfa.org/resource/extension-brotli/).

Създай **дълбок, но практичен 30–40 мин български аудио урок за Senior Java/Spring Boot инженер**, със следната история:

1. Доставчик изпраща PDF с бизнес данни; разработчикът започва parser, но PDF е optimized for visual presentation. Първият въпрос е дали supplier вече има JSON/XML. Използвай `Tagged PDF` като нюанс, не твърди, че всеки PDF е без структура.
2. Обясни `technical superiority != adoption` и `consumer/provider chicken-and-egg` чрез зрелия PDF ecosystem и много независими инструменти. Различавай **proposed / specified / implemented / interoperable / deployed**.
3. Съпостави с microservices: стар V1 consumer е строг и приема EUR implicit, новият V2 producer има `currency`, а V1/новият V2 трябва да работят едновременно при rollout. Използвай реалните 12 JUnit теста в `OrderEventEvolutionTest`, опиши 4 комбинации, capability intersection при fan-out, feature flag OFF, USD downgrade forbidden. Това е **наш educational analogy**, не код на Guust.
4. Покажи why deployment order matters: **expand readers first, then enable producers, verify adoption, only then contract/remove V1**. Обясни защо Kafka не прави автоматично capability negotiation и защо retained events/replay изискват да пазим старите readers.
5. PDF/Brotli историята: Guust като участник в разработката; оф. PDF Association extension от 19.08.2026 г., `PDFa`, `BrotliDecode`, `ExtensionLevel=1`, `ExtensionRevision=2026`. **Това е industry extension към ISO 32000-2:2020**, не задължителна core ISO функционалност за всички PDF 2.0 readers.
6. Benchmark correctness: document corpus, format compatibility matrix, compression ratio и file bytes, CPU encode/decode, memory, signatures/accessibility, malformed compressed data. **Не използвай универсален 15/20/25% gain без измерване**.
7. Кога не трябва да използваме нов format, кога е по-добре REST/JSON integration или стар формат с fallback. За всяка техника: **проблем, кога, кога НЕ, печалби, загуби, по-проста алтернатива, пример и тест**.
8. Представи последния призив на Guust за инициатива и координация (~33–34 мин) като професионален принцип, не като технически API.

В края дай 10 Senior questions без готови отговори, плюс code-review checklist за contract version, owners, producer/consumer rollout, actual telemetry и rollback. Не представяй conceptual case study като изпълнена PDF/Brotli интеграция.
