# Task 5 Report: Add Event Port Guardrails

## Outcome
Completed.

## Change Summary
- Added `application_must_not_depend_on_broker_transport_types` to block application-layer dependencies on `org.springframework.kafka..`, `org.apache.kafka..`, and `..common.kafka..`.
- Added `application_ports_must_not_expose_transport_vocabulary` to flag application interfaces that expose broker or infrastructure vocabulary in names, method names, or parameter types.
- Added the helper `notExposeTransportVocabularyInApplicationPort()` in `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`.

## Verification
- `cd backend && mvn test -pl :community-app -am -Dtest=DddLayeringArchTest`
- `rg "KafkaDispatchPort|send\\(String topic|KafkaTemplate" backend/community-app/src/main/java/com/nowcoder/community/*/application`

## Results
- Focused ArchUnit test suite passed: 51 tests, 0 failures, 0 errors, 0 skipped.
- Search returned no matches. `rg` exited with status 1, which is expected for an empty result set.

## Self-Review
- The change is limited to the requested arch test file and does not touch production code.
- The guardrails are aligned with the brief and catch both transport dependencies and transport vocabulary leakage from application ports.

## Review Fix Addendum
- Extended `notExposeTransportVocabularyInApplicationPort()` to inspect method return types and to conservatively flag broker-shaped `send`/`publish` signatures when the first parameter is `java.lang.String`.
- This closes the gap called out in review for `send(String topic, ...)`-style API leakage.
- Re-ran the focused verification after the fix:
  - `cd backend && mvn test -pl :community-app -am -Dtest=DddLayeringArchTest`
  - `rg "KafkaDispatchPort|send\\(String topic|KafkaTemplate" backend/community-app/src/main/java/com/nowcoder/community/*/application`
- Result: Maven passed with 51 tests, 0 failures, 0 errors, 0 skipped; `rg` returned no matches.
