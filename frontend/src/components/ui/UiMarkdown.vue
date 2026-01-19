<template>
  <div class="markdown-body" v-html="rendered"></div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  content: { type: String, default: '' }
})

const rendered = computed(() => {
  let text = props.content || ''
  
  // Escape HTML first to prevent XSS (basic)
  text = text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")

  // Headers
  text = text.replace(/^### (.*$)/gim, '<h3>$1</h3>')
  text = text.replace(/^## (.*$)/gim, '<h2>$1</h2>')
  text = text.replace(/^# (.*$)/gim, '<h1>$1</h1>')

  // Blockquote
  text = text.replace(/^\> (.*$)/gim, '<blockquote>$1</blockquote>')

  // Bold
  text = text.replace(/\*\*(.*)\*\*/gim, '<b>$1</b>')
  
  // Italic
  text = text.replace(/\*(.*)\*/gim, '<i>$1</i>')

  // Code Block
  text = text.replace(/```([\s\S]*?)```/gim, '<pre><code>$1</code></pre>')

  // Inline Code
  text = text.replace(/`([^`]+)`/gim, '<code>$1</code>')

  // Links
  text = text.replace(/\[([^\]]+)\]\(([^)]+)\)/gim, '<a href="$2" target="_blank" rel="noopener">$1</a>')

  // Unordered Lists
  text = text.replace(/^\s*-\s+(.*)/gim, '<li>$1</li>')
  text = text.replace(/(<li>.*<\/li>)/gim, '<ul>$1</ul>')
  // Fix nested uls (regex hack)
  text = text.replace(/<\/ul>\n<ul>/gim, '\n')

  // Line breaks
  text = text.replace(/\n/gim, '<br />')
  
  return text
})
</script>

<style scoped>
.markdown-body {
  font-size: 16px;
  line-height: 1.6;
  color: var(--text-1);
}

:deep(h1), :deep(h2), :deep(h3) {
  margin-top: 1em;
  margin-bottom: 0.5em;
  font-weight: 700;
  line-height: 1.3;
}
:deep(h1) { font-size: 1.8em; }
:deep(h2) { font-size: 1.5em; border-bottom: 1px solid var(--border); padding-bottom: 0.3em; }
:deep(h3) { font-size: 1.25em; }

:deep(p) { margin-bottom: 1em; }
:deep(ul) { margin-bottom: 1em; padding-left: 20px; }
:deep(li) { margin-bottom: 0.25em; list-style-type: disc; }

:deep(blockquote) {
  border-left: 4px solid var(--border);
  padding-left: 16px;
  color: var(--text-2);
  margin: 1em 0;
}

:deep(pre) {
  background: var(--surface-2);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 1em 0;
}
:deep(code) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
  background: rgba(0,0,0,0.05);
  padding: 2px 4px;
  border-radius: 4px;
}
:deep(pre) :deep(code) {
  background: none;
  padding: 0;
}

:deep(a) {
  color: var(--accent);
  text-decoration: none;
}
:deep(a):hover {
  text-decoration: underline;
}
</style>
