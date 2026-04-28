# Growth And Notice DDD Tactical Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `growth` and `notice` into strict DDD Tactical Layering and retire their legacy raw `service`/`entity`/`mapper` collaboration surfaces.

**Architecture:** Growth application services own task progress and user-level orchestration; domain services own task period, grant, and level rules; mapper-backed persistence is hidden behind repository interfaces. Notice controllers and event listeners call notice application services only, while notice projection from content/social events is an infrastructure event adapter that maps contract events into notice application commands.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, Spring events, ArchUnit, JUnit 5, Mockito, Maven.

---

## Scope

This plan covers these remaining strict-DDD slices:

- `growth`
- `notice`

It assumes `content`, `user`, `social`, `wallet`, and `market` either already comply or are migrated by their own plan. Growth and notice must collaborate with those domains only through `api.*` and `contracts.event`.

---

## Target Package Shape

```text
com.nowcoder.community.growth
  application
    command
    result
  domain
    model
    repository
    service
  infrastructure
    persistence
      mapper
      dataobject
  api
    action
    query
    model
  service              # only Growth*ApiAdapter classes after migration

com.nowcoder.community.notice
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
      mapper
      dataobject
  service              # empty unless a future foreign API adapter is published
```

---

## File Structure Map

### Growth Application

- Move: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressApplicationService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressApplicationService.java`
- Move/rename: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UserLevelService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/growth/application/UserLevelApplicationService.java`
- Move: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/GrowthBusinessTimeService.java` -> `backend/community-app/src/main/java/com/nowcoder/community/growth/application/GrowthBusinessTimeService.java`
- Create commands:
  - `growth/application/command/RecordTaskProgressCommand.java`
  - `growth/application/command/TriggerPostPublishedCommand.java`
  - `growth/application/command/TriggerCommentCreatedCommand.java`
  - `growth/application/command/TriggerLikeCreatedCommand.java`
  - `growth/application/command/UpdateUserLevelConfigCommand.java`
- Create results:
  - `growth/application/result/UserLevelSummaryResult.java`
  - `growth/application/result/UserLevelConfigResult.java`
  - `growth/application/result/TaskProgressResult.java`

### Growth Domain And Infrastructure

- Move: `growth/service/TaskPeriodKeyResolver.java` -> `growth/domain/service/TaskPeriodKeyResolver.java`
- Move entities to infrastructure row objects:
  - `growth/entity/TaskTemplate.java` -> `growth/infrastructure/persistence/dataobject/TaskTemplateDataObject.java`
  - `growth/entity/UserTaskProgress.java` -> `growth/infrastructure/persistence/dataobject/UserTaskProgressDataObject.java`
  - `growth/entity/UserLevelRuleConfig.java` -> `growth/infrastructure/persistence/dataobject/UserLevelRuleConfigDataObject.java`
  - Create `growth/infrastructure/persistence/dataobject/UserTaskEventLogDataObject.java` for `UserTaskEventLogMapper` rows.
  - `growth/entity/RewardAccount.java` -> `growth/infrastructure/persistence/dataobject/RewardAccountDataObject.java`
  - `growth/entity/RewardGrantRecord.java` -> `growth/infrastructure/persistence/dataobject/RewardGrantRecordDataObject.java`
  - `growth/entity/RewardLedgerEntry.java` -> `growth/infrastructure/persistence/dataobject/RewardLedgerEntryDataObject.java`
- Move mappers to `growth/infrastructure/persistence/mapper`:
  - `TaskTemplateMapper`
  - `UserTaskProgressMapper`
  - `UserTaskEventLogMapper`
  - `UserLevelRuleConfigMapper`
- Create repository interfaces:
  - `growth/domain/repository/TaskTemplateRepository.java`
  - `growth/domain/repository/UserTaskProgressRepository.java`
  - `growth/domain/repository/UserTaskEventLogRepository.java`
  - `growth/domain/repository/UserLevelRuleConfigRepository.java`
- Create repository implementations:
  - `growth/infrastructure/persistence/MyBatisTaskTemplateRepository.java`
  - `growth/infrastructure/persistence/MyBatisUserTaskProgressRepository.java`
  - `growth/infrastructure/persistence/MyBatisUserTaskEventLogRepository.java`
  - `growth/infrastructure/persistence/MyBatisUserLevelRuleConfigRepository.java`
- Create domain services:
  - `growth/domain/service/TaskProgressDomainService.java`
  - `growth/domain/service/UserLevelDomainService.java`
  - `growth/domain/service/RewardGrantDomainService.java`

### Growth API Adapters

- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/GrowthTaskProgressActionApiAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UserLevelQueryApiAdapter.java`
- Delete after replacement:
  - `growth/service/TaskProgressService.java`
  - `growth/service/UserLevelService.java`
  - `growth/service/TaskProgressApplicationService.java`

