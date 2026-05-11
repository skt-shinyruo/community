#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'

const DEFAULT_OUTPUT_DIR = 'target/community-playwright-regression/reports'
const DEFAULT_ARTIFACT_DIR = 'target/community-playwright-regression/artifacts'
const VALID_MODES = new Set(['full', 'targeted'])
const VALID_STATUSES = new Set(['IN_PROGRESS', 'PASS', 'FAIL', 'BLOCKED'])

function usage() {
  return [
    'Usage: node .agents/skills/community-playwright-regression/scripts/create-run-report.mjs [options]',
    '',
    'Options:',
    '  --mode <full|targeted>      Regression mode, default full',
    '  --scope <text>              Targeted run scope or changed area',
    '  --run-id <id>               Run id, default pw-YYYYMMDDHHMMSS',
    '  --status <status>           Initial status, default IN_PROGRESS',
    `  --output-dir <path>         Report directory, default ${DEFAULT_OUTPUT_DIR}`,
    `  --artifact-dir <path>       Artifact root, default ${DEFAULT_ARTIFACT_DIR}`,
    '  --force                     Overwrite an existing report with the same run id',
    '  --help                      Show this help'
  ].join('\n')
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function defaultRunId(now = new Date()) {
  const year = now.getFullYear()
  const month = pad(now.getMonth() + 1)
  const day = pad(now.getDate())
  const hour = pad(now.getHours())
  const minute = pad(now.getMinutes())
  const second = pad(now.getSeconds())
  return `pw-${year}${month}${day}${hour}${minute}${second}`
}

function parseArgs(argv) {
  const args = {
    mode: 'full',
    scope: '',
    runId: defaultRunId(),
    status: 'IN_PROGRESS',
    outputDir: DEFAULT_OUTPUT_DIR,
    artifactDir: DEFAULT_ARTIFACT_DIR,
    force: false
  }

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '--help' || arg === '-h') {
      console.log(usage())
      process.exit(0)
    }
    if (arg === '--mode') {
      args.mode = String(argv[++i] || '').trim()
      continue
    }
    if (arg === '--scope') {
      args.scope = String(argv[++i] || '').trim()
      continue
    }
    if (arg === '--run-id') {
      args.runId = String(argv[++i] || '').trim()
      continue
    }
    if (arg === '--status') {
      args.status = String(argv[++i] || '').trim().toUpperCase()
      continue
    }
    if (arg === '--output-dir') {
      args.outputDir = String(argv[++i] || '').trim()
      continue
    }
    if (arg === '--artifact-dir') {
      args.artifactDir = String(argv[++i] || '').trim()
      continue
    }
    if (arg === '--force') {
      args.force = true
      continue
    }
    throw new Error(`unknown argument: ${arg}`)
  }

  if (!VALID_MODES.has(args.mode)) {
    throw new Error(`--mode must be one of: ${[...VALID_MODES].join(', ')}`)
  }
  if (!VALID_STATUSES.has(args.status)) {
    throw new Error(`--status must be one of: ${[...VALID_STATUSES].join(', ')}`)
  }
  if (!/^[A-Za-z0-9._-]+$/.test(args.runId)) {
    throw new Error('--run-id may contain only letters, numbers, dot, underscore, and hyphen')
  }
  if (!args.outputDir) throw new Error('--output-dir must not be empty')
  if (!args.artifactDir) throw new Error('--artifact-dir must not be empty')
  return args
}

function markdownTemplate({ args, reportPath, artifactPath, createdAt }) {
  const scope = args.scope || (args.mode === 'full' ? 'full matrix' : 'TODO: targeted changed area')
  return `# 社区 Playwright 回归测试报告

运行 ID: ${args.runId}
状态: ${args.status}
模式: ${args.mode}
范围: ${scope}
开始时间: ${createdAt}
完成时间:
报告文件: ${reportPath}
证据目录: ${artifactPath}

## 关闭清单

- [ ] 最终状态已设置为 PASS、FAIL 或 BLOCKED。
- [ ] 已记录路由 / 服务新鲜度结果。
- [ ] 每个已执行的功能区都有 PASS、FAIL、BLOCKED 或 NO_UI。
- [ ] 每个 FAIL 或 BLOCKED 项都包含 route/action、role、request/status、evidence 和 diagnosis。
- [ ] 已在可用时链接截图、trace、console、network 和下载证据。
- [ ] 已记录测试数据前缀和清理状态。

## 部署

- 命令:
- 健康检查:
- 基础地址: http://localhost:12881
- 网关 / API 基础地址: http://localhost:12880
- 运行时端点解析:
- 支持服务:

## 账号与角色

- 普通用户:
- 第二用户:
- 管理员:
- 版主:
- 归属路径:

## 汇总计数

| 状态 | 数量 |
| --- | ---: |
| PASS | 0 |
| FAIL | 0 |
| BLOCKED | 0 |
| NO_UI | 0 |
| NOT_RUN | 0 |

## 功能结果

| 区域 | 状态 | 证据 | 备注 |
| --- | --- | --- | --- |
| Auth | NOT_RUN | | |
| Client infra/session/idempotency | NOT_RUN | | |
| Direct HTTP surfaces | NOT_RUN | | |
| Posts/content | NOT_RUN | | |
| Search | NOT_RUN | | |
| Social/profile | NOT_RUN | | |
| Messages/IM | NOT_RUN | | |
| Notices | NOT_RUN | | |
| Wallet | NOT_RUN | | |
| Market | NOT_RUN | | |
| Drive | NOT_RUN | | |
| Moderation | NOT_RUN | | |
| Analytics | NOT_RUN | | |
| Ops | NOT_RUN | | |
| Admin users | NOT_RUN | | |
| Settings/avatar | NOT_RUN | | |
| Preview/system | NOT_RUN | | |
| Accessibility/screenshots | NOT_RUN | | |

## 失败项

| 路由 / 操作 | 请求 | 状态 | 角色 | 期望 | 实际 | 证据 | 诊断 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| | | | | | | | |

## 跳过 / 阻塞 / NO_UI

| 项目 | 状态 | 原因 | 归属测试层 |
| --- | --- | --- | --- |
| | | | |

## 证据

- 截图:
- Trace:
- Console/network 摘要:
- 下载:
- 支持的 Vitest/API 测试:

## 测试数据与清理

- 测试数据前缀: ${args.runId}
- 已创建数据:
- 清理动作:
- 清理状态:

## 备注

- 
`
}

function main() {
  const args = parseArgs(process.argv.slice(2))
  const reportDir = path.resolve(process.cwd(), args.outputDir)
  const artifactPath = path.resolve(process.cwd(), args.artifactDir, args.runId)
  const reportPath = path.join(reportDir, `${args.runId}.md`)

  fs.mkdirSync(reportDir, { recursive: true })
  fs.mkdirSync(artifactPath, { recursive: true })

  if (fs.existsSync(reportPath) && !args.force) {
    throw new Error(`report already exists: ${reportPath}; pass --force to overwrite`)
  }

  const createdAt = new Date().toISOString()
  fs.writeFileSync(
    reportPath,
    markdownTemplate({ args, reportPath, artifactPath, createdAt }),
    'utf8'
  )

  console.log(`Report: ${reportPath}`)
  console.log(`Artifacts: ${artifactPath}`)
}

try {
  main()
} catch (error) {
  console.error(error?.message || String(error))
  process.exit(1)
}
