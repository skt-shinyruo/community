import { computed, inject } from 'vue'

// UiField 向字段内的控件提供关联上下文（控件 id、描述关联、校验状态）；
// UiInput / UiTextarea 在字段内自动继承，字段外退化为纯原生属性透传。
export const uiFieldContextKey = Symbol('ui-field-context')

// 合并字段上下文与调用方显式传入的原生属性：显式传入的属性优先于字段上下文。
export function useFieldControlAttrs(attrs) {
  const field = inject(uiFieldContextKey, null)

  return computed(() => {
    const merged = { ...attrs }
    if (!field) return merged
    if (merged.id === undefined) merged.id = field.controlId
    const describedby = [field.describedBy.value, merged['aria-describedby']].filter(Boolean).join(' ')
    if (describedby) merged['aria-describedby'] = describedby
    if (field.invalid.value && merged['aria-invalid'] === undefined) merged['aria-invalid'] = 'true'
    if (field.required.value && merged.required === undefined) merged.required = true
    return merged
  })
}
