# Поддържане на AI знанията

## Входяща лекция / статия / release

1. Запиши оригиналния URL, автор, дата (ако е известна), дата на проверка и вид evidence: оригинален текст, предоставен transcript, документация, експеримент или само публично резюме. Ако няма transcript, не измисляй timestamps или твърдения от видеото.
2. Сравни с [AI index](README.md), agentic-development, testing, Spring AI, security и System Design. Маркирай ново, допълнение, дублирано или извън scope. Не създавай паралелна глава за същия проблем.
3. Раздели изрично: какво казва източникът; какво потвърждава официалната документация; какво е наша интерпретация; какво доказва lab; какво остава непроверено.
4. Прилагай [общия учебен стандарт](../docs/LEARNING-MODULE-TEMPLATE.md): реален казус, bad/good, проблем, кога да/не, печалби/загуби, алтернатива, тест/доказателство, exit checklist. Организационна техника може да е non-runnable note с конкретен workshop/измерване; не измисляй Java за нея.
5. Добави реални negative tests и независими oracles при промяна на поведение. За AI output: проверявай citations/grounding, regression/eval data, adversarial inputs, security boundary и cost. Не използвай LLM за единствен съдия на собствените му резултати. Offline deterministic tests са различни от live model eval.
6. Промени [регистъра](SOURCE-REGISTER.md) и индексите в същия PR. Изпълни приложимите проверки и опиши точно версия, команда, резултат/блокер. PR не означава проверена компетентност.
7. При бъдеща актуализация: провери breaking changes, model/API/SDK versions, deprecated подходи, качество, разход и security. Датирай volatile claims. При остаряло решение запази причината/историята и посочи successor, вместо да презаписваш историята.

## Дълготрайно срещу променливо

- Дълготрайно: context quality, failure modes, eval design, permissions, human accountability, domain invariants, feedback loops, engineering economics.
- Променливо: model rankings, token pricing, rate limits, feature flags, vendor APIs, preview features. Датирай и проверявай при употреба; не вписвай като универсална препоръка.
- Изходни данни: не качвай служебен код, записи от интервю, production PII, ключове или пълни чужди transcripts в публичния repository. Ползвай синтетични примери и кратки преразкази; пази одобрението и provenance при реален workplace анализ.

## Review checklist

- [ ] Източникът е достъпен и преразказът е верен на него.
- [ ] Няма дублиране; относителните линкове работят.
- [ ] Изводите не са само vendor marketing; няма измислени метрики.
- [ ] Решението има критерий за успех и контрапример.
- [ ] Тестовете и ограниченията са отчетени; няма изкуствено завишен статус.
- [ ] Source register и roadmap са актуални.