### Notice Controller And DTOs

- Move:
  - `notice/dto/MarkNoticeReadRequest.java` -> `notice/controller/dto/MarkNoticeReadRequest.java`
  - `notice/dto/NoticeItemResponse.java` -> `notice/controller/dto/NoticeItemResponse.java`
  - `notice/dto/NoticeTopicSummaryResponse.java` -> `notice/controller/dto/NoticeTopicSummaryResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/controller/NoticeController.java`

### Notice Application

- Move: `notice/service/NoticeApplicationService.java` -> `notice/application/NoticeApplicationService.java`
- Move: `notice/service/NoticeProjectionApplicationService.java` -> `notice/application/NoticeProjectionApplicationService.java`
- Create:
  - `notice/application/command/ListNoticeItemsCommand.java`
  - `notice/application/command/MarkNoticeReadCommand.java`
  - `notice/application/command/CreateNoticeCommand.java`
  - `notice/application/command/ProjectContentNoticeCommand.java`
  - `notice/application/command/ProjectSocialNoticeCommand.java`
  - `notice/application/result/NoticeItemResult.java`
  - `notice/application/result/NoticeTopicSummaryResult.java`

### Notice Domain And Infrastructure

- Move: `notice/entity/NoticeRecord.java` -> `notice/infrastructure/persistence/dataobject/NoticeRecordDataObject.java`
- Move: `notice/mapper/NoticeMapper.java` -> `notice/infrastructure/persistence/mapper/NoticeMapper.java`
- Create: `notice/domain/repository/NoticeRepository.java`
- Create: `notice/infrastructure/persistence/MyBatisNoticeRepository.java`
- Create:
  - `notice/domain/service/NoticeDomainService.java`
  - `notice/domain/service/NoticeProjectionDomainService.java`
  - `notice/domain/model/NoticeTopic.java`
  - `notice/domain/model/NoticeProjection.java`
- Move: `notice/event/NoticeProjectionListener.java` -> `notice/infrastructure/event/NoticeProjectionListener.java`
- Delete after replacement:
  - `notice/service/NoticeService.java`
  - `notice/service/NoticeProjectionService.java`
  - `notice/service/NoticeApplicationService.java`
  - `notice/service/NoticeProjectionApplicationService.java`

### Guardrails And Docs

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/NoticeModuleArchTest.java`
- Modify docs:
  - `docs/ARCHITECTURE.md`
  - `docs/SYSTEM_DESIGN.md`
  - `docs/CORE_LOGIC.md`
  - `docs/business-logic/growth-task-grant-level-flow.md`
  - `docs/business-logic/notice-projection-read-flow.md`

---

## Task 1: Add Growth And Notice RED Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/NoticeModuleArchTest.java`

- [x] **Step 1: Add growth and notice retirement rules**

Add these rules to `DddLayeringArchTest`:

```java
@ArchTest
static final ArchRule growth_service_package_must_only_publish_foreign_api_adapters =
        classes()
                .that().resideInAnyPackage("..growth.service..")
                .should().haveSimpleNameEndingWith("ApiAdapter")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_growth_mapper_entity_packages_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..growth.mapper..", "..growth.entity..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule notice_service_package_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..notice.service..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_notice_mapper_entity_event_packages_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..notice.mapper..", "..notice.entity..", "..notice.event..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule notice_controller_and_event_adapters_must_call_application_only =
        noClasses()
                .that().resideInAnyPackage("..notice.controller..", "..notice.infrastructure.event..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..notice.service..",
                        "..notice.domain..",
                        "..notice.infrastructure.persistence..",
                        "..notice.mapper..",
                        "..notice.entity.."
                )
                .allowEmptyShould(true);
```

