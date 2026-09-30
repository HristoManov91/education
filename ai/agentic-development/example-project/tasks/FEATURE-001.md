# FEATURE-001 — Export report as CSV

Това е пример за **task contract**, не permanent instruction.

## Goal

Позволи на authenticated user да export-не текущия report като CSV.

## Verified baseline

- Report read model вече съществува.
- Authorization вече ограничава report-а до current user.
- Няма CSV endpoint.

## Scope

- Добави CSV export endpoint върху съществуващия report read model.
- Reuse existing authorization and filtering.
- UTF-8 output with header row.

## Acceptance criteria

1. Unauthorized caller не получава чужд report.
2. Export съдържа същите rows/filters като screen report-а.
3. Filename е deterministic и безопасен.
4. Empty report връща CSV само с headers.
5. Automated tests покриват authorization, filtered data и empty result.

## Constraints

- Не създавай втори report query/model само за CSV.
- Не променяй business filtering semantics.

## Out of scope

- XLSX.
- Scheduled email exports.
- Background generation for very large reports.

## Open decisions

Няма.

След merge този файл може да остане като history, но неговите feature-specific decisions не трябва да се копират в root `AGENTS.md`.