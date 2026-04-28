# Search And Analytics DDD Tactical Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `search` and `analytics` into strict DDD Tactical Layering while preserving search reindexing, content outbox indexing, and analytics ingestion behavior.

**Architecture:** Search controllers and outbox handlers call search application services only; Elasticsearch/in-memory repositories and index management live under infrastructure. Analytics controllers and request-capture filters call analytics application services only; Redis repositories live under infrastructure, and foreign analytics ingestion is exposed through a `service.*ApiAdapter`.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data Elasticsearch, Redis, Spring Web filters, Outbox handlers, ArchUnit, JUnit 5, Mockito, Maven.

---

## Scope

This plan covers these remaining strict-DDD slices:

- `search`
- `analytics`

It also verifies that `message` has no production legacy owner package left in `community-app`; the existing message tests remain contract/removal tests only.

---

## Target Package Shape

```text
com.nowcoder.community.search
  controller
    dto
  application
    command
    result
  domain
    model
    repository
    service
  infrastructure
    event
    persistence
      dataobject
  api
    action
    model
  service              # only Search*ApiAdapter classes after migration

com.nowcoder.community.analytics
  controller
    dto
  application
    command
    result
  domain
    model
    repository
    service
  infrastructure
    persistence
    web
  api
    action
  service              # only Analytics*ApiAdapter classes after migration
```

---

## File Structure Map

### Search Controller And DTOs

- Move:
  - `search/dto/SearchPostItem.java` -> `search/controller/dto/SearchPostItemResponse.java`
  - `search/dto/SearchReindexResponse.java` -> `search/controller/dto/SearchReindexResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/controller/SearchController.java`

### Search Application

- Move: `search/service/SearchApplicationService.java` -> `search/application/SearchApplicationService.java`
- Move/rename: `search/service/SearchAdminService.java` -> `search/application/SearchAdminApplicationService.java`
- Move/rename: `search/service/SearchReindexExecutionService.java` -> `search/application/SearchReindexApplicationService.java`
- Move/rename: `search/service/ReindexJobService.java` -> `search/application/ReindexJobApplicationService.java`
- Move: `search/service/PostSearchPayloadMapper.java` -> `search/application/PostSearchPayloadMapper.java`
- Create:
  - `search/application/command/SearchPostsCommand.java`
  - `search/application/command/ReindexPostsCommand.java`
  - `search/application/result/SearchPostResult.java`
  - `search/application/result/SearchReindexResult.java`
  - `search/application/result/ReindexJobResult.java`

### Search Domain And Infrastructure

- Move:
  - `search/repo/PostSearchRepository.java` -> `search/domain/repository/PostSearchRepository.java`
  - `search/repo/InMemoryPostSearchRepository.java` -> `search/infrastructure/persistence/InMemoryPostSearchRepository.java`
  - `search/repo/ElasticsearchPostSearchRepository.java` -> `search/infrastructure/persistence/ElasticsearchPostSearchRepository.java`
  - `search/repo/EsPostDocument.java` -> `search/infrastructure/persistence/dataobject/EsPostDocument.java`
  - `search/repo/PostIndexManager.java` -> `search/infrastructure/persistence/PostIndexManager.java`
  - `search/repo/PostIndexInitializer.java` -> `search/infrastructure/persistence/PostIndexInitializer.java`
  - `search/repo/KeywordHighlightSupport.java` -> `search/domain/service/KeywordHighlightSupport.java`
- Create:
  - `search/domain/model/PostSearchDocument.java`
  - `search/domain/model/PostSearchQuery.java`
  - `search/domain/service/PostSearchDomainService.java`
  - `search/domain/service/SearchReindexDomainService.java`
- Move event adapters:
  - `search/event/PostOutboxEnqueuer.java` -> `search/infrastructure/event/PostOutboxEnqueuer.java`
  - `search/event/PostOutboxHandler.java` -> `search/infrastructure/event/PostOutboxHandler.java`
- Delete after replacement:
  - `search/service/PostSearchService.java`
  - `search/service/SearchApplicationService.java`
  - `search/service/SearchAdminService.java`
  - `search/service/SearchReindexExecutionService.java`
  - `search/service/ReindexJobService.java`
  - `search/repo/*`
  - `search/event/*`