- [x] **Step 2: Remove DTO boundary exceptions**

Remove these entries from `DtoBoundaryArchTest.LEGACY_SERVICE_RESPONSE_DTO_CALLERS`:

```java
"com.nowcoder.community.growth.service.UserLevelService",
"com.nowcoder.community.notice.service.NoticeApplicationService",
"com.nowcoder.community.notice.service.NoticeService"
```

- [x] **Step 3: Tighten notice module arch test**

Update `NoticeModuleArchTest` so it expects:

```text
notice.controller -> notice.application
notice.infrastructure.event -> notice.application
notice.application -> notice.domain.repository / notice.domain.service
notice.infrastructure.persistence -> notice.domain.repository
```

It must not permit controller/listener dependencies on `notice.service`, `notice.mapper`, or `notice.entity`.

- [x] **Step 4: Run RED architecture check**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DddLayeringArchTest,DtoBoundaryArchTest,NoticeModuleArchTest test
```

Expected: FAIL because growth and notice still contain legacy service/entity/mapper packages.

---

## Task 2: Establish Growth Domain And Infrastructure

**Files:**
- Create/move growth files listed in the File Structure Map.
- Move tests from `growth/service` and `growth/mapper` into `growth/application`, `growth/domain`, and `growth/infrastructure/persistence`.

- [x] **Step 1: Write growth domain tests**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/growth/domain/service/TaskProgressDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/growth/domain/service/UserLevelDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/growth/domain/service/RewardGrantDomainServiceTest.java
```

Minimum cases:

```java
@Test
void periodKeyResolverShouldUseDailyWeeklyAndLifetimeKeys() {
    TaskPeriodKeyResolver resolver = new TaskPeriodKeyResolver();
    LocalDate date = LocalDate.parse("2026-04-28");

    assertThat(resolver.resolve("DAILY", date)).isEqualTo("2026-04-28");
    assertThat(resolver.resolve("LIFETIME", date)).isEqualTo("LIFETIME");
}

@Test
void userLevelShouldUseConfiguredThresholds() {
    UserLevelDomainService service = new UserLevelDomainService();

    assertThat(service.levelForSignInDays(0, 12, 88)).isEqualTo(1);
    assertThat(service.levelForSignInDays(12, 12, 88)).isEqualTo(2);
    assertThat(service.levelForSignInDays(88, 12, 88)).isEqualTo(3);
}

@Test
void rewardGrantShouldRejectDuplicateSourceEvent() {
    RewardGrantDomainService service = new RewardGrantDomainService();

    assertThatThrownBy(() -> service.validateSourceEventId(""))
            .isInstanceOf(BusinessException.class);
}
```

- [x] **Step 2: Run growth domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.growth.domain.service.TaskProgressDomainServiceTest,com.nowcoder.community.growth.domain.service.UserLevelDomainServiceTest,com.nowcoder.community.growth.domain.service.RewardGrantDomainServiceTest test
```

Expected: compile failure until growth domain services exist.

- [x] **Step 3: Move growth persistence behind repositories**

Move all current growth mappers to `growth.infrastructure.persistence.mapper` and data objects to `growth.infrastructure.persistence.dataobject`.

Repository interface minimum:

```java
public interface TaskTemplateRepository {
    List<TaskTemplateDataObject> findActiveByTriggerEventType(String triggerEventType);
    List<TaskTemplateDataObject> findActiveOrdered();
}

public interface UserTaskProgressRepository {
    UserTaskProgressDataObject find(UUID userId, String taskCode, String periodKey);
    int insert(UserTaskProgressDataObject progress);
    int updateProgress(UUID progressId, int delta, Date updateTime);
}

public interface UserTaskEventLogRepository {
    boolean insertIfAbsent(UUID userId, String sourceEventId, Date createTime);
}

public interface UserLevelRuleConfigRepository {
    UserLevelRuleConfigDataObject findActive();
    int insert(UserLevelRuleConfigDataObject config);
}
```

- [x] **Step 4: Implement growth domain services**

Implement domain logic extracted from `TaskProgressService` and `UserLevelService`:

```java
public final class TaskProgressDomainService {
    public void validateEvent(UUID userId, String triggerEventType, String sourceEventId, LocalDate bizDate);
    public int cappedDelta(int currentProgress, int targetProgress, int increment);
}

