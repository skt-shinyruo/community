# Community 公共区壳层与阅读流收敛设计稿

**Date:** 2026-03-23
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

收敛 `frontend/` 公共区的壳层与主路径页面，让产品从“组件和说明块的堆叠”收敛成“阅读优先的讨论工作区”。

本次设计只聚焦公共区与核心浏览路径，目标如下：

- 让桌面端公共区稳定为 `左侧常驻导航 + 紧凑顶栏 + 单主内容列`
- 删除公共区常驻右侧栏，不再保留等价替身
- 让帖子首页与搜索页第一屏优先露出操作区和内容本身
- 让移动端以高频路径优先，而不是桌面壳层的机械缩放
- 收紧页面说明、快捷入口、辅助提示等低价值 UI，减少对讨论流的干扰

---

## 2. Design Conclusions

### 2.1 Public Product Direction

公共区采用“混合型壳层”：

- 壳层保留产品工作区的稳定结构
- 内容区采用更阅读优先的层级
- 不走纯工具台风格
- 也不走纯内容站风格

设计关键词：

- discussion workspace
- reading first
- fewer competing regions
- navigation is structural, not promotional

### 2.2 Relationship To The 2026-03-20 Redesign Spec

本稿不是整个前端 redesign 的替代品，而是对公共区壳层与主路径的后续收口。

与 [2026-03-20-community-ui-redesign-design.md](/home/feng/code/project/community/docs/superpowers/specs/2026-03-20-community-ui-redesign-design.md) 的关系如下：

- 该旧稿仍然定义站点级目标、整体视觉语言、后台方向、共享设计系统方向
- 本稿覆盖旧稿中“公共区壳层 / 公共主路径 / 公共区移动导航”的具体结论
- 若两份文档在公共区壳层上冲突，以本稿为准
- 后续 implementation plan 必须显式采用本稿的公共区结论，而不是继续沿用旧稿中的 right panel 或 drawer-first mobile shell 假设

### 2.3 Confirmed Decisions

本次确认的设计决策如下：

- 删除公共区常驻右侧栏
- 桌面端保留正常宽度的左侧栏
- 桌面端顶栏保留标题与搜索，但整体缩小，标题区不再占用太多首屏
- 首页正文直接进入筛选条和帖子流
- 搜索页直接进入搜索工作区和结果
- 移动端采用底部四项 tab：`帖子 / 搜索 / 我 / 更多`
- 删除右侧栏后，不保留原有“上下文模块”的等价替身
- 公共区仅保留列表本身需要的筛选与线程元信息，不再保留独立的快捷入口、提示块、当前页说明块

---

## 3. User Experience Problems To Solve

当前公共区存在以下核心问题：

- 顶栏标题、副标题、页面大标题、正文说明块在多个层级重复表达“你现在在哪”
- 首页首屏由 hero、概览块、工具条、空状态或发帖块共同争抢注意力，讨论流进入太晚
- 搜索页呈现为“说明页 + 表单区 + 结果区”，而不是直接可操作的搜索工作区
- 右侧栏长期承载快速入口、讨论提示、热门标签、分类与治理入口，语义过杂且稀释主内容
- 公共区页面看起来像大量通用卡片的堆叠，而不是稳定的内容浏览系统
- 移动端虽然有适配，但仍然残留太多桌面逻辑，首屏内容密度不够直接

---

## 4. Scope

本次设计的强约束范围为：

- `frontend/src/App.vue`
- `frontend/src/components/layout/AppShell.vue`
- `frontend/src/components/layout/Topbar.vue`
- `frontend/src/components/layout/SidebarNav.vue`
- `frontend/src/components/layout/RightPanel.vue`
- `frontend/src/components/layout/MobileNav.vue`
- `frontend/src/router/navigation.js`
- `frontend/src/router/navigation.test.js`
- `frontend/src/views/PostsView.vue`
- `frontend/src/views/SearchView.vue`
- `frontend/src/views/PostDetailView.vue`

次级继承范围为：

