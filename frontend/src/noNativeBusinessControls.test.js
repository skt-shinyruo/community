import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse as parseScript } from '@babel/parser'
import { parse as parseTemplate } from '@vue/compiler-dom'
import { parse as parseSfc } from '@vue/compiler-sfc'
import { describe, expect, it } from 'vitest'

const guardFilePath = fileURLToPath(import.meta.url)
const srcRoot = path.dirname(guardFilePath)
const frontendRoot = path.resolve(srcRoot, '..')
const sourceFilePattern = /\.(vue|js|ts)$/
const scriptFilePattern = /\.(js|ts)$/
const guardedDirectories = [
  path.join(srcRoot, 'views'),
  path.join(srcRoot, 'components', 'layout'),
  path.join(srcRoot, 'components', 'modals'),
  path.join(srcRoot, 'components', 'posts')
]
const forbiddenNativeTags = new Set(['button', 'input', 'textarea', 'datalist', 'option', 'label'])
const scriptLikeCreateElementCallees = new Set([
  'h',
  'createVNode',
  '_createVNode',
  'createBlock',
  '_createBlock',
  'createElementVNode',
  '_createElementVNode',
  'createElementBlock',
  '_createElementBlock'
])

function collectSourceFiles(dirPath) {
  return readdirSync(dirPath, { withFileTypes: true }).flatMap((entry) => {
    const entryPath = path.join(dirPath, entry.name)

    if (entry.isDirectory()) return collectSourceFiles(entryPath)
    if (entry.isFile() && sourceFilePattern.test(entry.name)) return [entryPath]

    return []
  })
}

function getTemplateContent(source, filePath = 'inline.vue') {
  const { descriptor, errors } = parseSfc(source, { filename: filePath })

  if (errors.length > 0) {
    throw new Error(`Unable to parse Vue SFC template in ${filePath}`)
  }

  return descriptor.template?.content ?? ''
}

function getSfcScriptBlocks(source, filePath = 'inline.vue') {
  const { descriptor, errors } = parseSfc(source, { filename: filePath })

  if (errors.length > 0) {
    throw new Error(`Unable to parse Vue SFC in ${filePath}`)
  }

  return [descriptor.script, descriptor.scriptSetup]
    .filter(Boolean)
    .map((block, index) => {
      const lang = block.lang === 'ts' ? 'ts' : 'js'
      return {
        code: block.content ?? '',
        filePath: `${filePath}?script=${index}.${lang}`
      }
    })
}

function isForbiddenTag(tagName) {
  return forbiddenNativeTags.has(String(tagName || '').trim())
}

function nodeContainsForbiddenNativeControl(node) {
  if (!node || typeof node !== 'object') return false
  if (isForbiddenNativeTemplateNode(node)) return true

  if (Array.isArray(node.children) && node.children.some((child) => nodeContainsForbiddenNativeControl(child))) {
    return true
  }

  if (Array.isArray(node.branches) && node.branches.some((branch) => nodeContainsForbiddenNativeControl(branch))) {
    return true
  }

  return false
}

function isStaticDynamicTagAttribute(prop) {
  return prop?.type === 6 && prop.name === 'is' && isForbiddenTag(prop.value?.content)
}

