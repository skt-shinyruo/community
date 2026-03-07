# MyBatis Wiring Dedup Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Centralize MyBatis mapper scanning in the bootstrap and remove MyBatis type alias scanning by switching all mapper XML files to fully qualified type names.

**Architecture:** In a single Spring ApplicationContext, keep a single `@MapperScan` in the composition root scanning `@Mapper`-annotated interfaces. Remove per-domain `@MapperScan` configs. Eliminate `type-aliases-package` and make XML mappers reference fully qualified Java class names.

**Tech Stack:** Spring Boot 3.2.x, mybatis-spring-boot-starter 3.0.x, JUnit 5.

---

### Task 1: Add a guardrail test (RED)

**Files:**
- Create: `backend/community-bootstrap/src/test/java/com/nowcoder/community/bootstrap/arch/MybatisWiringArchTest.java`

**Step 1: Write failing test**

- Add a JUnit test that asserts:
  - `backend/community-bootstrap/src/main/resources/application.yml` does **not** contain `type-aliases-package`.
  - `backend/community-bootstrap/src/test/resources/application.yml` does **not** contain `type-aliases-package`.
  - Main sources contain **at most one** `@MapperScan` occurrence (central wiring).

**Step 2: Run test to verify it fails**

Run: `cd backend && mvn test -Dtest=MybatisWiringArchTest`  
Expected: FAIL because `type-aliases-package` and multiple `@MapperScan` currently exist.

---

### Task 2: Centralize mapper scanning (GREEN)

**Files:**
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/bootstrap/config/MybatisBootstrapConfig.java`
- Delete: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/config/MybatisConfig.java`
- Delete: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/config/MybatisConfig.java`
- Delete: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/config/MybatisConfig.java`

**Step 1: Add bootstrap MyBatis config**

- Create a single `@Configuration` class in `com.nowcoder.community.bootstrap.config`.
- Add `@MapperScan(annotationClass = org.apache.ibatis.annotations.Mapper.class, basePackages = "com.nowcoder.community")`.

**Step 2: Remove per-domain mapper scan configs**

- Delete the three domain `MybatisConfig` classes (they only existed to host `@MapperScan`).

**Step 3: Run guardrail test**

Run: `cd backend && mvn test -Dtest=MybatisWiringArchTest`  
Expected: Still FAIL (aliases still present) but mapper scan count should now satisfy the test.

---

### Task 3: Remove type-aliases-package and convert XML mapper types (GREEN)

**Files:**
- Modify: `backend/community-bootstrap/src/main/resources/application.yml`
- Modify: `backend/community-bootstrap/src/test/resources/application.yml`
- Modify: `backend/community-bootstrap/src/main/resources/mapper/*.xml`

**Step 1: Remove alias scanning from YAML**

- Delete `mybatis.type-aliases-package` lines from both YAML files.

**Step 2: Convert XML `resultType` / `parameterType` to fully qualified names**

- Replace each short type name with its fully qualified class name, e.g.:
  - `DiscussPost` → `com.nowcoder.community.content.entity.DiscussPost`
  - `Comment` → `com.nowcoder.community.content.entity.Comment`
  - `User` → `com.nowcoder.community.user.entity.User`
  - `Message` → `com.nowcoder.community.message.entity.Message`
  - etc.

**Step 3: Run guardrail test**

Run: `cd backend && mvn test -Dtest=MybatisWiringArchTest`  
Expected: PASS.

---

### Task 4: Full verification

**Files:**
- Test: whole module

**Step 1: Run full backend tests**

Run: `cd backend && mvn test`  
Expected: PASS.

