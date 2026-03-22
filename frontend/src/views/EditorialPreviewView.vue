<template>
  <div class="page preview-page">
    <section class="preview-intro card">
      <div class="preview-intro-copy">
        <div class="preview-eyebrow">Social Content Preview</div>
        <h1 class="preview-title">{{ currentVariant.pageTitle }}</h1>
        <p class="preview-dek">{{ currentVariant.pageDek }}</p>
      </div>

      <nav class="preview-nav" aria-label="首页方案切换">
        <RouterLink
          v-for="item in variantLinks"
          :key="item.key"
          class="preview-nav-item"
          :class="{ active: item.key === currentVariant.key }"
          :to="{ name: item.routeName }"
        >
          <span class="preview-nav-kicker">方案 {{ item.key.toUpperCase() }}</span>
          <strong>{{ item.label }}</strong>
          <span>{{ item.summary }}</span>
        </RouterLink>
      </nav>
    </section>

    <section class="preview-section">
      <div class="preview-section-head">
        <div class="preview-section-eyebrow">Homepage Preview</div>
        <h2 class="preview-section-title">{{ currentVariant.sectionTitle }}</h2>
      </div>

      <article class="homepage-stage card">
        <template v-if="currentVariant.key === 'a'">
          <div class="homepage-stage-grid">
            <section class="featured-stage">
              <div class="homepage-stage-head">
                <div class="stage-label">今日精选</div>
                <div class="stage-actions">
                  <span class="preview-chip active">精选</span>
                  <span class="preview-chip">最新</span>
                </div>
              </div>

              <article class="feature-story">
                <div class="feature-story-meta">
                  <div class="preview-author">
                    <UiAvatar :name="leadStory.author" :size="28" />
                    <span>{{ leadStory.author }}</span>
                  </div>
                  <span>{{ leadStory.time }}</span>
                </div>
                <h3 class="feature-story-title">{{ leadStory.title }}</h3>
                <p class="feature-story-copy">{{ leadStory.snippet }}</p>
                <div class="feature-story-tags">
                  <span v-for="tag in leadStory.tags" :key="tag" class="preview-chip preview-chip--muted">#{{ tag }}</span>
                </div>
              </article>

              <div class="feature-brief-list">
                <article v-for="item in featuredBriefs" :key="item.id" class="feature-brief">
                  <div class="feature-brief-kicker">{{ item.category }}</div>
                  <div class="feature-brief-title">{{ item.title }}</div>
                  <div class="feature-brief-meta">{{ item.comments }} 回复 · {{ item.likes }} 赞</div>
                </article>
              </div>
            </section>

            <aside class="side-notes">
              <div class="side-notes-title">这一版在强调什么</div>
              <div v-for="note in currentVariant.notes" :key="note" class="side-note-row">{{ note }}</div>
            </aside>
          </div>
        </template>

        <template v-else-if="currentVariant.key === 'b'">
          <div class="homepage-stage-grid">
            <section class="live-stage">
              <div class="live-composer">
                <UiAvatar name="Mara" :size="28" />
                <div class="live-composer-input">你今天准备发起什么讨论？</div>
                <button class="btn secondary" type="button">发帖</button>
              </div>

              <div class="live-feed">
                <article v-for="item in latestFeed" :key="item.id" class="live-card">
                  <div class="live-card-head">
                    <div class="preview-author">
                      <UiAvatar :name="item.author" :size="22" />
                      <span>{{ item.author }}</span>
                    </div>
                    <span>{{ item.time }}</span>
                  </div>
                  <h3 class="live-card-title">{{ item.title }}</h3>
                  <p class="live-card-copy">{{ item.snippet }}</p>
                  <div class="live-card-foot">
                    <span class="preview-chip">{{ item.category }}</span>
                    <span>{{ item.comments }} 回复</span>
                    <span>{{ item.likes }} 赞</span>
                  </div>
                </article>
              </div>
            </section>

            <aside class="side-notes">
              <div class="side-notes-title">这一版在强调什么</div>
              <div v-for="note in currentVariant.notes" :key="note" class="side-note-row">{{ note }}</div>
            </aside>
          </div>
        </template>

        <template v-else>
          <div class="homepage-stage-grid">
            <section class="hybrid-stage">
              <article class="hybrid-lead">
                <div class="hybrid-lead-kicker">封面讨论</div>
                <h3 class="hybrid-lead-title">{{ hybridLead.title }}</h3>
                <p class="hybrid-lead-copy">{{ hybridLead.snippet }}</p>
                <div class="hybrid-lead-meta">
                  <div class="preview-author">
                    <UiAvatar :name="hybridLead.author" :size="24" />
                    <span>{{ hybridLead.author }}</span>
                  </div>
                  <span>{{ hybridLead.time }}</span>
                </div>
              </article>

              <aside class="hybrid-rail">
                <div class="hybrid-rail-title">正在更新</div>
                <article v-for="item in hybridRail" :key="item.id" class="hybrid-rail-item">
                  <div class="hybrid-rail-item-title">{{ item.title }}</div>
                  <div class="hybrid-rail-item-meta">{{ item.category }} · {{ item.comments }} 回复</div>
                </article>
              </aside>
            </section>

            <aside class="side-notes">
              <div class="side-notes-title">这一版在强调什么</div>
              <div v-for="note in currentVariant.notes" :key="note" class="side-note-row">{{ note }}</div>
            </aside>
          </div>
        </template>
      </article>
    </section>

    <section class="preview-section">
      <div class="preview-section-head">
        <div class="preview-section-eyebrow">Supporting Surfaces</div>
        <h2 class="preview-section-title">配套页面气质</h2>
      </div>

      <div class="support-grid">
        <article class="support-card card">
          <div class="variant-kicker">搜索结果</div>
          <h3 class="support-title">更像进入讨论，而不是数据库过滤器</h3>
          <div class="support-searchbar">
            <div class="input">首页改版 讨论质量</div>
            <button class="btn secondary" type="button">搜索</button>
          </div>
          <div class="support-result-list">
            <article v-for="item in searchSamples" :key="item.id" class="support-result">
              <div class="support-result-head">
                <span class="preview-chip">{{ item.category }}</span>
                <strong>{{ item.score }}</strong>
              </div>
              <div class="support-result-title">{{ item.title }}</div>
              <div class="support-result-copy">{{ item.snippet }}</div>
            </article>
          </div>
        </article>

        <article class="support-card card">
          <div class="variant-kicker">帖子详情</div>
          <h3 class="support-title">主贴先给上下文，评论继续推动线程</h3>
          <article class="detail-sheet">
            <div class="detail-title">{{ detailSample.title }}</div>
            <div class="detail-copy">{{ detailSample.body }}</div>
            <div class="detail-comments">
              <div v-for="comment in detailComments" :key="comment.id" class="detail-comment">
                <div class="preview-author">
                  <UiAvatar :name="comment.author" :size="20" />
                  <span>{{ comment.author }}</span>
                </div>
                <p>{{ comment.content }}</p>
              </div>
            </div>
          </article>
        </article>

        <article class="support-card card">
          <div class="variant-kicker">个人主页</div>
          <h3 class="support-title">更像成员主页，而不是档案封面</h3>
          <div class="profile-sheet">
            <div class="preview-author profile-sheet-author">
              <UiAvatar :name="profileSample.name" :size="52" />
              <div>
                <div class="profile-sheet-name">{{ profileSample.name }}</div>
                <div class="profile-sheet-meta">{{ profileSample.role }}</div>
              </div>
            </div>
            <div class="profile-sheet-stats">
              <div v-for="stat in profileSample.stats" :key="stat.label" class="profile-sheet-stat">
                <strong>{{ stat.value }}</strong>
                <span>{{ stat.label }}</span>
              </div>
            </div>
            <p class="profile-sheet-copy">{{ profileSample.copy }}</p>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import UiAvatar from '../components/ui/UiAvatar.vue'