function isStaticDynamicTagBinding(prop) {
  if (prop?.type !== 7 || prop.name !== 'bind') return false
  if (prop.arg?.type !== 4 || prop.arg.content !== 'is') return false
  if (typeof prop.exp?.content !== 'string') return false

  const match = prop.exp.content.trim().match(/^(['"`])(.+)\1$/)
  return !!match && isForbiddenTag(match[2])
}

function isDynamicForbiddenComponentNode(node) {
  return (
    node?.type === 1 &&
    node.tag === 'component' &&
    Array.isArray(node.props) &&
    node.props.some((prop) => isStaticDynamicTagAttribute(prop) || isStaticDynamicTagBinding(prop))
  )
}

function isForbiddenNativeTemplateNode(node) {
  return node?.type === 1 && (isForbiddenTag(node.tag) || isDynamicForbiddenComponentNode(node))
}

function hasForbiddenNativeControlInVueTemplate(source, filePath = 'inline.vue') {
  const templateContent = getTemplateContent(source, filePath).trim()

  if (!templateContent) return false

  return nodeContainsForbiddenNativeControl(parseTemplate(templateContent, { comments: false }))
}

function parseScriptAst(source, filePath = 'inline.js') {
  const isTypeScriptFile = /\.ts($|\?)/.test(filePath)

  return parseScript(source, {
    sourceType: 'unambiguous',
    plugins: isTypeScriptFile ? ['typescript', 'jsx'] : ['jsx']
  })
}

function getStringLiteralValue(node) {
  if (node?.type === 'StringLiteral') return node.value
  return null
}

function isReactCreateElementCallee(node) {
  return (
    node?.type === 'MemberExpression' &&
    !node.computed &&
    node.object?.type === 'Identifier' &&
    node.object.name === 'React' &&
    node.property?.type === 'Identifier' &&
    node.property.name === 'createElement'
  )
}

function isDocumentCreateElementCallee(node) {
  return (
    node?.type === 'MemberExpression' &&
    !node.computed &&
    node.object?.type === 'Identifier' &&
    node.object.name === 'document' &&
    node.property?.type === 'Identifier' &&
    node.property.name === 'createElement'
  )
}

function isScriptForbiddenControlCreationCall(node) {
  if (node?.type !== 'CallExpression') return false
  const [firstArgument] = node.arguments || []
  const tagName = getStringLiteralValue(firstArgument)

  if (!isForbiddenTag(tagName)) return false

  if (node.callee?.type === 'Identifier' && scriptLikeCreateElementCallees.has(node.callee.name)) {
    return true
  }

  return isReactCreateElementCallee(node.callee) || isDocumentCreateElementCallee(node.callee)
}

function isJsxForbiddenControlElement(node) {
  return (
    node?.type === 'JSXElement' &&
    node.openingElement?.name?.type === 'JSXIdentifier' &&
    isForbiddenTag(node.openingElement.name.name)
  )
}

function scriptNodeContainsForbiddenNativeControl(node) {
  if (!node || typeof node !== 'object') return false
  if (Array.isArray(node)) return node.some((child) => scriptNodeContainsForbiddenNativeControl(child))
  if (isJsxForbiddenControlElement(node) || isScriptForbiddenControlCreationCall(node)) return true

  return Object.entries(node).some(([key, value]) => {
    if (
      key === 'loc' ||
      key === 'start' ||
      key === 'end' ||
      key === 'extra' ||
      key === 'comments' ||
      key === 'leadingComments' ||
      key === 'innerComments' ||
      key === 'trailingComments'
    ) {
      return false
    }

    return scriptNodeContainsForbiddenNativeControl(value)
  })
}

function hasForbiddenNativeControlInScriptSource(source, filePath = 'inline.js') {
  return scriptNodeContainsForbiddenNativeControl(parseScriptAst(source, filePath))
}

function hasForbiddenNativeControlInSourceFile(source, filePath) {
  if (/\.vue$/.test(filePath)) {
    return (
      hasForbiddenNativeControlInVueTemplate(source, filePath) ||
      getSfcScriptBlocks(source, filePath).some((block) => hasForbiddenNativeControlInScriptSource(block.code, block.filePath))
    )
  }

  if (scriptFilePattern.test(filePath)) {
    return hasForbiddenNativeControlInScriptSource(source, filePath)
  }

  return false
}

describe('noNativeBusinessControls', () => {
  it('ignores native-control mentions that only appear in comments or strings', () => {
    const source = `
      <template>
        <!-- <button><input /><textarea></textarea></button> -->
        <div>{{ '<label><input type="text" /></label>' }}</div>
      </template>
    `

    expect(hasForbiddenNativeControlInVueTemplate(source)).toBe(false)
  })

  it('detects real native business controls in vue template content', () => {
    const source = `
      <template>
        <section>
          <button type="button">send</button>
          <input type="text" />
          <textarea></textarea>
          <label for="x">Label</label>
        </section>
      </template>
    `

    expect(hasForbiddenNativeControlInVueTemplate(source)).toBe(true)
  })

  it('detects dynamic component rendering and script-based control creation', () => {
    const templateSource = `
      <template>
        <component :is="'button'" />
      </template>
    `

    const scriptSource = `
      import { h } from 'vue'

      export function renderComposer() {
        return h('textarea', { rows: 1 })
      }
    `

    expect(hasForbiddenNativeControlInVueTemplate(templateSource)).toBe(true)
    expect(hasForbiddenNativeControlInScriptSource(scriptSource)).toBe(true)
  })

  it('forbids native business controls inside guarded frontend surfaces', () => {
    const offenders = guardedDirectories
      .flatMap((dirPath) => collectSourceFiles(dirPath))
      .filter((filePath) => filePath !== guardFilePath)
      .filter((filePath) => hasForbiddenNativeControlInSourceFile(readFileSync(filePath, 'utf8'), filePath))
      .map((filePath) => path.relative(frontendRoot, filePath).split(path.sep).join('/'))
      .sort()

    if (offenders.length > 0) {
      throw new Error(
        [
          'Native business controls are forbidden in frontend/src views/layout/modals/posts. Migrate these files to shared ui or scene primitives:',
          ...offenders.map((filePath) => `- ${filePath}`)
        ].join('\n')
      )
    }
  })
})
