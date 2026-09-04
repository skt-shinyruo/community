<template>
  <div class="page settings-page">
    <nav class="settings-sections" aria-label="设置分区">
      <RouterLink
        v-for="section in SETTINGS_SECTIONS"
        :key="section.key"
        v-slot="{ href, navigate }"
        custom
        :to="{ name: 'settings', query: { section: section.key } }"
      >
        <a
          :href="href"
          class="settings-section-link"
          :class="{ 'settings-section-link--active': section.key === activeSection }"
          :aria-current="section.key === activeSection ? 'true' : undefined"
          @click="navigate"
        >{{ section.label }}</a>
      </RouterLink>
    </nav>

    <SettingsProfileSection v-if="activeSection === 'profile'" />
    <SettingsAppearanceSection v-else-if="activeSection === 'appearance'" />
    <SettingsAddressesSection v-else />
  </div>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SettingsAddressesSection from './settings/SettingsAddressesSection.vue'
import SettingsAppearanceSection from './settings/SettingsAppearanceSection.vue'
import SettingsProfileSection from './settings/SettingsProfileSection.vue'
import { SETTINGS_SECTIONS, normalizeSettingsSection } from './settingsSection'

const route = useRoute()
const router = useRouter()

const activeSection = computed(() => normalizeSettingsSection(route.query.section))

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
  margin: 0 auto;
  gap: var(--space-5);
}

.settings-sections {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.settings-section-link {
  display: inline-flex;
  align-items: center;
  min-height: var(--control-height);
  padding: 0 var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  color: var(--text-2);
  font-size: var(--text-sm);
  font-weight: 600;
  text-decoration: none;
  transition:
    color var(--duration-fast) var(--ease-standard),
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.settings-section-link:hover {
  color: var(--text-1);
  border-color: var(--border-strong);
}

.settings-section-link:focus-visible {
  outline: none;
  box-shadow: var(--focus-ring);
}

.settings-section-link--active {
  color: var(--accent-text);
  border-color: var(--accent);
  background: var(--accent-weak);
}
</style>
