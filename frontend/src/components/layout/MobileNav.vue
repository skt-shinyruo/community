<!-- MobileNav：移动端底部导航（与 Sidebar 共用 navigation SSOT）。 -->
<template>
  <nav class="mobile-nav" aria-label="移动端底部导航">
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
          v-else-if="item.icon === 'messages'"
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
    grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 60;
    height: 64px;
    padding-bottom: env(safe-area-inset-bottom, 0px);
    background: color-mix(in srgb, var(--bg) 82%, var(--surface) 18%);
    border-top: 1px solid var(--border);
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
  }

  .mobile-nav-item {
    height: 64px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 4px;
    color: var(--text-2);
    text-decoration: none;
  }

  .mobile-nav-item.active {
    color: var(--accent);
  }

  .mobile-nav-text {
    font-size: 11px;
    font-weight: 700;
    line-height: 1;
  }
}
</style>
