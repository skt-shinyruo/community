<!-- MobileNav：移动端主导航（固定 Posts / Search / Notices / Messages / Me）。 -->
<template>
  <nav class="mobile-nav" aria-label="移动端主导航">
    <RouterLink
      v-for="item in items"
      :key="item.key"
      class="mobile-nav-item"
      :class="{ active: isNavItemActive(route, item) }"
      :to="item.to"
      :aria-label="item.label"
    >
      <span class="mobile-nav-icon" aria-hidden="true">
        <svg
          v-if="item.icon === 'posts'"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
          <polyline points="9 22 9 12 15 12 15 22" />
        </svg>

        <svg
          v-else-if="item.icon === 'search'"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>

        <svg
          v-else-if="item.icon === 'bell'"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </svg>

        <svg
          v-else-if="item.icon === 'messages' && item.key !== 'more'"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
        </svg>

        <svg
          v-else-if="item.icon === 'user'"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
          <circle cx="12" cy="7" r="4" />
        </svg>

        <svg
          v-else-if="item.icon === 'sparkle' || item.key === 'market'"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <path d="M12 2l2.2 6.8H21l-5.6 4.1 2.1 7-5.5-4-5.5 4 2.1-7L3 8.8h6.8z" />
        </svg>

        <svg
          v-else-if="item.icon === 'more' || item.key === 'more'"
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
        >
          <circle cx="5" cy="12" r="1.5" />
          <circle cx="12" cy="12" r="1.5" />
          <circle cx="19" cy="12" r="1.5" />
        </svg>
      </span>

      <span class="mobile-nav-text">{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { getMobileNavigation, isNavItemActive } from '../../router/navigation'

defineProps({
  mode: { type: String, default: 'public' }
})

const route = useRoute()
const auth = useAuthStore()

const items = computed(() =>
  getMobileNavigation({
    authed: auth.authed,
    userId: auth.userId,
    roles: auth.authorities
  })
)
</script>

<style scoped>
.mobile-nav {
  display: none;
}

@media (max-width: 768px) {
  .mobile-nav {
    display: grid;
    grid-template-columns: repeat(5, minmax(0, 1fr));
    position: fixed;
    left: 14px;
    right: 14px;
    bottom: 14px;
    z-index: 60;
    min-height: 64px;
    padding: 4px;
    padding-bottom: calc(4px + env(safe-area-inset-bottom, 0px));
    border: 1px solid var(--border);
    border-radius: 18px;
    background: color-mix(in srgb, var(--surface) 88%, var(--bg) 12%);
    box-shadow: var(--shadow-lg);
    backdrop-filter: blur(18px);
    -webkit-backdrop-filter: blur(18px);
  }

  .mobile-nav-item {
    min-height: 58px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    color: var(--text-2);
    text-decoration: none;
    border-radius: 14px;
  }

  .mobile-nav-item.active {
    color: var(--accent);
    background: color-mix(in srgb, var(--accent) 12%, transparent);
  }

  .mobile-nav-text {
    font-size: 11px;
    font-weight: 700;
    line-height: 1;
  }
}
</style>
