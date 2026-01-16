# search

## Purpose
提供帖子全文检索能力，并与发帖/删帖事件联动维护索引。

## Module Overview
- **Responsibility：** ES 索引维护（保存/删除）；按关键字搜索；高亮 title/content
- **Status：** ✅Stable
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
- `GET /search?keyword=xxx`

## Data Models
### Elasticsearch: discuss_post
（详见 `helloagents/wiki/data.md` 的 “Elasticsearch 索引” 小节）

## Dependencies
- content（帖子数据源）
- infra（Kafka、Elasticsearch）

## Change History
- （暂无）
