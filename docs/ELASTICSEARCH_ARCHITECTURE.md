# Elasticsearch 实现架构与工作原理

本文档描述本项目Elasticsearch搜索引擎的设计实现、工作流程与架构特点。

---

## 1. 总体架构设计

本项目采用**可插拔搜索引擎架构**，Elasticsearch是可选实现之一，通过Spring Boot条件注解开关控制：

```java
@ConditionalOnProperty(name = "search.storage", havingValue = "es")
```

同时提供内存版实现(`InMemoryPostSearchRepository`)用于开发环境调试与单元测试，无需启动ES实例。

---

## 2. 索引设计：蓝绿部署模式

### 2.1 核心设计思想
**不直接操作真实索引，所有读写都通过固定别名访问**

| 角色 | 名称 | 说明 |
|------|------|------|
| 访问别名 | `community_posts_alias` | 业务代码唯一接触的入口，所有读写操作都指向此别名 |
| 真实索引 | `community_posts_vN` | 版本化命名，v1, v2, v3... 每个重建周期创建新版本 |
| 版本前缀 | `community_posts_v` | 索引命名约定 |

### 2.2 文档结构 (`EsPostDocument`)

| 字段 | 类型 | 说明 |
|------|------|------|
| postId | Integer | 业务主键，同时作为ES文档ID |
| userId | Integer | 发布用户ID |
| categoryId | Integer | 分类ID |
| tags | List<String> | Keyword类型，精确匹配 |
| title | String | 分词检索 |
| content | String | 分词检索 |
| type | Integer | 帖子类型 |
| status | Integer | 状态标记 |
| createTime | Long | 存储为毫秒时间戳，避免日期序列化问题 |
| score | Double | 热度排序分 |

> ✅ **最佳实践**：时间字段存储为原始毫秒数，彻底避免Spring Data Elasticsearch与Jackson日期转换不一致导致的查询报错。

---

## 3. 数据同步机制

### 3.1 实时增量同步流程

```mermaid
flowchart LR
    A[帖子写入/更新] --> B[写入 Outbox 表 outbox_event]
    B --> C[OutboxWorkerScheduler (@Scheduled) 轮询并 claim]
    C --> D[PostOutboxHandler]
    D --> E[PostSearchRepository]
    E --> F[Elasticsearch 索引]
    
    style F fill:#2563eb,color:white,stroke:none
    style C fill:#f59e0b,color:white,stroke:none
```

### 3.2 代码实现细节

#### 事件监听处理器
```java
// 对应实现：com.nowcoder.community.search.event.PostOutboxHandler
// 说明：搜索投影为了避免乱序“复活已删除帖子”，会基于当前 DB 状态进行投影（而不是直接信任事件载荷）。
@Component
public class PostOutboxHandler implements OutboxHandler {

    public static final String TOPIC = "projection.search.post";

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public void handle(OutboxEvent event) {
        // 1) 反序列化 payload，拿到 postId
        // 2) 调用 PostScanQueryApi.getPostProjectionAllowDeleted(postId) 获取当前投影
        // 3) status=2 或不存在则 delete(postId)，否则 upsert(projection)
    }
}
```

#### ES写入实现
```java
@Override
public void upsert(PostPayload post) {
    EsPostDocument doc = toDocument(post);
    if (doc == null) {
        return;
    }
    // 通过 alias 写入，自动路由到当前活跃索引（EsPostDocument 的 @Document indexName=INDEX_ALIAS）
    operations.save(doc);
}
```

### 3.3 可靠性保证
1.  **Outbox模式**：所有变更事件先写入数据库Outbox表，事务提交后才会投递，保证事件不丢失
2.  **至少一次投递**：通过 `OutboxWorkerScheduler`（Spring `@Scheduled` 轮询）+ `tryClaimProcessing`（DB 状态机 + 处理 lease）实现多实例安全消费
3.  **幂等处理**：`upsert` 天然幂等；同时搜索投影会基于当前 DB 状态投影，避免乱序事件导致“复活删除内容”
4.  **失败重试**：失败事件会回退为 `PENDING` 并写入 `next_retry_at`，`OutboxWorker` 采用指数退避与最大重试次数
5.  **租约恢复**：`recoverExpiredLeases` 会回收超时的 `PROCESSING` 事件，避免 worker 崩溃导致永久卡住

---

## 4. 全量索引重建机制

### 4.1 零停机重建流程

```
1. 触发重建请求
2. 生成新版本索引名称 community_posts_v{N+1}
3. 创建新索引并应用最新Mapping
4. 全量扫描数据库帖子表
5. 批量写入新版本索引
6. ✅ 原子操作：将别名从旧索引切换到新索引
7. 后台异步删除旧版本索引
```

### 4.2 关键特点
- ✅ 对外服务零中断：切换别名是原子操作，毫秒级完成
- ✅ 重建过程中旧索引继续正常服务
- ✅ 支持并发控制：同一时间只允许一个重建任务运行
- ✅ 失败安全：重建失败不会影响现有服务
- ✅ 支持手动触发与定时调度

---

## 5. 搜索查询实现

### 5.1 查询能力
| 功能 | 实现说明 |
|------|----------|
| 全文检索 | 对标题、内容字段进行分词模糊匹配 |
| 分类过滤 | categoryId 精确匹配 |
| 标签过滤 | 自动处理#前缀，精确匹配标签 |
| 排序策略 | 优先按热度分(score)降序，其次按创建时间降序 |
| 分页安全 | 限制单页最大50条，防止深度分页攻击 |
| 关键词高亮 | 对匹配关键词自动添加高亮标记 |

### 5.2 降级策略
当没有提供搜索关键词时，自动退化为 Match All 查询，兼容纯分类/标签过滤场景。

---

## 6. 运维管理

### 6.1 管理接口
| 接口 | 说明 |
|------|------|
| `POST /api/ops/search/reindex` | 手动触发全量重建 |
| 定时触发 | 可由外部调度器/脚本定时调用该接口（或通过平台任务触发） |

### 6.2 监控与观测
- 完整的日志埋点
- 索引重建进度跟踪
- 异常告警集成

---

## 7. 架构最佳实践

本项目ES集成体现了以下工业级设计原则：

### ✅ 故障隔离
ES集群完全故障不会导致主站不可用，业务读写不受影响

### ✅ 无阻塞架构
所有ES操作100%异步化，不阻塞数据库事务，不影响用户请求响应时间

### ✅ 平滑升级
索引结构变更、Mapping更新不需要停机，通过版本化索引+别名切换实现灰度发布

### ✅ 可测试性
内存版实现支持完整单元测试、集成测试，不需要外部依赖

### ✅ 最终一致性
在性能与一致性之间取得合理平衡，保证数据最终同步到ES

---

## 8. 部署配置

### 单机模式
```yaml
search:
  storage: es
  index.prefix: community_posts_v
```

### 集群模式
支持ES集群部署，通过Spring Data Elasticsearch标准配置连接。
