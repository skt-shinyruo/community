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

function nodeContainsNativeSelect(node) {
  if (!node || typeof node !== 'object') return false
  if (isNativeSelectTemplateNode(node)) return true

  if (Array.isArray(node.children) && node.children.some((child) => nodeContainsNativeSelect(child))) {
    return true
  }

  if (Array.isArray(node.branches) && node.branches.some((branch) => nodeContainsNativeSelect(branch))) {
    return true
  }

  return false
}

function isStaticSelectAttribute(prop) {
  return prop?.type === 6 && prop.name === 'is' && prop.value?.content === 'select'
}

function isStaticIsBinding(prop) {
  if (prop?.type !== 7 || prop.name !== 'bind') return false
  if (prop.arg?.type !== 4 || prop.arg.content !== 'is') return false
  if (typeof prop.exp?.content !== 'string') return false

  return /^(['"`])select\1$/.test(prop.exp.content.trim())
}

function isDynamicSelectComponentNode(node) {
  return (
    node?.type === 1 &&
    node.tag === 'component' &&
    Array.isArray(node.props) &&
    node.props.some((prop) => isStaticSelectAttribute(prop) || isStaticIsBinding(prop))
  )
}

function isNativeSelectTemplateNode(node) {
  return node?.type === 1 && (node.tag === 'select' || isDynamicSelectComponentNode(node))
}

function hasNativeSelectInVueTemplate(source, filePath = 'inline.vue') {
  const templateContent = getTemplateContent(source, filePath).trim()

  if (!templateContent) return false

  return nodeContainsNativeSelect(parseTemplate(templateContent, { comments: false }))
}

function parseScriptAst(source, filePath = 'inline.js') {
  const isTypeScriptFile = /\.ts($|\?)/.test(filePath)

  return parseScript(source, {
    sourceType: 'unambiguous',
    plugins: isTypeScriptFile ? ['typescript', 'jsx'] : ['jsx']
  })
}

function isSelectStringLiteral(node) {
  return node?.type === 'StringLiteral' && node.value === 'select'
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

function isScriptSelectCreationCall(node) {
  if (node?.type !== 'CallExpression') return false
  const [firstArgument] = node.arguments || []

  if (!isSelectStringLiteral(firstArgument)) return false

  if (node.callee?.type === 'Identifier' && scriptLikeCreateElementCallees.has(node.callee.name)) {
    return true
  }

  return isReactCreateElementCallee(node.callee) || isDocumentCreateElementCallee(node.callee)
}

function isJsxSelectElement(node) {
  return (
    node?.type === 'JSXElement' &&
    node.openingElement?.name?.type === 'JSXIdentifier' &&
    node.openingElement.name.name === 'select'
  )
}

function scriptNodeContainsNativeSelect(node) {
  if (!node || typeof node !== 'object') return false
  if (Array.isArray(node)) return node.some((child) => scriptNodeContainsNativeSelect(child))
  if (isJsxSelectElement(node) || isScriptSelectCreationCall(node)) return true

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

    return scriptNodeContainsNativeSelect(value)
  })
}

function hasNativeSelectInScriptSource(source, filePath = 'inline.js') {
  return scriptNodeContainsNativeSelect(parseScriptAst(source, filePath))
}

function hasNativeSelectInSourceFile(source, filePath) {
  if (/\.vue$/.test(filePath)) {
    return (
      hasNativeSelectInVueTemplate(source, filePath) ||
      getSfcScriptBlocks(source, filePath).some((block) => hasNativeSelectInScriptSource(block.code, block.filePath))
    )
  }

  if (scriptFilePattern.test(filePath)) {
    return hasNativeSelectInScriptSource(source, filePath)
  }

  return false
}

describe('noNativeSelect', () => {
  it('ignores select mentions that only appear in template comments or strings', () => {
    const source = `
      <template>
        <!-- <select name="comment-only"><option>noop</option></select> -->
        <div>{{ '<select name="string-only"></select>' }}</div>
      </template>
    `

    expect(hasNativeSelectInVueTemplate(source)).toBe(false)
  })

  it('detects a real native select element in vue template content', () => {
    const source = `
      <template>
        <form>
          <select name="category">
            <option value="">All</option>
          </select>
        </form>
      </template>
    `

    expect(hasNativeSelectInVueTemplate(source)).toBe(true)
  })

  it('detects dynamic component select rendering in vue template content', () => {
    const staticIsSource = `
      <template>
        <component is="select" name="category" />
      </template>
    `

    const boundIsSource = `
      <template>
        <component :is="'select'" name="category" />
      </template>
    `

    expect(hasNativeSelectInVueTemplate(staticIsSource)).toBe(true)
    expect(hasNativeSelectInVueTemplate(boundIsSource)).toBe(true)
  })

  it('detects real script-based select creation but ignores comments and ordinary strings', () => {
    const scriptWithSelectCreation = `
      import { h } from 'vue'

      export function renderCategoryField() {
        return h('select', { name: 'category' }, [])
      }
    `

    const scriptWithJsxSelect = `
      export function CategoryField() {
        return <select name="category"></select>
      }
    `

    const scriptWithCommentOnly = `
      // h('select', { name: 'comment-only' })
      export const docs = "Use <select> in examples only"
    `

    expect(hasNativeSelectInScriptSource(scriptWithSelectCreation)).toBe(true)
    expect(hasNativeSelectInScriptSource(scriptWithJsxSelect)).toBe(true)
    expect(hasNativeSelectInScriptSource(scriptWithCommentOnly)).toBe(false)
  })

  it('forbids native <select> usage anywhere inside src', () => {
    const offenders = collectSourceFiles(srcRoot)
      .filter((filePath) => filePath !== guardFilePath)
      .filter((filePath) => hasNativeSelectInSourceFile(readFileSync(filePath, 'utf8'), filePath))
      .map((filePath) => path.relative(frontendRoot, filePath).split(path.sep).join('/'))
      .sort()

    if (offenders.length > 0) {
      throw new Error(
        [
          'Native <select> usage is forbidden in frontend/src. Migrate these files to UiSelect:',
          ...offenders.map((filePath) => `- ${filePath}`)
        ].join('\n')
      )
    }
  })
})
