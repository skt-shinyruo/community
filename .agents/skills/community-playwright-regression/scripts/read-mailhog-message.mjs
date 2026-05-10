#!/usr/bin/env node

const DEFAULT_BASE_URL = 'http://localhost:8025'

function usage() {
  return [
    'Usage: node .agents/skills/community-playwright-regression/scripts/read-mailhog-message.mjs [options]',
    '',
    'Options:',
    '  --base-url <url>       MailHog base URL, default http://localhost:8025',
    '  --to <email-substring> Filter by recipient substring',
    '  --contains <text>      Filter by subject/body substring',
    '  --latest              Print only the newest matching message summary',
    '  --json                Print matching messages as JSON',
    '  --help                Show this help'
  ].join('\n')
}

function parseArgs(argv) {
  const args = {
    baseUrl: DEFAULT_BASE_URL,
    to: '',
    contains: '',
    latest: false,
    json: false
  }

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i]
    if (arg === '--help' || arg === '-h') {
      console.log(usage())
      process.exit(0)
    }
    if (arg === '--base-url') {
      args.baseUrl = String(argv[++i] || '').trim()
      continue
    }
    if (arg === '--to') {
      args.to = String(argv[++i] || '').trim().toLowerCase()
      continue
    }
    if (arg === '--contains') {
      args.contains = String(argv[++i] || '').trim().toLowerCase()
      continue
    }
    if (arg === '--latest') {
      args.latest = true
      continue
    }
    if (arg === '--json') {
      args.json = true
      continue
    }
    throw new Error(`unknown argument: ${arg}`)
  }

  if (!args.baseUrl) throw new Error('--base-url must not be empty')
  return args
}

function textPart(message) {
  const mime = message?.MIME
  const parts = Array.isArray(mime?.Parts) ? mime.Parts : []
  const bodyParts = parts
    .map((part) => part?.Body)
    .filter((body) => typeof body === 'string' && body.trim())

  if (bodyParts.length > 0) return bodyParts.join('\n\n')
  return typeof message?.Content?.Body === 'string' ? message.Content.Body : ''
}

function recipients(message) {
  const to = Array.isArray(message?.Content?.Headers?.To) ? message.Content.Headers.To : []
  const rawTo = Array.isArray(message?.Raw?.To) ? message.Raw.To : []
  return [...to, ...rawTo].map((item) => String(item || '')).filter(Boolean)
}

function subject(message) {
  const values = message?.Content?.Headers?.Subject
  return Array.isArray(values) ? String(values[0] || '') : ''
}

function created(message) {
  return String(message?.Created || message?.Raw?.Date || '')
}

function simplify(message) {
  const body = textPart(message)
  return {
    id: String(message?.ID || ''),
    created: created(message),
    to: recipients(message),
    subject: subject(message),
    body
  }
}

function matches(message, args) {
  const simple = simplify(message)
  if (args.to && !simple.to.join(' ').toLowerCase().includes(args.to)) return false
  if (args.contains) {
    const haystack = `${simple.subject}\n${simple.body}`.toLowerCase()
    if (!haystack.includes(args.contains)) return false
  }
  return true
}

function newestFirst(a, b) {
  return Date.parse(created(b) || 0) - Date.parse(created(a) || 0)
}

async function main() {
  const args = parseArgs(process.argv.slice(2))
  const url = new URL('/api/v2/messages', args.baseUrl)
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`MailHog request failed: ${response.status} ${response.statusText}`)
  }

  const payload = await response.json()
  const messages = Array.isArray(payload?.items) ? payload.items : []
  const matched = messages.filter((message) => matches(message, args)).sort(newestFirst)
  const output = (args.latest ? matched.slice(0, 1) : matched).map(simplify)

  if (args.json) {
    console.log(JSON.stringify(output, null, 2))
    return
  }

  if (output.length === 0) {
    console.log('No matching MailHog messages found.')
    return
  }

  for (const message of output) {
    console.log(`ID: ${message.id}`)
    console.log(`Created: ${message.created}`)
    console.log(`To: ${message.to.join(', ')}`)
    console.log(`Subject: ${message.subject}`)
    console.log('Body:')
    console.log(message.body.trim())
    console.log('---')
  }
}

main().catch((error) => {
  console.error(error?.message || String(error))
  process.exit(1)
})
