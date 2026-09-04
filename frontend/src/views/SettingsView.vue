<template>
  <div class="page settings-page">
    <UiPageHeader>
      <template #title>设置</template>
      <template #subtitle>维护公开资料、头像、外观偏好与收货地址。</template>
    </UiPageHeader>

    <UiTabs
      :model-value="activeSection"
      :tabs="sectionTabs"
      label="设置分区"
      @update:modelValue="onSectionSelect"
    >
      <template #panel="{ tab, active }">
        <SettingsProfileSection v-if="active && tab.value === 'profile'" />
        <SettingsAppearanceSection v-else-if="active && tab.value === 'appearance'" />
        <SettingsAddressesSection v-else-if="active && tab.value === 'addresses'" />
      </template>
    </UiTabs>
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UiPageHeader from '../components/ui/UiPageHeader.vue'
import UiTabs from '../components/ui/UiTabs.vue'
import SettingsAddressesSection from './settings/SettingsAddressesSection.vue'
import SettingsAppearanceSection from './settings/SettingsAppearanceSection.vue'
import SettingsProfileSection from './settings/SettingsProfileSection.vue'
import { SETTINGS_SECTIONS, normalizeSettingsSection } from './settingsSection'

const route = useRoute()
const router = useRouter()

const sectionTabs = SETTINGS_SECTIONS.map((section) => ({ value: section.key, label: section.label }))

const activeSection = computed(() => normalizeSettingsSection(route.query.section))

function onSectionSelect(section) {
  router.push({ name: 'settings', query: { section } })
}

// section query 是深链合同的唯一事实来源：缺省或无效值回落到默认 section，
// 并用 replace 把 URL 规范化，避免地址栏与实际展示的 section 不一致。
watch(
  activeSection,
  (section) => {
    if (route.query.section === section) return
    router.replace({ name: 'settings', query: { ...route.query, section } })
  },
  { immediate: true }
)
</script>

<style scoped>
.settings-page {
  max-width: 980px;
}
</style>
