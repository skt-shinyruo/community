# Trusted Client IP Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立从 NGINX、Gateway 到 Servlet 应用的一致可信代理模型，阻止伪造转发 Header 绕过限流、风控和帖子浏览指纹。

**Architecture:** `common-core` 提供不依赖 Spring/Servlet/WebFlux 的纯 `TrustedProxyChain`，只解析 IP literal 和 CIDR，不触发 DNS。NGINX 覆盖客户端转发 Header；Gateway 先在安全过滤器之前根据直接对端和可信 CIDR 从右向左剥离代理，删除全部传入转发 Header并写入单一规范 `X-Forwarded-For`；限流再紧接 Spring Security 运行以确保认证 principal 可见；Servlet `ClientIpResolver` 使用同一算法。Controller 只消费 resolver 结果。

**Tech Stack:** Java 21、Spring WebFlux Gateway、Servlet API、JUnit 5、MockMvc、Reactor Test、NGINX 配置、Maven。

---

## 信任模型

```text
untrusted client headers
  -> NGINX: discard Forwarded/XFF/X-Real-IP; XFF=$remote_addr
  -> Gateway socket peer must be trusted NGINX
       -> strip trusted hops from right to left
       -> canonical client = first untrusted hop
       -> rebuild XFF=<canonical client>
  -> Application socket peer must be trusted Gateway
       -> accept one canonical XFF literal
       -> otherwise use socket peer
```

不可信直接对端发送任何 `Forwarded`/`X-Forwarded-For`/`X-Real-IP` 时均忽略。IPv4-mapped IPv6 必须得到稳定规范形式；hostname、zone id、端口、控制字符、空 hop 和超长链必须拒绝或忽略，不能调用 DNS。

## 纯 IP/CIDR 领域无关组件

### 写解析器 RED 测试

**Files:**

- Create: `backend/community-common/common-core/src/main/java/com/nowcoder/community/common/net/TrustedProxyChain.java`
- Create: `backend/community-common/common-core/src/test/java/com/nowcoder/community/common/net/TrustedProxyChainTest.java`

- [ ] 参数化测试 IPv4、压缩 IPv6、IPv4-mapped IPv6、`/0`、`/32`、`/128` 和不匹配地址族。
- [ ] 断言 `example.com`、`1.2.3.4:80`、`[::1]:80`、`fe80::1%eth0`、空 hop、前后控制字符和非法 prefix 不触发 DNS，只返回无效结果。
- [ ] 测试以下信任链：

  ```text
  remote=203.0.113.9 (untrusted), XFF=198.51.100.1      => 203.0.113.9
  remote=10.0.0.5 (trusted), XFF=198.51.100.1          => 198.51.100.1
  remote=10.0.0.5, XFF=198.51.100.1, 10.0.0.4         => 198.51.100.1
  remote=10.0.0.5, XFF=192.0.2.66, 198.51.100.1,10... => 198.51.100.1
  ```

  第四行证明攻击者伪造的左侧前缀不会胜出：算法从右向左跳过可信代理，选择最靠右的第一个不可信 hop。
- [ ] 固定公共 API：

  ```java
  public final class TrustedProxyChain {
      public TrustedProxyChain(List<String> trustedCidrs);
      public Resolution resolve(String directPeer, List<String> forwardedFor);

      public record Resolution(String clientIp, Source source) {}
      public enum Source { DIRECT_PEER, FORWARDED_CHAIN }
  }
  ```

- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-common-core -am -Dtest=TrustedProxyChainTest test
  ```

  预期：编译因类不存在而失败。

### 实现无 DNS 的 literal parser

- [ ] 手工解析 IPv4 四段十进制和 IPv6 十六进制/`::` 压缩；只在完成字符级白名单和结构校验后使用地址字节，不调用可能解析 hostname 的 `InetAddress.getByName(String)`。
- [ ] CIDR 在构造器中一次性解析为不可变 network/mask；配置包含任何非法 CIDR 时抛出 `IllegalArgumentException`，避免生产静默扩大信任。
- [ ] Header 最大处理 32 个 hop、每项最大 64 字符；超限 Resolution 回退 direct peer 并由调用方记录 malformed metric。
- [ ] 从右向左遍历 `forwardedFor + directPeer`：只剥离位于 trusted CIDR 的 hop，遇到第一个不可信 literal 即返回；direct peer 不可信时完全忽略 Header。
- [ ] 运行 GREEN：

  ```bash
  cd backend
  mvn -pl :community-common-core -am -Dtest=TrustedProxyChainTest test
  ```

  预期：全部解析与链路用例通过。

- [ ] 提交纯组件：

  ```bash
  git add backend/community-common/common-core/src/main/java/com/nowcoder/community/common/net \
          backend/community-common/common-core/src/test/java/com/nowcoder/community/common/net
  git commit -m "feat(web): add trusted proxy chain resolver"
  ```

## Servlet resolver

### 写 `ClientIpResolver` RED 测试

**Files:**

- Modify: `backend/community-common/common-web/pom.xml`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/net/ClientIpResolver.java`
- Modify: `backend/community-common/common-web/src/main/java/com/nowcoder/community/common/web/net/TrustedProxyProperties.java`
- Create: `backend/community-common/common-web/src/test/java/com/nowcoder/community/common/web/net/ClientIpResolverTest.java`

- [ ] 使用 `MockHttpServletRequest` 覆盖 disabled、空 CIDR、不可信 direct peer、可信单跳、可信多跳、伪造左前缀、畸形 Header、IPv4/IPv6。
- [ ] 断言 source 保持稳定字符串：direct peer 为 `remote`，有效转发链为 `xff`；无法解析时不得返回 hostname。
- [ ] 加一个不解析 hostname 的测试：输入随机 `.invalid` 域名，在固定短时限内完成并返回 direct/null，不依赖外部 DNS 状态。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-common-web -am -Dtest=ClientIpResolverTest test
  ```

  预期：现有 resolver 选择 XFF 第一项，伪造左前缀断言失败。

### 让 Servlet adapter 委托共享算法

- [ ] `common-web` 显式依赖 `common-core`；`ClientIpResolver` 删除私有 `firstIp`、`normalizeIp`、`cidrMatch`。
- [ ] `TrustedProxyProperties` 启用时构造 `TrustedProxyChain(properties.getCidrs())`；disabled 时只使用 request socket peer。
- [ ] 把 `request.getHeaders("X-Forwarded-For")` 的所有行按逗号展开为有序 hop，不能只读第一个 Header 行。
- [ ] 保持返回契约 `ResolvedClientIp(String ip, String source)`，避免认证/验证码调用者无关改动。
- [ ] 再运行 `ClientIpResolverTest`，预期全部通过。
- [ ] 提交 Servlet resolver：

  ```bash
  git add backend/community-common/common-web
  git commit -m "fix(web): resolve servlet client ip from trusted chain"
  ```

## Gateway 规范化与限流

### 写 Gateway RED 测试

**Files:**

- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/ForwardedHeaderCanonicalizationWebFilter.java`
- Create: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/EdgeTrustedProxyProperties.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/ForwardedHeaderCanonicalizationWebFilterTest.java`
- Create: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/ForwardedHeaderRoutingIntegrationTest.java`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge/RateLimitWebFilterTest.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/EdgeConfig.java`
- Modify: `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge/RateLimitWebFilter.java`

- [ ] WebFilter 测试构造 socket remote address 和多个 `Forwarded`/XFF/X-Real-IP Header，断言 downstream request 只保留：

  ```http
  X-Forwarded-For: 198.51.100.1
  ```

  并删除 `Forwarded`、`X-Real-IP` 以及额外 XFF 值。
- [ ] 断言不可信 direct peer 时 canonical XFF 等于 socket peer，不等于攻击者 Header。
- [ ] `RateLimitWebFilterTest` 断言匿名 key 使用 canonical address：`ip:198.51.100.1:<path>`；认证 principal key 不变。
- [ ] 断言 canonicalization filter order 早于 rate limit filter，可通过 `Ordered.getOrder()` 精确比较。
- [ ] 路由 integration test 启动真实 Gateway filter chain 和本地 downstream stub，断言 downstream 最终只收到一个 canonical XFF，且没有 `Forwarded`/`X-Real-IP`；该测试必须能发现 Spring Cloud Gateway 自带 header filter 的二次追加。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-gateway -am \
    -Dtest='ForwardedHeaderCanonicalizationWebFilterTest,ForwardedHeaderRoutingIntegrationTest,RateLimitWebFilterTest' test
  ```

  预期：canonicalization 类不存在；现有限流仍使用 socket address。

