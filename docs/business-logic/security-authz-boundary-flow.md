# 安全链路与授权边界实现说明

这篇文档只讲 `community-app` 当前代码里的安全执行规则，不重复讲 gateway、浏览器入口或部署拓扑。

如果你想看更广义的整体安全模型，可以同时参考：

- `docs/SECURITY.md`

本文关注的是三个更贴近代码的问题：

- `community-app` 里到底有几条 `SecurityFilterChain`
- `/api/**`、`/files/**`、`/actuator/**` 分别由谁保护
- 各业务域如何声明自己的授权矩阵，未声明的接口最终会怎样

## 1. 先给结论

当前 `community-app` 的安全边界有两层：

- `/actuator/**` 由基础设施 filter chain 单独保护
- `/api/**` 和 `/files/**` 由主站业务 filter chain 统一保护

在主站业务面里，授权矩阵不是散落在 controller 上，而是统一收口到：

- `CommunitySecurityConfig`
- `ApiSecurityRules`

各业务模块只负责补充自己的 request matcher 规则；没有显式放开的接口，最终都会落到：

- `anyRequest().authenticated()`

## 2. 两条 SecurityFilterChain 是怎么分工的

## 2.1 Actuator 链

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/ServletInfraSecurityConfig.java`

这条链的特点：

- `@Order(1)`
- `securityMatcher("/actuator/**")`

说明它优先于主站业务链执行，并且只接管：

- `/actuator/**`

### 2.1.1 访问规则

当前 actuator 策略很明确：

- `OPTIONS` 放行
- `/actuator/health`、`/actuator/info` 放行
- `/actuator/prometheus` 需要 `ROLE_PROMETHEUS`
- 其余 `/actuator/**` 全拒绝

这意味着 actuator 不是“登录后都能看”，而是默认 deny all。

### 2.1.2 Prometheus 账号是怎么来的

同一个类里还会创建：

- `UserDetailsService prometheusUserDetailsService`

它从 `MetricsBasicAuthProperties` 读取用户名密码，并做两个 fail-closed 校验：

- 密码不能为空
- 密码 UTF-8 字节长度至少 12

也就是说，如果要暴露 Prometheus 指标：

- 必须显式配置一个足够强的 basic auth 密码

不会悄悄回退到弱默认值。

## 2.2 主站业务链

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java`

这条链的特点：

- `@Order(2)`
- `securityMatcher("/api/**", "/files/**")`
- stateless
- JWT resource server

它负责承接主站所有业务 HTTP 请求。

### 2.2.1 统一基础策略

这条链统一做了这些事：

- 开启 CORS
- 关闭 CSRF
- `SessionCreationPolicy.STATELESS`
- 认证失败 / 鉴权失败都走 `SecurityExceptionHandler`
- OAuth2 resource server + JWT

也就是说：

- 主站没有服务端 session
- 用户身份完全来自 bearer token
- 安全错误响应格式是统一的，不是各 controller 自己处理

### 2.2.2 一个容易忽略的细节

`CommunitySecurityConfig` 里还写了：

- `GET /actuator/health`
- `GET /actuator/info`

的 `permitAll`

但这条链自己的 matcher 只覆盖：

- `/api/**`
- `/files/**`

所以真正生效的 actuator 策略，仍然以 `ServletInfraSecurityConfig` 为准。

这两条 `requestMatchers` 在当前运行时属于冗余声明。

## 3. JWT 权限是怎么解析的

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/jwt/AuthoritiesConverterFactory.java`

## 3.1 当前约定

当前项目约定 JWT 的：

- `authorities` claim

是一个字符串数组，例如：

- `["ROLE_ADMIN", "ROLE_USER"]`

### 3.1.1 当 claim 真的是数组时

如果 `authorities` 是 `List<?>`：

- 逐个转字符串
- 去空白
- 直接包成 `SimpleGrantedAuthority`

这里不会再额外补 `ROLE_` 前缀。

所以 token 里存什么，最终 authority 就是什么。

### 3.1.2 fallback 行为

如果 `authorities` 不是列表，才会走 Spring 默认的：

- `JwtGrantedAuthoritiesConverter`

并按：

- claim name = `authorities`
- prefix = `ROLE_`

做兜底转换。

## 3.2 这意味着什么

当前主路径最稳妥的做法是：

- 在 token 中直接放完整的 `ROLE_*` 字符串数组

否则不同 claim 形态可能出现解析差异。

## 4. 当前用户信息为什么不在 controller 里自己 parse

核心类：

- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/auth/CurrentUser.java`

这个工具类统一负责：

- `requireJwt`
- `requireUserId`
- `tryUserId`
- `requireUserUuid`
- `tryUserUuid`

## 4.1 为什么它重要

因为当前系统里既有：

- `int userId`
- `UUID userId`

两种 subject 读取方式。

如果每个 controller 自己转：

- 容易出现重复逻辑
- 容易出现 subject 非法时返回语义不一致

`CurrentUser` 把这层规则统一成了：

- 缺认证信息 -> `UNAUTHORIZED`
- subject 非法 -> `INVALID_ARGUMENT`

## 5. 授权矩阵是怎么组装出来的

核心接口：

- `backend/community-app/src/main/java/com/nowcoder/community/app/security/ApiSecurityRules.java`

它是一个很轻的 SPI：

- 每个业务域提供一个 bean
- 在 `CommunitySecurityConfig` 中按顺序注入
- 逐个向同一个 `AuthorizationManagerRequestMatcherRegistry` 注册规则

这意味着授权矩阵的 SSOT 是：

- 一组按 `@Order` 排序的 `ApiSecurityRules`

不是分散在注解或 controller 里。

## 6. 各业务域当前声明了哪些规则

当前实现了 `ApiSecurityRules` 的类有：

- `AuthSecurityRules`
- `UserSecurityRules`
- `WalletSecurityRules`
- `MarketSecurityRules`
- `ContentSecurityRules`
- `SocialSecurityRules`
- `SearchSecurityRules`
- `AnalyticsSecurityRules`
- `OpsSecurityRules`

## 6.1 Auth

类：

- `backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java`

顺序：

- `@Order(10)`

放开的接口包括：

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `POST /api/auth/register`
- `POST /api/auth/register/code/resend`
- `POST /api/auth/register/code/verify`
- `GET /api/auth/captcha`
- `POST /api/auth/captcha/verify`
- `POST /api/auth/password/reset/request`
- `POST /api/auth/password/reset/confirm`

因此 auth 域的匿名入口是显式声明的，不靠默认放行。

## 6.2 User

类：

- `backend/community-app/src/main/java/com/nowcoder/community/user/security/UserSecurityRules.java`

顺序：

- `@Order(20)`

规则：

- `GET /files/**` 公开
- `/api/users/admin/**` 仅 `ADMIN`
- `GET /api/users/*` 公开
- `GET /api/users/*/recent-posts`、`recent-comments` 公开
- `POST /api/users/batch-summary` 公开

这意味着：

- 文件读取公开
- 头像上传 / 确认不公开，因为它们走 `/api/users/**` 写接口，最终需要认证并在业务层再次校验“只能改自己”

## 6.3 Wallet

类：

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/security/WalletSecurityRules.java`

顺序：

- `@Order(24)`

规则：

- `/api/wallet/admin/**` 仅 `ADMIN`
- `/api/wallet/**` 需要认证

所以钱包域没有公开读接口。

## 6.4 Market

类：

- `backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java`

顺序：

- `@Order(26)`

规则：

- `/api/admin/market/**` 仅 `ADMIN`
- `GET /api/market/listings`、`GET /api/market/listings/*` 公开
- 其余 `/api/market/**` 全部需要认证

## 6.5 Content

类：

- `backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java`

顺序：

- `@Order(30)`

公开读：

- 分类
- 热门标签 / 标签详情
- 帖子列表 / 帖子详情 / 评论 / 回复
- `POST /api/posts/batch-summary`

管理权限：

- `POST /api/posts/*/top`
- `POST /api/posts/*/wonderful`
- `POST /api/posts/*/delete`
- `/api/moderation/**`

需要 `ADMIN` 或 `MODERATOR`

这也意味着：

- 发帖
- 评论
- 收藏
- 订阅
- 举报

这些没被显式放开的写接口，都会落回默认认证要求。

## 6.6 Social

类：

- `backend/community-app/src/main/java/com/nowcoder/community/social/security/SocialSecurityRules.java`

顺序：

- `@Order(40)`

公开读：

- 点赞计数
- 关注 / 粉丝列表
- 关注 / 粉丝计数

未显式放开的社交写接口，例如：

- 点赞写
- 关注写
- 拉黑写

都要求认证。

## 6.7 Search

类：

- `backend/community-app/src/main/java/com/nowcoder/community/search/security/SearchSecurityRules.java`

顺序：

- `@Order(50)`

公开：

- `GET /api/search/posts`

## 6.8 Analytics

类：

- `backend/community-app/src/main/java/com/nowcoder/community/analytics/security/AnalyticsSecurityRules.java`

顺序：

- `@Order(60)`

规则：

- `/api/analytics/**` 仅 `ADMIN` 或 `MODERATOR`

## 6.9 Ops

类：

- `backend/community-app/src/main/java/com/nowcoder/community/ops/security/OpsSecurityRules.java`

顺序：

- `@Order(70)`

规则：

- `/api/ops/**` 仅 `ADMIN`

## 7. 没写规则的接口最后会怎样

这是理解当前代码最关键的一点：

- `ApiSecurityRules` 不是白名单总表
- 它只声明例外和强化规则

最后 `CommunitySecurityConfig` 会统一补一条：

- `anyRequest().authenticated()`

所以凡是落在 `/api/**` 或 `/files/**` 下，但没有显式 `permitAll` 的接口，默认都需要认证。

典型例子：

- `/api/im-governance/**`
- `/api/likes/**` 的写接口
- `/api/follows/**` 的写接口
- `/api/users/{userId}/avatar/**`
- `/api/posts` 的创建接口

这类接口很多都不是在 security rules 里单独声明“authenticated”，而是靠默认兜底生效。

## 8. 路径授权和业务内校验是两层东西

当前代码里经常同时存在两层保护：

### 8.1 第一层：路径级授权

由 `ApiSecurityRules` 决定：

- 这个接口是否匿名可进
- 是否要求 `ADMIN`
- 是否要求 `MODERATOR`

### 8.2 第二层：业务内主体校验

进入 service / controller 之后，还会继续做：

- 当前用户是否就是资源 owner
- 当前用户能否操作目标用户
- 当前状态是否允许执行该动作

例如头像接口虽然只要求“已登录”，但 controller 里还会检查：

- `userId.equals(currentUserId)`

所以不能把“authenticated”误解成“任何登录用户都能操作”。

## 9. 当前安全链路的几个重要取舍

## 9.1 权限矩阵收口在 app，不分散在每个模块里

这是为了避免：

- 各模块各写各的 filter chain
- request matcher 相互覆盖
- 不同服务返回不同错误语义

## 9.2 JWT subject 解析集中到 `CurrentUser`

这是为了避免：

- int / UUID 两套 subject 语义在不同模块漂移

## 9.3 actuator 和业务面严格分链

这是为了避免：

- Prometheus / 健康检查规则污染业务接口
- 业务 JWT 认证逻辑误套到 actuator

## 9.4 fail-closed 比便利更重要

从当前代码能明显看到几个偏 fail-closed 的选择：

- Prometheus 密码没配或太短直接抛错
- 未声明放开的业务接口默认要求认证
- `/actuator/**` 默认 deny all

## 10. 当前文档与已有安全总文档的关系

如果你想理解：

- 浏览器为什么默认走 gateway
- OriginGuard、CORS、trusted proxy、审计日志的全局边界

请继续看：

- `docs/SECURITY.md`

本文只解释 `community-app` 进程内部“接口最终怎么被放行或拒绝”。

## 11. 关键代码定位

- `backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java`
- `backend/community-app/src/main/java/com/nowcoder/community/app/security/ApiSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/security/AuthSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/security/UserSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/security/WalletSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/security/MarketSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/security/ContentSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/security/SocialSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/security/SearchSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/security/AnalyticsSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/security/OpsSecurityRules.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/ServletInfraSecurityConfig.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/jwt/AuthoritiesConverterFactory.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/security/auth/CurrentUser.java`
