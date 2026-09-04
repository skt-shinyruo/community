<!-- 字段包装原语：label、帮助文本、错误文本与控件的可访问关联；校验只使用 required / pattern / :invalid 原生语义，不引入表单校验库。 -->
<template>
  <div class="ui-field">
    <label :id="labelId" class="ui-field-label" :for="controlId">
      <span>{{ label }}</span>
      <span v-if="required" class="ui-field-required" aria-hidden="true">*</span>
    </label>
    <slot :control-id="controlId" :described-by="describedBy" :invalid="isInvalid" :required="required" />
    <p v-if="help" :id="helpId" class="ui-field-help">{{ help }}</p>
    <p v-if="error" :id="errorId" class="ui-field-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup>
import { computed, provide, useId } from 'vue'
import { uiFieldContextKey } from './fieldContext'

const props = defineProps({
  label: { type: String, required: true },
  help: { type: String, default: '' },
  error: { type: String, default: '' },
  required: { type: Boolean, default: false },
  invalid: { type: Boolean, default: false }
})

const uid = useId()
const controlId = `ui-field-control-${uid}`
const labelId = `ui-field-label-${uid}`
const helpId = `ui-field-help-${uid}`
const errorId = `ui-field-error-${uid}`

const isInvalid = computed(() => props.invalid || Boolean(props.error))
const describedBy = computed(() => {
  const ids = []
  if (props.help) ids.push(helpId)
  if (props.error) ids.push(errorId)
  return ids.join(' ')
})

provide(uiFieldContextKey, {
  controlId,
  labelId,
  describedBy,
  invalid: isInvalid,
  required: computed(() => props.required)
})
</script>

<style scoped>
.ui-field {
  display: grid;
  gap: var(--space-2);
}

.ui-field-label {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-1);
}

.ui-field-required {
  color: var(--danger-hover);
}

.ui-field-help,
.ui-field-error {
  margin: 0;
  font-size: var(--text-xs);
  line-height: var(--line-normal);
}

.ui-field-help {
  color: var(--text-3);
}

.ui-field-error {
  color: var(--danger-hover);
}
</style>
