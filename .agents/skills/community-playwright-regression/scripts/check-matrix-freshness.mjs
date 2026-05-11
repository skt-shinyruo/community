#!/usr/bin/env node

import fs from 'node:fs'
import path from 'node:path'

const repoRoot = process.cwd()
const matrixPath = path.join(
  repoRoot,
  '.agents/skills/community-playwright-regression/references/community-feature-matrix.md'
)

function readText(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8')
}

function fail(message) {
  failures.push(message)
}

function containsToken(text, token) {
  return text.includes(token) || text.includes(`\`${token}\``)
}

function listFiles(dir, predicate) {
  const abs = path.join(repoRoot, dir)
  return fs
    .readdirSync(abs, { withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => entry.name)
    .filter(predicate)
    .sort()
}

function listFilesRecursive(dir, predicate) {
  const abs = path.join(repoRoot, dir)
  const results = []

  function walk(current) {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name)
      if (entry.isDirectory()) {
        walk(fullPath)
        continue
      }
      if (!entry.isFile()) continue
      const relative = path.relative(repoRoot, fullPath)
      if (predicate(relative)) results.push(relative)
    }
  }

  walk(abs)
  return results.sort()
}

const failures = []
const matrix = fs.readFileSync(matrixPath, 'utf8')

const router = readText('frontend/src/router/index.js')
const routePaths = [...router.matchAll(/path:\s*'([^']+)'/g)]
  .map((match) => match[1])
  .filter((routePath) => routePath !== '/:pathMatch(.*)*')