- `frontend/src/views/ConversationsView.vue`
- `frontend/src/views/ConversationDetailView.vue`
- `frontend/src/views/NoticesView.vue`
- `frontend/src/views/NoticeDetailView.vue`
- `frontend/src/views/SettingsView.vue`
- `frontend/src/views/GrowthCenterView.vue`
- `frontend/src/views/RewardShopView.vue`
- `frontend/src/views/RewardOrderHistoryView.vue`
- `frontend/src/views/BookmarksView.vue`
- `frontend/src/views/LeaderboardView.vue`
- `frontend/src/views/UserProfileView.vue`
- `frontend/src/views/FolloweesView.vue`
- `frontend/src/views/FollowersView.vue`
- 其他公共区浏览型页面

本次设计不直接定义后台、认证页、系统异常页的最终视觉方案。

### 4.1 Public Navigation IA

为避免右栏删除后出现信息架构漂移，公共区导航必须按以下最小模型收口：

#### Desktop

- Explore
  - `/posts`
  - `/search`
  - `/leaderboard`
- Me
  - `/growth`
  - `/rewards/shop`
  - `/rewards/orders`
  - `/bookmarks`
  - `/messages`
  - `/messages/:conversationId`
  - `/notices`
  - `/notices/:topic`
  - `/users/:userId`
  - `/users/:userId/followees`
  - `/users/:userId/followers`
  - `/settings`

约束：

- `Leaderboard` 保留在公共探索路径中，而不是塞进隐藏菜单
- `Bookmarks`、个人主页与关注关系页归入 `Me` 语义，而不是伪装成全局发现页
- `Messages`、`Notices`、`Growth Center`、`Reward Shop`、`Reward Orders`、`Settings` 都视为 `Me` 体系，不得漂移到 `Explore`
- `Reward Orders` 作为 `Reward Shop` 的延伸，不要求成为桌面一级独立导航项，但必须在 `Me` 体系内稳定可达
- `Followees`、`Followers`、`Message Detail`、`Notice Detail` 都是对应一级入口的子页面，不要求额外升格为桌面一级导航项
- `Settings`、主题、密度、低频全局动作不作为正文区域的独立模块出现

#### Mobile

移动端底部 tab 固定为：

- `帖子`
- `搜索`
- `我`
- `更多`

其中：

- `搜索` tab 是移动端进入搜索能力的主入口
- `我` tab 打开个人中心型 hub，承载：个人主页、收藏、关注/粉丝、私信、通知、成长中心、奖励商城/兑换记录、设置
- `更多` 承载：排行榜与低频全局动作，例如主题、密度或其他非个人中心入口

明确约束：

- 非搜索页的移动端顶栏不应再出现与 `搜索` tab 并列竞争的全宽搜索输入区
- 如保留移动端搜索快捷动作，必须是轻量图标或跳转动作，且语义上仍然是“进入搜索 tab”，不是第二套搜索入口

#### Navigation SSOT Alignment

实现层必须同步反映到以下契约：

- `frontend/src/router/index.js` 中公共区 `navGroup: 'explore' / 'me'` 的事实分类
- `frontend/src/router/navigation.js` 中桌面 group 与移动端 allowlist 的重写
- `frontend/src/router/navigation.test.js` 中对 sidebar/mobile 导航的断言更新

当前代码里已经存在但本稿明确要求重新归位的公共区 `Me` 体系包括：

- `/growth`
- `/rewards/shop`
- `/rewards/orders`
- `/messages`
- `/messages/:conversationId`
- `/notices`
- `/notices/:topic`
- `/bookmarks`
- `/users/:userId`
- `/users/:userId/followees`
- `/users/:userId/followers`
- `/settings`

实现时不得因为旧 navigation SSOT 仍然保留历史分组，而偏离本稿定义。

这一定义是强约束，后续页面继承或壳层实现不得自行改写。

---

## 5. Desktop Shell

### 5.1 Overall Structure

桌面端公共区固定为两栏：

- 左：常驻导航
- 右：主内容列

明确取消：

- 常驻第三栏
- 常驻上下文面板
- 右栏等价替身

### 5.2 Left Navigation

左侧栏保留正常宽度，不压缩成纯图标轨。

它的职责只有三类：

- 品牌识别
- 一级导航
- 账户相关入口

左侧栏不再承担：

- 内容说明
- 话题推荐
- 分类说明
- 额外统计陈列

要求：

