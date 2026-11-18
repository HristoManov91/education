# Post-Quantum Cryptography за Java Backend — P2 практическа лаборатория

> **Devoxx Belgium 2026 · Julian Burr · Java 25 LTS · Spring/Security · P2**  
> [Оригинално видео — Post Quantum Today: How Your Passwords Will Be Safe in a Quantum Future](https://www.youtube.com/watch?v=yUMnuh5U5k4). Използван е предоставеният SRT транскрипт. **Нашият ML-KEM/HKDF/AES-GCM и ML-DSA код е учебно разширение, не код от лекцията.**
>
> **Важно:** демонстрацията не е готов application-level encryption protocol, не заменя TLS/HPKE, не е решение за production ключове/сертификати и не означава, че цялото Java приложение вече е post-quantum защитено.

## 1. Реалният казус: архив с поверителни данни

Имаме Spring Boot/Gateway система, която пренася чувствителен архив (напр. документи с 10-годишна поверителност):

```text
Browser ──TLS──> Reverse proxy / Gateway ──TLS──> Java service ──> encrypted archive
             │                  │
             └── attacker records traffic today
                          ↓
                stores packets for years
                          ↓
         quantum-capable attacker may decrypt
         traffic protected by vulnerable key exchange
```

В момента TLS handshake (договарянето на защитена връзка) може да използва класически ECDHE. Злонамерен наблюдател записва трафика, без да може да го прочете сега. Ако впоследствие се появи достатъчно мощен fault-tolerant quantum computer (квантов компютър с корекция на грешките), някои стари публично-ключови механизми могат да бъдат атакувани ретроспективно.

Това е **Harvest Now, Decrypt Later (HNDL)**: опасността зависи от **колко дълго данните остават чувствителни**, а не от това дали квантова атака е практична днес. При TLS 1.3 с класическо ECDHE компрометираното в бъдеще асиметрично key agreement може да позволи възстановяване на стари session keys от записан handshake, въпреки forward secrecy срещу обичайно последващо компрометиране на server private key.

**Bad, но убедителна идея:** „Ще обновим Java и всичко автоматично ще е quantum-safe.“ Пропуска се кой всъщност терминира TLS (load balancer, CDN, proxy, Gateway), кои peer-ове преговарят хибридна група и дали подписите/сертификатите още използват RSA/ECDSA.

**Цена на грешката:** изтекла поверителност след години; миграция под натиск; счупена interoperability (съвместимост) след неподготвена смяна на алгоритми; невалидни подписи или непроверими архиви.

### Какво ще научим

- Разграничаваме **password hashing**, symmetric encryption (симетрично криптиране), asymmetric key exchange (обмен на ключове) и digital signatures (цифрови подписи).
- Разбираме защо Shor застрашава RSA/ECC, но това **не** е „всички пароли могат да бъдат декриптирани“.
- Изпълняваме **ML-KEM-768 → HKDF-SHA256 → AES-256-GCM** и доказваме failure modes (поведения при грешка).
- Подписваме и валидираме с **ML-DSA-65**, включително изменени данни и чужд публичен ключ.
- Правим migration decision (инженерно решение за миграция) за TLS terminator, Gateway, сертификати, JWT и дългосрочни архиви.

## 2. Ментален модел: кои инструменти за какво са

| Примитив / протокол | Какво решава | Post-quantum извод |
| --- | --- | --- |
| `Argon2id` / `bcrypt` / PBKDF2 | Password hashing (еднопосочно проверяване на парола) | **Не е RSA/ECC**. Не се заменя с ML-KEM; остава нужда от сол, параметри, MFA и rate limiting |
| AES-256-GCM | Шифроване **и автентичност** на payload | Симетричната криптография не е директно разбита от Shor; AES-256 дава голям запас срещу известните квантови заплахи |
| RSA / ECDH / ECDSA / EdDSA | Ключово установяване / подписи, в зависимост от алгоритъма | Достатъчно мощен квантов компютър с Shor е заплаха за тях; не смесвай RSA подпис и ECDH ключов обмен |
| **ML-KEM** (FIPS 203) | Key encapsulation mechanism (механизъм за установяване на общ таен ключ) | Не е `Cipher` за прякото шифроване на произволен документ |
| **ML-DSA** (FIPS 204) | Цифров подпис и проверка | Отделен проблем от поверителността на TLS |
| **SLH-DSA** (FIPS 205) | Друг PQ подпис на hash-based основа | Алтернатива/резервна фамилия; **няма** Java 25 runnable lab тук |
| **X25519MLKEM768** | Хибриден TLS 1.3 key exchange (класически X25519 + ML-KEM) | Защита на **установяването на ключове**, но не подменя автоматично TLS сертификатните подписи |

**Едно правило за запомняне:** ML-KEM осигурява ключов материал; HKDF извежда ключ за конкретна употреба; AES-GCM криптира и проверява данните; ML-DSA подписва. Всеки има различен security contract (гаранции за сигурност).

### Какво вземаме от лекцията и какво проверяваме допълнително

| Време в транскрипта | Идея от Julian Burr | Как я използваме |
| --- | --- | --- |
| 02:18–18:14 | Qubits, superposition, Shor и period finding (намиране на период) | Кратък контекст, **без** собствен квантов алгоритъм |
| 18:14–20:53 | Големи resource и error-correction изисквания; бързо променящи се оценки | **Не** записваме конкретна „дата на криптоапокалипсиса“ като факт |
| 20:53–22:37 | HNDL и NIST standardization | Threat model (модел на заплахата) за дългосрочно чувствителен трафик |
| 23:18–31:53 | Lattices / learning with errors — интуитивна аналогия | Защо стандартизирани, анализирани алгоритми са по-добри от „собствена криптография“ |
| 32:14–35:29 | Доставчиците мигрират; Java 24 ML-KEM | Практически план за Java приложения; статусът на доставчик се проверява отделно |

**Проверка извън лекцията:** NIST FIPS 203/204/205 са стандартизирани през 2024 г.; ML-KEM и ML-DSA са налични като standard Java crypto primitives от Java 24; `KDF`/HKDF става финален API в Java 25 (JEP 510); Java 27 включва хибридния TLS 1.3 `X25519MLKEM768` (JEP 527). Производител и patch release може да променят поддръжката на LTS версии — не извеждай заключение само от `java -version`.

## 3. Bad → Good: как установяваме поверителен ключ

### Bad подход: „ML-KEM е Cipher“ или „ще си напишем hybrid протокол“

Наивен опит: генерираме ML-KEM ключова двойка и очакваме `Cipher.getInstance("ML-KEM")` да криптира файл. Това е грешен abstraction (ниво на отговорност): **KEM създава еднакъв shared secret (споделена тайна) от двете страни, а не шифрира application payload**. Другата опасна идея е сами да комбинираме два KEM-а или ECDH и ML-KEM с конкатенация; начинът за съчетаване на ключов материал трябва да следва анализиран **протокол** (напр. стандартизиран TLS hybrid), не домашна рецепта.

### Good учебен поток

```text
Recipient: ML-KEM-768 KeyPairGenerator
                  │ public key
                  ▼
Sender: KEM.newEncapsulator → [encapsulation bytes] + [shared SecretKey]
                                                    │
                                            HKDF-SHA256 + salt
                                                    │
                                                 AES-256-GCM
                                                    │
                          [kemCiphertext, salt, nonce, encryptedPayload]
                                                    │
Recipient: KEM.newDecapsulator(private) → same shared SecretKey
                                                    │
                                            same HKDF/AES key
                                                    │
                                        GCM tag + AAD verification
                                                    │
                                             plaintext OR FAIL
```

**Source:** [`QuantumSafeEnvelope.java`](src/main/java/bg/hristomanov/education/security/pqc/QuantumSafeEnvelope.java). Избираме `NamedParameterSpec.ML_KEM_768`, `KEM.getInstance("ML-KEM")`, финален `KDF.getInstance("HKDF-SHA256")` и `AES/GCM/NoPadding`.

Защитата срещу подмяна не идва само от KEM. При ML-KEM **implicit rejection** (при невалиден ciphertext с правилна дължина резултатът може да е друг таен ключ, без exception), затова AES-GCM authentication tag (таг за автентичност) е решаващ за окончателното приемане на съобщението. Контекстът `associatedData` е AAD: данните „tenant=42;record=abc“ не се шифроват, но са автентикирани; не можем да прехвърлим ciphertext към друг tenant и да го приемем успешно.

### README → код (директни връзки)

| Идея | Изпълним код | Доказателство |
| --- | --- | --- |
| ML-KEM → HKDF → AEAD, защитен envelope | [`QuantumSafeEnvelope.java`](src/main/java/bg/hristomanov/education/security/pqc/QuantumSafeEnvelope.java) | [`QuantumSafeEnvelopeTest.java`](src/test/java/bg/hristomanov/education/security/pqc/QuantumSafeEnvelopeTest.java): round-trip, wrong recipient, wrong tenant, tamper, truncation и defensive copies |
| ML-DSA sign/verify | [`PostQuantumSignatures.java`](src/main/java/bg/hristomanov/education/security/pqc/PostQuantumSignatures.java) | [`PostQuantumSignaturesTest.java`](src/test/java/bg/hristomanov/education/security/pqc/PostQuantumSignaturesTest.java): валидно, променено съдържание, чужд подписващ |
| TLS 1.3, Gateway, migration | [`TLS-AND-MIGRATION.md`](TLS-AND-MIGRATION.md) — **архитектурна бележка / manual verification**, не Java 25 TLS integration test | Реална JSSE handshake диагностика на Java 27+ (или конкретна backport версия), отделен compatibility checklist |
| Проверка на знанията | [`EXERCISES.md`](EXERCISES.md) | Микрозадачи, negative test изменения и code-review въпроси |

### Стартиране и доказателство

Нужно е **реално Java 25 JDK**, Maven 3.9+ и доставчик с ML-KEM/ML-DSA/HKDF. Не се изискват Spring context, Docker, мрежа, TLS сървър, външен identity provider или preview flags.

От корена на `education`:

```bash
mvn -pl spring/security/post-quantum-cryptography -am test
```

От директорията на модула:

```bash
mvn test
```

Тестовете използват локалния JCA/JSSE provider, нови ключове и nonces във всяко изпълнение. **Няма фиксирани ciphertext/signature bytes**: не правим тестовете зависими от случайността. С `-Dtest=QuantumSafeEnvelopeTest` или `-Dtest=PostQuantumSignaturesTest` може да се изпълнява само една част.

### Какво точно доказват тестовете

- **Положителен тест:** recipient decapsulates и GCM decrypt връща оригиналния plaintext.
- **Wrong recipient:** чужд `PrivateKey` не може да доведе до успешно AEAD decrypt.
- **Wrong AAD/tenant:** дешифрирането отказва при подменен бизнес контекст.
- **Tampered payload / KEM ciphertext:** неподходящ authentication key или GCM tag води до failure; **не** изискваме ML-KEM да хвърли exception при всеки tamper.
- **Truncated encapsulation:** невалидна дължина се отхвърля.
- **ML-DSA:** коректен подпис се приема; променен текст или грешен public key се отхвърля.

Не доказваме доказателства за FIPS сертификация на конкретния provider, network interoperability, PQ TLS negotiated group или security на цял wire protocol. Това изисква отделно тестване и security review.

## 4. ML-DSA: различен проблем от ML-KEM

Схемата е: `KeyPairGenerator.getInstance("ML-DSA")` със `NamedParameterSpec.ML_DSA_65` → `Signature.getInstance("ML-DSA")` → `initSign()/update()/sign()`; отсрещната страна използва `initVerify()/update()/verify()`. Виж [`PostQuantumSignatures.java`](src/main/java/bg/hristomanov/education/security/pqc/PostQuantumSignatures.java) и [отрицателните тестове](src/test/java/bg/hristomanov/education/security/pqc/PostQuantumSignaturesTest.java).

**Нюанс:** дори подписът да е ML-DSA, той сам не осигурява confidentiality (поверителност). И обратно: хибридният TLS key exchange не означава, че JWT, certificate chain или подписаните архиви са с post-quantum signatures. За реален протокол са нужни ясни domain separation (контекст на подписа), canonical payload format (еднозначно представяне), key identity, key rotation и verifier interoperability.

## 5. Decision cards: кога, кога НЕ и каква е цената

| Решение | Кога е подходящо | Кога НЕ | Печалби | Загуби / алтернативи | Доказателство |
| --- | --- | --- | --- | --- | --- |
| **ML-KEM + HKDF + AEAD** | Учебна демонстрация на KEM; production — само в утвърден протокол с review и KMS | „Ще си пишем secure messaging протокол“ | Quantum-resistant key establishment + authenticated payload | Повече bytes/CPU, key management, protocol design; **избери готов TLS 1.3/HPKE** | [KEM/GCM JUnit](src/test/java/bg/hristomanov/education/security/pqc/QuantumSafeEnvelopeTest.java) |
| **ML-DSA** | Нови подписи с дългосрочна валидност и договорена interop поддръжка | Принудителна едностранна смяна на JWT/cert без verifier support | PQ подписен primitive | Размери, съвместимост, lifecycle; алтернатива: настоящ PKI + план за миграция | [ML-DSA JUnit](src/test/java/bg/hristomanov/education/security/pqc/PostQuantumSignaturesTest.java) |
| **Hybrid TLS 1.3** | Дългосрочно чувствителен трафик и TLS peer-ове, които могат да преговарят групата | Blind `jdk.tls.namedGroups` на неподдържана версия; непроверен proxy/CDN | HNDL resilience на key exchange | Повече handshake bytes, compatibility/rollback; алтернатива: staged rollout през managed provider | [TLS/migration](TLS-AND-MIGRATION.md) (**manual**, без fake test) |
| **Password hashing** | За съхранение на пароли: Argon2id/bcrypt с подходящи параметри, MFA | Никога не ги заменяй с ML-KEM/ML-DSA | Запазваме правилния trust boundary | Не решава phishing и слабите пароли; алтернатива: passkeys/IdP | [Spring Security course](../bootiful-security/README.md) |

### Сравнение на цена / correctness

- **Security:** използваме утвърдени Java provider implementations, а не собствен ML-KEM; остават trust към provider, entropy source и реалните endpoints.
- **Ресурси:** ML-KEM, ML-DSA и хибридният TLS имат различен размер на ключове, handshake и подписи; измервай според реалния трафик, не приемай latency „нула“.
- **Ownership / lifecycle:** получателят притежава private key, подписващият има свой signing key, TLS терминиращият компонент носи отговорността за negotiated protocol; ключовете имат ротация/отнемане/backup.
- **Observability:** проверявай negotiated group, cert chain, failure rate и fallback, но никога не логвай private keys, shared secrets, plaintext или sensitive AAD.
- **Testing:** unit tests доказват криптографския flow; integration тестовете трябва да проверят реалния Gateway/CDN/client/server configuration.

## 6. Production: какво лабораторията не покрива

Това **не** е безопасен production wire format. Липсват versioned serialization, key IDs, persistent key lifecycle, ротация/отнемане, Replay protection (защита от повторно изпращане), message framing, streaming, key custody (HSM/KMS), handling на големи документи, nonce policy across deployments, retry/idempotency и independent cryptographic review. Ако трябва application-level encryption, започни от стандартизиран protocol (напр. HPKE с подходяща suite и library) и security team, а не от копиране на този клас.

TLS key exchange не замества подписите на сертификатите и удостоверяването на peer identity; подписан JWT с RSA не става автоматично quantum-safe, ако мрежата му е през hybrid TLS. Шифрирането на диск с AES-256 е различен threat model от записан TLS handshake.

Виж [`TLS-AND-MIGRATION.md`](TLS-AND-MIGRATION.md) за реалния backend избор и [`EXERCISES.md`](EXERCISES.md) за самопроверка.

## 7. Връщане към казуса: какво решихме

За дългосрочния архив вече **не** си казваме „Java 27 значи quantum-safe“. Правим crypto inventory (инвентар на алгоритми/криптографски зависимости), установяваме реалната TLS граница, тестваме договорената група и мигрираме по риск. Ако имаме app-level нужда от криптиране, знаем разликата между KEM, KDF, AEAD и подписи и можем да валидираме с отрицателни тестове.

**Три правила:**

1. Ако данните ще останат поверителни години, първо оценявай HNDL и действителния TLS terminator.
2. Ако шифрираш payload, KEM не замества AEAD; използвай утвърден протокол и проверявай tamper/identity/context.
3. Ако мигрираш подписи, провери verifiers, certificates, JWT/PKI и key lifecycle **отделно** от TLS.

**Code review checklist:** кой приема TLS; кой private key е нужен; кой tenant/AAD е свързан; какво става при wrong key/tamper; коя Java/provider версия е реална; negotiated group доказана ли е; какъв е rollback; логваме ли тайни; къде е независимият integration/security тест?

## Оригинални източници и проверка

- **Лекцията:** [Julian Burr — Post Quantum Today (Devoxx)](https://www.youtube.com/watch?v=yUMnuh5U5k4); предоставен `Post_Quantum_Today_Julian_Burr_yUMnuh5U5k4.srt` (локално от потребителя; не е публикуван в repo).
- **NIST:** [FIPS 203 — ML-KEM](https://csrc.nist.gov/pubs/fips/203/final), [FIPS 204 — ML-DSA](https://csrc.nist.gov/pubs/fips/204/final), [FIPS 205 — SLH-DSA](https://csrc.nist.gov/pubs/fips/205/final).
- **OpenJDK:** [JEP 496 — ML-KEM](https://openjdk.org/jeps/496), [JEP 497 — ML-DSA](https://openjdk.org/jeps/497), [JEP 510 — KDF](https://openjdk.org/jeps/510), [JEP 527 — Hybrid TLS 1.3](https://openjdk.org/jeps/527).
- **JDK 25 API:** [KEM](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KEM.html), [KDF](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/javax/crypto/KDF.html), [Java Security Standard Algorithm Names](https://docs.oracle.com/en/java/javase/25/docs/specs/security/standard-names.html).
- **IETF:** [RFC 10024 — PQ/Traditional Hybrid for TLS 1.3](https://www.rfc-editor.org/rfc/rfc10024.html); [RFC 5869 — HKDF](https://www.rfc-editor.org/rfc/rfc5869.html).
- **Oracle/Inside Java:** [JDK 27 Security Enhancements](https://inside.java/2026/09/16/jdk27-security-enhancements/), [JDK 27 release notes — negotiated group debug](https://www.oracle.com/java/technologies/javase/27-relnote-issues.html).
- Свързано: [Java Evolution Guide `6](../../../java/JAVA-EVOLUTION-GUIDE.md), [Bootiful Security](../bootiful-security/README.md), [STRIDE Threat Modeling](../threat-modeling/README.md).

**Граница на източниците:** транскриптът е обзор, не нормативна криптографска спецификация. Примерите, API версиите, negative tests и production checklist са **нашата проверена инженерна надстройка** с официални източници. Прогнозите за qubit counts и сроковете на доставчиците от лекцията не се приемат за гаранции.
