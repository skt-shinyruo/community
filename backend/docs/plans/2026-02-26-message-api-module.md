# Message API Module Enablement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Turn `message-api` into a real Maven module and align message-domain structure with other domains by introducing `message/` (aggregate) containing `message-api` + `message-service`, while extracting HTTP DTOs/error-codes into `message-api` and removing entity leakage from notices endpoints.

**Architecture:** Add a `message` aggregate parent module; move the existing `message-service` under it; create `message-api` as a contracts jar; move `com.nowcoder.community.message.api.*` contract types into `message-api` without changing package names; update `message-service` to depend on `message-api`; adjust notices DTO/controller to avoid returning internal entity types.

**Tech Stack:** Java 17, Spring Boot 3, Maven multi-module reactor, Jakarta Validation.

---

### Task 1: Add `message/` aggregate module and wire into reactor

**Files:**
- Create: `message/pom.xml`
- Modify: `pom.xml`

**Step 1: Edit root reactor modules**

In `pom.xml`, replace:
- `<module>message-service</module>`

With:
- `<module>message</module>`

**Step 2: Create `message/pom.xml`**

Create `message/pom.xml` (pattern matches `social/pom.xml` / `user/pom.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.nowcoder.community</groupId>
        <artifactId>community</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>message</artifactId>
    <packaging>pom</packaging>

    <modules>
        <module>message-api</module>
        <module>message-service</module>
    </modules>
</project>
```

**Step 3: Verify reactor still parses**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community -q -DskipTests validate`

Expected:
- `BUILD SUCCESS`

**Step 4: Commit**

Run:
- `git add pom.xml message/pom.xml`
- `git commit -m "build: add message aggregate module"`

---

### Task 2: Move `message-service` under `message/` and update its parent

**Files:**
- Move: `message-service/` → `message/message-service/`
- Modify: `message/message-service/pom.xml`

**Step 1: Move directory**

Run:
- `git mv message-service message/message-service`

**Step 2: Update parent POM**

In `message/message-service/pom.xml`, change parent from:
- `<artifactId>community</artifactId>`

To:
- `<artifactId>message</artifactId>`

(Keep `groupId` + `version` unchanged.)

**Step 3: Verify module compiles**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community -q -pl message/message-service -am -DskipTests compile`

Expected:
- `BUILD SUCCESS`

**Step 4: Commit**

Run:
- `git add message/message-service/pom.xml`
- `git add -A message/message-service`
- `git commit -m "refactor: move message-service under message module"`

---

### Task 3: Create `message-api` Maven module

**Files:**
- Create: `message/message-api/pom.xml`
- Create: `message/message-api/src/main/java/...` (empty for now)

**Step 1: Create pom**

Create `message/message-api/pom.xml` (pattern matches `user/user-api/pom.xml`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xmlns="http://maven.apache.org/POM/4.0.0"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.nowcoder.community</groupId>
        <artifactId>message</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>

    <artifactId>message-api</artifactId>
    <name>message-api</name>
    <description>Message HTTP API DTOs and shared contracts</description>
    <packaging>jar</packaging>

    <dependencies>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>contracts-core</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.nowcoder.community</groupId>
            <artifactId>common</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

**Step 2: Verify it builds**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community -q -pl message/message-api -am -DskipTests compile`

Expected:
- `BUILD SUCCESS`

**Step 3: Commit**

Run:
- `git add message/message-api/pom.xml`
- `git commit -m "build: add message-api module"`

---

### Task 4: Move message contract types from `message-service` to `message-api`

**Files:**
- Move: `message/message-service/src/main/java/com/nowcoder/community/message/api/MessageErrorCode.java`
- Move: `message/message-service/src/main/java/com/nowcoder/community/message/api/dto/*.java`
- Modify: `message/message-api` (destination paths)

**Step 1: Move `MessageErrorCode`**

Run:
- `git mv message/message-service/src/main/java/com/nowcoder/community/message/api/MessageErrorCode.java \\
  message/message-api/src/main/java/com/nowcoder/community/message/api/MessageErrorCode.java`

**Step 2: Move DTO package**

Run:
- `git mv message/message-service/src/main/java/com/nowcoder/community/message/api/dto \\
  message/message-api/src/main/java/com/nowcoder/community/message/api/dto`

**Step 3: Verify `message-api` compiles**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community -q -pl message/message-api -am -DskipTests compile`

Expected:
- `BUILD SUCCESS`

**Step 4: Commit**

Run:
- `git add -A message/message-api/src/main/java/com/nowcoder/community/message/api`
- `git commit -m "refactor: extract message DTOs into message-api"`

---

### Task 5: Wire `message-service` to depend on `message-api`

**Files:**
- Modify: `message/message-service/pom.xml`

**Step 1: Add dependency**

Add:

```xml
<dependency>
    <groupId>com.nowcoder.community</groupId>
    <artifactId>message-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

**Step 2: Verify compile**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community -q -pl message/message-service -am -DskipTests compile`

Expected:
- `BUILD SUCCESS`

**Step 3: Commit**

Run:
- `git add message/message-service/pom.xml`
- `git commit -m "build: depend on message-api from message-service"`

---

### Task 6: Remove notice entity leakage from contracts and controllers

**Files:**
- Modify: `message/message-api/src/main/java/com/nowcoder/community/message/api/dto/NoticeTopicSummaryResponse.java`
- Modify: `message/message-service/src/main/java/com/nowcoder/community/message/service/NoticeService.java`
- Modify: `message/message-service/src/main/java/com/nowcoder/community/message/api/NoticeController.java`

**Step 1: Update DTO contract**

In `NoticeTopicSummaryResponse`, change:
- `private Message latest;`

To:
- `private LetterItemResponse latest;`

And update getters/setters accordingly.

**Step 2: Update service mapping**

In `NoticeService#topicSummary`, convert `Message` → `LetterItemResponse` before setting `latest`.
(Keep DB/entity unchanged; only map at boundary.)

**Step 3: Update notices list endpoint return type**

In `NoticeController#list`, change return type from:
- `Result<List<Message>>`

To:
- `Result<List<LetterItemResponse>>`

And map the list via a helper conversion (same fields as `MessageController#toLetterItem`).

**Step 4: Run focused compile + tests**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community -q -pl message/message-service -am test`

Expected:
- `BUILD SUCCESS`

**Step 5: Commit**

Run:
- `git add message/message-api/src/main/java/com/nowcoder/community/message/api/dto/NoticeTopicSummaryResponse.java`
- `git add message/message-service/src/main/java/com/nowcoder/community/message/service/NoticeService.java`
- `git add message/message-service/src/main/java/com/nowcoder/community/message/api/NoticeController.java`
- `git commit -m "refactor: return DTOs for notices"`

---

### Task 7: Update repo docs to reflect `message/` module structure

**Files:**
- Modify: `README.md`

**Step 1: Update module list**

Adjust the module description to reflect:
- `message/` aggregate
  - `message/message-api`
  - `message/message-service`

**Step 2: Verify no build impact**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community -q -DskipTests validate`

Expected:
- `BUILD SUCCESS`

**Step 3: Commit**

Run:
- `git add README.md`
- `git commit -m "docs: document message aggregate modules"`

---

### Task 8: Full verification

**Step 1: Run full reactor tests**

Run:
- `mvn -Dmaven.repo.local=/tmp/m2repo-community test`

Expected:
- `BUILD SUCCESS`

