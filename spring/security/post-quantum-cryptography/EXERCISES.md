# P2 — Упражнения и active recall: Post-Quantum Cryptography

> Не са доказателство за усвоени знания, докато не бъдат решени и проверени. 20–30 мин на сесия. [Основен урок](README.md) → [ML-KEM/AES тест](src/test/java/bg/hristomanov/education/security/pqc/QuantumSafeEnvelopeTest.java) → [ML-DSA тест](src/test/java/bg/hristomanov/education/security/pqc/PostQuantumSignaturesTest.java) → [TLS migration](TLS-AND-MIGRATION.md).

## Минисесия A: разграничи primitive-ите

1. Защо `Argon2id`/`bcrypt` не се заменят с `ML-KEM`? Каква е разликата между password hashing и публично-ключово key agreement?
2. Как Shor се отнася до RSA и ECC, а Grover — до symmetric cryptography? Защо „квантов компютър разбива AES“ е неправилно категорично твърдение?
3. Какъв точно е механизмът на HNDL при архивиран TLS 1.3 трафик с класическо ECDHE и кое друго условие трябва да е изпълнено, за да съществува реалният риск?

## Минисесия B: изпълними negative tests

Изпълни `mvn -pl spring/security/post-quantum-cryptography -am test` от root.

4. В [`QuantumSafeEnvelopeTest.java`](src/test/java/bg/hristomanov/education/security/pqc/QuantumSafeEnvelopeTest.java) опитай да запазиш оригиналния recipient и ciphertext, но да смениш `associatedData` от `tenant=42` на `tenant=43`. Обясни защо няма успешен GCM decrypt.
5. Подмени **един** byte в ML-KEM encapsulation със същата дължина и определи кое е реалното очакване: exception от KEM или различен secret + GCM reject? Защо test assertion е `GeneralSecurityException`, а не безусловно `DecapsulateException`?
6. Подмени документа след `PostQuantumSignatures.sign` и тествай `verify`. След това подмени signer public key. Кое доказва всеки тест и кое не доказва за JWT/PKI?

## Минисесия C: Senior migration decision

7. Имаш Browser → CDN → Spring Cloud Gateway (Java 27) → Spring Boot (Java 25). Дългосрочно чувствителен payload, при CDN→Gateway се преговаря hybrid, а browser→CDN е classic. Твърдението „трафикът е PQ защитен“ вярно ли е? Дай security boundary (граница на доверие), измерване и риск.
8. В canary Java 27 client срещу legacy server TLS handshake е успешен. Показва ли това `X25519MLKEM768`? Как би доказал negotiated group и какво става, ако форсираш `hybrid-only`?
9. Целта е да защитиш и 15-годишна автентичност на подписан документ. Защо hybrid TLS няма да мигрира автоматично RSA/ECDSA document signing? Изброй verifier interoperability, signed payload encoding, key IDs и key rotation.

## Checkpoint / очаквани артефакти

- Положителни и отрицателни JUnit tests на Java 25 — **не** само демонстрация на `KeyPairGenerator`.
- Кратък crypto inventory на въображаеми 3 connection hops.
- Decision table: кога да използваме managed TLS/HPKE вместо application crypto.
- Конкретен rollback и success signal за hybrid TLS canary.

**Не записвай „усвоено“ в progress, преди реални отговори, review и проверка на тестовете.** 