public final class UserLevelDomainService {
    public int levelForSignInDays(int signInDays, int lv2Days, int lv3Days);
    public void validateLevelConfig(int windowDays, int lv2Days, int lv3Days);
}

public final class RewardGrantDomainService {
    public void validateSourceEventId(String sourceEventId);
}
```

- [x] **Step 5: Run growth foundation tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.growth.domain.service.TaskProgressDomainServiceTest,com.nowcoder.community.growth.domain.service.UserLevelDomainServiceTest,com.nowcoder.community.growth.domain.service.RewardGrantDomainServiceTest,com.nowcoder.community.growth.infrastructure.persistence.TaskProgressMapperPersistenceTest test
```

Expected: PASS.

---

## Task 3: Move Growth Application And API Adapters

**Files:**
- Move growth application and tests listed in the File Structure Map.

- [x] **Step 1: Move growth service tests**

Move:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceUnitTest.java backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceUnitTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/growth/service/UserLevelServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/growth/application/UserLevelApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/growth/service/UserLevelServiceUnitTest.java backend/community-app/src/test/java/com/nowcoder/community/growth/application/UserLevelApplicationServiceUnitTest.java
```

Rewrite tests to call application commands:

```java
applicationService.recordProgress(new RecordTaskProgressCommand(
        uuid(1),
        ContentEventTypes.POST_PUBLISHED,
        "post-published:" + uuid(2),
        LocalDate.parse("2026-04-28")
));
```

- [x] **Step 2: Run growth application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.growth.application.TaskProgressApplicationServiceTest,com.nowcoder.community.growth.application.TaskProgressApplicationServiceUnitTest,com.nowcoder.community.growth.application.UserLevelApplicationServiceTest,com.nowcoder.community.growth.application.UserLevelApplicationServiceUnitTest test
```

Expected: compile failure until growth application package and commands/results exist.

- [x] **Step 3: Implement growth application services**

`TaskProgressApplicationService` public methods:

```java
void recordProgress(RecordTaskProgressCommand command);
void triggerPostPublished(TriggerPostPublishedCommand command);
void triggerCommentCreated(TriggerCommentCreatedCommand command);
void triggerLikeCreated(TriggerLikeCreatedCommand command);
```

`UserLevelApplicationService` public methods:

```java
UserLevelSummaryResult evaluateLevel(UUID userId);
UserLevelSummaryResult evaluateLevel(UUID userId, LocalDate bizDate);
UserLevelConfigResult getConfig();
UserLevelConfigResult updateConfig(UpdateUserLevelConfigCommand command);
```

Application services must import repositories and domain services, not mappers, entities, HTTP DTOs, or same-domain API adapters.

- [x] **Step 4: Implement growth API adapters**

Create:

```java
@Service
public class GrowthTaskProgressActionApiAdapter implements GrowthTaskProgressActionApi {
    private final TaskProgressApplicationService applicationService;
}

@Service
public class UserLevelQueryApiAdapter implements UserLevelQueryApi {
    private final UserLevelApplicationService applicationService;
}
```

Adapters map `api.model.UserLevelSummaryView` to/from application results. Same-domain growth code must not inject these adapters.

- [x] **Step 5: Delete growth raw service surfaces**

Delete:

```text
growth/service/TaskProgressService.java
growth/service/UserLevelService.java
growth/service/TaskProgressApplicationService.java
```

The remaining production classes in `growth.service` must be exactly:

```text
GrowthTaskProgressActionApiAdapter
UserLevelQueryApiAdapter
```

- [x] **Step 6: Run growth suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=LegacyGrowthSurfaceRetirementTest,com.nowcoder.community.growth.application.TaskProgressApplicationServiceTest,com.nowcoder.community.growth.application.TaskProgressApplicationServiceUnitTest,com.nowcoder.community.growth.application.UserLevelApplicationServiceTest,com.nowcoder.community.growth.application.UserLevelApplicationServiceUnitTest,DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

---

## Task 4: Establish Notice Domain And Infrastructure

**Files:**
- Create/move notice files listed in the File Structure Map.

