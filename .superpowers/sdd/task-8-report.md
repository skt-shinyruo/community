What changed
- Added a new `## Application Service Collaboration` section to `docs/handbook/architecture.md`.
- Documented when same-domain `ApplicationService -> ApplicationService` collaboration is allowed, how to name explicit process managers, and why domain-named facade services must not fan out to multiple same-domain application services.
- Added the transactional self-invocation note from the task brief.

Search results and doc decision
- Ran `rg "KafkaDispatchPort|Kafka-shaped|topic.*application|application.*topic" docs/handbook docs/superpowers/specs`.
- Matches were found only in `docs/superpowers/specs/2026-07-05-community-app-architecture-boundary-hardening-design.md` and `docs/superpowers/specs/2026-06-09-kafka-event-backbone-design.md`, plus one handbook index entry in `docs/handbook/core-logic-index.md`.
- `docs/handbook/system-design.md` did not match the requested Kafka-shaped application-port wording, so it did not need an edit.

Verification output summary
- `git diff --check 35d9a780..HEAD` passed with no output.
- `cd backend && mvn test -pl :community-app -am -Dtest=DddLayeringArchTest` passed.
- Test summary: `Tests run: 51, Failures: 0, Errors: 0, Skipped: 0`.

Files changed
- `docs/handbook/architecture.md`

Self-review / concerns
- Scope stayed documentation-only as requested.
- I left approved specs untouched because the search results were in spec files, not the handbook text that needed synchronization.
- No concerns beyond keeping the handbook aligned with the approved spec terminology if that spec is later revised.
