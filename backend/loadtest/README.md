# 压测（k6）使用说明

本目录用于给微服务体系提供可重复执行的压测脚本与容量基线记录模板。

## 1) 前置条件

- 服务已启动（建议使用 docker compose 全依赖模式）。
- 网关地址可访问（默认 `http://localhost:8080`）。
- 具备可用测试账号（默认使用 `aaa/aaa`，并依赖 `bbb` 作为私信目标）。

## 2) 运行方式

### 方式 A：本机安装 k6

```bash
k6 version
k6 run loadtest/k6/community-baseline.js
```

### 方式 B：Docker 方式运行 k6（推荐）

```bash
docker run --rm -i grafana/k6:0.49.0 run - < loadtest/k6/community-baseline.js
```

## 3) 环境变量

- `BASE_URL`：网关地址（默认 `http://localhost:8080`）
- `USERNAME` / `PASSWORD`：登录账号（默认 `aaa/aaa`）
- `TO_NAME`：私信目标用户名（默认 `bbb`）
- `DURATION`：每个场景持续时间（默认 `30s`）
- `VUS`：每个场景 VU 数（默认 `5`）

示例：

```bash
BASE_URL=http://localhost:8080 USERNAME=aaa PASSWORD=aaa VUS=10 DURATION=2m \
  k6 run loadtest/k6/community-baseline.js
```

## 4) 输出与基线记录

- k6 默认会输出 p(90)/p(95)/p(99) 等统计信息。
- 建议把每次跑出来的关键结果同步到：
  - `helloagents/plan/202601170935_legacy_cutover_prod_parity/why.md#success-metrics`
  - `helloagents/wiki/` 的性能与发布门禁章节（见后续任务 16.1）

