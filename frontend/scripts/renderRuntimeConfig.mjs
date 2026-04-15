import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const outputPath = path.resolve(process.cwd(), 'dist/app-config.js')

const config = {
  apiBaseUrl: String(process.env.FRONTEND_RUNTIME_API_BASE_URL ?? process.env.GATEWAY_PUBLIC_BASE_URL ?? '').trim(),
  imHttpBaseUrl: String(
    process.env.FRONTEND_RUNTIME_IM_HTTP_BASE_URL ?? process.env.GATEWAY_PUBLIC_BASE_URL ?? ''
  ).trim(),
  imWsUrl: String(process.env.FRONTEND_RUNTIME_IM_WS_URL ?? process.env.IM_WS_PUBLIC_URL ?? '').trim()
}

await mkdir(path.dirname(outputPath), { recursive: true })
await writeFile(
  outputPath,
  `globalThis.__COMMUNITY_RUNTIME_CONFIG__ = Object.freeze(${JSON.stringify(config, null, 2)});\n`,
  'utf8'
)
