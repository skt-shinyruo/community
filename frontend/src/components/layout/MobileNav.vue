<!-- MobileNav：移动端主导航（固定 Posts / Search / Notices / Messages / Me），承载未读角标。 -->
<template>
  <nav class="mobile-nav" aria-label="移动端主导航">
    <RouterLink
      v-for="item in items"
      :key="item.key"
      class="mobile-nav-item"
      :class="{ active: isNavItemActive(route, item) }"
      :to="item.to"
      :aria-label="navItemAriaLabel(item)"
    >
      <span class="mobile-nav-icon" aria-hidden="true">
        <component :is="resolveNavIcon(item.icon)" v-if="resolveNavIcon(item.icon)" :size="20" />
        <span v-if="navBadgeCount(item) > 0" class="mobile-nav-badge">{{ formatUnreadCount(navBadgeCount(item)) }}</span>
      </span>

      <span class="mobile-nav-text">{{ item.label }}</span>
    </RouterLink>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { useInboxUnreadStore, formatUnreadCount } from '../../stores/inboxUnread'
import { getMobileNavigation, isNavItemActive } from '../../router/navigation'
import { resolveNavIcon } from './navIcons'

defineProps({
  mode: { type: String, default: 'public' }
})

const route = useRoute()
const auth = useAuthStore()
const inboxUnread = useInboxUnreadStore()

const items = computed(() =>
  getMobileNavigation({
    authed: auth.authed,
    userId: auth.userId,
    roles: auth.authorities
  })
)

function navBadgeCount(item) {
  if (item?.badge === 'notices') return inboxUnread.noticeUnread
  if (item?.badge === 'messages') return inboxUnread.messageUnread
  return 0
}

function navItemAriaLabel(item) {
  const count = navBadgeCount(item)
  if (count > 0) return `${item.label}，${formatUnreadCount(count)} 条未读`
  return item.label
}
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
    z-index: var(--z-nav);
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
    color: var(--accent-text);
    background: var(--accent-weak);
  }

  .mobile-nav-icon {
    position: relative;
    display: grid;
    place-items: center;
  }

  .mobile-nav-badge {
    position: absolute;
    top: -6px;
    left: calc(50% + 2px);
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: var(--radius-full);
    background: var(--accent-weak);
    border: 1px solid color-mix(in srgb, var(--accent) 24%, var(--border) 76%);
    color: var(--accent-text);
    font-size: 10px;
    font-weight: 700;
    line-height: 1;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .mobile-nav-text {
    font-size: 11px;
    font-weight: 700;
    line-height: 1;
  }
}
</style>