### 实现 Gateway filter

- [ ] `EdgeTrustedProxyProperties` 使用 `gateway.trusted-proxy`，字段与 Servlet 配置的 `enabled/cidrs` 同义；在 `EdgeConfig` 的 `@EnableConfigurationProperties` 注册。
- [ ] `EdgeConfig` 显式创建 `ForwardedHeaderCanonicalizationWebFilter` bean，并把同一个 canonical client attribute 提供给限流；不能只注册 properties 而漏掉运行时 filter。
- [ ] `ForwardedHeaderCanonicalizationWebFilter` 实现 `Ordered`，保持 `HIGHEST_PRECEDENCE + 20` 并先于安全过滤器清洗 Header；`RateLimitWebFilter` **不再使用原先的 `HIGHEST_PRECEDENCE + 30`**，改为 `SecurityProperties.DEFAULT_FILTER_ORDER + 1`（当前为 `-99`），确保 Spring Security 已经写入 authenticated principal 后再生成限流 key。
- [ ] filter 用 `ServerHttpRequest.mutate().headers(...)` 原子清除并重建 Header；不能修改原始只读 headers map。
- [ ] 把 canonical address 放入 exchange attribute 常量，例如：

  ```java
  public static final String CANONICAL_CLIENT_IP_ATTRIBUTE =
          ForwardedHeaderCanonicalizationWebFilter.class.getName() + ".clientIp";
  ```

- [ ] `RateLimitWebFilter.remoteAddressKey` 先读取该 attribute；缺失时 fail closed 到 socket peer，不能重新自行解析 XFF。
- [ ] 增加 malformed/truncated chain counter，但 metric tag 只能是低基数 outcome，不能把 IP 放进 tag。
- [ ] 在 Gateway 两份运行时配置中同时设置：

  ```yaml
  spring:
    cloud:
      gateway:
        forwarded:
          enabled: false
        x-forwarded:
          enabled: false
  ```

  关闭 Spring Cloud Gateway 4.1 的 `ForwardedHeadersFilter` 和 `XForwardedHeadersFilter`，防止它们在自定义 WebFilter 后再次添加 `Forwarded` 或把 NGINX peer 追加进 XFF。application-context test 断言这两个 bean 不存在。
- [ ] 再运行两组 Gateway 测试，预期全部通过。
- [ ] 提交 Gateway 规范化：

  ```bash
  git add backend/community-gateway/src/main/java/com/nowcoder/community/gateway/edge \
          backend/community-gateway/src/test/java/com/nowcoder/community/gateway/edge
  git commit -m "fix(gateway): canonicalize forwarded client address"
  ```

## 帖子浏览指纹

### 写 Controller RED 测试

**Files:**

- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java`

- [ ] 在 `PostControllerUnitTest` mock `ClientIpResolver` 返回 `198.51.100.1/xff`，请求同时携带伪造 `X-Forwarded-For: 192.0.2.66`，捕获 `RecordPostViewCommand` 并断言 fingerprint 包含 resolver 地址、不含伪造地址。
- [ ] 断言登录用户 fingerprint 仍为 `auth:<uuid>`，不会把 IP 混入登录身份。
- [ ] 运行 RED：

  ```bash
  cd backend
  mvn -pl :community-app -am -Dtest=PostControllerUnitTest test
  ```

  预期：Controller 私有 `remoteAddress()` 当前信任 XFF 第一项，断言失败。

### 注入 shared resolver

- [ ] `PostController` 构造器增加 `ClientIpResolver`，`viewerFingerprint` 改为实例方法并调用 `clientIpResolver.resolve(request)`。
- [ ] 删除 `remoteAddress(HttpServletRequest)` 及 `StringUtils` import；user-agent 只作为原有 fingerprint 的非权威补充，不参与 IP 信任判定。
- [ ] resolver 返回 null 时使用空字符串或稳定 `unknown`，不能重新读取 Header。
- [ ] 再运行 `PostControllerUnitTest`，预期通过。
- [ ] 提交 Controller 消费者：

  ```bash
  git add backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java \
          backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerUnitTest.java
  git commit -m "fix(content): use canonical client ip for view identity"
  ```

## NGINX 与运行时配置

### 写配置 RED 检查

**Files:**

- Modify: `deploy/nginx/nginx.single.conf`
- Modify: `deploy/nginx/nginx.cluster.conf`
- Modify: `deploy/nacos/config/community-shared.yaml`
- Modify: `backend/community-gateway/src/main/resources/application.yml`
- Modify: `deploy/nacos/config/community-gateway.yaml`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `deploy/nacos/config/community-app.yaml`
- Create: `deploy/tests/trusted_proxy_headers.sh`

- [ ] 脚本断言两份 NGINX 配置均不含 `$proxy_add_x_forwarded_for`，并对每个 proxy location 明确隐藏客户端 Header和设置 `$remote_addr`。
- [ ] 脚本断言 Nacos/应用配置没有 `0.0.0.0/0`、`::/0`，Gateway 的 `forwarded.enabled`/`x-forwarded.enabled` 均为 false，且 production profile 的 Gateway 信任 NGINX 网络、Community 应用只信任 Gateway 网络。
- [ ] 运行 RED：

  ```bash
  bash deploy/tests/trusted_proxy_headers.sh
  ```

  预期：现有 NGINX 中 `$proxy_add_x_forwarded_for` 导致失败。

### 清洗边缘 Header 并配置真实 CIDR

- [ ] 在两个 NGINX 配置的 API/files 代理 location 加入：

  ```nginx
  proxy_set_header Forwarded "";
  proxy_set_header X-Real-IP $remote_addr;
  proxy_set_header X-Forwarded-For $remote_addr;
  ```

- [ ] Gateway 的 trusted CIDR 只配置 NGINX 容器/负载均衡器网络；Servlet 应用的 trusted CIDR 只配置 Gateway 网络。local 默认可 disabled，但部署配置必须列出真实网络。
- [ ] 容器网络可能变化时使用受控环境变量注入 CIDR，并在启动日志只打印 CIDR 数量/配置来源，不打印请求 IP 列表。
- [ ] 再运行脚本，预期退出码 `0`。
- [ ] 提交部署配置：

  ```bash
  git add deploy/nginx deploy/nacos/config \
          backend/community-gateway/src/main/resources/application.yml \
          backend/community-app/src/main/resources/application.yml \
          deploy/tests/trusted_proxy_headers.sh
  git commit -m "fix(deploy): sanitize forwarded client headers"
  ```

## 验证与发布

- [ ] 运行聚焦测试：

  ```bash
  cd backend
  mvn -pl :community-common-core,:community-common-web,:community-gateway,:community-app -am \
    -Dtest='TrustedProxyChainTest,ClientIpResolverTest,ForwardedHeaderCanonicalizationWebFilterTest,ForwardedHeaderRoutingIntegrationTest,RateLimitWebFilterTest,PostControllerUnitTest' test
  ```

  预期：`BUILD SUCCESS`。

- [ ] 运行架构守卫：

  ```bash
  cd backend
  mvn test -pl :community-app -Dtest='*ArchTest'
  ```

  预期：Controller 仅调用同域 ApplicationService；`ClientIpResolver` 作为 shared transport helper 不引入 domain/infrastructure 依赖。

- [ ] 运行静态扫描：

  ```bash
  rg -n 'proxy_add_x_forwarded_for|getHeader\("X-Forwarded-For"\)|InetAddress\.getByName' \
    deploy/nginx \
    backend/community-gateway/src/main \
    backend/community-app/src/main/java/com/nowcoder/community/content/controller \
    backend/community-common/common-web/src/main
  ```

  预期：没有 `$proxy_add_x_forwarded_for`、Controller 直接读 XFF 或 resolver DNS 解析；Gateway canonicalizer 自身读取 XFF 是允许的唯一匹配，应人工确认其从右向左调用共享组件。

- [ ] 集成环境从不可信客户端发送 `X-Forwarded-For: 1.1.1.1, 2.2.2.2`，确认 Gateway/access log/rate-limit key/帖子浏览 fingerprint 使用真实客户端地址且彼此一致。
- [ ] 发布必须先更新 NGINX/Gateway 清洗，再启用 Servlet trusted CIDR；反向顺序会短暂让应用信任未清洗 Header，禁止执行。
- [ ] `git diff --check`，预期无 whitespace 错误。
