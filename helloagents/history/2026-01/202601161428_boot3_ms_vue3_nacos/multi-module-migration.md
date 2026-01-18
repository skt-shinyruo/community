# Maven 多模块改造与目录迁移 Runbook（迭代 0）

Directory: `helloagents/plan/202601161428_boot3_ms_vue3_nacos/`

> 目标：把当前单体仓库改造成 Maven 多模块，为后续微服务拆分提供“可渐进、可回滚”的结构。  
> 原则：**小步提交、每步可编译、失败可回退**。

---

## 1. 迁移策略（推荐）

### 1.1 目标模块结构（迭代 0 最小集）
- `common`：通用返回结构、错误码、异常处理、traceId 工具、事件 envelope（后续）
- `gateway`：Spring Cloud Gateway
- `auth-service`：JWT 登录/刷新/登出
- `legacy-community`：把当前单体“下沉为一个模块”，迁移期承载旧逻辑（Boot 3 下可启动）

---

## 2. 分步迁移（每一步都要能编译）

### Step 0：准备（已完成）
- 创建独立分支：`feat/202601161428-boot3-ms-vue3-nacos`

### Step 1：根 POM 改造为父工程（不移动任何代码）
**目标：** 新增模块清单与 dependencyManagement/pluginManagement，但旧代码先不动。  
**验证：** `mvn -q -DskipTests package` 通过。

建议拆分提交：
- commit A：根 `pom.xml` 改为 packaging=pom（保留 groupId/version），新增 `<modules>`（模块可以先空目录）
- commit B：新增 `common/pom.xml`、`gateway/pom.xml`、`auth-service/pom.xml`、`legacy-community/pom.xml`（先最小化依赖）

### Step 2：创建 legacy-community 模块（仍不搬 src/）
**目标：** `legacy-community` 模块能独立编译（先空壳）。  
**验证：** `mvn -q -pl legacy-community -am test` 通过。

### Step 3：搬迁 Java 源码（先 main/java）
**目标：** `src/main/java/**` → `legacy-community/src/main/java/**`。  
**验证：** `mvn -q -pl legacy-community -am test` 通过。

注意点：
- `CommunityApplication` 启动类路径不变（包名不变），仅目录层级变更
- Spring Boot 扫描基于包名，不基于目录；目录迁移不会影响扫描，但资源/配置会

### Step 4：搬迁资源文件（main/resources）
**目标：** `src/main/resources/**` → `legacy-community/src/main/resources/**`（templates/static/mapper/logback 等）。  
**验证：**
- `mvn -q -pl legacy-community -am test` 通过
- 启动 `legacy-community` 无 classpath 资源缺失报错（mapper、templates 等）

重点检查清单：
- `mapper/*.xml` 是否仍可被 MyBatis 扫描（需要配置 `mybatis.mapper-locations`）
- `templates/` 与 `static/` 是否仍在 classpath
- `logback-spring*.xml` 是否仍可被加载

### Step 5：搬迁测试（src/test）
**目标：** `src/test/**` → `legacy-community/src/test/**`。  
**验证：** `mvn -q test` 通过（或先 `-DskipTests`，再逐步修复测试）。

### Step 6：根目录清理与适配
**目标：** 根目录不再存在 `src/`；构建产物路径与 wrapper 正常。  
**验证：** 全量 `mvn -q -DskipTests package` 通过。

---

## 3. 回滚策略

### 3.1 单步回滚
- 若某一步失败且不易修复：直接 `git reset --hard <上一步commit>` 回到可编译状态

### 3.2 保持“每步可编译”的原因
- 迁移期不可避免会出现依赖兼容问题；若没有可编译基线，将很难定位问题是“结构迁移”还是“版本升级”导致

---

## 4. 验证命令建议（迭代 0）

- 全量编译（不跑测试）：`mvn -q -DskipTests package`
- 单模块编译（含依赖）：`mvn -q -pl legacy-community -am test`
- 构建所有服务模块：`mvn -q -pl gateway,auth-service,common -am test`
