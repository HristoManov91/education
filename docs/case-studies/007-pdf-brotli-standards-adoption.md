# Case 007 — PDF/Brotli: от техническа идея до реална екосистемна съвместимост

> **P2 engineering case, НЕ production incident и НЕ изпълним PDF/Brotli parser.**  
> Първоизточник: [Guust Ysebie — Engineering Lessons from Working on Software Older Than Me](https://www.youtube.com/watch?v=VaT7DgmVSms), Devoxx Belgium 2026, предоставен SRT (~34 мин).  
> Контекст: авторът работи по iText SDK/Apryse, PDF accessibility/signing/performance и се включва в работата за Brotli compression. Тази история е **perspective of a participant**, не независима оценка на всички PDF viewers.

## 1. Проблемът преди решението: PDF трябва да се чете от много различни инструменти

PDF е стар, зрял формат за преносимо представяне на документи. Има огромна екосистема от генератори, viewers, валидатори, браузъри, принтери и архивни потоци. **Нова compression техника** може да даде по-малки stream данни, но създава нова зависимост: PDF producer може да започне да пише encoding, който стар PDF reader не познава.

**Bad:** избираме алгоритъм само по по-добър compression benchmark на един документ и веднага го включваме по подразбиране за всички експорти.

**Good:** първо проверяваме PDF syntax и официалното extension поведение, после намираме реални independent reader/writer implementations, правим compatibility matrix и постепенно активираме format feature там, където всички критични consumers го поддържат.

Лекторът обобщава различните уроци като „solve the right problem“, „technical superiority is not adoption“, „interoperability requires multiple implementations“ и „propose → implement → deploy“. Това е **нашето структурирано извличане** от презентацията, не цитирана негова официална методология.

## 2. Как е стигнал до проблема — 03–21 минути

- **03:00–08:30 — Wrong problem:** при опит за извличане на бизнес данни от PDF лекторът осъзнава, че трябва да попита supplier дали има структурирана JSON форма. PDF drawing operations не са гарантиран домейн schema.
- **08:30–12:00 — Better format does not guarantee adoption:** широкото внедряване на PDF го прави практичен въпреки ограниченията му. Намаляването на размер или усъвършенстването на syntax не е достатъчно, ако няма читатели.
- **12:00–20:00 — Ecosystem interoperability:** open/public specification, независими implementers, стандартизация и real tools са значими, когато форматът трябва да живее десетилетия. Един producer/vendor не е достатъчен.
- **20:00–32:00 — Brotli extension:** лекторът обяснява compression choices, публикуване на предложение и координация с PDF projects/readers. **Историята е от неговата перспектива**; тя сама не доказва кой точно viewer поддържа кой extension build.

## 3. Фактическа проверка към ноември 2026 — industry extension ≠ core ISO PDF 2.0

PDF Association посочва **19 август 2026 г.** като дата на публикуване на **“PDF 2.0 Extension for Brotli compression”**: [Technical Resources](https://pdfa.org/resources/). Спецификацията е разработена от PDF Technical Working Group и разширява **ISO 32000-2:2020**, вместо да доказва, че Brotli е задължителен в базовия ISO текст.

[PDF Association — Extensions](https://pdfa.org/extensions/) посочва:

| Елемент | Стойност |
| --- | --- |
| Extension prefix | `PDFa` |
| `BaseVersion` | `2.0` |
| `ExtensionLevel` | `1` |
| `ExtensionRevision` | `2026` |
| Нов stream filter | `BrotliDecode`, чрез RFC 7932 |

Това е **публикувана industry-ratified extension specification**, не доказателство за universal installed-base support. Непознаващ extension-а стар reader може да не декодира stream-а. Неговата несъвместимост не доказва, че е „дефектен PDF 2.0 reader“ по core стандарта.

**Не твърдим** на база тази лекция, че всички browsers, PDF libraries или archival formats вече поддържат Brotli; съвместимостта се проверява **по конкретен product/version и реален файл**.

## 4. Adoption stages — кога техническата промяна се превръща в продуктова стойност

| Фаза | Какво имаме | Какво НЕ трябва да заключаваме | Минимален evidence |
| --- | --- | --- | --- |
| **Proposed** | Дизайн, discussion, тестови payload-и | Че индустрията го поддържа | Public proposal/consensus и threat model |
| **Specified** | Публикувана extension spec | Че consumers умеят да четат | Проверим стандарт + version |
| **Implemented** | Writer/reader реализация в даден SDK | Че interoperability е доказана | Unit tests, fuzzing и corpus, parser error handling |
| **Interoperable** | Множество **независими** producer/reader комбинации | Че всички използват новото поведение | Cross-vendor corpus/roundtrip, reader version matrix |
| **Deployed/adopted** | Реални файлове, реални клиенти и наблюдения | Че feature може безусловно да стане default | Producer toggle, fallback, telemetry, compatibility support policy |

Същата логика е изпълнена с малък Java event protocol пример в [P1 OrderEventEvolution lab](../../spring/messaging/integration-patterns/05-PROTOCOL-ADOPTION-AND-COMPATIBILITY.md): readers се обновяват преди writers; mixed consumers принуждават producer към съвместим fallback; unknown currency не може да се downgrade-не без загуба на смисъл. **Това е аналогия за adoption, не PDF/Brotli технически тест.**

## 5. Performance и correctness: какво трябва да измерим

Лекторът дава мотивация за Brotli compression, но **не публикуваме универсален процент за всички PDF**. За производствено решение трябва benchmarking върху реален документен корпус, с ясно разграничаване на:

1. **Корпус:** text-heavy PDF, vector graphics, embedded fonts, images, already-compressed JPEG/PNG, tagged PDF, signed PDF, encrypted PDF, linearized PDF.
2. **Размер:** оригинален compressed/uncompressed stream size, цялостен PDF file size, percent delta на всеки документ и дистрибуция (median/p95), не само средна стойност.
3. **CPU/Latency:** compress time/writer CPU, decode time, viewer startup/first page, memory peak, mobile/low-power devices.
4. **Съвместимост:** списък с независими readers/versions, supporting explicit `BrotliDecode`, fallback outcomes, corruption/malformed input, resource limits.
5. **Инварианти:** еднакво визуално съдържание, необходимата logical accessibility structure, валидиране на подписи и релевантни архивни ограничения. Не приемай, че byte-for-byte identity е необходима след recompression.
6. **Безопасност:** decompression bombs, memory/CPU bounds, third-party dependency security, rollback и дългосрочна поддръжка на създадените файлове.

**Нямаме измерен PDF/Brotli benchmark в Education.** Не използвай проценти от слайдовете като резултат на нашия код.

## 6. Make vs Buy / кога да НЕ въвеждаме новия формат

**Кога:** има измерим size/transport benefit за конкретния corpus, всички критични потребители поддържат encoding-а или има договорен fallback, а форматът се поддържа от достатъчно независими доставчици.

**Кога НЕ:** имаме исторически clients/readers без upgrade path, документи с regulatory archival requirements, слаб real-life size benefit или липсващи cross-reader tests.

**Печалби:** по-малки файлове и потенциално спестен bandwidth/storage; сътрудничество с отворен стандарт и екосистема.

**Загуби:** migration/compatibility matrix, CPU decode/encode overhead, възможни откази в стар viewers, усложнен fallback.

**По-просто:** продължи с widely-supported PDF compression, докато има доказан reader adoption; или използвай структуриран JSON за business integration вместо PDF extraction.

**Proof (сега):** [Java25 contract rollout tests](../../spring/messaging/integration-patterns/src/test/java/bg/hristomanov/education/integrationpatterns/evolution/OrderEventEvolutionTest.java) доказват **аналогичния producer/consumer rollout**, не съвместимост с PDF viewers. [Source-first integration decisions](../../spring/messaging/integration-patterns/04-SOLVE-THE-RIGHT-INTEGRATION-PROBLEM.md) обясняват първата история на Guust. PDF/Brotli твърденията са спецификационно обосновани, не runnable demo.

## 7. Senior recall / практическа употреба

- Защо силен compression ratio не означава adoption?
- Кога публична спецификация е недостатъчна за interoperability?
- Какво доказва един успешен writer → reader test и какво остава непроверено?
- Как би организирал rollout с исторически readers, които никой вече не обновява?
- Какви safety checks са нужни за decompress върху недоверени bytes?
- Защо подписан PDF може да има различен policy от обикновен документ?
- Кога не е нужно изобщо да парсваш PDF, ако целта е бизнес JSON?

**Инженерно заключение:** стандарт, implementation и реална adoption са **три различни milestones**; оценяваме ги отделно.

## Original/official sources

- [Guust Ysebie — Engineering Lessons from Working on Software Older Than Me](https://www.youtube.com/watch?v=VaT7DgmVSms), предоставен SRT (~34 мин).
- [PDF Association — Official Technical Resources / Brotli extension publication](https://pdfa.org/resources/).
- [PDF Association — Brotli compression in PDF](https://pdfa.org/resource/extension-brotli/).
- [PDF Association — Extensions / PDFa BrotliDecode](https://pdfa.org/extensions/).
- [RFC 7932 — Brotli Compressed Data Format](https://www.rfc-editor.org/rfc/rfc7932).
