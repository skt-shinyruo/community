Status: DONE

What changed:
- Renamed `ContentEntityQueryService` to `ContentEntityQueryApiAdapter` and `PostScanService` to `PostScanQueryApiAdapter`.
- Updated the adapter constructors and internal self-reference in `PostScanQueryApiAdapter`.
- Renamed the matching adapter tests to the new class names.
- Added an ArchUnit rule in `InfraBoundaryArchTest` that requires owner `api.query` / `api.action` implementations under `infrastructure.api` to end with `ApiAdapter`.

RED command/output summary and why expected:
- Command: `cd backend && mvn test -pl :community-app -am -Dtest=InfraBoundaryArchTest`
- Result: failed as expected with 2 violations.
- Failure output named the two existing classes:
  - `com.nowcoder.community.content.infrastructure.api.ContentEntityQueryService`
  - `com.nowcoder.community.content.infrastructure.api.PostScanService`
- This was expected because the new naming rule was added before the rename, so the current `*Service` classes had to fail.

GREEN command/output summary:
- Command: `cd backend && mvn test -pl :community-app -am -Dtest=InfraBoundaryArchTest`
- Result: passed.
- Summary: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

`rg` result after rename:
- Command: `rg -n "ContentEntityQueryService|PostScanService" backend/community-app/src`
- Result: no matches.

Files changed:
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryApiAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/api/PostScanQueryApiAdapter.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/ContentEntityQueryApiAdapterTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/api/PostScanQueryApiAdapterTest.java`

Self-review findings/concerns:
- The naming rule is intentionally narrow: it only checks classes in `..infrastructure.api..` that implement owner `api.query` or `api.action` interfaces.
- No behavior changed beyond renaming and wiring updates; the focused ArchUnit test passed after the rename.
- The repository-wide search confirms the old concrete names are gone from `backend/community-app/src`.

Follow-up review fix:
- Restricted `infrastructure_owner_api_implementations_should_be_named_adapters` to concrete classes only by returning early for interfaces and abstract classes.
- Kept the localized `..infrastructure.api..` scope and the existing behavior for concrete owner API implementations.

Follow-up verification:
- PASS: `cd backend && mvn test -pl :community-app -am -Dtest=InfraBoundaryArchTest`