const props = defineProps({
  variant: { type: String, default: 'a' }
})

const variantLinks = [
  {
    key: 'a',
    routeName: 'editorialPreviewA',
    label: '作者与精选内容优先',
    summary: '内容感最强，像首页封面。'
  },
  {
    key: 'b',
    routeName: 'editorialPreviewB',
    label: '最新讨论流优先',
    summary: '社区更新感最强，像内容流。'
  },
  {
    key: 'c',
    routeName: 'editorialPreviewC',
    label: '双栏混合',
    summary: '兼顾封面感和社区活跃度。'
  }
]

const variantMap = {
  a: {
    key: 'a',
    pageTitle: '预览 A：作者与精选内容优先',
    pageDek: '这版把首页第一屏做成“内容社区封面”，先让你看到值得读的作者与精选讨论，再决定要不要进入更多线程。',
    sectionTitle: '方案 A：作者与精选内容优先',
    notes: ['作者感最强，容易建立“内容社区”气质。', '首页更像封面页，适合做精选和专题推荐。', '缺点是实时更新感会比动态流弱一些。']
  },
  b: {
    key: 'b',
    pageTitle: '预览 B：最新讨论流优先',
    pageDek: '这版直接让首页像一个持续更新的内容流，强调“社区正在发生什么”，更像现代社交内容社区。',
    sectionTitle: '方案 B：最新讨论流优先',
    notes: ['更新感最强，打开就能感到社区在流动。', '发帖和互动更近，社交氛围会更强。', '缺点是首页气质会更像动态流，不够“封面”。']
  },
  c: {
    key: 'c',
    pageTitle: '预览 C：双栏混合',
    pageDek: '这版让封面讨论和实时更新并排出现，既不放弃内容社区的“精选感”，也保留社区正在更新的活跃感。',
    sectionTitle: '方案 C：双栏混合',
    notes: ['最平衡，封面感和更新感都保留。', '第一屏信息密度更高，适合做社区首页主版式。', '缺点是布局更复杂，需要更细致的视觉控制。']
  }
}

