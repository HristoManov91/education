# Advanced Security — FHE vs TEE vs MPC vs обикновено криптиране

> **Decision guide, не runnable TEE/MPC lab.** Реалната криптографска демонстрация е [BFV OpenFHE](openfhe-lab/README.md). Преди избор винаги дефинирай **кой точно не трябва да вижда plaintext-а**.

## 1. Сравнение

| Подход | Каква защита дава | Кога използваме | Кога НЕ / trade-off | Как доказваме |
| --- | --- | --- | --- | --- |
| **TLS + Oracle TDE** | Transit confidentiality + encrypted storage; application/DB все пак обработват plaintext | Стандартен trusted backend | Не скрива стойностите от самия compute process | TLS config, DB key access policy, audit |
| **Application field-level encryption** | Колони/документи шифрирани до application boundary | Чувствителни полета, KMS и отделни ключове | Сървърът с ключа вижда plaintext в heap; трудно търсене/ротация | AEAD и KMS тестове, role review |
| **FHE** | Worker изпълнява ограничен circuit върху ciphertext без secret key | Сериозна нужда от encrypted outsourcing, предвидима математика | Високи CPU/RAM/bytes, noise/modulus correctness, сложни compares | [истински BFV oracle test](openfhe-lab/test_real_openfhe.py), measurements |
| **TEE / confidential computing** | Plaintext само в hardware-attested isolated environment според hardware/firmware trust | Сложни алгоритми, приемлив vendor/hardware trust | Side channels, attestation, key release и rollback/patching | Remote attestation + measured binaries |
| **MPC** | Няколко страни изчисляват над private inputs при определен collusion model | Различни собственици на данни, които не искат да ги разменят | Коммуникационни rounds, coordination, dishonesty/collusion | Threat model + audited MPC implementation |
| **Client-side computation** | Няма нужда да изпращаме входовете към service | Клиентът има всички необходими данни | Не работи, ако трябва merge на няколко независими private datasets | Simple local calculation test |

**Не бъркай privacy с integrity:** compute server без secret key може да подмени ciphertext, да върне невалидно изчисление или да отказва заявки; FHE не доказва честността на input-а. Metadata като брой полета, дължини, timing и честота на заявки могат да изтекат. Компрометиран клиент остава компрометиран, независимо от FHE.

## 2. Decision flow

```text
Трябва ли backend да вижда входните стойности?
  Да: TLS / TDE / least privilege / KMS / audited APIs.
  Не:
    Може ли всичко да се сметне локално? -> Client-side aggregation.
    Приемаме ли hardware trust + remote attestation? -> TEE candidate.
    Има ли няколко независими собственици на данни? -> MPC candidate.
    Има ли малък, фиксиран числов circuit? -> BFV/CKKS FHE candidate.
    Динамично filtering, sorting, сложни branches, tight latency? -> Не избирай FHE без POC.
```

## 3. За Java/Spring + Oracle, кога има практическа полза?

- **Вътрешен Spring Boot CRUD + Oracle 19c:** обичайно TLS/TDE, authorization, audit, KMS и минимизиране на достъпа са по-прости и правилни. FHE не е заместител на JPA, SQL или Oracle TDE.
- **Външен финансов/статистически доставчик:** ако по договор/архитектура compute party **не може** да вижда входните числа, ограничени агрегации са кандидат за POC. Измери реалния latency/serialization/memory budget.
- **Real-time GPS и географски справки:** разнообразни SQL predicates, closest-point searches и сортиране не са евтини BFV operations. Без измерен prototype ползвай класически access control и trusted computation.

## 4. Decision contract

**Проблем:** скрити данни при аутсорснато изчисление.

**Кога:** ограничен numeric circuit, реална бизнес необходимост и собственик на криптопараметри/ключове.

**Кога НЕ:** стандартен CRUD, строг latency budget, липса на криптографски експерт и реални tests.

**Печалба:** server без access до decryption key. **Цена:** CPU, bytes, RAM, complexity, noise, version/ABI issues, risk от silent incorrect decrypt.

**По-просто:** trusted service или локално изчисление, а понякога TEE/MPC според trust model.

**Proof:** [Java score oracle](src/test/java/bg/hristomanov/education/privacy/RiskScoreCalculatorTest.java), [OpenFHE real run](openfhe-lab/test_real_openfhe.py), [protocol negatives](openfhe-lab/test_protocol.py) и реален threat model за deployment.

Контекст: [STRIDE](../threat-modeling/README.md), [CRA](../../../docs/security/cra/README.md), [Java native interop](../../../java/jvm/native-interoperability/README.md).