- [x] **Step 1: Write notice domain tests**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/notice/domain/service/NoticeDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/notice/domain/service/NoticeProjectionDomainServiceTest.java
```

Minimum cases:

```java
@Test
void validateListShouldNormalizePageAndSize() {
    NoticeDomainService service = new NoticeDomainService();

    assertThat(service.pageOrDefault(null)).isEqualTo(1);
    assertThat(service.sizeOrDefault(1000)).isEqualTo(50);
}

@Test
void projectionShouldIgnoreEventsWithoutReceiver() {
    NoticeProjectionDomainService service = new NoticeProjectionDomainService();

    assertThat(service.shouldProject(null, "topic", "{}")).isFalse();
}
```

- [x] **Step 2: Run notice domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.notice.domain.service.NoticeDomainServiceTest,com.nowcoder.community.notice.domain.service.NoticeProjectionDomainServiceTest test
```

Expected: compile failure until notice domain services exist.

- [x] **Step 3: Move notice persistence behind repository**

Move `NoticeMapper` and `NoticeRecord` to infrastructure. Create:

```java
public interface NoticeRepository {
    void insert(NoticeRecordDataObject notice);
    List<NoticeRecordDataObject> findByUserAndTopic(UUID userId, String topic, int offset, int limit);
    int unreadCount(UUID userId, String topic);
    int markRead(UUID userId, List<UUID> ids);
    List<NoticeTopicSummaryResult> topicSummary(UUID userId);
}
```

Only `MyBatisNoticeRepository` may import `NoticeMapper`.

- [x] **Step 4: Implement notice domain services**

Implement:

```java
public final class NoticeDomainService {
    public int pageOrDefault(Integer page);
    public int sizeOrDefault(Integer size);
    public void validateCreate(UUID toUserId, String topic, String contentJson);
}

public final class NoticeProjectionDomainService {
    public boolean shouldProject(UUID toUserId, String topic, String contentJson);
    public NoticeProjection projection(UUID toUserId, String topic, String contentJson);
}
```

- [x] **Step 5: Run notice foundation tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.notice.domain.service.NoticeDomainServiceTest,com.nowcoder.community.notice.domain.service.NoticeProjectionDomainServiceTest,com.nowcoder.community.notice.infrastructure.persistence.NoticeMapperPersistenceTest test
```

Expected: PASS.

---

## Task 5: Move Notice Application, Controller, And Event Adapter

**Files:**
- Move notice application, controller DTO, event adapter, and tests listed in the File Structure Map.

- [x] **Step 1: Move notice service and listener tests**

Move:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/notice/service/NoticeServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerTest.java backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListenerTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerStructureTest.java backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionListenerStructureTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/notice/mapper/NoticeMapperPersistenceTest.java backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/persistence/NoticeMapperPersistenceTest.java
```

Rewrite service tests to assert application result records, not HTTP DTOs.

- [x] **Step 2: Run notice application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.notice.application.NoticeApplicationServiceTest,com.nowcoder.community.notice.infrastructure.event.NoticeProjectionListenerTest,NoticeControllerUnitTest test
```

Expected: compile failure until notice application, infrastructure event, and controller DTO packages exist.

- [x] **Step 3: Implement notice application services**

`NoticeApplicationService` public methods:

```java
List<NoticeItemResult> listNoticeItems(ListNoticeItemsCommand command);
int unreadCount(UUID userId, String topic);
List<NoticeTopicSummaryResult> topicSummary(UUID userId);
void markRead(MarkNoticeReadCommand command);
void createNotice(CreateNoticeCommand command);
```

`NoticeProjectionApplicationService` public methods:

```java
void projectContentEvent(ContentContractEvent event);
void projectSocialEvent(SocialContractEvent event);
```

Application services own pagination, command/result assembly, repository calls, and projection orchestration. They must not import `notice.controller.dto`, `notice.mapper`, or `notice.entity`.

- [x] **Step 4: Move notice event adapter**

`NoticeProjectionListener` lives in `notice.infrastructure.event` and injects only:

```java
private final NoticeProjectionApplicationService noticeProjectionApplicationService;
```

It may import `content.contracts.event.ContentContractEvent` and `social.contracts.event.SocialContractEvent` because those are published asynchronous contracts.

- [x] **Step 5: Move notice controller DTO conversion**

`NoticeController` imports only:

```java
com.nowcoder.community.notice.application.NoticeApplicationService
com.nowcoder.community.notice.application.command.*
com.nowcoder.community.notice.application.result.*
com.nowcoder.community.notice.controller.dto.*
```

Controller conversion helpers:

```java
private NoticeItemResponse toResponse(NoticeItemResult result) {
    NoticeItemResponse response = new NoticeItemResponse();
    response.setId(result.id());
    response.setTopic(result.topic());
    response.setContentJson(result.contentJson());
    response.setRead(result.read());
    response.setCreateTime(result.createTime());
    return response;
}
```

- [x] **Step 6: Delete notice raw service surfaces**

Delete:

```text
notice/service/NoticeService.java
notice/service/NoticeProjectionService.java
notice/service/NoticeApplicationService.java
notice/service/NoticeProjectionApplicationService.java
notice/event/NoticeProjectionListener.java
notice/entity/NoticeRecord.java
notice/mapper/NoticeMapper.java
```

No production classes should remain in `notice.service`, `notice.entity`, `notice.mapper`, or `notice.event`.

- [x] **Step 7: Run notice suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=NoticeControllerUnitTest,NoticeModuleArchTest,com.nowcoder.community.notice.application.NoticeApplicationServiceTest,com.nowcoder.community.notice.infrastructure.event.NoticeProjectionListenerTest,com.nowcoder.community.notice.infrastructure.event.NoticeProjectionListenerStructureTest,com.nowcoder.community.notice.infrastructure.persistence.NoticeMapperPersistenceTest,DddLayeringArchTest,ControllerBoundaryArchTest test
```