const currentVariant = computed(() => variantMap[props.variant] || variantMap.a)

const leadStory = {
  author: 'Mara',
  time: '今天 09:40',
  title: '社区首页到底该先展示观点，还是先展示热度指标？',
  snippet: '如果第一屏只能留下一个最重要的信号，它应该帮助读者建立判断，而不是只诱导点击。这决定了首页到底像内容社区，还是像工具列表。',
  tags: ['首页改版', '信息架构', '讨论质量']
}

const featuredBriefs = [
  { id: 1, category: '产品设计', title: '把空状态做成“今日未开刊”，会不会更像内容社区？', comments: 16, likes: 82 },
  { id: 2, category: '工程协作', title: '为什么很多重设计最后都只剩一套更整齐的后台？', comments: 21, likes: 104 }
]

const latestFeed = [
  {
    id: 11,
    author: 'Lin',
    time: '6 分钟前',
    category: '产品设计',
    title: '如果首页让读者先看摘要，再决定是否点进详情，会不会更有效？',
    snippet: '摘要不是多余信息，它是帮助读者判断阅读成本的第一层内容。',
    comments: 18,
    likes: 73
  },
  {
    id: 12,
    author: 'Echo',
    time: '14 分钟前',
    category: '写作与表达',
    title: '标题必须交代问题本身，还是应该保留一点悬念？',
    snippet: '内容社区和流量社区对标题的要求并不一样。',
    comments: 26,
    likes: 91
  },
  {
    id: 13,
    author: 'Qiu',
    time: '27 分钟前',
    category: '前端实现',
    title: '白天白底黑字、夜间黑壳深灰内容面，这套规则够不够稳定？',
    snippet: '主题不是换颜色，而是保证阅读结构在两套模式下都成立。',
    comments: 12,
    likes: 57
  }
]

const hybridLead = {
  author: 'Editor Han',
  time: '今天 10:20',
  title: '先看到值得读的封面讨论，旁边再持续感知社区正在更新什么。',
  snippet: '这套混合首页兼顾内容社区的“精选感”和论坛社区的“活跃感”，不会一打开就像后台列表，也不会完全失去实时更新的社区氛围。'
}

