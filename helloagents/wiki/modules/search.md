# search

## Purpose
提供帖子全文检索能力，并与发帖/删帖事件联动维护索引（迭代 1 起迁移到 `search-service`）。

## Module Overview
- **Responsibility：** ES 索引维护（保存/删除）；按关键字搜索；高亮 title/content
- **Status：** 🟡In Progress
- **Last Updated：** 2026-01-16

## Specifications

### Requirement: 全局搜索
**Module:** search
用户可根据关键字搜索帖子，并高亮匹配内容。

#### Scenario: 搜索帖子并展示高亮
- 返回搜索结果与高亮字段
- 支持按置顶/分数/时间排序

### Requirement: 索引同步
**Module:** search
发帖/删帖后索引自动更新。

#### Scenario: 消费发帖事件写入索引
- Kafka 消费 publish 事件
- 保存帖子到 ES

#### Scenario: 消费删帖事件删除索引
- Kafka 消费 delete 事件
- 从 ES 删除帖子

## API Interfaces（现状）
- `GET /api/search/posts?keyword=xxx`
- `POST /api/search/internal/reindex`（仅管理员；用于迁移期 reindex）
- `POST /internal/search/reindex`（服务内部入口：需要 `X-Internal-Token`）

## Data Models
### Elasticsearch: discuss_post
（详见 `helloagents/wiki/data.md` 的 “Elasticsearch 索引” 小节）

## Dependencies
- content（帖子数据源）
- infra（Kafka、Elasticsearch）
- MySQL（迁移期用于 reindex 冷启动）

## Change History
- （暂无）