const routeAliases = [...router.matchAll(/alias:\s*\[([^\]]*)\]/g)]
  .flatMap((match) => [...match[1].matchAll(/'([^']+)'/g)].map((aliasMatch) => aliasMatch[1]))

for (const routePath of [...routePaths, ...routeAliases]) {
  if (!containsToken(matrix, routePath)) {
    fail(`missing route in matrix: ${routePath}`)
  }
}
if (!matrix.includes('wildcard')) {
  fail('missing wildcard route disposition in matrix')
}

const serviceFiles = listFiles(
  'frontend/src/api/services',
  (name) => name.endsWith('.js') && !name.endsWith('.test.js')
)
for (const fileName of serviceFiles) {
  if (!containsToken(matrix, fileName)) {
    fail(`missing frontend service in matrix: ${fileName}`)
  }
}

const clientFiles = [
  'http.js',
  'imCoreHttp.js',
  'uploadSession.js',
  'renderRuntimeConfig.mjs',
  'endpointResolution.js',
  'runtimeConfig.js',
  'imRealtimeClient.js',
  'PostBlockEditor.vue',
  'PostBlockRenderer.vue'
]
for (const fileName of clientFiles) {
  if (!containsToken(matrix, fileName)) {
    fail(`missing client infrastructure file in matrix: ${fileName}`)
  }
}

const skillSupportFiles = [
  'create-run-report.mjs'
]
for (const fileName of skillSupportFiles) {
  if (!containsToken(matrix, fileName)) {
    fail(`missing skill support file in matrix: ${fileName}`)
  }
}

const frontendCodeFiles = listFilesRecursive('frontend/src', (relative) =>
  (relative.endsWith('.js') || relative.endsWith('.vue')) &&
  !relative.endsWith('.test.js') &&
  !relative.includes('/api/services/')
)
const directHttpFiles = []
for (const relative of frontendCodeFiles) {
  const text = readText(relative)
  if (/(^|[^A-Za-z0-9_$])(http|imCoreHttp)\.(get|post|put|delete|patch)\s*\(/.test(text) || /new\s+WebSocket\s*\(/.test(text)) {
    directHttpFiles.push(relative)
  }
}
for (const relative of directHttpFiles) {
  const base = path.basename(relative)
  if (!containsToken(matrix, base) && !containsToken(matrix, relative)) {
    fail(`missing direct HTTP/WebSocket surface in matrix: ${relative}`)
  }
}

const navigation = readText('frontend/src/router/navigation.js')
const navKeys = [...navigation.matchAll(/key:\s*'([^']+)'/g)]
  .map((match) => match[1])
  .filter((key, index, keys) => keys.indexOf(key) === index)
for (const key of navKeys) {
  if (!containsToken(matrix, key)) {
    fail(`missing navigation key in matrix: ${key}`)
  }
}

const backendEndpointFamilies = [
  '/api/auth',
  '/api/posts',
  '/api/posts/media',
  '/api/categories',
  '/api/tags',
  '/api/subscriptions/categories',
  '/api/bookmarks',
  '/api/reports',
  '/api/moderation',
  '/api/users',
  '/api/users/admin',
  '/api/likes',
  '/api/follows',
  '/api/blocks',
  '/api/search',
  '/api/ops',
  '/api/analytics',
  '/api/wallet',
  '/api/wallet/admin',
  '/api/market',
  '/api/admin/market/disputes',
  '/api/drive',
  '/api/drive/shares',
  '/api/im/conversations',
  '/api/im/unread',
  '/api/im/sessions',
  '/api/im/rooms',
  '/api/oss/objects',
  '/files',
  '/internal/oss/objects',
  '/internal/im/realtime/projections'
]
for (const endpointFamily of backendEndpointFamilies) {
  if (!containsToken(matrix, endpointFamily)) {
    fail(`missing backend endpoint family in matrix: ${endpointFamily}`)
  }
}

const controllerFiles = [
  ...listFilesRecursive('backend/community-app/src/main/java', (relative) => relative.endsWith('Controller.java')),
  ...listFilesRecursive('backend/community-im/im-core/src/main/java', (relative) => relative.endsWith('Controller.java')),
  ...listFilesRecursive('backend/community-oss/src/main/java', (relative) => relative.endsWith('Controller.java'))
]
for (const relative of controllerFiles) {
  const base = path.basename(relative, '.java')
  if (!containsToken(matrix, base)) {
    fail(`missing backend controller disposition in matrix: ${base}`)
  }
}

const requiredTerms = [
  'RoomController',
  'OssObjectController',
  'InternalOssObjectController',
  'InternalRealtimeProjectionController',
  'ImPolicySnapshotController',
  'Idempotency-Key',
  'MailHog',
  'ROLE_MODERATOR',
  'debugEmailCode',
  'debugResetLink',
  'UnreadController',
  'HomeView.vue',
  'SettingsView.vue',
  'Topbar.vue',
  'target/community-playwright-regression/reports/<runId>.md',
  'target/community-playwright-regression/artifacts/<runId>/',
  'create-run-report.mjs',
  'renderRuntimeConfig.mjs',
  'POST /api/auth/refresh',
  'POST /api/market/orders/{orderId}/deliver',
  'POST /api/market/orders/{orderId}/ship',
  'POST /api/market/orders/{orderId}/confirm',
  'POST /api/market/orders/{orderId}/cancel',
  'POST /api/im/rooms/{roomId}/join',
  'POST /api/oss/objects/{objectId}/grants'
]
for (const term of requiredTerms) {
  if (!matrix.includes(term)) {
    fail(`missing required coverage term in matrix: ${term}`)
  }
}

if (failures.length > 0) {
  console.error('community Playwright regression matrix freshness failed:')
  for (const item of failures) {
    console.error(`- ${item}`)
  }
  process.exit(1)
}

console.log(
  [
    'community Playwright regression matrix freshness ok',
    `${routePaths.length} routes`,
    `${routeAliases.length} aliases`,
    `${serviceFiles.length} services`,
    `${clientFiles.length} client infrastructure files`,
    `${skillSupportFiles.length} skill support files`,
    `${directHttpFiles.length} direct HTTP/WebSocket files`,
    `${navKeys.length} navigation keys`,
    `${backendEndpointFamilies.length} backend endpoint families`,
    `${controllerFiles.length} controllers`
  ].join('; ')
)
