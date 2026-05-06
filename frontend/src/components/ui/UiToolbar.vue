<template>
  <section class="ui-toolbar" aria-label="页面工具栏">
    <div v-if="$slots.leading" class="ui-toolbar-leading">
      <slot name="leading" />
    </div>
    <div v-if="$slots.filters" class="ui-toolbar-filters">
      <slot name="filters" />
    </div>
    <div v-if="$slots.actions" class="ui-toolbar-actions">
      <slot name="actions" />
    </div>
  </section>
</template>

<script setup>
defineOptions({ name: 'UiToolbar' })
</script>
