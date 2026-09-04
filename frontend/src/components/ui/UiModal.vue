<!-- 模态弹窗外壳：原生 <dialog> + useModalFocus 焦点圈定，统一 title/尺寸与 header/body/footer slots。
     Escape、backdrop 点击与关闭按钮都只发出 close 请求，由使用方决定何时卸载；busy 期间禁止关闭。 -->
<template>
  <dialog
    ref="dialogRef"
    class="ui-modal"
    :class="`ui-modal--${safeSize}`"
    role="dialog"
    aria-modal="true"
    :aria-labelledby="labelledBy"
    :aria-describedby="bodyId"
    :aria-busy="busy || undefined"
    @click.self="requestClose"
    @cancel.prevent="requestClose"
    @keydown="onKeydown"
  >
    <div class="ui-modal__card">
      <header v-if="title || $slots.header" class="ui-modal__header">
        <slot name="header" :title-id="titleId">
          <h2 :id="titleId" class="ui-modal__title">{{ title }}</h2>
        </slot>
        <UiIconButton
          class="ui-modal__close"
          :aria-label="closeLabel"
          :title="closeLabel"
          size="sm"
          :disabled="busy"
          @click="requestClose"
        >×</UiIconButton>
      </header>
      <div :id="bodyId" class="ui-modal__body">
        <slot />
      </div>
      <footer v-if="$slots.footer" class="ui-modal__footer">
        <slot name="footer" />
      </footer>
    </div>
  </dialog>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, useId } from 'vue'
import UiIconButton from './UiIconButton.vue'
import { useModalFocus } from '../../composables/useModalFocus'

const props = defineProps({
  title: { type: String, default: '' },
  size: { type: String, default: 'md' }, // sm | md | lg
  busy: { type: Boolean, default: false },
  closeLabel: { type: String, default: '关闭' }
})

const emit = defineEmits(['close'])

const uid = useId()
const titleId = `ui-modal-title-${uid}`
const bodyId = `ui-modal-body-${uid}`

const dialogRef = ref(null)
const { onKeydown } = useModalFocus(dialogRef)

const SIZES = ['sm', 'md', 'lg']
const safeSize = computed(() => (SIZES.includes(props.size) ? props.size : 'md'))

// 自定义 header slot 时由使用方通过 slot prop titleId 自行关联标题。
const labelledBy = computed(() => (props.title ? titleId : undefined))

function requestClose() {
  if (props.busy) return
  emit('close')
}

onMounted(() => dialogRef.value?.showModal?.())
onBeforeUnmount(() => dialogRef.value?.close?.())
</script>

<style scoped>
.ui-modal {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  max-width: none;
  max-height: none;
  margin: 0;
  padding: var(--space-4);
  border: 0;
  background: transparent;
  color: inherit;
  display: grid;
  place-items: center;
  z-index: var(--z-modal);
}

.ui-modal::backdrop {
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(4px);
}

.ui-modal__card {
  width: 100%;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  padding: var(--card-padding);
  animation: ui-modal-in var(--duration-slow) var(--ease-enter);
}

.ui-modal--sm .ui-modal__card {
  max-width: 400px;
}

.ui-modal--md .ui-modal__card {
  max-width: 500px;
}

.ui-modal--lg .ui-modal__card {
  max-width: 720px;
}

@keyframes ui-modal-in {
  from {
    opacity: 0;
    transform: scale(0.95);
  }

  to {
    opacity: 1;
    transform: scale(1);
  }
}

.ui-modal__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}

.ui-modal__title {
  margin: 0;
  font-size: var(--text-lg);
  font-weight: 700;
  line-height: var(--line-tight);
}

.ui-modal__close {
  flex: none;
}

.ui-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-3);
  margin-top: var(--space-4);
}
</style>
