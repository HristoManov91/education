# P1 · Solve the Right Integration Problem — business data ≠ document appearance

> **Guust Ysebie — [Engineering Lessons from Working on Software Older Than Me](https://www.youtube.com/watch?v=VaT7DgmVSms)**, Devoxx Belgium 2026, предоставен SRT, приблизително **03:00–08:30**. Тази глава е integration decision guide, **не нов PDF parser или OCR implementation**. Работещото Java25 упражнение за producer/consumer contract е в [05-PROTOCOL-ADOPTION-AND-COMPATIBILITY.md](05-PROTOCOL-ADOPTION-AND-COMPATIBILITY.md).

## 1. Казус: „Извлечи тези полета от изпратения PDF“

Външен доставчик ни изпраща PDF с данни за фактури. Нашата задача е да извлечем контрагент, сума, дата и продукти и да ги запишем в Oracle. Естественият първи импулс е PDF parser + текстови heuristics + OCR fallback.

**Но PDF първоначално решава друг проблем:** преносимо възпроизвеждане на визуално съдържание. Текстът може да е разделен в drawing operations, в различен ред от човешкото четене. Не е гарантирано, че visual table е еднозначен business record.

Говорителят разказва, че след дълги опити с parser би предпочел **първо да попита изпращащата организация дали не разполага с JSON**. Това е инженерният урок; в SRT няма доказан production benchmark за PDF extraction или универсален API при всички suppliers.

**Не правим неверния извод**, че всеки PDF е напълно без структура: Tagged PDF/PDF/UA могат да съдържат logical structure. Наличието и качеството на таговете обаче не гарантира стабилен домейн schema за нашия конкретен доставчик.

## 2. Decision path — първо договори информационната граница

| Достъпно от източника | Подход | Кога | Кога НЕ / компромис |
| --- | --- | --- | --- |
| Структурирани бизнес данни през REST/API, JSON/XML | **Schema-first integration** с versioned contract, validation и authentication | При контролируем supplier и реални домейн полета | Ако PDF е правният оригинал, не го изхвърляй; пази проследима document identity |
| PDF за архив/подпис + структурирани полета | **PDF + JSON/XML pair** с correlation id и интегритет на двете части | Ако получателят иска и човешки документ, и машинни данни | Въвежда reconciliation: кой е authoritative при разминаване? |
| Само PDF, но има надежден logical tagging | PDF extraction с parser и versioned corpus | Когато промяна на producer е невъзможна | Tags често са непълни; нужен correctness evidence върху реален corpus |
| Само raster/image PDF | OCR + confidence, validation и human review на ниска увереност | Last resort; непредвидими layouts | Скъпо, вероятност от silent errors, PII/privacy и обработка на голям обем |
| Няма достъпен надежден contract | **Не автоматизирай без контрол**; negotiate supplier change | Когато грешка е по-скъпа от manual processing | Отлага функционалност, но не произвежда фалшиви бизнес факти |

**Bad:** „PDF е визуално четим, следователно parser ще извлече правилно всички business fields“.  
**Good:** „Изяснявам кой притежава източника на истината, после избирам формат и контролирам грешките“.

## 3. Минимален договор за структурирана интеграция

Например supplier подава:

~~~json
{
  "invoiceId": "INV-2026-001",
  "issuedOn": "2026-11-13",
  "supplierVat": "BG123456789",
  "totalCents": 12500,
  "currency": "EUR",
  "documentSha256": "example-reviewed-digest"
}
~~~

**Това е илюстративен payload, НЕ реална валидирана схема и НЕ данъчен/счетоводен модел.** За production трябва да се договорят типове, задължителност, валута, rounding, signature/document binding, idempotency key, error contract, update/correction semantics и source authenticity.

Ако supplier изпраща и PDF: съпостави `invoiceId`, `documentSha256` и произхода/подписа на документа. Дигест, който двете страни могат да подменят без автентикация, не гарантира trusted origin.

### Предложени проверки за реален проект

- **Producer contract test** валидира actual JSON срещу договорен JSON Schema/OpenAPI; не просто съвпадение на Java record.
- **Consumer contract test** гарантира обработка на стара и нова версия; декодерът отказва непознати стойности, вместо тихо да смени бизнес смисъла.
- **PDF fallback tests:** фиксиран corpus от различни доставчици/layouts; истински expected business data oracle; malformed documents, multi-page, mixed ordering, scans и password-protected PDFs.
- **Reconciliation:** при различни JSON и PDF суми има определен owner/manual review, а не автоматично приемане на първата стойност.
- **Operational evidence:** % успешно обработени документи, false extraction rate, latency/CPU/cost, error queue и човешки преглед.

За **изпълними contract, negative и rollout тестове** използвай [Versioned Order Event Lab](05-PROTOCOL-ADOPTION-AND-COMPATIBILITY.md) и съществуващия [AsyncAPI contract](03-ASYNCAPI-CONTRACT-FIRST.md). Няма parser implementation тук; не твърдим, че сме доказали OCR accuracy.

## 4. Senior decision contract

**Проблем:** получаваме данни във формат, оптимизиран за човешки display, а трябва reliable business-state integration.

**Кога:** преди изграждане на PDF extraction или сложен transformation pipeline.

**Кога НЕ:** ако имаш регулаторна/бизнес причина да обработваш оригинален PDF и доставчикът не предлага структурирана форма — тогава parser е необходим.

**Печалби:** по-малка вероятност от silent mis-extraction, по-ясен contract и ownership, по-евтино развитие.

**Загуби:** нужда от координация със supplier; потенциално два артефакта и reconciliation.

**По-проста алтернатива:** първо supplier JSON/XML contract; ако няма, ограничен PDF parser/verified corpus, не framework от нулата.

**Proof:** [Java25 producer/consumer compatibility tests](src/test/java/bg/hristomanov/education/integrationpatterns/evolution/OrderEventEvolutionTest.java). За PDF-specific extraction correctness се изисква отделен корпус; този урок **не твърди**, че го предоставя.

## Източници

- [Guust Ysebie — Devoxx lecture](https://www.youtube.com/watch?v=VaT7DgmVSms), предоставен SRT (~03–08 мин).
- [PDF Association — PDF 2.0 overview and specifications](https://pdfa.org/resource/pdf-2-0-iso-32000-2/).
- [WTPDF / Tagged PDF semantics](https://pdfa.org/resource/well-tagged-pdf/).
- [Съществуващият Education integration decision guide](01-INTEGRATION-DECISION-GUIDE.md).
