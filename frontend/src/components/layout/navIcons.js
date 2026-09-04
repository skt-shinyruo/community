// 壳层导航图标映射：navigation.js 的 icon key -> lucide 命名导入组件。
// 只覆盖壳层（SidebarNav / MobileNav）使用的 key，不新增本地 path 表。
import {
  Bell,
  Bookmark,
  ChartLine,
  Folder,
  House,
  LogIn,
  MessageSquare,
  Search,
  Settings,
  Shield,
  Store,
  User,
  Wallet
} from 'lucide-vue-next'

export const NAV_ICONS = Object.freeze({
  posts: House,
  search: Search,
  bookmark: Bookmark,
  user: User,
  settings: Settings,
  bell: Bell,
  messages: MessageSquare,
  analytics: ChartLine,
  folder: Folder,
  login: LogIn,
  shield: Shield,
  store: Store,
  wallet: Wallet
})

export function resolveNavIcon(key) {
  return NAV_ICONS[String(key || '')] || null
}
