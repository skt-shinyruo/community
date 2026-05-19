import fs from 'node:fs/promises'
import path from 'node:path'

const reportPath = path.resolve('reports/latest-results.json')
const outputDir = path.resolve('reports')

function collectSpecs(suite, prefix = []) {
  const currentPrefix = suite.title ? [...prefix, suite.title] : prefix
  const ownSpecs = (suite.specs || []).flatMap((spec) =>
    spec.tests.map((test) => {
      const latest = test.results?.[test.results.length - 1]
      return {
        name: [...currentPrefix, spec.title].filter(Boolean).join(' > '),
        status: latest?.status || test.status || 'unknown',
        error: latest?.error?.message || ''
      }
    })
  )
  const childSpecs = (suite.suites || []).flatMap((child) => collectSpecs(child, currentPrefix))
  return [...ownSpecs, ...childSpecs]
}

const raw = await fs.readFile(reportPath, 'utf8')
const json = JSON.parse(raw)
const specs = collectSpecs(json)
const passed = specs.filter((item) => item.status === 'passed').length
const failed = specs.filter((item) => item.status === 'failed' || item.status === 'timedOut').length
const skipped = specs.filter((item) => item.status === 'skipped').length
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')

const lines = [
  '# single Playwright 本地测试报告',
  '',
  `- 生成时间：${new Date().toISOString()}`,
  `- 前端入口：${process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881'}`,
  `- Gateway：${process.env.SINGLE_API_BASE_URL || 'http://localhost:12880'}`,
  `- 通过：${passed}`,
  `- 失败：${failed}`,
  `- 跳过：${skipped}`,
  '',
  '## 用例明细',
  '',
  '| 状态 | 用例 | 错误摘要 |',
  '| --- | --- | --- |',
  ...specs.map((item) => {
    const error = item.error.replace(/\s+/g, ' ').slice(0, 180)
    return `| ${item.status} | ${item.name} | ${error} |`
  }),
  ''
]

await fs.mkdir(outputDir, { recursive: true })
const outputPath = path.join(outputDir, `single-playwright-report-${timestamp}.md`)
await fs.writeFile(outputPath, lines.join('\n'), 'utf8')
console.log(outputPath)
