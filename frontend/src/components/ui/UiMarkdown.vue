<template>
  <div class="markdown-body" :class="{ compact: variant === 'compact' }" v-html="rendered"></div>
</template>

<script setup>
import { computed } from 'vue'
import { renderMarkdown } from './markdown'

const props = defineProps({
  content: { type: String, default: '' },
  // default：正文阅读模式；compact：评论/卡片内的紧凑渲染。
  variant: { type: String, default: 'default' }
})

const rendered = computed(() => renderMarkdown(props.content))
</script>

<style scoped>
.markdown-body {
  font-size: var(--text-md);
  line-height: var(--line-loose);
  color: var(--text-1);
}

.markdown-body.compact {
  font-size: var(--text-sm);
  line-height: var(--line-normal);
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

:deep(p) { margin: 0 0 1em 0; }
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
  border: 1px solid var(--border);
  overflow-x: auto;
  margin: 1em 0;
}
:deep(code) {
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 0.9em;
  background: var(--hover-bg);
  padding: 2px 4px;
  border-radius: 4px;
}
:deep(pre code) {
  background: none;
  padding: 0;
}

:deep(a) {
  color: var(--link-color);
  text-decoration: none;
}
:deep(a):hover {
  text-decoration: underline;
}
</style>
