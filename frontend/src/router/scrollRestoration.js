// 路由切换的滚动位置决策：前进/后退恢复 savedPosition，锚点跳对应元素，
// 跨路由导航回顶，同路径 query 切换（tab 深链等）保持当前位置。

export function resolveScrollPosition(to, from, savedPosition) {
  if (savedPosition) return savedPosition
  if (to?.hash) return { el: to.hash }
  if (to?.path && from?.path && to.path === from.path) return false
  return { top: 0 }
}