Expected: PASS.

---

## Task 6: Docs, Scans, And Verification

**Files:**
- Modify docs listed in the Guardrails And Docs section.
- Verify only for scans and Maven commands.

- [x] **Step 1: Scan for retired growth and notice surfaces**

Run:

```bash
cd /home/feng/code/project/community
rg -n "growth\\.(service|entity|mapper)\\.|notice\\.(service|entity|mapper|event)\\." backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: only `growth.service.*ApiAdapter`, ArchUnit rule strings, and no notice legacy package references remain.

- [x] **Step 2: Update docs**

Update:

```text
docs/ARCHITECTURE.md
docs/SYSTEM_DESIGN.md
docs/CORE_LOGIC.md
docs/business-logic/growth-task-grant-level-flow.md
docs/business-logic/notice-projection-read-flow.md
```

Document:

```text
GrowthTaskProgressActionApi -> GrowthTaskProgressActionApiAdapter -> TaskProgressApplicationService
UserLevelQueryApi -> UserLevelQueryApiAdapter -> UserLevelApplicationService
NoticeProjectionListener -> NoticeProjectionApplicationService -> NoticeRepository
NoticeController -> NoticeApplicationService
```

- [x] **Step 3: Run focused growth and notice suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=LegacyGrowthSurfaceRetirementTest,NoticeModuleArchTest,NoticeControllerUnitTest,com.nowcoder.community.growth.application.TaskProgressApplicationServiceTest,com.nowcoder.community.growth.application.TaskProgressApplicationServiceUnitTest,com.nowcoder.community.growth.application.UserLevelApplicationServiceTest,com.nowcoder.community.growth.application.UserLevelApplicationServiceUnitTest,com.nowcoder.community.notice.application.NoticeApplicationServiceTest,com.nowcoder.community.notice.infrastructure.event.NoticeProjectionListenerTest,com.nowcoder.community.notice.infrastructure.event.NoticeProjectionListenerStructureTest test
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

- Growth API contracts are implemented by adapters only: Tasks 3 and 6.
- Growth rules and persistence are separated into domain and infrastructure: Tasks 2 and 3.
- Notice controller and listener call application services only: Task 5.
- Notice projection uses published `contracts.event` payloads at the infrastructure boundary: Task 5.
- Legacy `service`, `entity`, `mapper`, and `event` packages are retired or reduced to API adapters: Tasks 1, 3, 5, and 6.

### Placeholder Scan

No step uses placeholder markers. Every task lists exact files, concrete class names, exact commands, and expected outcomes.

### Type Consistency

Growth and notice controllers/adapters use command/result records at the application boundary. Domain services use domain models and rule inputs. Infrastructure repositories are the only classes that import MyBatis mapper/dataobject types.