const hybridRail = [
  { id: 21, category: '讨论质量', title: '为什么“未读”比“热度”更能帮助回访用户继续阅读？', comments: 34 },
  { id: 22, category: '社区运营', title: '作者排行应该放右栏，还是独立成内容页？', comments: 19 },
  { id: 23, category: '工程实现', title: 'Mock 预览页能不能成为设计评审入口？', comments: 11 }
]

const searchSamples = [
  { id: 31, category: '产品设计', score: '0.98', title: '首页改版到底该先解决什么问题？', snippet: '不是先换颜色，而是先决定读者第一眼看到的是什么。' },
  { id: 32, category: '前端实现', score: '0.91', title: '做一个可直看的 UI 预览页，为什么比长篇说明更有效？', snippet: '因为你能直接比较不同首页方案，而不是在脑内翻译。' }
]

const detailSample = {
  title: '帖子详情页应该先强调主贴上下文，还是先强调互动操作？',
  body: '先把主贴问题、作者和分类交代清楚，再让评论顺着回复关系推进讨论。重要的是读者能看懂线程，而不是先看到一排按钮。'
}

const detailComments = [
  { id: 41, author: 'Aki', content: '评论区应该让读者先看“谁在回应什么”，而不是先看到零碎按钮。' },
  { id: 42, author: 'Mina', content: '如果回复像注释一样组织，阅读连贯性会明显更强。' }
]

const profileSample = {
  name: 'Mara',
  role: '产品讨论发起人',
  copy: '她持续参与首页、信息架构和社区表达方式的讨论，主页应该先展示她在社区里的角色和最近的公开存在感。',
  stats: [
    { label: '获赞', value: '1.2k' },
    { label: '关注', value: '84' },
    { label: '粉丝', value: '2.7k' }
  ]
}
</script>

<style scoped>
.preview-page {
  max-width: 1320px;
  gap: 30px;
}

.preview-intro,
.preview-intro-copy,
.preview-section,
.preview-section-head,
.homepage-stage,
.support-card,
.feature-story,
.live-card,
.hybrid-lead,
.support-result,
.detail-sheet,
.profile-sheet {
  display: grid;
  gap: 14px;
}

.preview-intro {
  grid-template-columns: minmax(0, 0.9fr) minmax(340px, 0.9fr);
  padding: 28px;
}

.preview-eyebrow,
.preview-section-eyebrow,
.variant-kicker,
.preview-nav-kicker,
.stage-label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--editorial-accent);
}

.preview-title,
.preview-section-title,
.feature-story-title,
.live-card-title,
.hybrid-lead-title,
.support-title,
.support-result-title,
.detail-title,
.profile-sheet-name {
  margin: 0;
  font-family: var(--font-display);
  color: var(--editorial-ink);
  letter-spacing: -0.04em;
}

.preview-title {
  font-size: clamp(34px, 4vw, 54px);
  line-height: 0.96;
}

.preview-dek,
.feature-story-copy,
.live-card-copy,
.hybrid-lead-copy,
.support-result-copy,
.detail-copy,
.profile-sheet-copy {
  margin: 0;
  color: var(--text-2);
  font-size: 15px;
  line-height: 1.8;
}

.preview-nav {
  display: grid;
  gap: 12px;
}

.preview-nav-item {
  display: grid;
  gap: 5px;
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid var(--editorial-rule);
  background: color-mix(in srgb, var(--editorial-paper-2) 84%, transparent);
  color: inherit;
  text-decoration: none;
}

.preview-nav-item.active {
  border-color: var(--editorial-accent);
  background: color-mix(in srgb, var(--editorial-accent) 8%, var(--surface) 92%);
}

.preview-nav-item strong {
  font-size: 18px;
  color: var(--editorial-ink);
}

.preview-nav-item span:last-child {
  color: var(--text-2);
  line-height: 1.7;
}

.preview-section-title {
  font-size: clamp(28px, 3.3vw, 42px);
  line-height: 1.02;
}

