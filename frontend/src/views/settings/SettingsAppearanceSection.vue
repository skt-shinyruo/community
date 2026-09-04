<template>
  <UiCard class="settings-panel settings-appearance">
    <section class="settings-section">
      <div class="settings-section-head">
        <div>
          <div class="settings-eyebrow">Theme</div>
          <h2 id="settings-theme-heading">主题</h2>
          <p>浅色、深色或跟随系统；偏好保存在当前设备上，刷新后保持。</p>
        </div>
      </div>

      <div class="settings-choice-group" role="radiogroup" aria-labelledby="settings-theme-heading">
        <label
          v-for="option in themeOptions"
          :key="option.value"
          class="settings-choice-item"
          :class="{ 'settings-choice-item--active': ui.theme === option.value }"
        >
          <input
            v-model="themePreference"
            class="settings-choice-input"
            type="radio"
            name="settings-theme"
            :value="option.value"
          />
          <span class="settings-choice-copy">
            <span class="settings-choice-label">{{ option.label }}</span>
            <span class="settings-choice-description">{{ option.description }}</span>
          </span>
        </label>
      </div>
      <p v-if="ui.theme === 'system'" class="settings-choice-hint">
        正在跟随系统，当前生效：{{ ui.effectiveTheme === 'dark' ? '深色' : '浅色' }}。
      </p>
    </section>

    <section class="settings-section">
      <div class="settings-section-head">
        <div>
          <div class="settings-eyebrow">Density</div>
          <h2 id="settings-density-heading">密度</h2>
          <p>密度只改变令牌与间距，组件和交互保持一致。</p>
        </div>
      </div>

      <div class="settings-choice-group" role="radiogroup" aria-labelledby="settings-density-heading">
        <label
          v-for="option in densityOptions"
          :key="option.value"
          class="settings-choice-item"
          :class="{ 'settings-choice-item--active': ui.density === option.value }"
        >
          <input
            v-model="densityPreference"
            class="settings-choice-input"
            type="radio"
            name="settings-density"
            :value="option.value"
          />
          <span class="settings-choice-copy">
            <span class="settings-choice-label">{{ option.label }}</span>
            <span class="settings-choice-description">{{ option.description }}</span>
          </span>
        </label>
      </div>
    </section>
  </UiCard>
</template>

<script setup>
import { computed } from 'vue'
import UiCard from '../../components/ui/UiCard.vue'
import { useUiStore } from '../../stores/ui'

const ui = useUiStore()

const themeOptions = Object.freeze([
  { value: 'light', label: '浅色', description: '始终使用浅色主题。' },
  { value: 'dark', label: '深色', description: '始终使用深色主题。' },
  { value: 'system', label: '跟随系统', description: '按系统的明暗偏好实时切换。' }
])

const densityOptions = Object.freeze([
  { value: 'compact', label: '紧凑', description: '默认密度，桌面信息密度更高。' },
  { value: 'comfortable', label: '舒适', description: '更宽松的阅读与操作间距。' }
])

const themePreference = computed({
  get: () => ui.theme,
  set: (value) => ui.setTheme(value)
})

const densityPreference = computed({
  get: () => ui.density,
  set: (value) => ui.setDensity(value)
})
</script>

<style scoped>
.settings-panel {
  display: grid;
  gap: 0;
  padding: 0;
  overflow: hidden;
}

.settings-section {
  padding: var(--space-6);
  border-bottom: 1px solid var(--border);
  display: grid;
  gap: var(--space-5);
}

.settings-section:last-child {
  border-bottom: none;
}

.settings-section-head p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.6;
}

.settings-eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-3);
  font-weight: 700;
}

.settings-section-head h2 {
  margin: 6px 0 4px;
  font-size: 1.15rem;
}

.settings-choice-group {
  display: grid;
  gap: var(--space-3);
  max-width: 520px;
}

.settings-choice-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  background: var(--surface);
  cursor: pointer;
  transition:
    border-color var(--duration-fast) var(--ease-standard),
    background-color var(--duration-fast) var(--ease-standard);
}

.settings-choice-item:hover {
  border-color: var(--border-strong);
}

.settings-choice-item--active {
  border-color: var(--accent);
  background: var(--accent-weak);
}

.settings-choice-item:has(.settings-choice-input:focus-visible) {
  box-shadow: var(--focus-ring);
}

.settings-choice-input {
  margin-top: var(--space-1);
  accent-color: var(--accent);
}

.settings-choice-copy {
  display: grid;
  gap: 2px;
}

.settings-choice-label {
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--text-1);
}

.settings-choice-description {
  font-size: var(--text-xs);
  color: var(--text-3);
  line-height: var(--line-normal);
}

.settings-choice-hint {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--text-2);
}

@media (max-width: 900px) {
  .settings-section {
    padding: var(--space-5);
  }
}
</style>
