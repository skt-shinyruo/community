export function buildComposerCategoryOptions(categories) {
  return [
    { label: '不选择', value: '' },
    ...(Array.isArray(categories) ? categories : []).map((category) => ({
      label: category.name,
      value: String(category.id)
    }))
  ]
}