### Search API Adapter

- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/service/SearchReindexActionApiAdapter.java`

### Analytics Controller And DTOs

- Move: `analytics/dto/RangeQuery.java` -> `analytics/controller/dto/RangeQuery.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/controller/AnalyticsController.java`

### Analytics Application

- Move: `analytics/service/AnalyticsApplicationService.java` -> `analytics/application/AnalyticsApplicationService.java`
- Move/rename: `analytics/ingest/AnalyticsIngestService.java` -> `analytics/application/AnalyticsIngestApplicationService.java`
- Create:
  - `analytics/application/command/RecordRequestCommand.java`
  - `analytics/application/command/RecordLoginSuccessCommand.java`
  - `analytics/application/command/AnalyticsRangeQuery.java`
  - `analytics/application/result/AnalyticsCountResult.java`

### Analytics Domain And Infrastructure

- Move:
  - `analytics/repo/AnalyticsRepository.java` -> `analytics/domain/repository/AnalyticsRepository.java`
  - `analytics/repo/AnalyticsUserOrdinalRepository.java` -> `analytics/domain/repository/AnalyticsUserOrdinalRepository.java`
  - `analytics/repo/RedisAnalyticsRepository.java` -> `analytics/infrastructure/persistence/RedisAnalyticsRepository.java`
  - `analytics/repo/RedisAnalyticsUserOrdinalRepository.java` -> `analytics/infrastructure/persistence/RedisAnalyticsUserOrdinalRepository.java`
- Move web ingestion helpers:
  - `analytics/ingest/AnalyticsIngestProperties.java` -> `analytics/infrastructure/web/AnalyticsIngestProperties.java`
  - `analytics/ingest/AnalyticsRequestCaptureFilter.java` -> `analytics/infrastructure/web/AnalyticsRequestCaptureFilter.java`
  - `analytics/ingest/AnalyticsRequestClassifier.java` -> `analytics/infrastructure/web/AnalyticsRequestClassifier.java`
  - `analytics/ingest/AnalyticsPrincipalResolver.java` -> `analytics/infrastructure/web/AnalyticsPrincipalResolver.java`
- Create:
  - `analytics/domain/model/AnalyticsRange.java`
  - `analytics/domain/model/AnalyticsRequestEvent.java`
  - `analytics/domain/service/AnalyticsDomainService.java`
  - `analytics/domain/service/AnalyticsIngestDomainService.java`
- Delete after replacement:
  - `analytics/service/AnalyticsService.java`
  - `analytics/service/AnalyticsApplicationService.java`
  - `analytics/ingest/AnalyticsIngestService.java`
  - `analytics/repo/*`

### Analytics API Adapter

- Create: `backend/community-app/src/main/java/com/nowcoder/community/analytics/service/AnalyticsIngestActionApiAdapter.java`

### Guardrails And Docs

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- Modify docs:
  - `docs/ARCHITECTURE.md`
  - `docs/SYSTEM_DESIGN.md`
  - `docs/CORE_LOGIC.md`
  - `docs/business-logic/search-projection-reindex-flow.md`
  - `docs/business-logic/analytics-uv-dau-flow.md`
  - `docs/business-logic/analytics-ingest-flow.md`

---

## Task 1: Add Search And Analytics RED Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`

- [x] **Step 1: Add search and analytics retirement rules**

Add these rules to `DddLayeringArchTest`:

```java
@ArchTest
static final ArchRule search_service_package_must_only_publish_foreign_api_adapters =
        classes()
                .that().resideInAnyPackage("..search.service..")
                .should().haveSimpleNameEndingWith("ApiAdapter")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_search_repo_event_packages_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..search.repo..", "..search.event..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule search_controller_and_event_adapters_must_not_depend_on_legacy_surfaces =
        noClasses()
                .that().resideInAnyPackage("..search.controller..", "..search.infrastructure.event..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..search.service..",
                        "..search.repo..",
                        "..search.event..",
                        "..search.infrastructure.persistence..",
                        "..search.domain.."
                )
                .allowEmptyShould(true);

@ArchTest
static final ArchRule analytics_service_package_must_only_publish_foreign_api_adapters =
        classes()
                .that().resideInAnyPackage("..analytics.service..")
                .should().haveSimpleNameEndingWith("ApiAdapter")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_analytics_repo_ingest_packages_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..analytics.repo..", "..analytics.ingest..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule analytics_controller_and_filters_must_call_application_only =
        noClasses()
                .that().resideInAnyPackage("..analytics.controller..", "..analytics.infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..analytics.service..",
                        "..analytics.repo..",
                        "..analytics.ingest..",
                        "..analytics.infrastructure.persistence..",
                        "..analytics.domain.."
                )
                .allowEmptyShould(true);
```

- [x] **Step 2: Remove DTO boundary exception for search**

Remove this entry from `DtoBoundaryArchTest.LEGACY_SERVICE_RESPONSE_DTO_CALLERS`:

```java
"com.nowcoder.community.search.service.SearchAdminService"
```

- [x] **Step 3: Run RED architecture check**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: FAIL because search and analytics still contain legacy `service`, `repo`, `event`, and `ingest` packages.

---

## Task 2: Establish Search Domain And Infrastructure

**Files:**
- Create/move search files listed in the File Structure Map.

- [x] **Step 1: Write search domain tests**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/search/domain/service/PostSearchDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/search/domain/service/SearchReindexDomainServiceTest.java
```

Minimum cases:

```java
@Test
void normalizeQueryShouldClampPageAndSize() {
    PostSearchDomainService service = new PostSearchDomainService();

    PostSearchQuery query = service.normalize(" java ", null, "", -1, 1000);

    assertThat(query.keyword()).isEqualTo("java");
    assertThat(query.page()).isEqualTo(1);
    assertThat(query.size()).isEqualTo(50);
}

@Test
void reindexShouldSkipWhenJobNotAcquired() {
    SearchReindexDomainService service = new SearchReindexDomainService();

    assertThat(service.skippedResult("job-1", "already_running").skipped()).isTrue();
}
```

- [x] **Step 2: Run search domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.search.domain.service.PostSearchDomainServiceTest,com.nowcoder.community.search.domain.service.SearchReindexDomainServiceTest test
```

Expected: compile failure until search domain package exists.

- [x] **Step 3: Move search repository and index infrastructure**

Move repository interface to `search.domain.repository`. Move Elasticsearch/in-memory implementations, document type, initializer, and index manager to `search.infrastructure.persistence`.

Repository contract:

```java
public interface PostSearchRepository {
    List<SearchPostResult> search(PostSearchQuery query);
    void save(PostSearchDocument document);
    void delete(UUID postId);
    void clear();
}
```

Only infrastructure classes may import Spring Data Elasticsearch types.

- [x] **Step 4: Move search event adapters**

Move `PostOutboxEnqueuer` and `PostOutboxHandler` to `search.infrastructure.event`. They may import published content outbox/contract types and must call only `SearchApplicationService`.

- [x] **Step 5: Run search foundation tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.search.domain.service.PostSearchDomainServiceTest,com.nowcoder.community.search.domain.service.SearchReindexDomainServiceTest,com.nowcoder.community.search.infrastructure.persistence.InMemoryPostSearchRepositoryTest,com.nowcoder.community.search.infrastructure.event.PostOutboxEnqueuerTest,com.nowcoder.community.search.infrastructure.event.PostOutboxHandlerTest test
```

Expected: PASS.

---

## Task 3: Move Search Application, Controller, And API Adapter

**Files:**
- Move search application and tests listed in the File Structure Map.

- [x] **Step 1: Move search tests**

Move:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/search/service/PostSearchServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchAdminServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchAdminApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchReindexApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/search/service/ReindexJobServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/search/application/ReindexJobApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/search/repo/InMemoryPostSearchRepositoryTest.java backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/persistence/InMemoryPostSearchRepositoryTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/search/event/PostOutboxEnqueuerTest.java backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxEnqueuerTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/search/event/PostOutboxHandlerTest.java backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxHandlerTest.java
```

- [x] **Step 2: Run search application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.search.application.SearchApplicationServiceTest,com.nowcoder.community.search.application.SearchAdminApplicationServiceTest,com.nowcoder.community.search.application.SearchReindexApplicationServiceTest,SearchControllerTest test
```

Expected: compile failure until application and controller DTO packages exist.

- [x] **Step 3: Implement search application services**

`SearchApplicationService` public methods:

```java
List<SearchPostResult> searchPosts(SearchPostsCommand command);
void indexPost(PostSearchDocument document);
void deletePost(UUID postId);
```

`SearchReindexApplicationService` public method:

```java
SearchReindexResult reindex(ReindexPostsCommand command);
```

`SearchAdminApplicationService` public method:

```java
SearchReindexResult reindex();
```

Application services may call `content.api.query.PostScanQueryApi` for reindex scans and `PostSearchRepository` for indexing. They must not import `search.controller.dto`, `search.infrastructure`, `search.repo`, or `search.event`.

- [x] **Step 4: Implement search API adapter**

Create:

```java
@Service
public class SearchReindexActionApiAdapter implements SearchReindexActionApi {
    private final SearchReindexApplicationService applicationService;

    @Override
    public SearchReindexResult reindex() {
        return applicationService.reindex(new ReindexPostsCommand());
    }
}
```

- [x] **Step 5: Move search controller DTO conversion**

`SearchController` imports only `search.application`, `search.application.command`, `search.application.result`, and `search.controller.dto`.

Controller conversion helper:

```java
private SearchPostItemResponse toResponse(SearchPostResult result) {
    SearchPostItemResponse response = new SearchPostItemResponse();
    response.setPostId(result.postId());
    response.setTitle(result.title());
    response.setContent(result.content());
    response.setScore(result.score());
    return response;
}
```

- [x] **Step 6: Delete search raw service surfaces**

Delete:

```text
search/service/PostSearchService.java
search/service/SearchApplicationService.java
search/service/SearchAdminService.java
search/service/SearchReindexExecutionService.java
search/service/ReindexJobService.java
search/service/PostSearchPayloadMapper.java
search/repo/*
search/event/*
```

The remaining production class in `search.service` must be exactly:

```text
SearchReindexActionApiAdapter
```

- [x] **Step 7: Run search suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=SearchControllerTest,SearchEventSurfaceRetirementTest,com.nowcoder.community.search.application.SearchApplicationServiceTest,com.nowcoder.community.search.application.SearchAdminApplicationServiceTest,com.nowcoder.community.search.application.SearchReindexApplicationServiceTest,com.nowcoder.community.search.application.ReindexJobApplicationServiceTest,com.nowcoder.community.search.infrastructure.event.PostOutboxEnqueuerTest,com.nowcoder.community.search.infrastructure.event.PostOutboxHandlerTest,DddLayeringArchTest,ControllerBoundaryArchTest test
```

Expected: PASS.

---

## Task 4: Establish Analytics Domain And Infrastructure

**Files:**
- Create/move analytics files listed in the File Structure Map.

- [x] **Step 1: Write analytics domain tests**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/analytics/domain/service/AnalyticsDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/analytics/domain/service/AnalyticsIngestDomainServiceTest.java
```

Minimum cases:

```java
@Test
void rangeShouldRejectEndBeforeStartAndTooLargeWindow() {
    AnalyticsDomainService service = new AnalyticsDomainService(31);

    assertThatThrownBy(() -> service.validateRange(LocalDate.parse("2026-04-28"), LocalDate.parse("2026-04-01")))
            .isInstanceOf(BusinessException.class);
    assertThatThrownBy(() -> service.validateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-28")))
            .isInstanceOf(BusinessException.class);
}

@Test
void ingestShouldRecordDauOnlyForAuthenticatedUser() {
    AnalyticsIngestDomainService service = new AnalyticsIngestDomainService();

    assertThat(service.shouldRecordDau(null)).isFalse();
    assertThat(service.shouldRecordDau(uuid(1))).isTrue();
}
```

- [x] **Step 2: Run analytics domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.analytics.domain.service.AnalyticsDomainServiceTest,com.nowcoder.community.analytics.domain.service.AnalyticsIngestDomainServiceTest test
```

Expected: compile failure until analytics domain package exists.

- [x] **Step 3: Move analytics repositories to domain and infrastructure**

Move repository interfaces to `analytics.domain.repository`. Move Redis implementations to `analytics.infrastructure.persistence`.

Repository contracts remain behaviorally equivalent:

```java
public interface AnalyticsRepository {
    void recordUv(LocalDate date, String ip);
    void recordDau(LocalDate date, int userOrdinal);
    long calculateUv(LocalDate start, LocalDate end);
    long calculateDau(LocalDate start, LocalDate end);
}

public interface AnalyticsUserOrdinalRepository {
    int ordinalOf(UUID userId);
}
```

- [x] **Step 4: Move analytics web ingestion adapters**

Move request filter/classifier/principal resolver/properties into `analytics.infrastructure.web`. `AnalyticsRequestCaptureFilter` injects `AnalyticsIngestApplicationService`, not `AnalyticsIngestActionApi` and not raw services.

- [x] **Step 5: Run analytics foundation tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.analytics.domain.service.AnalyticsDomainServiceTest,com.nowcoder.community.analytics.domain.service.AnalyticsIngestDomainServiceTest,com.nowcoder.community.analytics.infrastructure.persistence.RedisAnalyticsRepositoryTest,com.nowcoder.community.analytics.infrastructure.persistence.RedisAnalyticsUserOrdinalRepositoryTest,com.nowcoder.community.analytics.infrastructure.web.AnalyticsRequestClassifierTest,com.nowcoder.community.analytics.infrastructure.web.AnalyticsPrincipalResolverTest test
```

Expected: PASS.

---

## Task 5: Move Analytics Application, Controller, Filter, And API Adapter

**Files:**
- Move analytics application and tests listed in the File Structure Map.

- [x] **Step 1: Move analytics tests**

Move:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/service/AnalyticsServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsIngestServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestCaptureFilterTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestCaptureFilterTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsRequestClassifierTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsRequestClassifierTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/ingest/AnalyticsPrincipalResolverTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/web/AnalyticsPrincipalResolverTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsRepositoryTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/persistence/RedisAnalyticsRepositoryTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/RedisAnalyticsUserOrdinalRepositoryTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/persistence/RedisAnalyticsUserOrdinalRepositoryTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/analytics/repo/AnalyticsRepositorySelectionTest.java backend/community-app/src/test/java/com/nowcoder/community/analytics/infrastructure/persistence/AnalyticsRepositorySelectionTest.java
```

- [x] **Step 2: Run analytics application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.analytics.application.AnalyticsApplicationServiceTest,com.nowcoder.community.analytics.application.AnalyticsIngestApplicationServiceTest,AnalyticsControllerUnitTest test
```

Expected: compile failure until analytics application and controller DTO packages exist.

- [x] **Step 3: Implement analytics application services**

`AnalyticsApplicationService` public methods:

```java
long calculateUv(AnalyticsRangeQuery query);
long calculateDau(AnalyticsRangeQuery query);
```

`AnalyticsIngestApplicationService` public methods:

```java
void recordRequest(RecordRequestCommand command);
void recordLoginSuccess(RecordLoginSuccessCommand command);
```

Application services must import repositories and domain services only. They must not import `analytics.controller.dto`, `analytics.infrastructure`, `analytics.repo`, or `analytics.ingest`.

- [x] **Step 4: Implement analytics API adapter**

Create:

```java
@Service
public class AnalyticsIngestActionApiAdapter implements AnalyticsIngestActionApi {
    private final AnalyticsIngestApplicationService applicationService;

    @Override
    public void recordLoginSuccess(UUID userId) {
        applicationService.recordLoginSuccess(new RecordLoginSuccessCommand(userId));
    }
}
```

- [x] **Step 5: Move analytics controller DTO conversion**

`AnalyticsController` imports only `analytics.application`, `analytics.application.command`, `analytics.application.result`, and `analytics.controller.dto`.

It maps `RangeQuery` into `AnalyticsRangeQuery` before calling the application service.

- [x] **Step 6: Delete analytics raw service surfaces**

Delete:

```text
analytics/service/AnalyticsService.java
analytics/service/AnalyticsApplicationService.java
analytics/ingest/AnalyticsIngestService.java
analytics/ingest/*
analytics/repo/*
```

The remaining production class in `analytics.service` must be exactly:

```text
AnalyticsIngestActionApiAdapter
```

- [x] **Step 7: Run analytics suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=AnalyticsControllerUnitTest,com.nowcoder.community.analytics.application.AnalyticsApplicationServiceTest,com.nowcoder.community.analytics.application.AnalyticsIngestApplicationServiceTest,com.nowcoder.community.analytics.infrastructure.web.AnalyticsRequestCaptureFilterTest,com.nowcoder.community.analytics.infrastructure.web.AnalyticsRequestClassifierTest,com.nowcoder.community.analytics.infrastructure.web.AnalyticsPrincipalResolverTest,com.nowcoder.community.analytics.infrastructure.persistence.RedisAnalyticsRepositoryTest,com.nowcoder.community.analytics.infrastructure.persistence.RedisAnalyticsUserOrdinalRepositoryTest,DddLayeringArchTest,ControllerBoundaryArchTest test
```

Expected: PASS.

---

## Task 6: Docs, Scans, And Verification

**Files:**
- Modify docs listed in the Guardrails And Docs section.
- Verify only for scans and Maven commands.

- [x] **Step 1: Scan for retired search, analytics, and message production surfaces**

Run:

```bash
cd /home/feng/code/project/community
rg -n "search\\.(service|repo|event)\\.|analytics\\.(service|repo|ingest)\\.|com\\.nowcoder\\.community\\.message\\." backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: only `search.service.SearchReindexActionApiAdapter`, `analytics.service.AnalyticsIngestActionApiAdapter`, ArchUnit rule strings, and message tests remain.

- [x] **Step 2: Update docs**

Update:

```text
docs/ARCHITECTURE.md
docs/SYSTEM_DESIGN.md
docs/CORE_LOGIC.md
docs/business-logic
```

Document:

```text
SearchController -> SearchApplicationService
Search outbox adapters -> SearchApplicationService
SearchReindexActionApi -> SearchReindexActionApiAdapter -> SearchReindexApplicationService
AnalyticsController -> AnalyticsApplicationService
AnalyticsRequestCaptureFilter -> AnalyticsIngestApplicationService
AnalyticsIngestActionApi -> AnalyticsIngestActionApiAdapter -> AnalyticsIngestApplicationService
```

- [x] **Step 3: Run focused search and analytics suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=SearchControllerTest,SearchEventSurfaceRetirementTest,AnalyticsControllerUnitTest,com.nowcoder.community.search.application.SearchApplicationServiceTest,com.nowcoder.community.search.application.SearchAdminApplicationServiceTest,com.nowcoder.community.search.application.SearchReindexApplicationServiceTest,com.nowcoder.community.search.infrastructure.event.PostOutboxEnqueuerTest,com.nowcoder.community.search.infrastructure.event.PostOutboxHandlerTest,com.nowcoder.community.analytics.application.AnalyticsApplicationServiceTest,com.nowcoder.community.analytics.application.AnalyticsIngestApplicationServiceTest,com.nowcoder.community.analytics.infrastructure.web.AnalyticsRequestCaptureFilterTest test
```

Expected: PASS.

- [x] **Step 4: Run architecture suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest,ListenerBoundaryArchTest,DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

- [x] **Step 5: Run full backend verification**

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected: PASS with zero failures and zero errors.

---

## Self-Review

### Spec Coverage

- Search controller and outbox adapters call application services only: Tasks 3 and 6.
- Elasticsearch/in-memory search storage is hidden behind domain repository interfaces: Task 2.
- Search reindex foreign API is implemented by an adapter only: Task 3.
- Analytics controller/filter/API call application services only: Task 5.
- Analytics Redis storage is hidden behind domain repository interfaces: Task 4.
- Legacy `service`, `repo`, `event`, and `ingest` packages are retired or reduced to API adapters: Tasks 1, 3, 5, and 6.

### Placeholder Scan

No step uses placeholder markers. Every task lists exact file paths, concrete class names, exact commands, and expected outcomes.

### Type Consistency

Search and analytics use command/result records at application boundaries. Domain services use domain models and rule inputs. Infrastructure owns Elasticsearch, Redis, Spring web filter, and outbox handler details.