- 视觉上更像结构性导航，而不是主视觉区
- 保持稳定、低干扰、可扫读
- 正文区在视觉上必须明显强于左侧栏

### 5.3 Shell State Contract

右栏移除不仅是视觉变化，也意味着公共区壳层 contract 要同步收口。

本次设计要求：

- 公共区不再依赖“显示/隐藏右栏”的交互
- 不再存在“哪些 route name 会挂右侧 slot”的公共壳层分支
- 顶栏不再提供右栏开关动作
- 与公共区右栏相关的 UI state、布局 class 和路由 allowlist 必须一并清理或退场

实现可以保留最小兼容过渡，但最终公共区不应继续以右栏存在为前提组织代码。

### 5.4 Topbar

顶栏收敛为“方位条”，不是页面主视觉。

桌面端顶栏仅保留：

- 当前页标题
- 一行非常轻的辅助定位信息
- 全局搜索
- 少量全局动作

要求：

- 标题优先于搜索，但搜索继续常驻
- 标题区必须继续缩小，不能压住帖子列表首屏
- 主题、密度、其他低频全局动作统一收进一个菜单
- 顶栏高度与文案密度都低于当前实现

明确禁止：

- 把多个独立图标动作平铺成一排控制台
- 在正文里再重复一个大尺寸同名标题区

---

## 6. Page Hierarchy Rules

### 6.1 Shared Rule

公共区页面统一遵循：

1. 方位
2. 操作
3. 内容

不允许再出现：

1. 方位
2. 说明
3. 概览
4. 工具
5. 内容

这种五层并排抢视线的结构。

### 6.2 Posts Index

帖子首页正文必须直接从筛选工具条和帖子流开始。

要求：

- 不再保留大 hero
- 不再保留独立概览块
- 不再保留独立快速入口块
- 不再保留正文顶部说明块
- 空状态内嵌到内容区，不再做大面积宣传板

登录用户的发帖入口可以保留，但必须是轻量入口：

- 是线程流的前置动作，不是第二个主视觉区
- 不能形成大卡片式首屏竞争

### 6.3 Search

搜索页必须改成操作型页面。

要求：

- 首屏直接进入搜索输入、筛选与结果
- 不再使用“解释搜索页用途”的大段说明
- 搜索输入区、筛选区、结果区是一个连续工作流
- 结果项继续使用轻量线程卡片语言，不造独立结果视觉体系

### 6.4 Post Detail

详情页允许保留比列表页略强的头部信息，但不能重新引入大 hero。

要求：

- 主贴信息与正文自然衔接
- 分类、标签、作者、时间等元信息贴着主贴存在
- 评论区紧跟主贴之后
- 不再依赖右栏或额外说明模块去补充“上下文感”

---

## 7. Context And Auxiliary Information

### 7.1 Removal Strategy

删除右侧栏后，本次设计选择“基本不保留额外上下文模块”。

因此以下内容应从公共区主页面移除：

- 快速入口模块
- 讨论提示模块
- 热门标签独立模块
- 分类独立模块
- 当前页说明块

### 7.2 What Remains

允许保留的“上下文”只存在于以下位置：

- 列表工具条里的分类与标签筛选
- 线程卡片内部的分类、标签、未读、状态信息
- 详情页主贴内部的必要元信息

换句话说，本次设计不是把右栏搬家，而是减少不必要的辅助区。

---

## 8. Components And Interaction Rules

### 8.1 Toolbar

帖子页和搜索页的工具条是正文第一操作层。

要求：

- 比内容更轻，不做重卡片
- 吸顶可选，但必须稳定
- 只放高频且当前页必要的控件
- 不承载额外说明文案

### 8.2 Surfaces

公共区默认采用轻表面，不再大量依赖重阴影大卡片。

允许强调的对象只有：

- 线程卡片
- 搜索工作区
- 输入触发器
- 弹层与菜单

不鼓励：

- 首屏大面积概览卡
- 解释型大卡片
- 为了“丰富”而堆叠多个表面层

### 8.3 Drawers And Menus

抽屉只保留给：

- 移动端“更多”
- 顶栏全局菜单

本次不再单独设计一个“上下文抽屉”来承接右栏内容。

### 8.4 Empty States

空状态必须内嵌到内容区本身。

要求：