.homepage-stage {
  padding: 24px;
}

.homepage-stage-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(260px, 0.7fr);
  gap: 20px;
}

.featured-stage,
.live-stage,
.hybrid-stage,
.side-notes,
.support-grid,
.support-result-list,
.detail-comments,
.profile-sheet-stats {
  display: grid;
  gap: 14px;
}

.homepage-stage-head,
.feature-story-meta,
.live-card-head,
.live-card-foot,
.hybrid-lead-meta,
.support-result-head,
.preview-author {
  display: flex;
  align-items: center;
  gap: 10px;
  justify-content: space-between;
  flex-wrap: wrap;
}

.preview-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid var(--editorial-rule);
  background: color-mix(in srgb, var(--surface) 94%, var(--editorial-paper-2) 6%);
  font-size: 11px;
  font-weight: 800;
}

.preview-chip.active {
  background: var(--editorial-accent);
  border-color: var(--editorial-accent);
  color: var(--surface);
}

.preview-chip--muted {
  background: color-mix(in srgb, var(--editorial-accent) 8%, var(--surface) 92%);
}

.feature-story,
.live-card,
.hybrid-lead,
.support-result,
.detail-sheet,
.profile-sheet,
.side-note-row {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid var(--editorial-rule);
  background: color-mix(in srgb, var(--surface) 94%, var(--editorial-paper-2) 6%);
}

.feature-story-title,
.hybrid-lead-title {
  font-size: clamp(34px, 3.2vw, 52px);
  line-height: 0.98;
  max-width: 12ch;
}

.live-card-title,
.support-result-title,
.detail-title,
.profile-sheet-name {
  font-size: clamp(24px, 2.4vw, 32px);
  line-height: 1.06;
}

.feature-brief-list,
.live-feed {
  display: grid;
  gap: 12px;
}

.feature-brief,
.hybrid-rail-item {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid var(--editorial-rule);
  background: color-mix(in srgb, var(--surface) 96%, var(--editorial-paper-2) 4%);
}

.feature-brief-title,
.hybrid-rail-item-title {
  font-weight: 700;
  color: var(--editorial-ink);
  margin-top: 4px;
}

.feature-brief-meta,
.hybrid-rail-item-meta,
.profile-sheet-meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-3);
}

.live-composer,
.support-searchbar,
.profile-sheet-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.live-composer-input {
  flex: 1;
  min-width: 220px;
  min-height: 44px;
  display: grid;
  align-items: center;
  padding: 0 16px;
  border-radius: 16px;
  border: 1px solid var(--editorial-rule);
  background: color-mix(in srgb, var(--surface) 94%, var(--editorial-paper-2) 6%);
  color: var(--text-3);
}

.side-notes {
  align-content: start;
}

.side-notes-title {
  font-weight: 800;
  color: var(--editorial-ink);
}

.side-note-row {
  line-height: 1.75;
  color: var(--text-2);
}

.support-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.support-card {
  padding: 22px;
}

.support-title {
  font-size: clamp(22px, 2.2vw, 30px);
  line-height: 1.08;
}

.support-searchbar .input:first-child {
  flex: 1;
}

.support-result-head strong {
  font-size: 14px;
}

.detail-comments {
  margin-top: 6px;
}

.detail-comment {
  padding: 12px 14px;
  border-left: 2px solid var(--editorial-rule);
}

.detail-comment p {
  margin: 0;
  color: var(--text-2);
  line-height: 1.7;
}

.profile-sheet-author {
  margin-bottom: 8px;
}

.profile-sheet-stat {
  min-width: 100px;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--editorial-rule);
  background: color-mix(in srgb, var(--surface) 96%, var(--editorial-paper-2) 4%);
}

.profile-sheet-stat strong {
  font-family: var(--font-display);
  font-size: 24px;
}

.profile-sheet-stat span {
  font-size: 12px;
  color: var(--text-3);
}

@media (max-width: 1120px) {
  .preview-intro,
  .homepage-stage-grid,
  .support-grid {
    grid-template-columns: 1fr;
  }
}
</style>