- 简洁
- 直给
- 只提供 1 到 2 个动作
- 不造独立首屏气氛

---

## 9. Mobile Rules

移动端公共区保留底部四项 tab：

- 帖子
- 搜索
- 我
- 更多

要求：

- 第一屏优先露出筛选和真实内容
- 不机械照搬桌面顶栏文案密度
- `搜索` tab 是移动端搜索的主入口，其他页面不再放置第二套主搜索输入区
- “更多”承接次级入口与全局动作
- 移动端和桌面端保持同一产品语言

不允许：

- 把桌面右栏逻辑搬到移动端
- 用大标题区占掉首屏内容

---

## 10. Target Impression

公共区目标观感应为：

- calm but active
- structured but not dashboard-like
- content-forward
- credible
- fast to scan

具体来说：

- 首页看起来像讨论流，不像组件展板
- 搜索页看起来像工作区，不像说明页
- 详情页看起来像阅读页，不像由多块功能卡拼起来

---

## 11. Success Criteria

本次改造完成后，应满足：

1. 桌面端进入 `/posts`，首屏能直接看到：
   - 左侧栏
   - 紧凑顶栏
   - 筛选工具条
   - 至少一部分真实帖子流
2. 桌面端进入 `/search`，用户无需先读说明就能开始操作
3. 删除右侧栏后，页面不出现新的等价说明区或快捷入口区
4. 顶栏标题区明显比当前更小，不再压住帖子列表空间
5. 移动端第一屏优先露出内容而不是说明
6. 公共区页面在语法上读起来是同一个产品
7. `Leaderboard`、`Bookmarks`、`Profile`、`Followees`、`Followers` 在新壳层下仍有明确且稳定的入口，不依赖旧右栏残留
8. 公共区代码中不再把“右栏是否打开”作为核心布局分支
9. `Messages`、`Notices`、`Growth Center`、`Reward Shop / Reward Orders`、`Settings` 在桌面与移动端均有稳定归属，不因右栏移除而漂移
10. 移动端不存在与 `搜索` tab 竞争的第二套主搜索入口

---

## 12. Non-Goals

本次设计不要求：

- 改动后台页最终形态
- 重做认证页视觉
- 引入新的组件库或框架
- 扩展新的业务功能
- 为了补偿右栏删除而发明新的“内容模块”

---

## 13. Rollout Order

推荐实现顺序：

1. 公共区壳层
   - `AppShell`
   - `Topbar`
   - `SidebarNav`
   - `RightPanel`
   - `MobileNav`
2. 帖子首页
3. 搜索页
4. 帖子详情页
5. 其他公共区页面继承收口

此顺序不可颠倒，因为壳层和首页规则定义了后续页面的视觉语法。

---

## 14. Mock Reference

本设计在确认前使用过交互式 mock 收敛方向，但 `.superpowers/brainstorm/` 下的会话产物不是稳定文档依赖，不应作为长期规范引用。

本稿中的正式结论，仅以下列设计判断为准：

- 无常驻右侧栏
- 正常宽度左侧栏
- 缩小的标题优先顶栏
- 首页直接进入筛选条与帖子流
- 搜索页改为操作型页面
- 移动端底部四项 tab

---

## 15. Planning Notes

进入 implementation planning 时，应重点覆盖：

- 壳层删改范围与受影响路由
- 桌面左侧导航与移动端 `我 / 更多` 的具体信息架构映射
- 右栏移除后的依赖清理
- 右栏状态、壳层分支、顶栏动作与 route allowlist 的代码级删除面
- `App.vue` 中 public right-panel route allowlist 的删除
- `navigation.js` / `navigation.test.js` 中桌面与移动导航 contract 的重写
- 首页与搜索页的模板重排
- 顶栏动作收口
- 移动端底部导航与“更多”菜单
- 回归验证：`/posts`、`/search`、`/posts/:postId`、`/leaderboard`、`/bookmarks`、`/messages`、`/messages/:conversationId`、`/notices`、`/notices/:topic`、`/growth`、`/rewards/shop`、`/rewards/orders`、`/settings`、`/users/:userId`、`/users/:userId/followees`、`/users/:userId/followers`
- 壳层验证：auth/public 切换、桌面/移动切换、公共区无右栏残留入口
